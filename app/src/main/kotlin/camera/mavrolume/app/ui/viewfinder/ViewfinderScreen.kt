package camera.mavrolume.app.ui.viewfinder

import android.Manifest
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.Settings
import android.app.Activity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.ImageCapture
import androidx.camera.view.PreviewView
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.window.layout.FoldingFeature
import androidx.window.layout.WindowInfoTracker
import camera.mavrolume.app.BuildConfig
import camera.mavrolume.app.filmsim.Category
import camera.mavrolume.app.filmsim.FilmRecipes
import camera.mavrolume.app.filmsim.FilmSettings
import camera.mavrolume.app.filmsim.FilmSimulation
import java.util.Locale
import kotlin.math.roundToInt

private val Cream = Color(0xFFEAE3D6)
private val Lilac = Color(0xFFCEBFFD)
private fun Context.activity(): Activity? = when(this) { is Activity -> this; is ContextWrapper -> baseContext.activity(); else -> null }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ViewfinderScreen(
    onNavigateToGallery: () -> Unit,
    onNavigateToSettings: (Boolean) -> Unit,
    viewModel: ViewfinderViewModel = hiltViewModel(),
) {
    val context = LocalContext.current
    val lifecycle = LocalLifecycleOwner.current
    val state by viewModel.uiState.collectAsState()
    var permitted by remember { mutableStateOf(ContextCompat.checkSelfPermission(context,Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) }
    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { permitted = it }
    var editor by rememberSaveable { mutableStateOf(false) }
    var advanced by rememberSaveable { mutableStateOf(false) }
    var fold by remember { mutableStateOf<FoldingFeature?>(null) }
    LaunchedEffect(Unit) {
        if (!permitted) permission.launch(Manifest.permission.CAMERA)
        if (!BuildConfig.PHONE_ONLY) context.activity()?.let { activity -> WindowInfoTracker.getOrCreate(context).windowLayoutInfo(activity).collect { info ->
            fold = info.displayFeatures.filterIsInstance<FoldingFeature>().firstOrNull { it.state == FoldingFeature.State.HALF_OPENED || it.isSeparating }
        } }
    }
    DisposableEffect(lifecycle) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) permitted = ContextCompat.checkSelfPermission(context,Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED
        }
        lifecycle.lifecycle.addObserver(observer)
        onDispose { lifecycle.lifecycle.removeObserver(observer) }
    }
    BoxWithConstraints(Modifier.fillMaxSize().background(Color.Black)) {
        val wide = !BuildConfig.PHONE_ONLY && maxWidth >= 650.dp && maxHeight >= 600.dp
        val landscapeWindow = maxWidth > maxHeight
        val density = LocalDensity.current
        val hinge = fold
        val horizontalFold = hinge?.orientation == FoldingFeature.Orientation.HORIZONTAL
        val verticalFold = hinge?.orientation == FoldingFeature.Orientation.VERTICAL
        val cameraModifier = when {
            horizontalFold -> Modifier.fillMaxWidth().height(with(density) { hinge!!.bounds.top.toDp() }.coerceIn(minOf(180.dp,maxHeight),maxHeight))
            verticalFold -> Modifier.fillMaxHeight().width(with(density) { hinge!!.bounds.left.toDp() }.coerceIn(minOf(180.dp,maxWidth),maxWidth))
            wide -> Modifier.fillMaxHeight().width((maxWidth-360.dp).coerceAtLeast(200.dp))
            landscapeWindow -> Modifier.fillMaxHeight().width((maxWidth-260.dp).coerceAtLeast(180.dp))
            else -> Modifier.fillMaxSize()
        }
        BoxWithConstraints(cameraModifier) {
            val landscape = landscapeWindow
            val ratio = if (state.aspectRatio == AspectRatio.FULL) (if (landscape) 4f/3f else 3f/4f)
                else if (landscape) state.aspectRatio.ratio else 1f/state.aspectRatio.ratio
            val frameWidth = minOf(maxWidth,maxHeight*ratio)
            LaunchedEffect(ratio) { viewModel.configureViewport(ratio) }
            if (permitted) {
                Box(Modifier.width(frameWidth).aspectRatio(ratio).align(Alignment.Center)) {
                    CameraPreview(viewModel,Modifier.fillMaxSize()) { editor = true }
                    if (state.isFocusLocked) Text("FOCUSED",color = Cream,fontSize = 10.sp,modifier = Modifier.align(Alignment.Center).border(1.dp,Cream).padding(12.dp))
                }
            } else Column(Modifier.align(Alignment.Center).padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Text("Let light in.",color = Cream,fontSize = 28.sp)
                Text("Camera access is needed to take photos.",color = Cream)
                TextButton(onClick = { permission.launch(Manifest.permission.CAMERA) }) { Text("Allow camera") }
                TextButton(onClick = { context.startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS,Uri.parse("package:${context.packageName}"))) }) { Text("Open app settings") }
            }
            Row(Modifier.fillMaxWidth().statusBarsPadding().padding(12.dp),verticalAlignment = Alignment.CenterVertically,horizontalArrangement = Arrangement.SpaceBetween) {
                Text("Mavrolume",color = Cream,fontSize = 19.sp,letterSpacing = (-0.5).sp)
                Row {
                    TextButton(onClick = { viewModel.setMegapixels(if(state.megapixels == 12) 50 else 12) }, enabled = state.ready && !state.isCapturing && state.supports50Mp) { Text("${state.megapixels} MP",fontSize = 11.sp) }
                    TextButton(onClick = viewModel::cycleFlash,enabled = state.flashAvailable && state.exposureMode == ExposureMode.AUTO) {
                        Text(when(state.flashMode) { ImageCapture.FLASH_MODE_ON -> "Flash on"; ImageCapture.FLASH_MODE_AUTO -> "Flash auto"; else -> "Flash off" },fontSize = 11.sp)
                    }
                    TextButton(onClick = { editor = true; advanced = true }) { Text("•••",color = Cream) }
                }
            }
            if (!horizontalFold && !verticalFold && !landscapeWindow) CameraControls(state,viewModel,onNavigateToGallery,{ editor = true },Modifier.align(Alignment.BottomCenter))
            state.errorMessage?.let { message ->
                Surface(color = Color(0xEE26232A),shape = RoundedCornerShape(16.dp),modifier = Modifier.align(Alignment.Center).padding(24.dp)) {
                    Column(Modifier.padding(16.dp)) {
                        Text(message,color = Cream)
                        Row { TextButton(onClick = { viewModel.clearError(); viewModel.rebindCamera() }) { Text("Retry") }; TextButton(onClick = viewModel::clearError) { Text("Dismiss") } }
                    }
                }
            }
        }
        if (landscapeWindow && !horizontalFold && !verticalFold && !(wide && editor)) {
            Box(Modifier.align(Alignment.CenterEnd).width(260.dp).fillMaxHeight().systemBarsPadding().verticalScroll(rememberScrollState())) {
                CameraControls(state,viewModel,onNavigateToGallery,{ editor = true },Modifier)
            }
        }
        if (horizontalFold || verticalFold) {
            val panel = if (horizontalFold) Modifier.fillMaxWidth().padding(top = with(density) { hinge!!.bounds.bottom.toDp() }).fillMaxHeight()
                else Modifier.fillMaxHeight().padding(start = with(density) { hinge!!.bounds.right.toDp() }).fillMaxWidth()
            Column(panel.background(Color(0xFF151419)).navigationBarsPadding()) {
                CameraControls(state,viewModel,onNavigateToGallery,{ editor = true },Modifier)
                if (editor) Box(Modifier.weight(1f)) { FilmEditor(state,viewModel,advanced,{ advanced = it },{ editor = false }) }
            }
        } else if (wide) {
            Surface(color = Color(0xFF151419),modifier = Modifier.align(Alignment.CenterEnd).width(360.dp).fillMaxHeight().systemBarsPadding()) {
                ProPanel(state,viewModel,onNavigateToGallery,onNavigateToSettings)
            }
        } else if (editor) {
            ModalBottomSheet(onDismissRequest = { editor = false },containerColor = Color(0xFF151419),sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
                Box(Modifier.fillMaxWidth().fillMaxHeight(0.78f)) { FilmEditor(state,viewModel,advanced,{ advanced = it },{ editor = false }) }
            }
        }
    }
}

