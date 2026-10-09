package camera.mavrolume.app.export

import android.content.ContentResolver
import android.net.Uri
import android.util.Log
import androidx.exifinterface.media.ExifInterface
import java.io.ByteArrayInputStream

object ExifHelper {

    private const val TAG = "ExifHelper"

    // Tags we want to preserve from the camera's original JPEG
    private val PRESERVE_TAGS = arrayOf(
        ExifInterface.TAG_MAKE,
        ExifInterface.TAG_MODEL,
        ExifInterface.TAG_FOCAL_LENGTH,
        ExifInterface.TAG_F_NUMBER,
        ExifInterface.TAG_EXPOSURE_TIME,
        ExifInterface.TAG_PHOTOGRAPHIC_SENSITIVITY,
        ExifInterface.TAG_WHITE_BALANCE,
        ExifInterface.TAG_FLASH,
        ExifInterface.TAG_DATETIME,
        ExifInterface.TAG_DATETIME_ORIGINAL,
        ExifInterface.TAG_DATETIME_DIGITIZED,
        ExifInterface.TAG_GPS_LATITUDE,
        ExifInterface.TAG_GPS_LATITUDE_REF,
        ExifInterface.TAG_GPS_LONGITUDE,
        ExifInterface.TAG_GPS_LONGITUDE_REF,
        ExifInterface.TAG_GPS_ALTITUDE,
        ExifInterface.TAG_GPS_ALTITUDE_REF,
        ExifInterface.TAG_IMAGE_WIDTH,
        ExifInterface.TAG_IMAGE_LENGTH,
        ExifInterface.TAG_ORIENTATION,
        ExifInterface.TAG_LENS_MAKE,
        ExifInterface.TAG_LENS_MODEL,
    )

    /**
     * Extract EXIF tags from raw JPEG bytes (before any processing).
     * Returns a map of tag name → value string.
     */
    fun extractFromBytes(jpegBytes: ByteArray): Map<String, String> {
        val exif = ExifInterface(ByteArrayInputStream(jpegBytes))
        val tags = mutableMapOf<String, String>()
        for (tag in PRESERVE_TAGS) {
            exif.getAttribute(tag)?.let { tags[tag] = it }
        }
        return tags
    }

    /**
     * Apply preserved EXIF tags and Mavrolume metadata to a saved image URI.
     *
     * Custom TAG_USER_COMMENT format: "Mavrolume|{simCode}|{ratioLabel}"
     * e.g. "Mavrolume|CHR|XPAN"
     */
    fun applyToUri(
        resolver: ContentResolver,
        uri: Uri,
        tags: Map<String, String>,
        simCode: String,
        ratioLabel: String,
    ): Boolean {
        return try {
            val fd = resolver.openFileDescriptor(uri, "rw") ?: return false
            fd.use {
                val exif = ExifInterface(it.fileDescriptor)

                // Re-apply preserved camera EXIF
                for ((tag, value) in tags) {
                    // Skip dimension tags — our processed image may be cropped
                    if (tag == ExifInterface.TAG_IMAGE_WIDTH ||
                        tag == ExifInterface.TAG_IMAGE_LENGTH
                    ) continue
                    exif.setAttribute(tag, value)
                }

                // Reset orientation — our output is already rotated correctly
                exif.setAttribute(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL.toString())

                // Film recipe and framing metadata
                exif.setAttribute(ExifInterface.TAG_USER_COMMENT, "Mavrolume|$simCode|$ratioLabel")
                exif.setAttribute(ExifInterface.TAG_SOFTWARE, "Mavrolume")

                exif.saveAttributes()
            }
            true
        } catch (e: Exception) {
            Log.w(TAG, "Failed to write EXIF metadata", e)
            false
        }
    }
}
