package camera.mavrolume.app.camera

import android.opengl.EGL14
import android.opengl.EGLConfig
import android.opengl.EGLContext
import android.opengl.EGLDisplay
import android.opengl.EGLSurface
import android.opengl.GLES20
import android.opengl.GLES31
import android.util.Log
import android.view.Surface
import java.util.concurrent.Executors
import java.util.concurrent.Future

/**
 * Manages an EGL display/context/config for OpenGL ES 3.1 rendering.
 * All GL calls must happen on the dedicated GL thread via [runOnGlThread].
 */
class GlContext {

    private val executor = Executors.newSingleThreadExecutor { r ->
        Thread(r, "GlContext-thread").apply { isDaemon = true }
    }

    private var eglDisplay: EGLDisplay = EGL14.EGL_NO_DISPLAY
    private var eglContext: EGLContext = EGL14.EGL_NO_CONTEXT
    private var eglConfig: EGLConfig? = null
    private var eglSurface: EGLSurface = EGL14.EGL_NO_SURFACE

    companion object {
        private const val TAG = "GlContext"
    }

    /** Initialize EGL display and context. Must be called before any GL operations. */
    fun init() {
        if (eglDisplay != EGL14.EGL_NO_DISPLAY) { makeCurrent(); return }
        eglDisplay = EGL14.eglGetDisplay(EGL14.EGL_DEFAULT_DISPLAY)
        check(eglDisplay != EGL14.EGL_NO_DISPLAY) { "Unable to get EGL display" }

        val version = IntArray(2)
        check(EGL14.eglInitialize(eglDisplay, version, 0, version, 1)) {
            "Unable to initialize EGL"
        }

        val configAttribs = intArrayOf(
            EGL14.EGL_RED_SIZE, 8,
            EGL14.EGL_GREEN_SIZE, 8,
            EGL14.EGL_BLUE_SIZE, 8,
            EGL14.EGL_ALPHA_SIZE, 8,
            EGL14.EGL_RENDERABLE_TYPE, 0x00000040, // EGL_OPENGL_ES3_BIT_KHR
            EGL14.EGL_SURFACE_TYPE, EGL14.EGL_PBUFFER_BIT or EGL14.EGL_WINDOW_BIT,
            EGL14.EGL_NONE,
        )
        val configs = arrayOfNulls<EGLConfig>(1)
        val numConfigs = IntArray(1)
        check(
            EGL14.eglChooseConfig(eglDisplay, configAttribs, 0, configs, 0, 1, numConfigs, 0)
        ) { "Unable to choose EGL config" }
        eglConfig = configs[0] ?: error("No EGL config found")

        val contextAttribs = intArrayOf(
            EGL14.EGL_CONTEXT_CLIENT_VERSION, 3,
            EGL14.EGL_NONE,
        )
        eglContext = EGL14.eglCreateContext(
            eglDisplay, eglConfig, EGL14.EGL_NO_CONTEXT, contextAttribs, 0,
        )
        check(eglContext != EGL14.EGL_NO_CONTEXT) { "Unable to create EGL context" }

        // Create a 1x1 pbuffer surface as default (for offscreen ops)
        val pbufferAttribs = intArrayOf(
            EGL14.EGL_WIDTH, 1,
            EGL14.EGL_HEIGHT, 1,
            EGL14.EGL_NONE,
        )
        eglSurface = EGL14.eglCreatePbufferSurface(eglDisplay, eglConfig, pbufferAttribs, 0)

        makeCurrent()
    }

    /** Create a window surface for the given Android Surface. */
    fun createWindowSurface(surface: Surface): EGLSurface {
        val attribs = intArrayOf(EGL14.EGL_NONE)
        return EGL14.eglCreateWindowSurface(eglDisplay, eglConfig, surface, attribs, 0)
            ?: error("Failed to create window surface")
    }

    /** Make the given surface (or default pbuffer) current on this thread. */
    fun makeCurrent(surface: EGLSurface = eglSurface) {
        check(EGL14.eglMakeCurrent(eglDisplay, surface, surface, eglContext)) {
            "eglMakeCurrent failed"
        }
    }

    /** Swap buffers on the given surface. */
    fun swapBuffers(surface: EGLSurface): Boolean {
        return EGL14.eglSwapBuffers(eglDisplay, surface)
    }

    /** Destroy an EGL surface. */
    fun destroySurface(surface: EGLSurface) {
        EGL14.eglDestroySurface(eglDisplay, surface)
    }

    // ── Shader helpers ──────────────────────────────────────────────

    fun compileShader(type: Int, source: String): Int {
        val shader = GLES31.glCreateShader(type)
        GLES31.glShaderSource(shader, source)
        GLES31.glCompileShader(shader)
        val status = IntArray(1)
        GLES31.glGetShaderiv(shader, GLES31.GL_COMPILE_STATUS, status, 0)
        if (status[0] == 0) {
            val log = GLES31.glGetShaderInfoLog(shader)
            GLES31.glDeleteShader(shader)
            error("Shader compilation failed: $log")
        }
        return shader
    }

    fun linkProgram(vertexShader: Int, fragmentShader: Int): Int {
        val program = GLES31.glCreateProgram()
        GLES31.glAttachShader(program, vertexShader)
        GLES31.glAttachShader(program, fragmentShader)
        GLES31.glLinkProgram(program)
        val status = IntArray(1)
        GLES31.glGetProgramiv(program, GLES31.GL_LINK_STATUS, status, 0)
        if (status[0] == 0) {
            val log = GLES31.glGetProgramInfoLog(program)
            GLES31.glDeleteProgram(program)
            error("Program linking failed: $log")
        }
        return program
    }

