package camera.mavrolume.app.camera

import android.graphics.SurfaceTexture
import android.opengl.GLES11Ext
import android.opengl.GLES31
import android.os.Handler
import android.util.Log
import android.view.Surface
import androidx.camera.core.CameraEffect
import androidx.camera.core.SurfaceOutput
import androidx.camera.core.SurfaceProcessor
import androidx.camera.core.SurfaceRequest
import camera.mavrolume.app.filmsim.FilmSimulation
import camera.mavrolume.app.filmsim.LutLoader
import camera.mavrolume.app.filmsim.FilmSettings
import camera.mavrolume.app.filmsim.MavrolumeShader
import java.util.concurrent.Executors

/**
 * CameraEffect + SurfaceProcessor that applies an analytical film simulation
 * and optional LoG focus peaking to the camera preview in real-time via OpenGL shaders.
 *
 * Two-pass pipeline per frame:
 *  Pass 1: Camera OES texture → SDR normalization shader → intermediate FBO texture
 *  Pass 2: Intermediate texture → LoG peaking fragment shader → output Surface
 *
 * Why two passes: LoG needs 13 texture samples from a standard sampler2D (fast).
 * Sampling an OES texture 13 times per fragment is slow and driver-dependent.
 */
class LutPreviewEffect(
    private val lutLoader: LutLoader,
    initialSim: FilmSimulation,
    onError: (Throwable) -> Unit = {},
) : CameraEffect(
    PREVIEW,
    EFFECT_EXECUTOR,
    LutSurfaceProcessor(lutLoader, initialSim),
    { throwable -> Log.e("LutPreviewEffect", "Effect error", throwable); onError(throwable) },
) {
    private val processor get() = surfaceProcessor as LutSurfaceProcessor

    /** Switch the LUT applied to preview -- thread-safe, atomic. */
    fun switchLut(sim: FilmSimulation) {
        processor.pendingSim = sim
    }

    /** Enable or disable GPU focus peaking overlay. */
    fun setPeakingEnabled(enabled: Boolean) {
        processor.peakingEnabled = enabled
    }

    /** Set peaking overlay color as [r, g, b] floats (0..1). */
    fun setPeakingColor(color: FloatArray) {
        processor.peakingColor = color
    }


    fun setFilmSettings(settings: FilmSettings) { processor.settings = settings }

    /** Release GL/EGL resources. Call when the effect is no longer needed. */
    fun release() {
        processor.release()
    }

    companion object {
        private val EFFECT_EXECUTOR = Executors.newSingleThreadExecutor { r ->
            Thread(r, "LutEffect-executor").apply { isDaemon = true }
        }
    }
}

/**
 * SurfaceProcessor that receives camera frames and renders them with a LUT shader,
 * then optionally applies LoG focus peaking in a second pass.
 */
