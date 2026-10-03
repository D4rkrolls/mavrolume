package camera.mavrolume.app.ui.gallery

import android.content.Intent
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.rememberTransformableState
import androidx.compose.foundation.gestures.transformable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.hilt.navigation.compose.hiltViewModel
import coil.compose.AsyncImage
import camera.mavrolume.app.ui.theme.FilmAccent
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GalleryScreen(
    onBack: () -> Unit,
    viewModel: GalleryViewModel = hiltViewModel(),
) {
    val photos by viewModel.photos.collectAsState()
    val selectedIndex by viewModel.selectedIndex.collectAsState()
    val context = LocalContext.current

    LaunchedEffect(Unit) {
        viewModel.loadPhotos()
    }

    BackHandler(enabled = selectedIndex >= 0) {
        viewModel.clearSelection()
    }

    Box(modifier = Modifier.fillMaxSize()) {
        Scaffold(
            topBar = {
                TopAppBar(
                    title = { Text("Gallery") },
                    navigationIcon = {
                        IconButton(onClick = onBack) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = "Back",
                            )
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = Color.Black,
                        titleContentColor = Color.White,
                        navigationIconContentColor = Color.White,
                    ),
                )
            },
            containerColor = Color.Black,
        ) { padding ->
            if (photos.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(padding),
                    contentAlignment = Alignment.Center,
                ) {
                    Text("No photos yet", color = Color.Gray)
                }
            } else {
                LazyVerticalGrid(
                    columns = GridCells.Fixed(3),
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(padding),
                    contentPadding = PaddingValues(2.dp),
                    horizontalArrangement = Arrangement.spacedBy(2.dp),
                    verticalArrangement = Arrangement.spacedBy(2.dp),
                ) {
                    itemsIndexed(photos, key = { _, p -> p.uri.toString() }) { index, photo ->
                        PhotoCell(
                            photo = photo,
                            onClick = { viewModel.selectPhoto(index) },
                        )
                    }
                }
            }
        }

        // Detail overlay with pager
        if (selectedIndex >= 0 && photos.isNotEmpty()) {
            DetailPager(
                photos = photos,
                initialIndex = selectedIndex,
                onIndexChanged = { viewModel.updateSelectedIndex(it) },
                onBack = { viewModel.clearSelection() },
                onDelete = { photo -> viewModel.deletePhoto(photo) },
                onShare = { photo ->
                    val shareIntent = Intent(Intent.ACTION_SEND).apply {
                        type = "image/jpeg"
                        putExtra(Intent.EXTRA_STREAM, photo.uri)
                        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    }
                    context.startActivity(Intent.createChooser(shareIntent, "Share photo"))
                },
            )
        }
    }
}

@Composable
private fun PhotoCell(
    photo: GalleryPhoto,
    onClick: () -> Unit,
) {
    Box(
        modifier = Modifier
            .aspectRatio(1f)
            .clip(RoundedCornerShape(2.dp))
            .clickable(onClick = onClick),
    ) {
        AsyncImage(
            model = photo.uri,
            contentDescription = photo.displayName,
            modifier = Modifier.fillMaxSize(),
            contentScale = ContentScale.Crop,
        )

        Text(
            text = photo.simCode,
            color = Color.White,
            style = MaterialTheme.typography.labelSmall,
            modifier = Modifier
                .align(Alignment.TopStart)
                .padding(4.dp)
                .background(Color.Black.copy(alpha = 0.6f), RoundedCornerShape(3.dp))
                .padding(horizontal = 4.dp, vertical = 1.dp),
        )

        if (photo.ratioLabel != "FULL") {
            Text(
                text = photo.ratioLabel,
                color = FilmAccent,
                style = MaterialTheme.typography.labelSmall,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(4.dp)
                    .background(Color.Black.copy(alpha = 0.6f), RoundedCornerShape(3.dp))
                    .padding(horizontal = 4.dp, vertical = 1.dp),
            )
        }
    }
}

