package camera.mavrolume.app.ui.viewfinder

import android.app.Application
import android.content.ContentValues
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.ImageFormat
import android.graphics.Matrix
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CaptureRequest
import android.net.Uri
import android.provider.MediaStore
import android.util.Log
import android.util.Rational
import androidx.camera.core.resolutionselector.ResolutionSelector
import android.view.Surface
import androidx.camera.camera2.interop.Camera2CameraControl
import androidx.camera.camera2.interop.Camera2CameraInfo
import androidx.camera.camera2.interop.CaptureRequestOptions
import androidx.camera.core.*
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.core.content.ContextCompat
import androidx.exifinterface.media.ExifInterface
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import camera.mavrolume.app.camera.LutPreviewEffect
import camera.mavrolume.app.crop.CropEngine
import camera.mavrolume.app.data.UserPreferencesRepository
import camera.mavrolume.app.export.ExifHelper
import camera.mavrolume.app.filmsim.*
import camera.mavrolume.app.filmsim.ImageProcessor
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.lang.ref.WeakReference
import java.io.File
import java.io.ByteArrayOutputStream
import java.util.Locale
import java.util.concurrent.TimeUnit
import kotlin.math.roundToInt
import javax.inject.Inject

/** Only cameras exposed by CameraX are listed; physical lens routing remains vendor-controlled. */
data class LensOption(val id: String, val label: String)

// ── Enums ────────────────────────────────────────────────────────────────

enum class LensSelection(val label: String) {
    ULTRAWIDE("0.5x"),
    WIDE("1x"),
    TELEPHOTO("Tele"),
}

enum class ExposureMode { AUTO, MANUAL }

enum class WhiteBalanceMode(val label: String) {
    AUTO("AWB"),
    DAYLIGHT("SUN"),
    CLOUDY("CLD"),
    BLUE_ROOM("TNG"),
    FLUORESCENT("FL"),
    SHADE("SHD"),
}

enum class MeteringMode(val label: String) {
    MULTI("MULTI"),
    CENTER("CTR"),
    SPOT("SPOT"),
}

enum class PeakingColor(val label: String, val argb: Int) {
    PURPLE("P", 0xCCA855F7.toInt()),
}

enum class AspectRatio(val label: String, val ratio: Float) {
    RATIO_4_3("4:3", 4f / 3f),
    RATIO_3_2("3:2", 3f / 2f),
    FULL("FULL", 0f),       // No crop, use native sensor ratio
    XPAN("XPAN", 65f / 24f),    // Hasselblad XPan 65:24 (≈2.708:1)
    CINEMA("2.39", 2.39f),  // Anamorphic cinema
    RATIO_3_1("3:1", 3.0f),
    WIDE("16:9", 16f / 9f),
    SQUARE("1:1", 1.0f),
}

enum class GridOverlay(val label: String) {
    NONE("OFF"),
    THIRDS("3RD"),
    GOLDEN("GLD"),
}

enum class GrainLevel(val label: String, val multiplier: Float) {
    OFF("OFF", 0.0f),
    LOW("LOW", 0.5f),
    MID("MID", 1.0f),
    HIGH("HIGH", 1.5f),
}

enum class PickerMode { LENS, FILM, CROP }

// ── Constants ────────────────────────────────────────────────────────────

internal val ISO_STOPS = listOf(50, 100, 200, 400, 800, 1600, 3200, 6400)

internal val SHUTTER_SPEEDS: List<Pair<Long, String>> = listOf(
    125_000L to "1/8000",
    250_000L to "1/4000",
    500_000L to "1/2000",
    1_000_000L to "1/1000",
    2_000_000L to "1/500",
    4_000_000L to "1/250",
    8_000_000L to "1/125",
    16_666_667L to "1/60",
    33_333_333L to "1/30",
    66_666_667L to "1/15",
    125_000_000L to "1/8",
    250_000_000L to "1/4",
    500_000_000L to "1/2",
    1_000_000_000L to "1\"",
)

private const val MIN_FREE_BYTES = 50L * 1024 * 1024 // 50 MB

// ── State ────────────────────────────────────────────────────────────────

