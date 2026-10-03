package camera.mavrolume.app.filmsim

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Rect
import android.opengl.GLES30 as GL
import android.opengl.GLUtils
import camera.mavrolume.app.camera.GlContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.nio.ByteBuffer
import java.nio.ByteOrder
import javax.inject.Inject
import javax.inject.Singleton

/** Native GLES export, with no bundled JNI library or page-size compatibility dependency. */
@Singleton
class ImageProcessor @Inject constructor() {
    private val mutex = Mutex()
    private val gl = GlContext()
    suspend fun applySimulation(source: Bitmap,sim: FilmSimulation,settings: FilmSettings = FilmSettings()): Bitmap {
        // Overlapped tiles retain full capture resolution without full-size GPU textures.
        val output = Bitmap.createBitmap(source.width,source.height,Bitmap.Config.ARGB_8888)
        val canvas = Canvas(output)
        val pad = kotlin.math.ceil(minOf(source.width,source.height)*0.009).toInt()+2
        try {
            for(y in 0 until source.height step 1024) for(x in 0 until source.width step 1024) {
                val w = minOf(1024,source.width-x); val h = minOf(1024,source.height-y)
                val left = maxOf(0,x-pad); val top = maxOf(0,y-pad)
                val right = minOf(source.width,x+w+pad); val bottom = minOf(source.height,y+h+pad)
                val tile = Bitmap.createBitmap(source,left,top,right-left,bottom-top)
                try {
                    val rendered = renderTile(tile,sim,settings,source.width,source.height,left,top)
                    try { canvas.drawBitmap(rendered,Rect(x-left,y-top,x-left+w,y-top+h),Rect(x,y,x+w,y+h),null) }
                    finally { rendered.recycle() }
                } finally { if(tile !== source) tile.recycle() }
            }
            return output
        } catch(e: Throwable) { output.recycle(); throw e }
    }
    private suspend fun renderTile(source: Bitmap,sim: FilmSimulation,settings: FilmSettings,fullWidth: Int,fullHeight: Int,left: Int,top: Int): Bitmap = mutex.withLock {
        withContext(Dispatchers.Default) { gl.runOnGlThread {
            gl.init()
            val limit = IntArray(1)
            GL.glGetIntegerv(GL.GL_MAX_TEXTURE_SIZE,limit,0)
            check(maxOf(source.width,source.height) <= limit[0]) { "GPU tile exceeds texture limit" }
            val input = source
            val width = input.width
            val height = input.height
            val textures = IntArray(2)
            val fbo = IntArray(1)
            val vao = IntArray(1)
            var program = 0
            try {
                val vs = gl.compileShader(GL.GL_VERTEX_SHADER,"""
#version 310 es
out vec2 uv;
void main() {
    vec2 p = vec2(float((gl_VertexID << 1) & 2), float(gl_VertexID & 2));
    uv = p;
    gl_Position = vec4(p*2.0-1.0,0.0,1.0);
}
""".trimIndent())
                val fs = gl.compileShader(GL.GL_FRAGMENT_SHADER,"""
#version 310 es
precision highp float;
in vec2 uv;
out vec4 fragColor;
uniform sampler2D uTexture;
uniform vec2 uFullSize, uTileOrigin, uTileSize;
#define SAMPLE(p) texture(uTexture, ((p)*uFullSize-uTileOrigin)/uTileSize)
""".trimIndent()+"\n"+MavrolumeShader.body+"\nvoid main() { fragColor = vec4(mavrolumeRender((uTileOrigin+uv*uTileSize)/uFullSize),1.0); }")
                program = gl.linkProgram(vs,fs)
                GL.glDeleteShader(vs); GL.glDeleteShader(fs)
                GL.glGenTextures(2,textures,0)
                textures.forEach { texture ->
                    GL.glBindTexture(GL.GL_TEXTURE_2D,texture)
                    GL.glTexParameteri(GL.GL_TEXTURE_2D,GL.GL_TEXTURE_MIN_FILTER,GL.GL_LINEAR)
                    GL.glTexParameteri(GL.GL_TEXTURE_2D,GL.GL_TEXTURE_MAG_FILTER,GL.GL_LINEAR)
                    GL.glTexParameteri(GL.GL_TEXTURE_2D,GL.GL_TEXTURE_WRAP_S,GL.GL_CLAMP_TO_EDGE)
                    GL.glTexParameteri(GL.GL_TEXTURE_2D,GL.GL_TEXTURE_WRAP_T,GL.GL_CLAMP_TO_EDGE)
                }
                GL.glBindTexture(GL.GL_TEXTURE_2D,textures[0])
                GLUtils.texImage2D(GL.GL_TEXTURE_2D,0,input,0)
                GL.glBindTexture(GL.GL_TEXTURE_2D,textures[1])
                GL.glTexImage2D(GL.GL_TEXTURE_2D,0,GL.GL_RGBA8,width,height,0,GL.GL_RGBA,GL.GL_UNSIGNED_BYTE,null)
                GL.glGenFramebuffers(1,fbo,0)
                GL.glBindFramebuffer(GL.GL_FRAMEBUFFER,fbo[0])
                GL.glFramebufferTexture2D(GL.GL_FRAMEBUFFER,GL.GL_COLOR_ATTACHMENT0,GL.GL_TEXTURE_2D,textures[1],0)
                check(GL.glCheckFramebufferStatus(GL.GL_FRAMEBUFFER) == GL.GL_FRAMEBUFFER_COMPLETE) { "Export framebuffer unavailable" }
                GL.glViewport(0,0,width,height)
                GL.glUseProgram(program)
                GL.glActiveTexture(GL.GL_TEXTURE0)
                GL.glBindTexture(GL.GL_TEXTURE_2D,textures[0])
                GL.glUniform1i(GL.glGetUniformLocation(program,"uTexture"),0)
                settings.uniforms(sim).forEach { (name,value) -> GL.glUniform1f(GL.glGetUniformLocation(program,name),value) }
                GL.glUniform2f(GL.glGetUniformLocation(program,"uResolution"),fullWidth.toFloat(),fullHeight.toFloat())
                GL.glUniform2f(GL.glGetUniformLocation(program,"uFullSize"),fullWidth.toFloat(),fullHeight.toFloat())
                GL.glUniform2f(GL.glGetUniformLocation(program,"uTileOrigin"),left.toFloat(),top.toFloat())
                GL.glUniform2f(GL.glGetUniformLocation(program,"uTileSize"),width.toFloat(),height.toFloat())
                GL.glUniform1f(GL.glGetUniformLocation(program,"uSeed"),17f)
                GL.glGenVertexArrays(1,vao,0)
                GL.glBindVertexArray(vao[0])
                GL.glDrawArrays(GL.GL_TRIANGLES,0,3)
                val buffer = ByteBuffer.allocateDirect(width*height*4).order(ByteOrder.nativeOrder())
                GL.glReadPixels(0,0,width,height,GL.GL_RGBA,GL.GL_UNSIGNED_BYTE,buffer)
                check(GL.glGetError() == GL.GL_NO_ERROR) { "GPU export failed" }
                buffer.rewind()
                Bitmap.createBitmap(width,height,Bitmap.Config.ARGB_8888).also { it.copyPixelsFromBuffer(buffer) }
            } finally {
                GL.glBindFramebuffer(GL.GL_FRAMEBUFFER,0)
                GL.glDeleteFramebuffers(1,fbo,0)
                GL.glDeleteTextures(2,textures,0)
                GL.glDeleteVertexArrays(1,vao,0)
                if(program != 0) GL.glDeleteProgram(program)
                if(input !== source) input.recycle()
            }
        } }
    }
}