@Composable
private fun CameraPreview(vm: ViewfinderViewModel,modifier: Modifier,onSwipeUp: ()->Unit) {
    val context = LocalContext.current
    val owner = LocalLifecycleOwner.current
    val preview = remember(context) { PreviewView(context).apply { implementationMode = PreviewView.ImplementationMode.COMPATIBLE; scaleType = PreviewView.ScaleType.FILL_CENTER } }
    DisposableEffect(preview,owner) {
        vm.startCamera(owner,preview)
        val displays = context.getSystemService(Context.DISPLAY_SERVICE) as android.hardware.display.DisplayManager
        var rotation = preview.display?.rotation
        val listener = object : android.hardware.display.DisplayManager.DisplayListener {
            override fun onDisplayAdded(id: Int) = Unit
            override fun onDisplayRemoved(id: Int) = Unit
            override fun onDisplayChanged(id: Int) {
                val next = preview.display?.rotation
                if (id == preview.display?.displayId && next != rotation) { rotation = next; vm.rebindCamera() }
            }
        }
        displays.registerDisplayListener(listener,android.os.Handler(android.os.Looper.getMainLooper()))
        val observer = LifecycleEventObserver { _, event -> if (event == Lifecycle.Event.ON_RESUME) vm.rebindCamera() }
        owner.lifecycle.addObserver(observer)
        onDispose { displays.unregisterDisplayListener(listener); owner.lifecycle.removeObserver(observer); vm.releaseCamera() }
    }
    AndroidView(factory = { preview },modifier = modifier
        .pointerInput(Unit) { detectTapGestures { vm.tapToFocus(it.x,it.y) } }
        .pointerInput(Unit) { var drag = 0f; detectVerticalDragGestures(onDragStart = { drag = 0f },onDragEnd = { if(drag < -70) onSwipeUp() }) { change, amount -> drag += amount; change.consume() } })
}