data class CameraUiState(
    val megapixels: Int = 12,
    val supports50Mp: Boolean = false,
    val resolutionLabel: String = "12 MP",
    val filmSettings: FilmSettings = FilmSettings(),
    val ready: Boolean = false,
    val manualCapable: Boolean = false,
    val flashAvailable: Boolean = false,
    val flashMode: Int = ImageCapture.FLASH_MODE_OFF,
    val zoom: Float = 1f,
    val minZoom: Float = 1f,
    val maxZoom: Float = 1f,
    val lensOptions: List<LensOption> = emptyList(),
    val selectedCameraId: String? = null,
    val exposureStep: Float = 0f,
    val shutterIndices: List<Int> = SHUTTER_SPEEDS.indices.toList(),
    // Lens & film
    val currentLens: LensSelection = LensSelection.WIDE,
    val selectedSim: FilmSimulation = FilmSimulation.DAYBREAK,
    // Auto exposure
    val exposureCompensation: Int = 0,
    val requestedExposureEv: Float = 0f,
    val exposureCompRange: IntRange = -12..12,
    // Exposure mode
    val exposureMode: ExposureMode = ExposureMode.AUTO,
    val iso: Int = 400,
    val isoStops: List<Int> = ISO_STOPS,
    val shutterSpeedIndex: Int = 6, // 1/125
    val shutterSpeedLabel: String = "1/125",
    // Focus
    val isFocusLocked: Boolean = false,
    val isManualFocus: Boolean = false,
    val focusDistance: Float = 0f,
    val maxFocusDistance: Float = 10f,
    // White balance
    val whiteBalanceMode: WhiteBalanceMode = WhiteBalanceMode.AUTO,
    // Metering
    val meteringMode: MeteringMode = MeteringMode.MULTI,
    // Crop & grid
    val aspectRatio: AspectRatio = AspectRatio.RATIO_4_3,
    val gridOverlay: GridOverlay = GridOverlay.NONE,
    // Grain
    val grainLevel: GrainLevel = GrainLevel.OFF,
    // Carousel
    val pickerMode: PickerMode = PickerMode.FILM,
    val filmCategory: Category = Category.RECIPES,
    // Overlays
    val showHistogram: Boolean = false,
    val showFocusPeaking: Boolean = false,
    val peakingColor: PeakingColor = PeakingColor.PURPLE,
    val showLevel: Boolean = false,
    // Capture
    val isCapturing: Boolean = false,
    val lastCapturedUri: Uri? = null,
    // RAW
    val rawCapable: Boolean = false,
    val saveDng: Boolean = false,
    // Error
    val errorMessage: String? = null,
    // Preferences
    val saveOriginal: Boolean = true,
)

