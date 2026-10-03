package camera.mavrolume.app.filmsim

import android.graphics.Bitmap
import javax.inject.Inject
import javax.inject.Singleton

/** Identity bridge for the inherited LUT transport. Profiles are now analytical GLSL. */
@Singleton
class LutLoader @Inject constructor() {
    private val identity by lazy {
        val pixels = IntArray(512*512)
        for (b in 0..63) for (g in 0..63) for (r in 0..63) {
            val x = (b%8)*64+r
            val y = (b/8)*64+g
            pixels[y*512+x] = (255 shl 24) or ((r*255/63) shl 16) or ((g*255/63) shl 8) or (b*255/63)
        }
        Bitmap.createBitmap(pixels,512,512,Bitmap.Config.ARGB_8888)
    }
    fun load(sim: FilmSimulation): Bitmap = identity
    fun getLutBitmap(sim: FilmSimulation): Bitmap = load(sim)
    fun preloadAll() { identity }
}