@Composable
private fun CameraControls(state: CameraUiState,vm: ViewfinderViewModel,gallery: ()->Unit,edit: ()->Unit,modifier: Modifier) {
    var category by rememberSaveable { mutableStateOf(state.selectedSim.category) }
    Column(modifier.fillMaxWidth().background(Color.Black.copy(alpha = 0.70f)).navigationBarsPadding().padding(vertical = 8.dp),horizontalAlignment = Alignment.CenterHorizontally) {
        Row { Category.entries.forEach { group ->
            TextButton(onClick = { category = group }) { Text(group.displayName,color = if(category == group) Lilac else Cream,fontSize = 11.sp) }
        } }
        Row(Modifier.horizontalScroll(rememberScrollState()).padding(horizontal = 12.dp),horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilmSimulation.entries.filter { it.category == category }.forEach { sim ->
                FilterChip(selected = state.selectedSim == sim,onClick = { vm.selectSim(sim) },label = { Text(sim.displayName,fontSize = 12.sp) })
            }
        }
        Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 12.dp),verticalAlignment = Alignment.CenterVertically,horizontalArrangement = Arrangement.Center) {
            listOf(state.minZoom,1f,2f,3f).distinct().filter { it in state.minZoom..state.maxZoom }.forEach { zoom ->
                TextButton(onClick = { vm.setZoom(zoom) }) { Text(String.format(Locale.US,"%.1f×",zoom),color = if(kotlin.math.abs(state.zoom-zoom)<0.05f) Lilac else Cream) }
            }
        }
        if (state.exposureCompRange.first < state.exposureCompRange.last && state.exposureMode == ExposureMode.AUTO) Row(Modifier.padding(horizontal = 24.dp),verticalAlignment = Alignment.CenterVertically) {
            Text("EV",fontSize = 10.sp,color = Cream)
            Slider(value = state.exposureCompensation.toFloat(),onValueChange = { vm.setExposureCompensation(it.roundToInt()) },valueRange = state.exposureCompRange.first.toFloat()..state.exposureCompRange.last.toFloat(),modifier = Modifier.weight(1f).padding(horizontal = 12.dp))
            Text(String.format(Locale.US,"%+.1f",state.exposureCompensation*state.exposureStep),color = Cream,fontSize = 11.sp)
        }
        Row(Modifier.fillMaxWidth().padding(horizontal = 24.dp),horizontalArrangement = Arrangement.SpaceEvenly,verticalAlignment = Alignment.CenterVertically) {
            TextButton(onClick = gallery,enabled = !state.isCapturing) { Text("Library",color = Cream) }
            Box(Modifier.size(76.dp).semantics { contentDescription = "Take photo" }.border(2.dp,Cream,CircleShape).padding(7.dp).clip(CircleShape)
                .background(if(state.isCapturing) Lilac else Cream).clickable(enabled = state.ready && !state.isCapturing) { vm.capturePhoto() },contentAlignment = Alignment.Center) {
                if (state.isCapturing) CircularProgressIndicator(Modifier.size(28.dp),color = Color.Black,strokeWidth = 2.dp)
            }
            TextButton(onClick = edit) { Text("Edit film",color = Cream) }
        }
        Text(if(state.isCapturing) "DEVELOPING…" else "${state.selectedSim.displayName.uppercase()}  /  ${state.aspectRatio.label}",color = Cream.copy(alpha = 0.6f),fontSize = 9.sp,letterSpacing = 2.sp,modifier = Modifier.padding(top = 10.dp))
    }
}