@androidx.annotation.OptIn(markerClass = [androidx.camera.camera2.interop.ExperimentalCamera2Interop::class])
@HiltViewModel
class ViewfinderViewModel @Inject constructor(
    private val application: Application,
    private val imageProcessor: ImageProcessor,
    private val lutLoader: LutLoader,
    private val prefsRepo: UserPreferencesRepository,
) : ViewModel() {
    private val _uiState = MutableStateFlow(CameraUiState())
    val uiState: StateFlow<CameraUiState> = _uiState.asStateFlow()
    private var provider: ProcessCameraProvider? = null
    private var ownerRef: WeakReference<LifecycleOwner>? = null
    private var viewRef: WeakReference<PreviewView>? = null
    private val owner get() = ownerRef?.get()
    private val view get() = viewRef?.get()
    private var camera: Camera? = null
    private var observedInfo: CameraInfo? = null
    private var capture: ImageCapture? = null
    private var effect: LutPreviewEffect? = null
    private var rawSession = false
    private var liveEffectFailed = false
    private var persistenceJob: Job? = null
    private var focusJob: Job? = null
    private var generation = 0
    private var outputRatio = 3f/4f
    private var pendingRebind = false
    private val mainExecutor get() = ContextCompat.getMainExecutor(application)

    init {
        viewModelScope.launch {
            val p = prefsRepo.preferencesFlow.first()
            _uiState.value = _uiState.value.copy(
                selectedSim = FilmSimulation.fromSaved(p.lastFilmSim),
                requestedExposureEv = FilmSimulation.fromSaved(p.lastFilmSim).meteredEv,
                aspectRatio = AspectRatio.entries.find { it.name == p.lastAspectRatio } ?: AspectRatio.RATIO_4_3,
                filmSettings = if (p.filmSettings == null) FilmSettings.forProfile(FilmSimulation.fromSaved(p.lastFilmSim)) else FilmSettings.decode(p.filmSettings),
                saveOriginal = p.saveOriginal, saveDng = p.saveDng,
            )
            updateEffect()
            if (camera != null) bindCamera()
            prefsRepo.preferencesFlow.collect { preferences ->
                val changed = preferences.saveDng != _uiState.value.saveDng
                _uiState.value = _uiState.value.copy(saveOriginal = preferences.saveOriginal, saveDng = preferences.saveDng)
                if (changed && !_uiState.value.isCapturing) bindCamera()
            }
        }
    }

    fun clearError() { _uiState.value = _uiState.value.copy(errorMessage = null) }
    private fun error(message: String, e: Throwable? = null) {
        Log.w("Mavrolume",message,e)
        _uiState.value = _uiState.value.copy(errorMessage = message)
    }

    fun startCamera(context: LifecycleOwner, preview: PreviewView) {
        ownerRef = WeakReference(context)
        viewRef = WeakReference(preview)
        val token = ++generation
        val future = ProcessCameraProvider.getInstance(application)
        future.addListener({
            if (token != generation) return@addListener
            runCatching { provider = future.get(); bindCamera() }
                .onFailure { error("Camera could not start. Close other camera apps and retry.",it) }
        },mainExecutor)
    }

    fun rebindCamera() { if (_uiState.value.isCapturing) pendingRebind = true else bindCamera() }
    fun releaseCamera() {
        generation++
        owner?.let { observedInfo?.cameraState?.removeObservers(it) }
        observedInfo = null
        provider?.unbindAll()
        effect?.release(); effect = null
        camera = null; capture = null; viewRef = null; ownerRef = null
        _uiState.value = _uiState.value.copy(ready = false)
    }

    /** Match the capture crop and PreviewView viewport, including after fold/rotation resize. */
    fun configureViewport(ratio: Float) {
        if (kotlin.math.abs(ratio-outputRatio) < 0.001f) return
        outputRatio = ratio
        rebindCamera()
    }

    private fun bindCamera(allowRaw: Boolean = true, allowEffect: Boolean = !liveEffectFailed, chooseResolution: Boolean = true) {
        val p = provider ?: return
        val lifecycle = owner ?: return
        val previewView = view ?: return
        if (_uiState.value.isCapturing) { pendingRebind = true; return }
        pendingRebind = false
        _uiState.value = _uiState.value.copy(ready = false)
        observedInfo?.cameraState?.removeObservers(lifecycle)
        observedInfo = null
        p.unbindAll()
        effect?.release(); effect = null
        try {
            val infos = p.availableCameraInfos
            val options = infos.map { info ->
                val c = Camera2CameraInfo.from(info)
                val facing = c.getCameraCharacteristic(CameraCharacteristics.LENS_FACING)
                val focal = c.getCameraCharacteristic(CameraCharacteristics.LENS_INFO_AVAILABLE_FOCAL_LENGTHS)?.firstOrNull()
                LensOption(c.cameraId, if (facing == CameraCharacteristics.LENS_FACING_FRONT) "Front ${c.cameraId}"
                    else "Rear ${c.cameraId}" + (focal?.let { " · ${String.format(Locale.US,"%.1f",it)}mm" } ?: ""))
            }
            val info = infos.find { Camera2CameraInfo.from(it).cameraId == _uiState.value.selectedCameraId }
                ?: infos.firstOrNull { Camera2CameraInfo.from(it).getCameraCharacteristic(CameraCharacteristics.LENS_FACING) == CameraCharacteristics.LENS_FACING_BACK }
                ?: infos.first()
            val c2 = Camera2CameraInfo.from(info)
            val id = c2.cameraId
            val selector = CameraSelector.Builder().addCameraFilter { list -> list.filter { Camera2CameraInfo.from(it).cameraId == id } }.build()
            val caps = c2.getCameraCharacteristic(CameraCharacteristics.REQUEST_AVAILABLE_CAPABILITIES) ?: intArrayOf()
            val raw = runCatching {
                ImageCapture.getImageCaptureCapabilities(info).supportedOutputFormats.contains(ImageCapture.OUTPUT_FORMAT_RAW_JPEG)
            }.getOrDefault(false)
            val manual = caps.contains(CameraCharacteristics.REQUEST_AVAILABLE_CAPABILITIES_MANUAL_SENSOR)
            val isoRange = c2.getCameraCharacteristic(CameraCharacteristics.SENSOR_INFO_SENSITIVITY_RANGE)
            val exposureRange = c2.getCameraCharacteristic(CameraCharacteristics.SENSOR_INFO_EXPOSURE_TIME_RANGE)
            val isoStops = ISO_STOPS.filter { isoRange?.contains(it) == true }.ifEmpty { listOf(isoRange?.lower ?: 100) }
            val shutterIndices = SHUTTER_SPEEDS.indices.filter { exposureRange?.contains(SHUTTER_SPEEDS[it].first) == true }.ifEmpty { listOf(6) }
            val focusLimit = c2.getCameraCharacteristic(CameraCharacteristics.LENS_INFO_MINIMUM_FOCUS_DISTANCE) ?: 0f
            val map = c2.getCameraCharacteristic(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP)
            val sizes = map?.getOutputSizes(ImageFormat.JPEG)?.toList().orEmpty() +
                runCatching { map?.getHighResolutionOutputSizes(ImageFormat.JPEG)?.toList().orEmpty() }.getOrDefault(emptyList())
            val supports50 = sizes.any { it.width.toLong()*it.height in 45000000L..56000000L }
            val requestedMp = if (_uiState.value.megapixels == 50 && supports50) 50 else 12
            val resolutionSelector = ResolutionSelector.Builder()
                .setAllowedResolutionMode(if(requestedMp == 50) ResolutionSelector.PREFER_HIGHER_RESOLUTION_OVER_CAPTURE_RATE else ResolutionSelector.PREFER_CAPTURE_RATE_OVER_HIGHER_RESOLUTION)
                .setResolutionFilter { candidates, _ -> candidates.sortedBy { kotlin.math.abs(it.width.toLong()*it.height - requestedMp*1000000L) } }
                .build()
            val rotation = previewView.display?.rotation ?: Surface.ROTATION_0
            rawSession = raw && allowRaw && _uiState.value.saveDng
            val captureBuilder = ImageCapture.Builder().setTargetRotation(rotation)
                .setCaptureMode(ImageCapture.CAPTURE_MODE_MAXIMIZE_QUALITY)
                .setJpegQuality(95)
                .setOutputFormat(if (rawSession) ImageCapture.OUTPUT_FORMAT_RAW_JPEG else ImageCapture.OUTPUT_FORMAT_JPEG)
            if (chooseResolution) captureBuilder.setResolutionSelector(resolutionSelector)
            val imageCapture = captureBuilder.build()
            val preview = Preview.Builder().setTargetRotation(rotation).build().also { it.surfaceProvider = previewView.surfaceProvider }
            val fx = if (allowEffect) LutPreviewEffect(lutLoader,_uiState.value.selectedSim) { e ->
                viewModelScope.launch {
                    if (!liveEffectFailed) {
                        liveEffectFailed = true
                        error("Live preview effects are unavailable with this camera. Captured photos will still be processed.",e)
                        bindCamera(allowEffect = false)
                    }
                }
            } else null
            effect = fx
            val viewport = ViewPort.Builder(Rational((outputRatio*10000).toInt(),10000),rotation)
                .setScaleType(ViewPort.FILL_CENTER).build()
            val groupBuilder = UseCaseGroup.Builder().addUseCase(preview).addUseCase(imageCapture).setViewPort(viewport)
            if (fx != null) groupBuilder.addEffect(fx)
            val group = groupBuilder.build()
            camera = p.bindToLifecycle(lifecycle,selector,group)
            capture = imageCapture
            val actualSize = imageCapture.resolutionInfo?.resolution
            val actualMp = actualSize?.let { it.width.toLong()*it.height/1000000f } ?: 0f
            val actual50 = actualMp in 45f..56f
            if (requestedMp == 50 && !actual50) error("50 MP is unavailable with this lens/session. Using ${String.format(Locale.US,"%.1f",actualMp)} MP.")
            val zoom = info.zoomState.value
            val exposure = info.exposureState
            _uiState.value = _uiState.value.copy(
                megapixels = if(requestedMp == 50 && actual50) 50 else 12,
                supports50Mp = supports50,
                resolutionLabel = String.format(Locale.US,"%.1f MP · before crop",actualMp),
                ready = true, lensOptions = options, selectedCameraId = id, rawCapable = raw && allowRaw,
                manualCapable = manual, flashAvailable = info.hasFlashUnit(),
                minZoom = zoom?.minZoomRatio ?: 1f, maxZoom = zoom?.maxZoomRatio ?: 1f,
                zoom = _uiState.value.zoom.coerceIn(zoom?.minZoomRatio ?: 1f,zoom?.maxZoomRatio ?: 1f),
                exposureCompRange = exposure.exposureCompensationRange.lower..exposure.exposureCompensationRange.upper,
                exposureStep = exposure.exposureCompensationStep.toFloat(),
                exposureCompensation = if (exposure.exposureCompensationStep.toFloat() > 0f)
                    (_uiState.value.requestedExposureEv / exposure.exposureCompensationStep.toFloat()).roundToInt()
                        .coerceIn(exposure.exposureCompensationRange.lower,exposure.exposureCompensationRange.upper) else 0,
                maxFocusDistance = focusLimit,
                focusDistance = _uiState.value.focusDistance.coerceIn(0f,focusLimit),
                isoStops = isoStops, iso = _uiState.value.iso.coerceIn(isoStops.first(),isoStops.last()),
                shutterIndices = shutterIndices, shutterSpeedIndex = shutterIndices.minBy { kotlin.math.abs(it-_uiState.value.shutterSpeedIndex) },
                exposureMode = if (manual) _uiState.value.exposureMode else ExposureMode.AUTO,
                isManualFocus = _uiState.value.isManualFocus && focusLimit > 0f,
            )
            imageCapture.flashMode = if (info.hasFlashUnit()) _uiState.value.flashMode else ImageCapture.FLASH_MODE_OFF
            camera?.cameraControl?.setZoomRatio(_uiState.value.zoom)
            applyExposureCompensation()
            applyCameraSettings()
            updateEffect()
            observedInfo = info
            info.cameraState.observe(lifecycle) { state -> state.error?.let { error("Camera unavailable (${it.code}). Tap Retry.") } }
        } catch (e: Exception) {
            if (rawSession && allowRaw) {
                rawSession = false
                error("This camera cannot combine RAW and live effects. Using JPEG.",e)
                viewModelScope.launch { prefsRepo.setSaveDng(false) }
                bindCamera(false,allowEffect,chooseResolution)
            } else if (_uiState.value.megapixels == 50) {
                _uiState.value = _uiState.value.copy(megapixels = 12)
                error("50 MP cannot run in this camera configuration. Switched to 12 MP.",e)
                bindCamera(allowRaw,allowEffect,chooseResolution)
            } else if (allowEffect) {
                liveEffectFailed = true
                error("Live preview effects are unavailable with this camera. Captured photos will still be processed.",e)
                bindCamera(allowRaw,false,chooseResolution)
            } else if (chooseResolution) {
                error("This camera rejected the requested capture size. Using its default size.",e)
                bindCamera(allowRaw,false,false)
            } else error("Camera setup failed. Tap Retry or choose another camera.",e)
        }
    }

    fun setMegapixels(value: Int) {
        if (_uiState.value.isCapturing || value !in listOf(12,50)) return
        if (value == 50 && !_uiState.value.supports50Mp) return
        _uiState.value = _uiState.value.copy(megapixels = value)
        bindCamera()
    }

    fun selectCamera(id: String) {
        if (_uiState.value.isCapturing || id == _uiState.value.selectedCameraId) return
        liveEffectFailed = false
        _uiState.value = _uiState.value.copy(selectedCameraId = id,zoom = 1f, exposureMode = ExposureMode.AUTO)
        bindCamera()
    }
    fun setZoom(value: Float) {
        if (_uiState.value.isCapturing) return
        val zoom = value.coerceIn(_uiState.value.minZoom,_uiState.value.maxZoom)
        camera?.cameraControl?.setZoomRatio(zoom)
        _uiState.value = _uiState.value.copy(zoom = zoom)
    }
    fun cycleFlash() {
        if (!_uiState.value.flashAvailable || _uiState.value.exposureMode == ExposureMode.MANUAL) return
        val mode = when (_uiState.value.flashMode) {
            ImageCapture.FLASH_MODE_OFF -> ImageCapture.FLASH_MODE_AUTO
            ImageCapture.FLASH_MODE_AUTO -> ImageCapture.FLASH_MODE_ON
            else -> ImageCapture.FLASH_MODE_OFF
        }
        capture?.flashMode = mode
        _uiState.value = _uiState.value.copy(flashMode = mode)
    }
    fun selectSim(sim: FilmSimulation) {
        _uiState.value = _uiState.value.copy(selectedSim = sim, requestedExposureEv = sim.meteredEv)
        applyExposureCompensation()
        setFilmSettings(FilmSettings.forProfile(sim))
        viewModelScope.launch { prefsRepo.setLastFilmSim(sim.name) }
    }
    fun setFilmSettings(settings: FilmSettings) {
        _uiState.value = _uiState.value.copy(filmSettings = settings)
        updateEffect()
        persistenceJob?.cancel()
        persistenceJob = viewModelScope.launch { delay(250); prefsRepo.setFilmSettings(settings.encode()) }
    }
    private fun updateEffect() {
        effect?.switchLut(_uiState.value.selectedSim)
        effect?.setFilmSettings(_uiState.value.filmSettings)
        effect?.setPeakingEnabled(_uiState.value.showFocusPeaking)
    }
    fun setAspectRatio(ratio: AspectRatio) {
        if (_uiState.value.isCapturing) return
        _uiState.value = _uiState.value.copy(aspectRatio = ratio)
        viewModelScope.launch { prefsRepo.setLastAspectRatio(ratio.name) }
    }
    fun setSaveOriginal(value: Boolean) { viewModelScope.launch { prefsRepo.setSaveOriginal(value) } }
    fun setSaveDng(value: Boolean) { if (!_uiState.value.isCapturing) viewModelScope.launch { prefsRepo.setSaveDng(value) } }
    fun setExposureCompensation(index: Int) {
        val range = _uiState.value.exposureCompRange
        val v = index.coerceIn(range.first,range.last)
        _uiState.value = _uiState.value.copy(requestedExposureEv = v * _uiState.value.exposureStep)
        applyExposureCompensation()
    }
    private fun applyExposureCompensation() {
        val state = _uiState.value
        val index = if (state.exposureStep > 0f)
            (state.requestedExposureEv / state.exposureStep).roundToInt()
                .coerceIn(state.exposureCompRange.first,state.exposureCompRange.last) else 0
        _uiState.value = state.copy(exposureCompensation = index)
        if (state.exposureMode == ExposureMode.AUTO) camera?.cameraControl?.setExposureCompensationIndex(index)
    }
    fun setExposureMode(mode: ExposureMode) {
        if (mode == ExposureMode.MANUAL && !_uiState.value.manualCapable) return
        _uiState.value = _uiState.value.copy(exposureMode = mode)
        if (mode == ExposureMode.AUTO) applyExposureCompensation()
        capture?.flashMode = if (mode == ExposureMode.MANUAL) ImageCapture.FLASH_MODE_OFF else _uiState.value.flashMode
        applyCameraSettings()
    }
    fun setIso(value: Int) { _uiState.value = _uiState.value.copy(iso = value); applyCameraSettings() }
    fun setShutterSpeedIndex(index: Int) {
        if (index !in _uiState.value.shutterIndices) return
        _uiState.value = _uiState.value.copy(shutterSpeedIndex = index, shutterSpeedLabel = SHUTTER_SPEEDS[index].second)
        applyCameraSettings()
    }
    fun setManualFocus(value: Boolean) {
        if (value && _uiState.value.maxFocusDistance <= 0f) return
        _uiState.value = _uiState.value.copy(isManualFocus = value); applyCameraSettings()
    }
    fun setFocusDistance(value: Float) {
        _uiState.value = _uiState.value.copy(focusDistance = value.coerceIn(0f,_uiState.value.maxFocusDistance)); applyCameraSettings()
    }
    fun setPeaking(value: Boolean) { _uiState.value = _uiState.value.copy(showFocusPeaking = value); updateEffect() }
    fun setWhiteBalance(mode: WhiteBalanceMode) {
        _uiState.value = _uiState.value.copy(whiteBalanceMode = mode)
        applyCameraSettings()
    }
    fun tapToFocus(x: Float,y: Float) {
        val cam = camera ?: return
        val v = view ?: return
        if (_uiState.value.isManualFocus) return
        val action = FocusMeteringAction.Builder(v.meteringPointFactory.createPoint(x,y)).setAutoCancelDuration(4,TimeUnit.SECONDS).build()
        if (!cam.cameraInfo.isFocusMeteringSupported(action)) return
        focusJob?.cancel()
        focusJob = viewModelScope.launch { delay(4000); _uiState.value = _uiState.value.copy(isFocusLocked = false) }
        val result = cam.cameraControl.startFocusAndMetering(action)
        result.addListener({ runCatching { _uiState.value = _uiState.value.copy(isFocusLocked = result.get().isFocusSuccessful) } },mainExecutor)
    }
    private fun applyCameraSettings() {
        val cam = camera ?: return
        val state = _uiState.value
        val builder = CaptureRequestOptions.Builder()
        val awb = when (state.whiteBalanceMode) {
            WhiteBalanceMode.AUTO -> CaptureRequest.CONTROL_AWB_MODE_AUTO
            WhiteBalanceMode.DAYLIGHT -> CaptureRequest.CONTROL_AWB_MODE_DAYLIGHT
            WhiteBalanceMode.CLOUDY -> CaptureRequest.CONTROL_AWB_MODE_CLOUDY_DAYLIGHT
            WhiteBalanceMode.BLUE_ROOM -> CaptureRequest.CONTROL_AWB_MODE_INCANDESCENT
            WhiteBalanceMode.FLUORESCENT -> CaptureRequest.CONTROL_AWB_MODE_FLUORESCENT
            WhiteBalanceMode.SHADE -> CaptureRequest.CONTROL_AWB_MODE_SHADE
        }
        builder.setCaptureRequestOption(CaptureRequest.CONTROL_AWB_MODE, awb)
        if (state.exposureMode == ExposureMode.MANUAL && state.manualCapable) {
            builder.setCaptureRequestOption(CaptureRequest.CONTROL_AE_MODE,CaptureRequest.CONTROL_AE_MODE_OFF)
            builder.setCaptureRequestOption(CaptureRequest.SENSOR_SENSITIVITY,state.iso)
            builder.setCaptureRequestOption(CaptureRequest.SENSOR_EXPOSURE_TIME,SHUTTER_SPEEDS[state.shutterSpeedIndex].first)
        }
        if (state.isManualFocus) {
            builder.setCaptureRequestOption(CaptureRequest.CONTROL_AF_MODE,CaptureRequest.CONTROL_AF_MODE_OFF)
            builder.setCaptureRequestOption(CaptureRequest.LENS_FOCUS_DISTANCE,state.focusDistance)
        }
        Camera2CameraControl.from(cam.cameraControl).setCaptureRequestOptions(builder.build())
    }

    fun capturePhoto() {
        val imageCapture = capture ?: return
        val snapshot = _uiState.value
        if (snapshot.isCapturing || !snapshot.ready) return
        if (application.cacheDir.usableSpace < 150L*1024*1024) { error("Free at least 150 MB before taking a photo."); return }
        _uiState.value = snapshot.copy(isCapturing = true,errorMessage = null)
        val stem = "MAVROLUME_${System.currentTimeMillis()}"
        val jpeg = File(application.cacheDir,"$stem.jpg")
        val dng = File(application.cacheDir,"$stem.dng")
        val expectsRaw = rawSession
        val ratioAtShutter = outputRatio
        var jpegDone = false
        var rawDone = !expectsRaw
        var failed = false
        fun finishIfDone() {
            if (jpegDone && rawDone) {
                _uiState.value = _uiState.value.copy(isCapturing = false)
                if (pendingRebind) bindCamera()
            }
        }
        val callback = object : ImageCapture.OnImageSavedCallback {
            override fun onImageSaved(result: ImageCapture.OutputFileResults) {
                viewModelScope.launch {
                    val isRaw = result.imageFormat == ImageFormat.RAW_SENSOR
                    try {
                        if (isRaw) {
                            withContext(Dispatchers.IO) { saveFile(dng,"$stem.dng","image/x-adobe-dng","Pictures/Mavrolume/RAW") }
                        } else {
                            val uri = processJpeg(jpeg,stem,snapshot,ratioAtShutter)
                            _uiState.value = _uiState.value.copy(lastCapturedUri = uri)
                        }
                    } catch (e: Exception) { error(if (isRaw) "DNG could not be saved." else "Photo could not be processed or saved.",e) }
                    finally {
                        if (isRaw) { rawDone = true; dng.delete() } else { jpegDone = true; jpeg.delete() }
                        finishIfDone()
                        if (failed) _uiState.value = _uiState.value.copy(isCapturing = false)
                    }
                }
            }
            override fun onError(exception: ImageCaptureException) {
                failed = true
                jpeg.delete(); dng.delete()
                _uiState.value = _uiState.value.copy(isCapturing = false)
                error("Capture failed. Retry with RAW disabled if enabled.",exception)
            }
        }
        try {
            val jpegOptions = ImageCapture.OutputFileOptions.Builder(jpeg).build()
            if (expectsRaw) imageCapture.takePicture(ImageCapture.OutputFileOptions.Builder(dng).build(),jpegOptions,mainExecutor,callback)
            else imageCapture.takePicture(jpegOptions,mainExecutor,callback)
        } catch (e: Exception) {
            jpeg.delete(); dng.delete()
            _uiState.value = _uiState.value.copy(isCapturing = false)
            error("Capture could not start.",e)
        }
    }

    private suspend fun processJpeg(file: File,stem: String,state: CameraUiState,ratio: Float): Uri = withContext(Dispatchers.Default) {
        val bytes = file.readBytes()
        if (state.saveOriginal) saveFile(file,"${stem}_original.jpg","image/jpeg","Pictures/Mavrolume/Originals")
        val exif = ExifInterface(file)
        val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(bytes,0,bytes.size,options)
        options.inJustDecodeBounds = false
        options.inSampleSize = 1
        val decoded = BitmapFactory.decodeByteArray(bytes,0,bytes.size,options) ?: kotlin.error("JPEG decode failed")
        val matrix = Matrix().apply {
            if (exif.isFlipped) postScale(-1f,1f)
            postRotate(exif.rotationDegrees.toFloat())
        }
        val rotated = Bitmap.createBitmap(decoded,0,0,decoded.width,decoded.height,matrix,true)
        if (rotated !== decoded) decoded.recycle()
        val cropped = CropEngine.cropToRatio(rotated,ratio)
        if (cropped !== rotated) rotated.recycle()
        val processed = try { imageProcessor.applySimulation(cropped,state.selectedSim,state.filmSettings) } catch (e: Exception) { cropped.recycle(); throw e }
        if (processed !== cropped) cropped.recycle()
        val output = ByteArrayOutputStream().use { stream ->
            processed.compress(Bitmap.CompressFormat.JPEG,95,stream)
            processed.recycle(); stream.toByteArray()
        }
        val uri = saveBytes(output,"${stem}_${state.selectedSim.shortCode}.jpg","image/jpeg","Pictures/Mavrolume")
        ExifHelper.applyToUri(application.contentResolver,uri,ExifHelper.extractFromBytes(bytes),state.selectedSim.shortCode,state.aspectRatio.label)
        uri
    }

    private fun saveFile(file: File,name: String,mime: String,path: String): Uri = publish(name,mime,path) { output -> file.inputStream().use { it.copyTo(output) } }
    private fun saveBytes(bytes: ByteArray,name: String,mime: String,path: String): Uri = publish(name,mime,path) { it.write(bytes) }
    private fun publish(name: String,mime: String,path: String,write: (java.io.OutputStream)->Unit): Uri {
        val resolver = application.contentResolver
        val values = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME,name)
            put(MediaStore.MediaColumns.MIME_TYPE,mime)
            put(MediaStore.MediaColumns.RELATIVE_PATH,path)
            put(MediaStore.MediaColumns.IS_PENDING,1)
        }
        val uri = requireNotNull(resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI,values)) { "MediaStore insert failed" }
        try {
            requireNotNull(resolver.openOutputStream(uri)).use(write)
            resolver.update(uri,ContentValues().apply { put(MediaStore.MediaColumns.IS_PENDING,0) },null,null)
            return uri
        } catch (e: Exception) { resolver.delete(uri,null,null); throw e }
    }
}