internal class LutSurfaceProcessor(
    private val lutLoader: LutLoader,
    initialSim: FilmSimulation,
) : SurfaceProcessor {

    @Volatile
    var pendingSim: FilmSimulation = initialSim

    @Volatile
    var peakingEnabled: Boolean = false

    @Volatile
    var peakingColor: FloatArray = floatArrayOf(0.659f, 0.333f, 0.969f) // Purple

    @Volatile var settings = FilmSettings()
    @Volatile private var released = false
    private var outputDescriptor: SurfaceOutput? = null
    private var inputReturned = true
    private var outputReturned = true
    private var cleaned = false
    private val originalTransform = FloatArray(16)

    private val glContext = GlContext()

    private val callbackExecutor = Executors.newSingleThreadExecutor { r ->
        Thread(r, "LutProcessor-callback").apply { isDaemon = true }
    }


    // Pass 1: LUT shader
    private var lutProgram = 0
    private var oesTextureId = 0
    private var vao = 0
    private var vbo = 0
    private var uCameraLoc = -1
    private var uTransformLoc = -1

    // Pass 2: grade, shared photographic grain, and focus peaking
    private var peakingProgram = 0
    private var uPeakTexLoc = -1
    private var uPeakTexelSizeLoc = -1
    private var uPeakEnabledLoc = -1
    private var uPeakColorLoc = -1
    private var uPeakThresholdLoc = -1

    // Intermediate FBO for two-pass rendering
    private var fboId = 0
    private var fboTextureId = 0
    private var fboWidth = 0
    private var fboHeight = 0

    // Surface I/O
    private var inputSurfaceTexture: SurfaceTexture? = null
    private var inputSurface: Surface? = null
    private var outputSurface: android.opengl.EGLSurface? = null
    private var outputWidth = 0
    private var outputHeight = 0

    private val transformMatrix = FloatArray(16)

    companion object {
        private const val TAG = "LutSurfaceProcessor"

        // -- Vertex shader: fullscreen quad with SurfaceTexture transform --
        private const val VERTEX_SHADER = """
#version 310 es
layout(location = 0) in vec2 aPosition;
layout(location = 1) in vec2 aTexCoord;
uniform mat4 uTransform;
out vec2 vTexCoord;
void main() {
    gl_Position = vec4(aPosition, 0.0, 1.0);
    vTexCoord = (uTransform * vec4(aTexCoord, 0.0, 1.0)).xy;
}
"""

        // -- Fragment shader (Pass 1): Direct camera input normalization --
        private const val LUT_FRAGMENT_SHADER = """
#version 310 es
#extension GL_OES_EGL_image_external_essl3 : require
precision highp float;

uniform highp samplerExternalOES uCamera;
in vec2 vTexCoord;
out vec4 fragColor;

void main() {
    // OES/YUV conversion can produce small excursions outside SDR [0,1].
    // An identity Hald lookup is unnecessary and lets these values cross atlas tiles.
    // Preserve channel order; normalize at the input boundary before grading.
    fragColor = vec4(clamp(texture(uCamera, vTexCoord).rgb, 0.0, 1.0), 1.0);
}
"""

        // -- Vertex shader (Pass 2): simple passthrough --
        private const val PASSTHROUGH_VERTEX_SHADER = """
#version 310 es
layout(location = 0) in vec2 aPosition;
layout(location = 1) in vec2 aTexCoord;
out vec2 vTexCoord;
void main() {
    gl_Position = vec4(aPosition, 0.0, 1.0);
    vTexCoord = aTexCoord;
}
"""

        // -- Fragment shader (Pass 2): grade, grain, and focus peaking --
        private val PEAKING_FRAGMENT_SHADER = """
#version 310 es
precision highp float;

uniform highp sampler2D uTexture;
uniform vec2 uTexelSize;
uniform bool uPeakingEnabled;
uniform vec3 uPeakingColor;
uniform float uThreshold;
in vec2 vTexCoord;
out vec4 fragColor;

#define SAMPLE(uv) texture(uTexture, uv)
""" + MavrolumeShader.body + """

float luminance(vec3 c) {
    return 0.2126 * c.r + 0.7152 * c.g + 0.0722 * c.b;
}

void main() {
    vec3 color = mavrolumeRender(vTexCoord);

    if (uPeakingEnabled) {
        // 5x5 LoG kernel (sigma ~1.0), 13 non-zero entries
        //   0  0 -1  0  0
        //   0 -1 -2 -1  0
        //  -1 -2 16 -2 -1
        //   0 -1 -2 -1  0
        //   0  0 -1  0  0
        float center = luminance(texture(uTexture, vTexCoord).rgb);
        float response =
            center * 16.0
            - luminance(texture(uTexture, vTexCoord + vec2(-2.0, 0.0) * uTexelSize).rgb)
            - luminance(texture(uTexture, vTexCoord + vec2( 2.0, 0.0) * uTexelSize).rgb)
            - luminance(texture(uTexture, vTexCoord + vec2( 0.0,-2.0) * uTexelSize).rgb)
            - luminance(texture(uTexture, vTexCoord + vec2( 0.0, 2.0) * uTexelSize).rgb)
            - luminance(texture(uTexture, vTexCoord + vec2(-1.0,-1.0) * uTexelSize).rgb)
            - luminance(texture(uTexture, vTexCoord + vec2( 1.0,-1.0) * uTexelSize).rgb)
            - luminance(texture(uTexture, vTexCoord + vec2(-1.0, 1.0) * uTexelSize).rgb)
            - luminance(texture(uTexture, vTexCoord + vec2( 1.0, 1.0) * uTexelSize).rgb)
            - luminance(texture(uTexture, vTexCoord + vec2( 0.0,-1.0) * uTexelSize).rgb) * 2.0
            - luminance(texture(uTexture, vTexCoord + vec2( 0.0, 1.0) * uTexelSize).rgb) * 2.0
            - luminance(texture(uTexture, vTexCoord + vec2(-1.0, 0.0) * uTexelSize).rgb) * 2.0
            - luminance(texture(uTexture, vTexCoord + vec2( 1.0, 0.0) * uTexelSize).rgb) * 2.0;

        float absResponse = abs(response);
        if (absResponse > uThreshold) {
            float strength = clamp((absResponse - uThreshold) / (uThreshold * 2.0), 0.0, 1.0);
            color = mix(color, uPeakingColor, clamp(strength + 0.35, 0.0, 0.9));
        }
    }

    fragColor = vec4(color, 1.0);
}
"""

        // Fullscreen quad: positions (xy) + texcoords (uv)
        private val QUAD_VERTICES = floatArrayOf(
            // x,    y,    u,    v
            -1f, -1f,  0f, 0f,
             1f, -1f,  1f, 0f,
            -1f,  1f,  0f, 1f,
             1f,  1f,  1f, 1f,
        )
    }

    override fun onInputSurface(request: SurfaceRequest) {
        if (released) { request.willNotProvideSurface(); return }
        val size = request.resolution
        Log.d(TAG, "onInputSurface: ${size.width}x${size.height}")

        glContext.runOnGlThread {
            inputReturned = false
            glContext.init()
            initGlResources()

            inputSurfaceTexture = SurfaceTexture(oesTextureId).apply {
                setDefaultBufferSize(size.width, size.height)
            }
            inputSurface = Surface(inputSurfaceTexture)

            inputSurfaceTexture?.setOnFrameAvailableListener({ st ->
                renderFrame(st)
            }, Handler(android.os.Looper.getMainLooper()))
        }

        request.provideSurface(inputSurface!!, callbackExecutor) { result ->
            Log.d(TAG, "Input surface result: ${result.resultCode}")
            glContext.runOnGlThread {
                releaseInputSurface()
                inputReturned = true
                cleanupIfReleased()
            }
        }
    }

    override fun onOutputSurface(surfaceOutput: SurfaceOutput) {
        if (released) { surfaceOutput.close(); return }
        val size = surfaceOutput.size
        Log.d(TAG, "onOutputSurface: ${size.width}x${size.height}")

        val surface = surfaceOutput.getSurface(callbackExecutor) {
            Log.d(TAG, "Output surface close requested")
            glContext.runOnGlThread {
                glContext.makeCurrent()
                outputSurface?.let { glContext.destroySurface(it) }
                outputSurface = null
                outputDescriptor = null
                outputReturned = true
                cleanupIfReleased()
            }
            surfaceOutput.close()
        }

        glContext.runOnGlThread {
            glContext.init()
            outputReturned = false
            outputDescriptor = surfaceOutput
            outputSurface = glContext.createWindowSurface(surface)
            outputWidth = size.width
            outputHeight = size.height
            // Create/resize intermediate FBO to match output
            ensureFbo(outputWidth, outputHeight)
        }
    }

    private fun renderFrame(surfaceTexture: SurfaceTexture) {
        if (released) return
        glContext.postOnGlThread {
            if (released) return@postOnGlThread
            val outSurface = outputSurface ?: return@postOnGlThread

            try {
                surfaceTexture.updateTexImage()
                surfaceTexture.getTransformMatrix(originalTransform)
                outputDescriptor?.updateTransformMatrix(transformMatrix, originalTransform)

                // ── Pass 1: Camera OES → clamped SDR → FBO ──────────────────
                GLES31.glBindFramebuffer(GLES31.GL_FRAMEBUFFER, fboId)
                GLES31.glViewport(0, 0, fboWidth, fboHeight)
                GLES31.glClear(GLES31.GL_COLOR_BUFFER_BIT)

                GLES31.glUseProgram(lutProgram)

                GLES31.glActiveTexture(GLES31.GL_TEXTURE0)
                GLES31.glBindTexture(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, oesTextureId)
                GLES31.glUniform1i(uCameraLoc, 0)

                GLES31.glUniformMatrix4fv(uTransformLoc, 1, false, transformMatrix, 0)

                GLES31.glBindVertexArray(vao)
                GLES31.glDrawArrays(GLES31.GL_TRIANGLE_STRIP, 0, 4)
                GLES31.glBindVertexArray(0)

                // ── Pass 2: FBO texture → Peaking → output Surface ──
                GLES31.glBindFramebuffer(GLES31.GL_FRAMEBUFFER, 0)
                glContext.makeCurrent(outSurface)
                GLES31.glViewport(0, 0, outputWidth, outputHeight)
                GLES31.glClear(GLES31.GL_COLOR_BUFFER_BIT)

                GLES31.glUseProgram(peakingProgram)

                GLES31.glActiveTexture(GLES31.GL_TEXTURE0)
                GLES31.glBindTexture(GLES31.GL_TEXTURE_2D, fboTextureId)
                GLES31.glUniform1i(uPeakTexLoc, 0)

                GLES31.glUniform2f(
                    uPeakTexelSizeLoc,
                    1f / fboWidth.toFloat(),
                    1f / fboHeight.toFloat(),
                )
                GLES31.glUniform1i(uPeakEnabledLoc, if (peakingEnabled) 1 else 0)
                GLES31.glUniform3fv(uPeakColorLoc, 1, peakingColor, 0)
                GLES31.glUniform1f(uPeakThresholdLoc, 0.06f)

                settings.uniforms(pendingSim).forEach { (name, value) ->
                    GLES31.glUniform1f(GLES31.glGetUniformLocation(peakingProgram, name), value)
                }
                GLES31.glUniform2f(GLES31.glGetUniformLocation(peakingProgram, "uResolution"), outputWidth.toFloat(), outputHeight.toFloat())
                GLES31.glUniform1f(GLES31.glGetUniformLocation(peakingProgram, "uSeed"), 17f)

                GLES31.glBindVertexArray(vao)
                GLES31.glDrawArrays(GLES31.GL_TRIANGLE_STRIP, 0, 4)
                GLES31.glBindVertexArray(0)

                glContext.swapBuffers(outSurface)
            } catch (e: Exception) {
                Log.w(TAG, "Render frame failed", e)
            }
        }
    }

    private fun initGlResources() {
        // ── Pass 1 program: LUT shader ──
        val lutVs = glContext.compileShader(GLES31.GL_VERTEX_SHADER, VERTEX_SHADER)
        val lutFs = glContext.compileShader(GLES31.GL_FRAGMENT_SHADER, LUT_FRAGMENT_SHADER)
        lutProgram = glContext.linkProgram(lutVs, lutFs)
        GLES31.glDeleteShader(lutVs)
        GLES31.glDeleteShader(lutFs)

        uCameraLoc = GLES31.glGetUniformLocation(lutProgram, "uCamera")
        uTransformLoc = GLES31.glGetUniformLocation(lutProgram, "uTransform")

        // ── Pass 2 program: Peaking shader ──
        val peakVs = glContext.compileShader(GLES31.GL_VERTEX_SHADER, PASSTHROUGH_VERTEX_SHADER)
        val peakFs = glContext.compileShader(GLES31.GL_FRAGMENT_SHADER, PEAKING_FRAGMENT_SHADER)
        peakingProgram = glContext.linkProgram(peakVs, peakFs)
        GLES31.glDeleteShader(peakVs)
        GLES31.glDeleteShader(peakFs)

        uPeakTexLoc = GLES31.glGetUniformLocation(peakingProgram, "uTexture")
        uPeakTexelSizeLoc = GLES31.glGetUniformLocation(peakingProgram, "uTexelSize")
        uPeakEnabledLoc = GLES31.glGetUniformLocation(peakingProgram, "uPeakingEnabled")
        uPeakColorLoc = GLES31.glGetUniformLocation(peakingProgram, "uPeakingColor")
        uPeakThresholdLoc = GLES31.glGetUniformLocation(peakingProgram, "uThreshold")

        // Create OES texture for camera input
        oesTextureId = glContext.createOesTexture()

        // Create shared VAO/VBO for fullscreen quad (used by both passes)
        val vaos = IntArray(1)
        GLES31.glGenVertexArrays(1, vaos, 0)
        vao = vaos[0]
        GLES31.glBindVertexArray(vao)

        val vbos = IntArray(1)
        GLES31.glGenBuffers(1, vbos, 0)
        vbo = vbos[0]
        GLES31.glBindBuffer(GLES31.GL_ARRAY_BUFFER, vbo)

        val buffer = java.nio.ByteBuffer.allocateDirect(QUAD_VERTICES.size * 4)
            .order(java.nio.ByteOrder.nativeOrder())
            .asFloatBuffer()
            .put(QUAD_VERTICES)
            .position(0)
        GLES31.glBufferData(
            GLES31.GL_ARRAY_BUFFER,
            QUAD_VERTICES.size * 4,
            buffer,
            GLES31.GL_STATIC_DRAW,
        )

        // Position attribute (location 0): 2 floats, stride 16 bytes, offset 0
        GLES31.glEnableVertexAttribArray(0)
        GLES31.glVertexAttribPointer(0, 2, GLES31.GL_FLOAT, false, 16, 0)

        // TexCoord attribute (location 1): 2 floats, stride 16 bytes, offset 8 bytes
        GLES31.glEnableVertexAttribArray(1)
        GLES31.glVertexAttribPointer(1, 2, GLES31.GL_FLOAT, false, 16, 8)

        GLES31.glBindVertexArray(0)
    }

    /** Create or resize the intermediate FBO + texture for two-pass rendering. */
    private fun ensureFbo(width: Int, height: Int) {
        if (fboWidth == width && fboHeight == height && fboId != 0) return

        // Clean up old FBO
        if (fboId != 0) {
            GLES31.glDeleteFramebuffers(1, intArrayOf(fboId), 0)
            GLES31.glDeleteTextures(1, intArrayOf(fboTextureId), 0)
        }

        // Create texture for FBO color attachment
        val textures = IntArray(1)
        GLES31.glGenTextures(1, textures, 0)
        fboTextureId = textures[0]
        GLES31.glBindTexture(GLES31.GL_TEXTURE_2D, fboTextureId)
        GLES31.glTexImage2D(
            GLES31.GL_TEXTURE_2D, 0, GLES31.GL_RGBA8,
            width, height, 0,
            GLES31.GL_RGBA, GLES31.GL_UNSIGNED_BYTE, null,
        )
        GLES31.glTexParameteri(GLES31.GL_TEXTURE_2D, GLES31.GL_TEXTURE_MIN_FILTER, GLES31.GL_LINEAR)
        GLES31.glTexParameteri(GLES31.GL_TEXTURE_2D, GLES31.GL_TEXTURE_MAG_FILTER, GLES31.GL_LINEAR)
        GLES31.glTexParameteri(GLES31.GL_TEXTURE_2D, GLES31.GL_TEXTURE_WRAP_S, GLES31.GL_CLAMP_TO_EDGE)
        GLES31.glTexParameteri(GLES31.GL_TEXTURE_2D, GLES31.GL_TEXTURE_WRAP_T, GLES31.GL_CLAMP_TO_EDGE)

        // Create FBO
        val fbos = IntArray(1)
        GLES31.glGenFramebuffers(1, fbos, 0)
        fboId = fbos[0]
        GLES31.glBindFramebuffer(GLES31.GL_FRAMEBUFFER, fboId)
        GLES31.glFramebufferTexture2D(
            GLES31.GL_FRAMEBUFFER, GLES31.GL_COLOR_ATTACHMENT0,
            GLES31.GL_TEXTURE_2D, fboTextureId, 0,
        )

        val status = GLES31.glCheckFramebufferStatus(GLES31.GL_FRAMEBUFFER)
        if (status != GLES31.GL_FRAMEBUFFER_COMPLETE) {
            Log.e(TAG, "FBO incomplete: $status")
        }

        GLES31.glBindFramebuffer(GLES31.GL_FRAMEBUFFER, 0)
        fboWidth = width
        fboHeight = height
        Log.d(TAG, "FBO created: ${width}x${height}")
    }

    private fun releaseInputSurface() {
        inputSurface?.release()
        inputSurface = null
        inputSurfaceTexture?.release()
        inputSurfaceTexture = null
    }

    fun release() {
        if (released) return
        released = true
        inputSurfaceTexture?.setOnFrameAvailableListener(null)
        glContext.runOnGlThread { cleanupIfReleased() }
    }

    // Surface owners must return both surfaces before textures/context can be destroyed.
    private fun cleanupIfReleased() {
        if (!released || !inputReturned || !outputReturned || cleaned) return
        cleaned = true
        if (lutProgram != 0) GLES31.glDeleteProgram(lutProgram)
        if (peakingProgram != 0) GLES31.glDeleteProgram(peakingProgram)
        if (oesTextureId != 0) GLES31.glDeleteTextures(1, intArrayOf(oesTextureId), 0)
        if (fboId != 0) GLES31.glDeleteFramebuffers(1, intArrayOf(fboId), 0)
        if (fboTextureId != 0) GLES31.glDeleteTextures(1, intArrayOf(fboTextureId), 0)
        if (vao != 0) GLES31.glDeleteVertexArrays(1, intArrayOf(vao), 0)
        if (vbo != 0) GLES31.glDeleteBuffers(1, intArrayOf(vbo), 0)
        glContext.release()
        callbackExecutor.shutdown()
    }
}