/** The unfolded workspace keeps the viewfinder clear and exposes one control group at a time. */
@Composable
private fun ProPanel(state: CameraUiState, vm: ViewfinderViewModel, gallery: () -> Unit, settings: (Boolean) -> Unit) {
    var page by rememberSaveable { mutableStateOf("FILM") }
    var dial by rememberSaveable { mutableStateOf("FILM") }
    val f = state.filmSettings
    Column(Modifier.fillMaxSize().background(Color(0xFF151419)).padding(14.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Column { Text("MAVROLUME", color = Cream, fontSize = 16.sp, letterSpacing = 2.sp); Text("PRO · INNER DISPLAY", color = Lilac, fontSize = 10.sp) }
            TextButton(onClick = gallery) { Text("Library") }
        }
        Row(Modifier.horizontalScroll(rememberScrollState())) {
            listOf("FILM", "WB", "EXPOSURE", "FOCUS", "FRAME", "MORE").forEach { item ->
                FilterChip(selected = page == item, onClick = { page = item }, label = { Text(item, fontSize = 10.sp) }, modifier = Modifier.padding(end = 5.dp))
            }
        }
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text("DIAL", color = Lilac, fontSize = 10.sp)
            listOf("FILM", "EV", "WB", "FOCUS").forEach { mode ->
                TextButton(onClick = { dial = mode }) { Text(mode, color = if (dial == mode) Lilac else Cream, fontSize = 10.sp) }
            }
        }
        val dialValue = when (dial) {
            "FILM" -> FilmSimulation.entries.indexOf(state.selectedSim).toFloat()
            "EV" -> state.exposureCompensation.toFloat()
            "WB" -> WhiteBalanceMode.entries.indexOf(state.whiteBalanceMode).toFloat()
            else -> state.focusDistance
        }
        val dialRange = when (dial) {
            "FILM" -> 0f..FilmSimulation.entries.lastIndex.toFloat()
            "EV" -> state.exposureCompRange.first.toFloat()..state.exposureCompRange.last.toFloat()
            "WB" -> 0f..WhiteBalanceMode.entries.lastIndex.toFloat()
            else -> 0f..state.maxFocusDistance.coerceAtLeast(1f)
        }
        Slider(value = dialValue.coerceIn(dialRange.start, dialRange.endInclusive), onValueChange = { value ->
            when (dial) {
                "FILM" -> vm.selectSim(FilmSimulation.entries[value.roundToInt().coerceIn(0, FilmSimulation.entries.lastIndex)])
                "EV" -> vm.setExposureCompensation(value.roundToInt())
                "WB" -> vm.setWhiteBalance(WhiteBalanceMode.entries[value.roundToInt().coerceIn(0, WhiteBalanceMode.entries.lastIndex)])
                else -> if (state.isManualFocus) vm.setFocusDistance(value)
            }
        }, valueRange = dialRange, enabled = dial != "FOCUS" || state.isManualFocus, modifier = Modifier.semantics { contentDescription = "$dial command dial" })
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState())) {
            when (page) {
                "FILM" -> {
                    Text("Film look", color = Cream, fontSize = 18.sp)
                    Category.entries.forEach { category ->
                        Text(category.displayName, color = Lilac, fontSize = 11.sp, modifier = Modifier.padding(top = 10.dp))
                        Row(Modifier.horizontalScroll(rememberScrollState())) {
                            FilmSimulation.entries.filter { it.category == category }.forEach { sim ->
                                FilterChip(selected = state.selectedSim == sim, onClick = { vm.selectSim(sim) }, label = { Text(sim.displayName) }, modifier = Modifier.padding(end = 5.dp))
                            }
                        }
                    }
                    EditSlider("Film strength", f.strength, 0f..1f) { vm.setFilmSettings(f.copy(strength = it)) }
                    EditSlider("Saturation", f.saturation, 0f..2f) { vm.setFilmSettings(f.copy(saturation = it)) }
                    EditSlider("Grain", f.grain, 0f..1f) { vm.setFilmSettings(f.copy(grain = it)) }
                    EditSlider("Grain size", f.grainSize, 0.5f..4f) { vm.setFilmSettings(f.copy(grainSize = it)) }
                    EditSlider("Bloom", f.bloom, 0f..1f) { vm.setFilmSettings(f.copy(bloom = it)) }
                    EditSlider("Halation", f.halation, 0f..1f) { vm.setFilmSettings(f.copy(halation = it)) }
                }
                "WB" -> {
                    Text("White balance", color = Cream, fontSize = 18.sp)
                    Text("Camera preset plus fine color adjustment", color = Color.Gray, fontSize = 11.sp)
                    Row(Modifier.horizontalScroll(rememberScrollState())) {
                        WhiteBalanceMode.entries.forEach { mode -> FilterChip(selected = state.whiteBalanceMode == mode, onClick = { vm.setWhiteBalance(mode) }, label = { Text(mode.label) }, modifier = Modifier.padding(end = 5.dp)) }
                    }
                    EditSlider("Warmth", f.temperature, -1f..1f) { vm.setFilmSettings(f.copy(temperature = it)) }
                    EditSlider("Green ↔ magenta", f.tint, -1f..1f) { vm.setFilmSettings(f.copy(tint = it)) }
                }
                "EXPOSURE" -> {
                    Text("Exposure", color = Cream, fontSize = 18.sp)
                    Toggle("Manual ISO + shutter", state.exposureMode == ExposureMode.MANUAL, { vm.setExposureMode(if (it) ExposureMode.MANUAL else ExposureMode.AUTO) }, state.manualCapable)
                    if (state.exposureMode == ExposureMode.MANUAL) {
                        Text("ISO", color = Lilac, fontSize = 11.sp)
                        Row(Modifier.horizontalScroll(rememberScrollState())) { state.isoStops.forEach { iso -> FilterChip(selected = state.iso == iso, onClick = { vm.setIso(iso) }, label = { Text("$iso") }, modifier = Modifier.padding(end = 5.dp)) } }
                        val stops = state.shutterIndices
                        if (stops.size > 1) EditSlider("Shutter ${state.shutterSpeedLabel}", stops.indexOf(state.shutterSpeedIndex).coerceAtLeast(0).toFloat(), 0f..stops.lastIndex.toFloat()) { vm.setShutterSpeedIndex(stops[it.roundToInt()]) }
                    } else if (state.exposureCompRange.first < state.exposureCompRange.last) {
                        EditSlider("EV ${String.format(Locale.US, "%+.1f", state.exposureCompensation * state.exposureStep)}", state.exposureCompensation.toFloat(), state.exposureCompRange.first.toFloat()..state.exposureCompRange.last.toFloat()) { vm.setExposureCompensation(it.roundToInt()) }
                    }
                    EditSlider("Brightness", f.brightness, -0.3f..0.3f) { vm.setFilmSettings(f.copy(brightness = it)) }
                    EditSlider("Contrast", f.contrast, 0.5f..1.5f) { vm.setFilmSettings(f.copy(contrast = it)) }
                    EditSlider("Dynamic range", f.dynamicRange, -1f..1f) { vm.setFilmSettings(f.copy(dynamicRange = it)) }
                    EditSlider("Midtones", f.mids, -1f..1f) { vm.setFilmSettings(f.copy(mids = it)) }
                }
                "FOCUS" -> {
                    Text("Focus & lens", color = Cream, fontSize = 18.sp)
                    Toggle("Manual focus", state.isManualFocus, vm::setManualFocus, state.maxFocusDistance > 0f)
                    if (state.isManualFocus) EditSlider("Focus distance", state.focusDistance, 0f..state.maxFocusDistance, vm::setFocusDistance)
                    Toggle("Focus peaking", state.showFocusPeaking, vm::setPeaking)
                    state.lensOptions.forEach { lens -> TextButton(onClick = { vm.selectCamera(lens.id) }) { Text(lens.label, color = if (lens.id == state.selectedCameraId) Lilac else Cream) } }
                    if (state.minZoom < state.maxZoom) EditSlider("Zoom", state.zoom, state.minZoom..state.maxZoom, vm::setZoom)
                }
                "FRAME" -> {
                    Text("Frame", color = Cream, fontSize = 18.sp)
                    Row(Modifier.horizontalScroll(rememberScrollState())) { listOf(AspectRatio.RATIO_4_3, AspectRatio.RATIO_3_2, AspectRatio.WIDE, AspectRatio.SQUARE, AspectRatio.XPAN).forEach { ratio -> FilterChip(selected = state.aspectRatio == ratio, onClick = { vm.setAspectRatio(ratio) }, label = { Text(ratio.label) }, modifier = Modifier.padding(end = 5.dp)) } }
                    Text("Resolution", color = Cream, modifier = Modifier.padding(top = 12.dp))
                    Row { listOf(12, 50).forEach { mp -> FilterChip(selected = state.megapixels == mp, onClick = { vm.setMegapixels(mp) }, enabled = mp == 12 || state.supports50Mp, label = { Text("$mp MP") }, modifier = Modifier.padding(end = 6.dp)) } }
                    Text(state.resolutionLabel, color = Color.Gray, fontSize = 11.sp)
                    Toggle("Save original JPEG", state.saveOriginal, vm::setSaveOriginal)
                    Toggle("Save RAW / DNG", state.saveDng, vm::setSaveDng, state.rawCapable)
                }
                "MORE" -> {
                    Text("Finishing", color = Cream, fontSize = 18.sp)
                    EditSlider("Fade", f.fade, 0f..1f) { vm.setFilmSettings(f.copy(fade = it)) }
                    EditSlider("Mute", f.mute, 0f..1f) { vm.setFilmSettings(f.copy(mute = it)) }
                    EditSlider("Softness", f.softness, 0f..1f) { vm.setFilmSettings(f.copy(softness = it)) }
                    EditSlider("Color fringing", f.aberration, 0f..1f) { vm.setFilmSettings(f.copy(aberration = it)) }
                    TextButton(onClick = { vm.setFilmSettings(FilmSettings.forProfile(state.selectedSim)) }) { Text("Reset film look") }
                    TextButton(onClick = { settings(true) }) { Text("Settings") }
                }
            }
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            TextButton(onClick = vm::cycleFlash, enabled = state.flashAvailable && state.exposureMode == ExposureMode.AUTO) { Text(if (state.flashMode == ImageCapture.FLASH_MODE_OFF) "Flash off" else "Flash on") }
            Box(Modifier.size(70.dp).semantics { contentDescription = "Take photo" }.border(2.dp, Cream, CircleShape).padding(6.dp).clip(CircleShape).background(Cream).clickable(enabled = state.ready && !state.isCapturing) { vm.capturePhoto() })
            Text(state.selectedSim.displayName, color = Lilac, fontSize = 11.sp)
        }
    }
}