    fun linkComputeProgram(computeShader: Int): Int {
        val program = GLES31.glCreateProgram()
        GLES31.glAttachShader(program, computeShader)
        GLES31.glLinkProgram(program)
        val status = IntArray(1)
        GLES31.glGetProgramiv(program, GLES31.GL_LINK_STATUS, status, 0)
        if (status[0] == 0) {
            val log = GLES31.glGetProgramInfoLog(program)
            GLES31.glDeleteProgram(program)
            error("Compute program linking failed: $log")
        }
        return program
    }

    // ── Texture helpers ─────────────────────────────────────────────

    fun createOesTexture(): Int {
        val textures = IntArray(1)
        GLES31.glGenTextures(1, textures, 0)
        val texId = textures[0]
        GLES31.glBindTexture(0x8D65 /* GL_TEXTURE_EXTERNAL_OES */, texId)
        GLES31.glTexParameteri(0x8D65, GLES31.GL_TEXTURE_MIN_FILTER, GLES31.GL_LINEAR)
        GLES31.glTexParameteri(0x8D65, GLES31.GL_TEXTURE_MAG_FILTER, GLES31.GL_LINEAR)
        GLES31.glTexParameteri(0x8D65, GLES31.GL_TEXTURE_WRAP_S, GLES31.GL_CLAMP_TO_EDGE)
        GLES31.glTexParameteri(0x8D65, GLES31.GL_TEXTURE_WRAP_T, GLES31.GL_CLAMP_TO_EDGE)
        return texId
    }

    fun create2dTexture(): Int {
        val textures = IntArray(1)
        GLES31.glGenTextures(1, textures, 0)
        val texId = textures[0]
        GLES31.glBindTexture(GLES31.GL_TEXTURE_2D, texId)
        GLES31.glTexParameteri(GLES31.GL_TEXTURE_2D, GLES31.GL_TEXTURE_MIN_FILTER, GLES31.GL_LINEAR)
        GLES31.glTexParameteri(GLES31.GL_TEXTURE_2D, GLES31.GL_TEXTURE_MAG_FILTER, GLES31.GL_LINEAR)
        GLES31.glTexParameteri(GLES31.GL_TEXTURE_2D, GLES31.GL_TEXTURE_WRAP_S, GLES31.GL_CLAMP_TO_EDGE)
        GLES31.glTexParameteri(GLES31.GL_TEXTURE_2D, GLES31.GL_TEXTURE_WRAP_T, GLES31.GL_CLAMP_TO_EDGE)
        return texId
    }

    /**
     * Create a texture with specific internal format for compute shader intermediate buffers.
     */
    fun createTexture(width: Int, height: Int, internalFormat: Int): Int {
        val textures = IntArray(1)
        GLES31.glGenTextures(1, textures, 0)
        val texId = textures[0]
        GLES31.glBindTexture(GLES31.GL_TEXTURE_2D, texId)
        GLES31.glTexStorage2D(GLES31.GL_TEXTURE_2D, 1, internalFormat, width, height)
        GLES31.glTexParameteri(GLES31.GL_TEXTURE_2D, GLES31.GL_TEXTURE_MIN_FILTER, GLES31.GL_NEAREST)
        GLES31.glTexParameteri(GLES31.GL_TEXTURE_2D, GLES31.GL_TEXTURE_MAG_FILTER, GLES31.GL_NEAREST)
        return texId
    }

    /**
     * Read pixels from framebuffer into an IntArray (for Bitmap.setPixels).
     */
    fun readPixels(width: Int, height: Int): IntArray {
        val buffer = java.nio.IntBuffer.allocate(width * height)
        GLES31.glReadPixels(0, 0, width, height, GLES31.GL_RGBA, GLES31.GL_UNSIGNED_BYTE, buffer)
        return buffer.array()
    }

    // ── Threading ───────────────────────────────────────────────────

    /** Run a block on the dedicated GL thread. Blocks until completion. */
    fun <T> runOnGlThread(block: () -> T): T {
        val future: Future<T> = executor.submit<T>(block)
        return future.get()
    }

    /** Run a block on the GL thread, non-blocking. */
    fun postOnGlThread(block: () -> Unit) {
        executor.execute(block)
    }

    // ── Cleanup ─────────────────────────────────────────────────────

    fun release() {
        try {
            if (eglDisplay != EGL14.EGL_NO_DISPLAY) {
                EGL14.eglMakeCurrent(
                    eglDisplay, EGL14.EGL_NO_SURFACE, EGL14.EGL_NO_SURFACE, EGL14.EGL_NO_CONTEXT,
                )
                if (eglSurface != EGL14.EGL_NO_SURFACE) {
                    EGL14.eglDestroySurface(eglDisplay, eglSurface)
                }
                if (eglContext != EGL14.EGL_NO_CONTEXT) {
                    EGL14.eglDestroyContext(eglDisplay, eglContext)
                }
                EGL14.eglTerminate(eglDisplay)
            }
        } catch (e: Exception) {
            Log.w(TAG, "Error during EGL cleanup", e)
        }
        eglDisplay = EGL14.EGL_NO_DISPLAY
        eglContext = EGL14.EGL_NO_CONTEXT
        eglSurface = EGL14.EGL_NO_SURFACE
        executor.shutdown()
    }
}