// ── Detail pager with zoom, immersive mode, delete, EXIF ────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DetailPager(
    photos: List<GalleryPhoto>,
    initialIndex: Int,
    onIndexChanged: (Int) -> Unit,
    onBack: () -> Unit,
    onDelete: (GalleryPhoto) -> Unit,
    onShare: (GalleryPhoto) -> Unit,
) {
    val pagerState = rememberPagerState(
        initialPage = initialIndex.coerceIn(0, (photos.size - 1).coerceAtLeast(0)),
        pageCount = { photos.size },
    )

    // Sync pager page back to ViewModel
    LaunchedEffect(pagerState) {
        snapshotFlow { pagerState.currentPage }.collect { page ->
            onIndexChanged(page)
        }
    }

    // Immersive mode: tap image to toggle chrome
    var showChrome by remember { mutableStateOf(true) }
    val context = LocalContext.current

    // System bars control
    DisposableEffect(showChrome) {
        val activity = context as? android.app.Activity ?: return@DisposableEffect onDispose {}
        val window = activity.window
        val controller = WindowInsetsControllerCompat(window, window.decorView)

        if (!showChrome) {
            controller.hide(WindowInsetsCompat.Type.systemBars())
            controller.systemBarsBehavior =
                WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        } else {
            controller.show(WindowInsetsCompat.Type.systemBars())
        }

        onDispose {
            controller.show(WindowInsetsCompat.Type.systemBars())
        }
    }

    // Delete confirmation dialog
    var showDeleteDialog by remember { mutableStateOf(false) }

    // EXIF data
    val currentPhoto = photos.getOrNull(pagerState.currentPage)
    var exifInfo by remember { mutableStateOf("") }
    LaunchedEffect(currentPhoto?.uri) {
        currentPhoto?.let { photo ->
            exifInfo = withContext(Dispatchers.IO) {
                readExifInfo(context, photo.uri)
            }
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black),
    ) {
        HorizontalPager(
            state = pagerState,
            modifier = Modifier.fillMaxSize(),
            key = { photos[it].uri.toString() },
        ) { page ->
            ZoomableImage(
                photo = photos[page],
                onTap = { showChrome = !showChrome },
            )
        }

        // Top bar (togglable chrome)
        AnimatedVisibility(
            visible = showChrome,
            enter = fadeIn(),
            exit = fadeOut(),
        ) {
            TopAppBar(
                title = {
                    currentPhoto?.let { photo ->
                        Text(
                            text = "${photo.simCode} ${if (photo.ratioLabel != "FULL") photo.ratioLabel else ""}".trim(),
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back",
                        )
                    }
                },
                actions = {
                    IconButton(onClick = { showDeleteDialog = true }) {
                        Icon(
                            imageVector = Icons.Default.Delete,
                            contentDescription = "Delete",
                            tint = Color.White,
                        )
                    }
                    IconButton(onClick = { currentPhoto?.let { onShare(it) } }) {
                        Icon(
                            imageVector = Icons.Default.Share,
                            contentDescription = "Share",
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color.Black.copy(alpha = 0.7f),
                    titleContentColor = Color.White,
                    navigationIconContentColor = Color.White,
                    actionIconContentColor = Color.White,
                ),
            )
        }

        // EXIF info bar (bottom, togglable chrome)
        AnimatedVisibility(
            visible = showChrome && exifInfo.isNotEmpty(),
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier.align(Alignment.BottomCenter),
        ) {
            ExifInfoBar(info = exifInfo)
        }
    }

    // Delete confirmation dialog
    if (showDeleteDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteDialog = false },
            title = { Text("Delete photo?") },
            text = { Text("This photo will be permanently deleted.") },
            confirmButton = {
                TextButton(
                    onClick = {
                        showDeleteDialog = false
                        currentPhoto?.let { onDelete(it) }
                    },
                ) {
                    Text("Delete", color = Color.Red)
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteDialog = false }) {
                    Text("Cancel")
                }
            },
        )
    }
}

// ── Zoomable image (pinch-to-zoom, pan, double-tap reset) ───────────

@Composable
private fun ZoomableImage(
    photo: GalleryPhoto,
    onTap: () -> Unit,
) {
    var scale by remember { mutableFloatStateOf(1f) }
    var offset by remember { mutableStateOf(Offset.Zero) }

    val transformState = rememberTransformableState { zoomChange, panChange, _ ->
        scale = (scale * zoomChange).coerceIn(1f, 5f)
        if (scale > 1f) {
            offset = Offset(
                x = offset.x + panChange.x,
                y = offset.y + panChange.y,
            )
        } else {
            offset = Offset.Zero
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .clickable(
                indication = null,
                interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() },
            ) {
                if (scale > 1.1f) {
                    // Double-tap reset
                    scale = 1f
                    offset = Offset.Zero
                } else {
                    onTap()
                }
            }
            .transformable(state = transformState),
        contentAlignment = Alignment.Center,
    ) {
        AsyncImage(
            model = photo.uri,
            contentDescription = photo.displayName,
            modifier = Modifier
                .fillMaxWidth()
                .graphicsLayer(
                    scaleX = scale,
                    scaleY = scale,
                    translationX = offset.x,
                    translationY = offset.y,
                ),
            contentScale = ContentScale.Fit,
        )
    }
}

// ── EXIF info bar ───────────────────────────────────────────────────

@Composable
private fun ExifInfoBar(
    info: String,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .background(Color.Black.copy(alpha = 0.7f))
            .padding(horizontal = 16.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.SpaceEvenly,
    ) {
        info.split(" | ").forEach { item ->
            Text(
                text = item,
                color = Color.White.copy(alpha = 0.8f),
                fontSize = 12.sp,
            )
        }
    }
}

/** Read EXIF metadata from a content URI. */
private fun readExifInfo(context: android.content.Context, uri: android.net.Uri): String {
    return try {
        val inputStream = context.contentResolver.openInputStream(uri) ?: return ""
        val exif = androidx.exifinterface.media.ExifInterface(inputStream)
        inputStream.close()

        val parts = mutableListOf<String>()

        exif.getAttribute(androidx.exifinterface.media.ExifInterface.TAG_FOCAL_LENGTH)?.let { fl ->
            // Format "35/1" → "35mm"
            val rational = fl.split("/")
            if (rational.size == 2) {
                val mm = rational[0].toFloatOrNull()?.div(rational[1].toFloatOrNull() ?: 1f)
                if (mm != null) parts.add("${mm.toInt()}mm")
            }
        }

        exif.getAttribute(androidx.exifinterface.media.ExifInterface.TAG_F_NUMBER)?.let { f ->
            parts.add("f/$f")
        }

        exif.getAttribute(androidx.exifinterface.media.ExifInterface.TAG_EXPOSURE_TIME)?.let { et ->
            val seconds = et.toFloatOrNull()
            if (seconds != null) {
                if (seconds < 1f) {
                    parts.add("1/${(1f / seconds).toInt()}")
                } else {
                    parts.add("${seconds}s")
                }
            }
        }

        exif.getAttribute(androidx.exifinterface.media.ExifInterface.TAG_PHOTOGRAPHIC_SENSITIVITY)?.let { iso ->
            parts.add("ISO $iso")
        }

        parts.joinToString(" | ")
    } catch (e: Exception) {
        ""
    }
}