@Composable
private fun FilmEditor(state: CameraUiState,vm: ViewfinderViewModel,advanced: Boolean,setAdvanced: (Boolean)->Unit,close: ()->Unit) {
    Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(20.dp)) {
        Row(Modifier.fillMaxWidth(),horizontalArrangement = Arrangement.SpaceBetween,verticalAlignment = Alignment.CenterVertically) {
            Text(if(advanced) "Advanced" else "Make it yours",fontSize = 23.sp,color = Cream)
            TextButton(onClick = close) { Text("Done") }
        }
        Row { TextButton(onClick = { setAdvanced(false) }) { Text("Film & tone",color = if(!advanced) Lilac else Cream) }; TextButton(onClick = { setAdvanced(true) }) { Text("Advanced",color = if(advanced) Lilac else Cream) } }
        if (!advanced) {
            Text(state.selectedSim.displayName,color = Cream,fontSize = 18.sp)
            Text(state.selectedSim.description,color = Cream.copy(alpha = 0.6f),fontSize = 12.sp)
            var quality by rememberSaveable(state.selectedSim.name) { mutableStateOf(state.selectedSim.recipes.substringBefore(',').substringBefore('-')) }
            var tone by rememberSaveable(state.selectedSim.name) { mutableStateOf(state.selectedSim.recipes.substringBefore(',').substringAfter('-')) }
            Text("Texture & tone · selecting a look restores its base",color = Color.Gray,fontSize = 10.sp)
            Row(Modifier.horizontalScroll(rememberScrollState())) {
                state.selectedSim.recipes.split(',').forEach { recipe ->
                    TextButton(onClick = { quality = recipe.substringBefore('-'); tone = recipe.substringAfter('-'); vm.setFilmSettings(FilmRecipes.apply(recipe)) }) { Text(recipe) }
                }
            }
            Row(Modifier.horizontalScroll(rememberScrollState())) {
                FilmRecipes.qualities.forEach { q -> TextButton(onClick = { quality = q; vm.setFilmSettings(FilmRecipes.apply("$quality-$tone")) }) { Text(q,fontSize = 11.sp) } }
            }
            Row(Modifier.horizontalScroll(rememberScrollState())) {
                FilmRecipes.tones.forEach { (code,label) -> TextButton(onClick = { tone = code; vm.setFilmSettings(FilmRecipes.apply("$quality-$tone")) }) { Text(label,fontSize = 11.sp) } }
            }
            val f = state.filmSettings
            EditSlider("Strength",f.strength,0f..1f) { vm.setFilmSettings(f.copy(strength = it)) }
            EditSlider("Saturation",f.saturation,0f..2f) { vm.setFilmSettings(f.copy(saturation = it)) }
            EditSlider("Temperature",f.temperature,-1f..1f) { vm.setFilmSettings(f.copy(temperature = it)) }
            EditSlider("Tint",f.tint,-1f..1f) { vm.setFilmSettings(f.copy(tint = it)) }
            EditSlider("Grain",f.grain,0f..1f) { vm.setFilmSettings(f.copy(grain = it)) }
            EditSlider("Grain size",f.grainSize,0.5f..4f) { vm.setFilmSettings(f.copy(grainSize = it)) }
            EditSlider("Softness",f.softness,0f..1f) { vm.setFilmSettings(f.copy(softness = it)) }
            EditSlider("Color fringing",f.aberration,0f..1f) { vm.setFilmSettings(f.copy(aberration = it)) }
            EditSlider("Bloom",f.bloom,0f..1f) { vm.setFilmSettings(f.copy(bloom = it)) }
            EditSlider("Halation",f.halation,0f..1f) { vm.setFilmSettings(f.copy(halation = it)) }
            EditSlider("Exposure",f.exposure,-2f..2f) { vm.setFilmSettings(f.copy(exposure = it)) }
            EditSlider("Brightness",f.brightness,-0.3f..0.3f) { vm.setFilmSettings(f.copy(brightness = it)) }
            EditSlider("Contrast",f.contrast,0.5f..1.5f) { vm.setFilmSettings(f.copy(contrast = it)) }
            EditSlider("Dynamic range",f.dynamicRange,-1f..1f) { vm.setFilmSettings(f.copy(dynamicRange = it)) }
            EditSlider("Midtones",f.mids,-1f..1f) { vm.setFilmSettings(f.copy(mids = it)) }
            EditSlider("Fade",f.fade,0f..1f) { vm.setFilmSettings(f.copy(fade = it)) }
            EditSlider("Mute",f.mute,0f..1f) { vm.setFilmSettings(f.copy(mute = it)) }
            TextButton(onClick = { vm.setFilmSettings(FilmSettings.forProfile(state.selectedSim)) }) { Text("Reset film adjustments") }
        } else {
            Text("Photo resolution",color = Cream)
            Row {
                listOf(12,50).forEach { mp -> FilterChip(selected = state.megapixels == mp, onClick = { vm.setMegapixels(mp) }, enabled = state.ready && !state.isCapturing && (mp == 12 || state.supports50Mp), label = { Text("$mp MP") }, modifier = Modifier.padding(end = 8.dp)) }
            }
            Text(state.resolutionLabel,color = Color.Gray,fontSize = 11.sp)
            Text(if(state.supports50Mp) "50 MP takes longer. Cropping reduces the saved pixel count." else "50 MP is not exposed by this lens to third-party apps.",color = Color.Gray,fontSize = 11.sp)
            Text("Frame",color = Cream)
            Row(Modifier.horizontalScroll(rememberScrollState())) {
                listOf(AspectRatio.RATIO_4_3,AspectRatio.RATIO_3_2,AspectRatio.WIDE,AspectRatio.SQUARE,AspectRatio.XPAN).forEach { ratio ->
                    FilterChip(selected = ratio == state.aspectRatio,onClick = { vm.setAspectRatio(ratio) },label = { Text(ratio.label) },modifier = Modifier.padding(end = 6.dp))
                }
            }
            Text("Cameras exposed by this device",color = Cream)
            state.lensOptions.forEach { lens -> TextButton(onClick = { vm.selectCamera(lens.id) },enabled = !state.isCapturing) { Text(lens.label,color = if(lens.id == state.selectedCameraId) Lilac else Cream) } }
            Text("Zoom may use digital crop; Samsung decides physical lens routing.",color = Color.Gray,fontSize = 11.sp)
            if(state.minZoom < state.maxZoom) EditSlider("Zoom",state.zoom,state.minZoom..state.maxZoom,vm::setZoom)
            Toggle("Save original JPEG",state.saveOriginal,vm::setSaveOriginal)
            Toggle("Save RAW / DNG",state.saveDng,vm::setSaveDng,state.rawCapable && !state.isCapturing)
            if(!state.rawCapable) Text("RAW is unavailable with this camera configuration.",color = Color.Gray,fontSize = 11.sp)
            Toggle("Manual exposure",state.exposureMode == ExposureMode.MANUAL,{ vm.setExposureMode(if(it) ExposureMode.MANUAL else ExposureMode.AUTO) },state.manualCapable)
            if(state.exposureMode == ExposureMode.MANUAL) {
                Row(Modifier.horizontalScroll(rememberScrollState())) { state.isoStops.forEach { iso -> TextButton(onClick = { vm.setIso(iso) }) { Text("$iso",color = if(iso == state.iso) Lilac else Cream) } } }
                val allowed = state.shutterIndices
                if(allowed.size > 1) EditSlider("Shutter ${SHUTTER_SPEEDS[state.shutterSpeedIndex].second}",allowed.indexOf(state.shutterSpeedIndex).coerceAtLeast(0).toFloat(),0f..allowed.lastIndex.toFloat()) { vm.setShutterSpeedIndex(allowed[it.roundToInt()]) }
            }
            Toggle("Manual focus",state.isManualFocus,vm::setManualFocus,state.maxFocusDistance > 0f)
            if(state.isManualFocus) EditSlider("Focus distance",state.focusDistance,0f..state.maxFocusDistance,vm::setFocusDistance)
            Toggle("Focus peaking",state.showFocusPeaking,vm::setPeaking)
            HorizontalDivider(Modifier.padding(vertical = 16.dp))
            Text("Mavrolume · ${BuildConfig.VERSION_NAME}\nCreated by d4rkrolls\nIndependent profiles; no vendor affiliation.\nBased on FilmFrame by Ryuhei Yokokawa (MIT).",color = Color.Gray,fontSize = 12.sp)
            val context = LocalContext.current
            var license by remember { mutableStateOf(false) }
            TextButton(onClick = { license = !license }) { Text("Open-source license") }
            if(license) Text(remember { context.assets.open("LICENSE-FilmFrame.txt").bufferedReader().use { it.readText() } },color = Color.Gray,fontSize = 11.sp)
        }
    }
}

@Composable
private fun EditSlider(label: String,value: Float,range: ClosedFloatingPointRange<Float>,change: (Float)->Unit) {
    Column(Modifier.padding(top = 10.dp)) {
        Row(Modifier.fillMaxWidth(),horizontalArrangement = Arrangement.SpaceBetween) { Text(label,color = Cream,fontSize = 13.sp); Text(String.format(Locale.US,"%.2f",value),color = Lilac,fontSize = 12.sp) }
        Slider(value = value.coerceIn(range.start,range.endInclusive),onValueChange = change,valueRange = range,modifier = Modifier.semantics { contentDescription = label })
    }
}
@Composable
private fun Toggle(label: String,value: Boolean,change: (Boolean)->Unit,enabled: Boolean = true) {
    Row(Modifier.fillMaxWidth().padding(vertical = 6.dp),verticalAlignment = Alignment.CenterVertically) {
        Text(label,color = if(enabled) Cream else Color.Gray,modifier = Modifier.weight(1f),fontSize = 13.sp)
        Switch(checked = value,onCheckedChange = change,enabled = enabled)
    }
}
