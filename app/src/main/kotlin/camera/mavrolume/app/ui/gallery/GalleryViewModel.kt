package camera.mavrolume.app.ui.gallery

import android.app.Application
import android.net.Uri
import android.provider.MediaStore
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

data class GalleryPhoto(
    val uri: Uri,
    val displayName: String,
    val simCode: String,
    val ratioLabel: String,
    val dateAdded: Long,
)

@HiltViewModel
class GalleryViewModel @Inject constructor(
    private val application: Application,
) : ViewModel() {

    private val _photos = MutableStateFlow<List<GalleryPhoto>>(emptyList())
    val photos: StateFlow<List<GalleryPhoto>> = _photos.asStateFlow()

    /** Index into [photos] for the currently selected detail view, or -1 if none. */
    private val _selectedIndex = MutableStateFlow(-1)
    val selectedIndex: StateFlow<Int> = _selectedIndex.asStateFlow()

    fun loadPhotos() {
        viewModelScope.launch {
            _photos.value = withContext(Dispatchers.IO) { queryMediaStore() }
        }
    }

    fun selectPhoto(index: Int) {
        _selectedIndex.value = index
    }

    fun selectPhotoByRef(photo: GalleryPhoto) {
        val idx = _photos.value.indexOf(photo)
        if (idx >= 0) _selectedIndex.value = idx
    }

    fun clearSelection() {
        _selectedIndex.value = -1
    }

    fun updateSelectedIndex(index: Int) {
        _selectedIndex.value = index
    }

    fun deletePhoto(photo: GalleryPhoto) {
        viewModelScope.launch {
            try {
                withContext(Dispatchers.IO) {
                    application.contentResolver.delete(photo.uri, null, null)
                }
                val current = _photos.value.toMutableList()
                val idx = current.indexOf(photo)
                if (idx >= 0) {
                    current.removeAt(idx)
                    _photos.value = current
                    // Adjust selected index
                    when {
                        current.isEmpty() -> _selectedIndex.value = -1
                        idx >= current.size -> _selectedIndex.value = current.size - 1
                        else -> _selectedIndex.value = idx
                    }
                }
            } catch (e: Exception) {
                Log.w("GalleryVM", "Failed to delete photo", e)
            }
        }
    }

    private fun queryMediaStore(): List<GalleryPhoto> {
        val photos = mutableListOf<GalleryPhoto>()
        val projection = arrayOf(
            MediaStore.Images.Media._ID,
            MediaStore.Images.Media.DISPLAY_NAME,
            MediaStore.Images.Media.DATE_ADDED,
            MediaStore.Images.Media.RELATIVE_PATH,
        )
        val selection = "${MediaStore.Images.Media.RELATIVE_PATH} LIKE ?"
        val selectionArgs = arrayOf("Pictures/Mavrolume/%")
        val sortOrder = "${MediaStore.Images.Media.DATE_ADDED} DESC"

        application.contentResolver.query(
            MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
            projection,
            selection,
            selectionArgs,
            sortOrder,
        )?.use { cursor ->
            val idCol = cursor.getColumnIndexOrThrow(MediaStore.Images.Media._ID)
            val nameCol = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.DISPLAY_NAME)
            val dateCol = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.DATE_ADDED)
            val pathCol = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.RELATIVE_PATH)

            while (cursor.moveToNext()) {
                val relativePath = cursor.getString(pathCol)
                if (relativePath.contains("Originals") || relativePath.contains("RAW")) continue

                val id = cursor.getLong(idCol)
                val name = cursor.getString(nameCol)
                val dateAdded = cursor.getLong(dateCol)
                val uri = Uri.withAppendedPath(
                    MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                    id.toString(),
                )

                val parsed = parseFilename(name)

                photos.add(
                    GalleryPhoto(
                        uri = uri,
                        displayName = name,
                        simCode = parsed.first,
                        ratioLabel = parsed.second,
                        dateAdded = dateAdded,
                    )
                )
            }
        }
        return photos
    }

    private fun parseFilename(name: String): Pair<String, String> {
        val parts = name.substringBeforeLast(".").split("_")
        return (parts.lastOrNull() ?: "DARKMATTER") to ""
    }
}
