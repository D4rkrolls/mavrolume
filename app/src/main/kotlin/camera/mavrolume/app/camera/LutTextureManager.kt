package camera.mavrolume.app.camera

import android.graphics.Bitmap
import android.opengl.GLES31
import android.opengl.GLUtils
import android.util.Log
import camera.mavrolume.app.filmsim.FilmSimulation
import camera.mavrolume.app.filmsim.LutLoader

/**
 * Uploads Hald CLUT bitmaps as GL textures and caches them per FilmSimulation.
 * Must only be called from the GL thread.
 */
class LutTextureManager(
    private val lutLoader: LutLoader,
) {
    private val textureCache = mutableMapOf<FilmSimulation, Int>()
    private var currentSim: FilmSimulation? = null
    var currentTextureId: Int = 0
        private set

    companion object {
        private const val TAG = "LutTextureManager"
    }

    /**
     * Switch to the given simulation's LUT texture. Returns the GL texture handle.
     * Creates and uploads the texture if not cached.
     * Must be called on GL thread.
     */
    fun switchLut(sim: FilmSimulation): Int {
        textureCache[sim]?.let { texId ->
            currentSim = sim
            currentTextureId = texId
            return texId
        }

        // Load bitmap and upload as GL texture
        val bitmap = lutLoader.getLutBitmap(sim)
        val texId = uploadBitmapAsTexture(bitmap)

        textureCache[sim] = texId
        currentSim = sim
        currentTextureId = texId
        Log.d(TAG, "Uploaded LUT texture for ${sim.shortCode}: $texId (${bitmap.width}x${bitmap.height})")
        return texId
    }

    private fun uploadBitmapAsTexture(bitmap: Bitmap): Int {
        val textures = IntArray(1)
        GLES31.glGenTextures(1, textures, 0)
        val texId = textures[0]

        GLES31.glBindTexture(GLES31.GL_TEXTURE_2D, texId)
        GLES31.glTexParameteri(GLES31.GL_TEXTURE_2D, GLES31.GL_TEXTURE_MIN_FILTER, GLES31.GL_LINEAR)
        GLES31.glTexParameteri(GLES31.GL_TEXTURE_2D, GLES31.GL_TEXTURE_MAG_FILTER, GLES31.GL_LINEAR)
        GLES31.glTexParameteri(GLES31.GL_TEXTURE_2D, GLES31.GL_TEXTURE_WRAP_S, GLES31.GL_CLAMP_TO_EDGE)
        GLES31.glTexParameteri(GLES31.GL_TEXTURE_2D, GLES31.GL_TEXTURE_WRAP_T, GLES31.GL_CLAMP_TO_EDGE)
        GLUtils.texImage2D(GLES31.GL_TEXTURE_2D, 0, bitmap, 0)

        return texId
    }

    fun release() {
        val ids = textureCache.values.toIntArray()
        if (ids.isNotEmpty()) {
            GLES31.glDeleteTextures(ids.size, ids, 0)
        }
        textureCache.clear()
        currentSim = null
        currentTextureId = 0
    }
}
