package camera.mavrolume.app.crop

import android.graphics.Bitmap

object CropEngine {

    /**
     * Center-crops [source] to the given [targetRatio] (width / height).
     * Returns a new Bitmap if cropping is needed, or [source] itself if it already matches.
     */
    fun cropToRatio(source: Bitmap, targetRatio: Float): Bitmap {
        val srcRatio = source.width.toFloat() / source.height
        return if (targetRatio > srcRatio) {
            // Target is wider than source — crop top/bottom
            val cropHeight = (source.width / targetRatio).toInt()
            val yOffset = (source.height - cropHeight) / 2
            Bitmap.createBitmap(source, 0, yOffset, source.width, cropHeight)
        } else {
            // Target is taller/equal — crop left/right
            val cropWidth = (source.height * targetRatio).toInt()
            val xOffset = (source.width - cropWidth) / 2
            Bitmap.createBitmap(source, xOffset, 0, cropWidth, source.height)
        }
    }
}
