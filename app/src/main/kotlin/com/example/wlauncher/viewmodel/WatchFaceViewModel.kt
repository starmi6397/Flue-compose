package com.flue.launcher.viewmodel

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import android.os.SystemClock
import kotlin.math.roundToInt
import com.flue.launcher.watchface.WatchClockPosition
import com.flue.launcher.watchface.WatchClockColorMode
import com.flue.launcher.watchface.WatchFaceClockStyle
import com.flue.launcher.watchface.WatchFaceMd3eShape
import com.flue.launcher.watchface.LunchWatchFaceDescriptor
import com.flue.launcher.watchface.LunchWatchFaceRegistry
import com.flue.launcher.watchface.LunchWatchFaceScanner
import com.flue.launcher.watchface.jbwatch.JBWATCH_ID_PREFIX
import com.flue.launcher.watchface.jbwatch.JbWatchFaceStorage
import com.flue.launcher.watchface.InternalWatchFaceStorage
import com.flue.launcher.watchface.BUILT_IN_WATCHFACE_ID
import kotlinx.coroutines.delay
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.util.zip.ZipFile

class WatchFaceViewModel(application: Application) : AndroidViewModel(application) {

    companion object {
        private var cachedWatchFaces: List<LunchWatchFaceDescriptor>? = null
    }

    private val store = application.dataStore

    // ===== Available Watch Faces =====
    private val _availableWatchFaces = MutableStateFlow(cachedWatchFaces ?: LunchWatchFaceScanner.builtInDescriptors())
    val availableWatchFaces: StateFlow<List<LunchWatchFaceDescriptor>> = _availableWatchFaces.asStateFlow()

    // ===== Selected Watch Face =====
    private val _selectedWatchFaceId = MutableStateFlow(BUILT_IN_WATCHFACE_ID)
    val selectedWatchFaceId: StateFlow<String> = _selectedWatchFaceId.asStateFlow()

    private val _selectedWatchFace = MutableStateFlow(LunchWatchFaceScanner.builtInDescriptor(BUILT_IN_WATCHFACE_ID))
    val selectedWatchFace: StateFlow<LunchWatchFaceDescriptor> = _selectedWatchFace.asStateFlow()

    private val _watchFaceSelectionReady = MutableStateFlow(false)
    val watchFaceSelectionReady: StateFlow<Boolean> = _watchFaceSelectionReady.asStateFlow()

    private val _watchFaceRefreshToken = MutableStateFlow(0)
    val watchFaceRefreshToken: StateFlow<Int> = _watchFaceRefreshToken.asStateFlow()

    private val _watchFaceLastError = MutableStateFlow<String?>(null)
    val watchFaceLastError: StateFlow<String?> = _watchFaceLastError.asStateFlow()

    // ===== Photo Clock =====
    private val _builtInPhotoPath = MutableStateFlow<String?>(null)
    val builtInPhotoPath: StateFlow<String?> = _builtInPhotoPath.asStateFlow()

    private val _builtInPhotoClockPosition = MutableStateFlow(WatchClockPosition.CENTER)
    val builtInPhotoClockPosition: StateFlow<WatchClockPosition> = _builtInPhotoClockPosition.asStateFlow()

    private val _builtInPhotoClockSize = MutableStateFlow(64)
    val builtInPhotoClockSize: StateFlow<Int> = _builtInPhotoClockSize.asStateFlow()

    private val _builtInPhotoClockBold = MutableStateFlow(false)
    val builtInPhotoClockBold: StateFlow<Boolean> = _builtInPhotoClockBold.asStateFlow()

    private val _builtInPhotoClockStyle = MutableStateFlow(WatchFaceClockStyle.DIGITAL)
    val builtInPhotoClockStyle: StateFlow<WatchFaceClockStyle> = _builtInPhotoClockStyle.asStateFlow()

    private val _builtInPhotoMd3eShape = MutableStateFlow(WatchFaceMd3eShape.COOKIE)
    val builtInPhotoMd3eShape: StateFlow<WatchFaceMd3eShape> = _builtInPhotoMd3eShape.asStateFlow()

    private val _builtInPhotoUseThemeTextColor = MutableStateFlow(true)
    val builtInPhotoUseThemeTextColor: StateFlow<Boolean> = _builtInPhotoUseThemeTextColor.asStateFlow()

    private val _builtInPhotoTextColorArgb = MutableStateFlow(0xFFFFFFFF.toInt())
    val builtInPhotoTextColorArgb: StateFlow<Int> = _builtInPhotoTextColorArgb.asStateFlow()

    private val _builtInPhotoMd3eAutoColors = MutableStateFlow(true)
    val builtInPhotoMd3eAutoColors: StateFlow<Boolean> = _builtInPhotoMd3eAutoColors.asStateFlow()

    private val _builtInPhotoMd3eTextColorArgb = MutableStateFlow(0xFF202938.toInt())
    val builtInPhotoMd3eTextColorArgb: StateFlow<Int> = _builtInPhotoMd3eTextColorArgb.asStateFlow()

    private val _builtInPhotoMd3eFaceColorArgb = MutableStateFlow(0xFFEAF1FF.toInt())
    val builtInPhotoMd3eFaceColorArgb: StateFlow<Int> = _builtInPhotoMd3eFaceColorArgb.asStateFlow()

    private val _builtInPhotoMd3eHourColorArgb = MutableStateFlow(0xFF334155.toInt())
    val builtInPhotoMd3eHourColorArgb: StateFlow<Int> = _builtInPhotoMd3eHourColorArgb.asStateFlow()

    private val _builtInPhotoMd3eMinuteColorArgb = MutableStateFlow(0xFF5F84B6.toInt())
    val builtInPhotoMd3eMinuteColorArgb: StateFlow<Int> = _builtInPhotoMd3eMinuteColorArgb.asStateFlow()

    private val _builtInPhotoMd3eSecondColorArgb = MutableStateFlow(0xFF806EA4.toInt())
    val builtInPhotoMd3eSecondColorArgb: StateFlow<Int> = _builtInPhotoMd3eSecondColorArgb.asStateFlow()

    private val _builtInPhotoShowSeconds = MutableStateFlow(false)
    val builtInPhotoShowSeconds: StateFlow<Boolean> = _builtInPhotoShowSeconds.asStateFlow()

    private val _builtInPhotoCustomText = MutableStateFlow("")
    val builtInPhotoCustomText: StateFlow<String> = _builtInPhotoCustomText.asStateFlow()

    // ===== Video Clock =====
    private val _builtInVideoPath = MutableStateFlow<String?>(null)
    val builtInVideoPath: StateFlow<String?> = _builtInVideoPath.asStateFlow()

    private val _builtInVideoClockPosition = MutableStateFlow(WatchClockPosition.CENTER)
    val builtInVideoClockPosition: StateFlow<WatchClockPosition> = _builtInVideoClockPosition.asStateFlow()

    private val _builtInVideoClockSize = MutableStateFlow(64)
    val builtInVideoClockSize: StateFlow<Int> = _builtInVideoClockSize.asStateFlow()

    private val _builtInVideoClockBold = MutableStateFlow(false)
    val builtInVideoClockBold: StateFlow<Boolean> = _builtInVideoClockBold.asStateFlow()

    private val _builtInVideoClockStyle = MutableStateFlow(WatchFaceClockStyle.DIGITAL)
    val builtInVideoClockStyle: StateFlow<WatchFaceClockStyle> = _builtInVideoClockStyle.asStateFlow()

    private val _builtInVideoMd3eShape = MutableStateFlow(WatchFaceMd3eShape.COOKIE)
    val builtInVideoMd3eShape: StateFlow<WatchFaceMd3eShape> = _builtInVideoMd3eShape.asStateFlow()

    private val _builtInVideoUseThemeTextColor = MutableStateFlow(true)
    val builtInVideoUseThemeTextColor: StateFlow<Boolean> = _builtInVideoUseThemeTextColor.asStateFlow()

    private val _builtInVideoTextColorArgb = MutableStateFlow(0xFFFFFFFF.toInt())
    val builtInVideoTextColorArgb: StateFlow<Int> = _builtInVideoTextColorArgb.asStateFlow()

    private val _builtInVideoMd3eAutoColors = MutableStateFlow(true)
    val builtInVideoMd3eAutoColors: StateFlow<Boolean> = _builtInVideoMd3eAutoColors.asStateFlow()

    private val _builtInVideoMd3eTextColorArgb = MutableStateFlow(0xFF202938.toInt())
    val builtInVideoMd3eTextColorArgb: StateFlow<Int> = _builtInVideoMd3eTextColorArgb.asStateFlow()

    private val _builtInVideoMd3eFaceColorArgb = MutableStateFlow(0xFFEAF1FF.toInt())
    val builtInVideoMd3eFaceColorArgb: StateFlow<Int> = _builtInVideoMd3eFaceColorArgb.asStateFlow()

    private val _builtInVideoMd3eHourColorArgb = MutableStateFlow(0xFF334155.toInt())
    val builtInVideoMd3eHourColorArgb: StateFlow<Int> = _builtInVideoMd3eHourColorArgb.asStateFlow()

    private val _builtInVideoMd3eMinuteColorArgb = MutableStateFlow(0xFF5F84B6.toInt())
    val builtInVideoMd3eMinuteColorArgb: StateFlow<Int> = _builtInVideoMd3eMinuteColorArgb.asStateFlow()

    private val _builtInVideoMd3eSecondColorArgb = MutableStateFlow(0xFF806EA4.toInt())
    val builtInVideoMd3eSecondColorArgb: StateFlow<Int> = _builtInVideoMd3eSecondColorArgb.asStateFlow()

    private val _builtInVideoShowSeconds = MutableStateFlow(false)
    val builtInVideoShowSeconds: StateFlow<Boolean> = _builtInVideoShowSeconds.asStateFlow()

    private val _builtInVideoCustomText = MutableStateFlow("")
    val builtInVideoCustomText: StateFlow<String> = _builtInVideoCustomText.asStateFlow()

    private val _builtInVideoFillScreen = MutableStateFlow(true)
    val builtInVideoFillScreen: StateFlow<Boolean> = _builtInVideoFillScreen.asStateFlow()

    private val _builtInVideoClockColorMode = MutableStateFlow(WatchClockColorMode.AUTO)
    val builtInVideoClockColorMode: StateFlow<WatchClockColorMode> = _builtInVideoClockColorMode.asStateFlow()

    // ===== DingDingCat =====
    private val _dingDingCatFillScreen = MutableStateFlow(false)
    val dingDingCatFillScreen: StateFlow<Boolean> = _dingDingCatFillScreen.asStateFlow()

    private val _dingDingCatPlaybackSpeedPercent = MutableStateFlow(100)
    val dingDingCatPlaybackSpeedPercent: StateFlow<Int> = _dingDingCatPlaybackSpeedPercent.asStateFlow()

    private val _dingDingCatImportUnlocked = MutableStateFlow(false)
    val dingDingCatImportUnlocked: StateFlow<Boolean> = _dingDingCatImportUnlocked.asStateFlow()

    // ===== Status Indicators =====
    private val _watchFaceChargingPowerText = MutableStateFlow(true)
    val watchFaceChargingPowerText: StateFlow<Boolean> = _watchFaceChargingPowerText.asStateFlow()

    private val _watchFaceStatusIndicators = MutableStateFlow(true)
    val watchFaceStatusIndicators: StateFlow<Boolean> = _watchFaceStatusIndicators.asStateFlow()

    private val _watchFaceBottomFadeEnabled = MutableStateFlow(true)
    val watchFaceBottomFadeEnabled: StateFlow<Boolean> = _watchFaceBottomFadeEnabled.asStateFlow()

    private val _builtInManagerThumbnails = MutableStateFlow(true)
    val builtInManagerThumbnails: StateFlow<Boolean> = _builtInManagerThumbnails.asStateFlow()

    private val _builtInWatchFaceFontPath = MutableStateFlow<String?>(null)
    val builtInWatchFaceFontPath: StateFlow<String?> = _builtInWatchFaceFontPath.asStateFlow()

    // ===== Internal State =====
    private var watchFacePrefsHydrated = false
    private var watchFaceScanHydrated = false
    private var lastWatchFaceRefreshAt = 0L
    private var watchFaceRefreshJob: Job? = null
    private val pendingWriteJobs = java.util.concurrent.ConcurrentHashMap<String, Job>()

    init {
        viewModelScope.launch(Dispatchers.IO) {
            store.data.collect { prefs ->
                val loadedWatchFaceId = prefs[PrefKeys.KEY_SELECTED_WATCHFACE_ID] ?: BUILT_IN_WATCHFACE_ID
                if (_selectedWatchFaceId.value != loadedWatchFaceId) _selectedWatchFaceId.value = loadedWatchFaceId

                val loadedWatchFaceError = prefs[PrefKeys.KEY_LAST_WATCHFACE_ERROR]
                if (_watchFaceLastError.value != loadedWatchFaceError) _watchFaceLastError.value = loadedWatchFaceError

                val loadedPhotoPath = prefs[PrefKeys.KEY_BUILTIN_PHOTO_PATH]
                if (_builtInPhotoPath.value != loadedPhotoPath) _builtInPhotoPath.value = loadedPhotoPath

                val loadedVideoPath = prefs[PrefKeys.KEY_BUILTIN_VIDEO_PATH]
                if (_builtInVideoPath.value != loadedVideoPath) _builtInVideoPath.value = loadedVideoPath

                fun parseClockPosition(value: String?): WatchClockPosition =
                    try { value?.let { WatchClockPosition.valueOf(it) } ?: WatchClockPosition.CENTER }
                    catch (_: Exception) { WatchClockPosition.CENTER }

                fun parseClockStyle(value: String?): WatchFaceClockStyle =
                    try { value?.let { WatchFaceClockStyle.valueOf(it) } ?: WatchFaceClockStyle.DIGITAL }
                    catch (_: Exception) { WatchFaceClockStyle.DIGITAL }

                fun parseMd3eShape(value: String?): WatchFaceMd3eShape =
                    try { value?.let { WatchFaceMd3eShape.valueOf(it) } ?: WatchFaceMd3eShape.COOKIE }
                    catch (_: Exception) { WatchFaceMd3eShape.COOKIE }

                fun parseClockColorMode(value: String?): WatchClockColorMode =
                    try { value?.let { WatchClockColorMode.valueOf(it) } ?: WatchClockColorMode.AUTO }
                    catch (_: Exception) { WatchClockColorMode.AUTO }

                _builtInPhotoClockPosition.value = parseClockPosition(prefs[PrefKeys.KEY_PHOTO_CLOCK_POSITION])
                _builtInVideoClockPosition.value = parseClockPosition(prefs[PrefKeys.KEY_VIDEO_CLOCK_POSITION])
                _builtInPhotoClockSize.value = (prefs[PrefKeys.KEY_PHOTO_CLOCK_SIZE] ?: 64).coerceIn(28, 92)
                _builtInVideoClockSize.value = (prefs[PrefKeys.KEY_VIDEO_CLOCK_SIZE] ?: 64).coerceIn(28, 92)
                _builtInPhotoClockBold.value = prefs[PrefKeys.KEY_PHOTO_CLOCK_BOLD] ?: false
                _builtInVideoClockBold.value = prefs[PrefKeys.KEY_VIDEO_CLOCK_BOLD] ?: false
                _builtInVideoFillScreen.value = prefs[PrefKeys.KEY_VIDEO_FILL_SCREEN] ?: true
                _builtInVideoClockColorMode.value = parseClockColorMode(prefs[PrefKeys.KEY_VIDEO_CLOCK_COLOR_MODE])
                _builtInWatchFaceFontPath.value = prefs[PrefKeys.KEY_WATCHFACE_FONT_PATH]
                _builtInPhotoClockStyle.value = parseClockStyle(prefs[PrefKeys.KEY_PHOTO_CLOCK_STYLE])
                _builtInVideoClockStyle.value = parseClockStyle(prefs[PrefKeys.KEY_VIDEO_CLOCK_STYLE])
                _builtInPhotoMd3eShape.value = parseMd3eShape(prefs[PrefKeys.KEY_PHOTO_MD3E_SHAPE])
                _builtInVideoMd3eShape.value = parseMd3eShape(prefs[PrefKeys.KEY_VIDEO_MD3E_SHAPE])
                _builtInPhotoUseThemeTextColor.value = prefs[PrefKeys.KEY_PHOTO_USE_THEME_TEXT_COLOR] ?: true
                _builtInVideoUseThemeTextColor.value = prefs[PrefKeys.KEY_VIDEO_USE_THEME_TEXT_COLOR] ?: true
                _builtInPhotoTextColorArgb.value = prefs[PrefKeys.KEY_PHOTO_TEXT_COLOR] ?: 0xFFFFFFFF.toInt()
                _builtInVideoTextColorArgb.value = prefs[PrefKeys.KEY_VIDEO_TEXT_COLOR] ?: 0xFFFFFFFF.toInt()
                _builtInPhotoMd3eAutoColors.value = prefs[PrefKeys.KEY_PHOTO_MD3E_AUTO_COLORS] ?: true
                _builtInVideoMd3eAutoColors.value = prefs[PrefKeys.KEY_VIDEO_MD3E_AUTO_COLORS] ?: true
                _builtInPhotoMd3eTextColorArgb.value = prefs[PrefKeys.KEY_PHOTO_MD3E_TEXT_COLOR] ?: 0xFF202938.toInt()
                _builtInVideoMd3eTextColorArgb.value = prefs[PrefKeys.KEY_VIDEO_MD3E_TEXT_COLOR] ?: 0xFF202938.toInt()
                _builtInPhotoMd3eFaceColorArgb.value = prefs[PrefKeys.KEY_PHOTO_MD3E_FACE_COLOR] ?: 0xFFEAF1FF.toInt()
                _builtInVideoMd3eFaceColorArgb.value = prefs[PrefKeys.KEY_VIDEO_MD3E_FACE_COLOR] ?: 0xFFEAF1FF.toInt()
                _builtInPhotoMd3eHourColorArgb.value = prefs[PrefKeys.KEY_PHOTO_MD3E_HOUR_COLOR] ?: 0xFF334155.toInt()
                _builtInVideoMd3eHourColorArgb.value = prefs[PrefKeys.KEY_VIDEO_MD3E_HOUR_COLOR] ?: 0xFF334155.toInt()
                _builtInPhotoMd3eMinuteColorArgb.value = prefs[PrefKeys.KEY_PHOTO_MD3E_MINUTE_COLOR] ?: 0xFF5F84B6.toInt()
                _builtInVideoMd3eMinuteColorArgb.value = prefs[PrefKeys.KEY_VIDEO_MD3E_MINUTE_COLOR] ?: 0xFF5F84B6.toInt()
                _builtInPhotoMd3eSecondColorArgb.value = prefs[PrefKeys.KEY_PHOTO_MD3E_SECOND_COLOR] ?: 0xFF806EA4.toInt()
                _builtInVideoMd3eSecondColorArgb.value = prefs[PrefKeys.KEY_VIDEO_MD3E_SECOND_COLOR] ?: 0xFF806EA4.toInt()
                _builtInPhotoShowSeconds.value = prefs[PrefKeys.KEY_PHOTO_SHOW_SECONDS] ?: false
                _builtInVideoShowSeconds.value = prefs[PrefKeys.KEY_VIDEO_SHOW_SECONDS] ?: false
                _builtInPhotoCustomText.value = prefs[PrefKeys.KEY_PHOTO_CUSTOM_TEXT].orEmpty()
                _builtInVideoCustomText.value = prefs[PrefKeys.KEY_VIDEO_CUSTOM_TEXT].orEmpty()
                _builtInManagerThumbnails.value = prefs[PrefKeys.KEY_BUILTIN_MANAGER_THUMBNAILS] ?: true
                _watchFaceChargingPowerText.value = prefs[PrefKeys.KEY_WATCHFACE_CHARGING_POWER_TEXT] ?: true
                _watchFaceStatusIndicators.value = prefs[PrefKeys.KEY_WATCHFACE_STATUS_INDICATORS] ?: true
                _watchFaceBottomFadeEnabled.value = prefs[PrefKeys.KEY_WATCHFACE_BOTTOM_FADE] ?: true
                _dingDingCatFillScreen.value = prefs[PrefKeys.KEY_DINGDINGCAT_FILL_SCREEN] ?: false
                _dingDingCatPlaybackSpeedPercent.value = prefs[PrefKeys.KEY_DINGDINGCAT_PLAYBACK_SPEED_PERCENT] ?: 100
                _dingDingCatImportUnlocked.value = prefs[PrefKeys.KEY_DINGDINGCAT_IMPORT_UNLOCKED] ?: false

                watchFacePrefsHydrated = true
                syncSelectedWatchFace()
            }
        }
    }

    // ===== Watch Face Selection =====

    fun setSelectedWatchFace(id: String) {
        _selectedWatchFaceId.value = id
        _watchFaceLastError.value = null
        if (isImportedArchiveId(id) && _availableWatchFaces.value.none { it.id == id }) {
            viewModelScope.launch {
                val scanned = withContext(Dispatchers.IO) { scanAllWatchFaces() }
                publishWatchFaces(scanned)
                syncSelectedWatchFace(freshScanCompleted = true)
                _watchFaceRefreshToken.value = _watchFaceRefreshToken.value + 1
                store.edit {
                    it[PrefKeys.KEY_SELECTED_WATCHFACE_ID] = _selectedWatchFaceId.value
                    it.remove(PrefKeys.KEY_LAST_WATCHFACE_ERROR)
                }
            }
            return
        }
        syncSelectedWatchFace()
        _watchFaceRefreshToken.value = _watchFaceRefreshToken.value + 1
        persist { store.edit { it[PrefKeys.KEY_SELECTED_WATCHFACE_ID] = id; it.remove(PrefKeys.KEY_LAST_WATCHFACE_ERROR) } }
    }

    private fun syncSelectedWatchFace(freshScanCompleted: Boolean = false) {
        val requestedId = _selectedWatchFaceId.value.ifBlank { BUILT_IN_WATCHFACE_ID }
        val available = _availableWatchFaces.value
        val match = available.firstOrNull { it.id == requestedId }

        when {
            match != null -> {
                _selectedWatchFace.value = match
                LunchWatchFaceRegistry.setCurrentSelectedId(match.id)
            }
            isImportedArchiveId(requestedId) && _selectedWatchFace.value.isJbWatch -> {
                LunchWatchFaceRegistry.setCurrentSelectedId(requestedId)
            }
            isImportedArchiveId(requestedId) -> {
                if (watchFaceRefreshJob?.isActive != true) {
                    refreshWatchFaces(force = true)
                }
            }
            else -> {
                _selectedWatchFace.value = available.firstOrNull()
                    ?: LunchWatchFaceScanner.builtInDescriptor(BUILT_IN_WATCHFACE_ID)
                _selectedWatchFaceId.value = BUILT_IN_WATCHFACE_ID
                LunchWatchFaceRegistry.setCurrentSelectedId(BUILT_IN_WATCHFACE_ID)
            }
        }
        _watchFaceSelectionReady.value = true
    }

    private fun isImportedArchiveId(id: String): Boolean = id.startsWith(JBWATCH_ID_PREFIX)

    fun refreshWatchFaces(force: Boolean = false) {
        val now = SystemClock.elapsedRealtime()
        cachedWatchFaces?.let { cached ->
            if (_availableWatchFaces.value != cached) {
                _availableWatchFaces.value = cached
                LunchWatchFaceRegistry.update(cached)
                watchFaceScanHydrated = true
                syncSelectedWatchFace(freshScanCompleted = true)
            }
        }
        if (!force && watchFaceScanHydrated && now - lastWatchFaceRefreshAt < 25_000L) return
        lastWatchFaceRefreshAt = now
        watchFaceRefreshJob?.cancel()
        watchFaceRefreshJob = viewModelScope.launch {
            val scanned = withContext(Dispatchers.IO) { scanAllWatchFaces() }
            publishWatchFaces(scanned)
            syncSelectedWatchFace(freshScanCompleted = true)
        }
    }

    private fun scanAllWatchFaces(): List<LunchWatchFaceDescriptor> {
        val context = getApplication<Application>()
        return LunchWatchFaceScanner.builtInDescriptors() +
            LunchWatchFaceScanner.scanInstalled(context) +
            JbWatchFaceStorage.scan(context)
    }

    private fun publishWatchFaces(watchFaces: List<LunchWatchFaceDescriptor>) {
        cachedWatchFaces = watchFaces
        _availableWatchFaces.value = watchFaces
        LunchWatchFaceRegistry.update(watchFaces)
        watchFaceScanHydrated = true
    }

    fun fallbackToBuiltIn(error: String? = null) {
        _selectedWatchFaceId.value = BUILT_IN_WATCHFACE_ID
        _watchFaceLastError.value = error
        syncSelectedWatchFace()
        persist { store.edit { it[PrefKeys.KEY_SELECTED_WATCHFACE_ID] = BUILT_IN_WATCHFACE_ID; if (error.isNullOrBlank()) it.remove(PrefKeys.KEY_LAST_WATCHFACE_ERROR) else it[PrefKeys.KEY_LAST_WATCHFACE_ERROR] = error } }
    }

    fun clearWatchFaceError() {
        _watchFaceLastError.value = null
        persist { store.edit { it.remove(PrefKeys.KEY_LAST_WATCHFACE_ERROR) } }
    }

    fun requestWatchFaceRefresh() {
        _watchFaceRefreshToken.value = _watchFaceRefreshToken.value + 1
    }

    // ===== Import =====

    suspend fun importJbWatchFace(uri: Uri): Result<LunchWatchFaceDescriptor> {
        return runCatching {
            watchFaceRefreshJob?.cancel()
            val descriptor = withContext(Dispatchers.IO) {
                JbWatchFaceStorage.importArchive(getApplication(), uri)
            }
            finishImportedWatchFaceImport(descriptor)
        }
    }

    suspend fun importWatchFaceArchive(
        uri: Uri,
        allowDingDingCat: Boolean = false
    ): Result<LunchWatchFaceDescriptor> {
        return runCatching {
            watchFaceRefreshJob?.cancel()
            val descriptor = withContext(Dispatchers.IO) {
                withTemporaryWatchFaceArchive(uri) { archive ->
                    when (sniffWatchFaceArchive(archive)) {
                        WatchFaceArchiveKind.JbWatch -> JbWatchFaceStorage.importArchiveFile(getApplication(), archive)
                        WatchFaceArchiveKind.Unknown -> error("无法识别 jb_watch/.watch 表盘包：需要 watch.xml/watch.pxml")
                    }
                }
            }
            finishImportedWatchFaceImport(descriptor)
        }
    }

    private suspend fun finishImportedWatchFaceImport(descriptor: LunchWatchFaceDescriptor): LunchWatchFaceDescriptor {
        val scannedRaw = withContext(Dispatchers.IO) { scanAllWatchFaces() }
        val scanned = if (scannedRaw.any { it.id == descriptor.id }) scannedRaw else scannedRaw + descriptor
        publishWatchFaces(scanned)
        val selected = scanned.firstOrNull { it.id == descriptor.id } ?: descriptor
        _selectedWatchFaceId.value = selected.id
        _watchFaceLastError.value = null
        _selectedWatchFace.value = selected
        LunchWatchFaceRegistry.setCurrentSelectedId(selected.id)
        _watchFaceRefreshToken.value = _watchFaceRefreshToken.value + 1
        lastWatchFaceRefreshAt = SystemClock.elapsedRealtime()
        withContext(Dispatchers.IO) { store.edit { it[PrefKeys.KEY_SELECTED_WATCHFACE_ID] = selected.id; it.remove(PrefKeys.KEY_LAST_WATCHFACE_ERROR) } }
        syncSelectedWatchFace(freshScanCompleted = true)
        return selected
    }

    suspend fun deleteImportedWatchFace(id: String): Result<Unit> {
        return runCatching {
            val descriptor = _availableWatchFaces.value.firstOrNull { it.id == id && it.isJbWatch }
                ?: error("表盘不存在")
            val deleted = withContext(Dispatchers.IO) {
                if (descriptor.isJbWatch) JbWatchFaceStorage.delete(getApplication(), descriptor) else false
            }
            if (!deleted) error("删除失败")
            val scanned = withContext(Dispatchers.IO) { scanAllWatchFaces() }
            publishWatchFaces(scanned)
            if (_selectedWatchFaceId.value == id) {
                _selectedWatchFaceId.value = BUILT_IN_WATCHFACE_ID
                _watchFaceLastError.value = null
                persist { store.edit { it[PrefKeys.KEY_SELECTED_WATCHFACE_ID] = BUILT_IN_WATCHFACE_ID; it.remove(PrefKeys.KEY_LAST_WATCHFACE_ERROR) } }
            }
            syncSelectedWatchFace(freshScanCompleted = true)
            _watchFaceRefreshToken.value = _watchFaceRefreshToken.value + 1
        }
    }

    private suspend fun <T> withTemporaryWatchFaceArchive(uri: Uri, block: (File) -> T): T {
        val context = getApplication<Application>()
        val tempZip = File.createTempFile("watchface_import_", ".zip", context.cacheDir)
        try {
            context.contentResolver.openInputStream(uri)?.use { input ->
                tempZip.outputStream().use { output -> input.copyTo(output) }
            } ?: error("无法读取表盘包")
            return block(tempZip)
        } finally {
            tempZip.delete()
        }
    }

    private enum class WatchFaceArchiveKind { JbWatch, Unknown }

    private fun sniffWatchFaceArchive(archive: File): WatchFaceArchiveKind {
        return ZipFile(archive).use { zip ->
            val names = zip.entries().asSequence()
                .filterNot { it.isDirectory }
                .map { it.name.replace('\\', '/').substringAfterLast('/') }
                .toSet()
            when {
                "watch.xml" in names && "watch.pxml" in names -> WatchFaceArchiveKind.JbWatch
                else -> WatchFaceArchiveKind.Unknown
            }
        }
    }

    // ===== Setters =====

    fun setBuiltInPhotoPath(path: String?) {
        val previousPath = _builtInPhotoPath.value
        _builtInPhotoPath.value = path
        _watchFaceRefreshToken.value = _watchFaceRefreshToken.value + 1
        persistDebounced("photo_path") {
            store.edit {
                if (path.isNullOrBlank()) it.remove(PrefKeys.KEY_BUILTIN_PHOTO_PATH)
                else it[PrefKeys.KEY_BUILTIN_PHOTO_PATH] = path
            }
        }
    }

    fun setBuiltInVideoPath(path: String?) {
        _builtInVideoPath.value = path
        _watchFaceRefreshToken.value = _watchFaceRefreshToken.value + 1
        persistDebounced("video_path") {
            store.edit {
                if (path.isNullOrBlank()) it.remove(PrefKeys.KEY_BUILTIN_VIDEO_PATH)
                else it[PrefKeys.KEY_BUILTIN_VIDEO_PATH] = path
            }
        }
    }

    fun setBuiltInPhotoClockPosition(position: WatchClockPosition) {
        _builtInPhotoClockPosition.value = position
        persist { store.edit { it[PrefKeys.KEY_PHOTO_CLOCK_POSITION] = position.name } }
    }

    fun setBuiltInVideoClockPosition(position: WatchClockPosition) {
        _builtInVideoClockPosition.value = position
        persist { store.edit { it[PrefKeys.KEY_VIDEO_CLOCK_POSITION] = position.name } }
    }

    fun setBuiltInPhotoClockSize(sizeSp: Int) {
        _builtInPhotoClockSize.value = sizeSp.coerceIn(28, 92)
        persistDebounced("photo_clock_size") { store.edit { it[PrefKeys.KEY_PHOTO_CLOCK_SIZE] = _builtInPhotoClockSize.value } }
    }

    fun setBuiltInVideoClockSize(sizeSp: Int) {
        _builtInVideoClockSize.value = sizeSp.coerceIn(28, 92)
        persistDebounced("video_clock_size") { store.edit { it[PrefKeys.KEY_VIDEO_CLOCK_SIZE] = _builtInVideoClockSize.value } }
    }

    fun setBuiltInPhotoClockBold(enabled: Boolean) {
        _builtInPhotoClockBold.value = enabled
        persist { store.edit { it[PrefKeys.KEY_PHOTO_CLOCK_BOLD] = enabled } }
    }

    fun setBuiltInVideoClockBold(enabled: Boolean) {
        _builtInVideoClockBold.value = enabled
        persist { store.edit { it[PrefKeys.KEY_VIDEO_CLOCK_BOLD] = enabled } }
    }

    fun setBuiltInVideoFillScreen(fillScreen: Boolean) {
        _builtInVideoFillScreen.value = fillScreen
        persist { store.edit { it[PrefKeys.KEY_VIDEO_FILL_SCREEN] = fillScreen } }
    }

    fun setBuiltInVideoClockColorMode(mode: WatchClockColorMode) {
        _builtInVideoClockColorMode.value = mode
        persist { store.edit { it[PrefKeys.KEY_VIDEO_CLOCK_COLOR_MODE] = mode.name } }
    }

    fun setBuiltInWatchFaceFontPath(path: String?) {
        _builtInWatchFaceFontPath.value = path
        if (path.isNullOrBlank()) InternalWatchFaceStorage.clearFont(getApplication())
        persist { store.edit { if (path.isNullOrBlank()) it.remove(PrefKeys.KEY_WATCHFACE_FONT_PATH) else it[PrefKeys.KEY_WATCHFACE_FONT_PATH] = path } }
    }

    fun setBuiltInPhotoClockStyle(style: WatchFaceClockStyle) {
        _builtInPhotoClockStyle.value = style
        persist { store.edit { it[PrefKeys.KEY_PHOTO_CLOCK_STYLE] = style.name } }
    }

    fun setBuiltInVideoClockStyle(style: WatchFaceClockStyle) {
        _builtInVideoClockStyle.value = style
        persist { store.edit { it[PrefKeys.KEY_VIDEO_CLOCK_STYLE] = style.name } }
    }

    fun setBuiltInPhotoMd3eShape(shape: WatchFaceMd3eShape) {
        _builtInPhotoMd3eShape.value = shape
        persist { store.edit { it[PrefKeys.KEY_PHOTO_MD3E_SHAPE] = shape.name } }
    }

    fun setBuiltInVideoMd3eShape(shape: WatchFaceMd3eShape) {
        _builtInVideoMd3eShape.value = shape
        persist { store.edit { it[PrefKeys.KEY_VIDEO_MD3E_SHAPE] = shape.name } }
    }

    fun setBuiltInPhotoUseThemeTextColor(enabled: Boolean) {
        _builtInPhotoUseThemeTextColor.value = enabled
        persist { store.edit { it[PrefKeys.KEY_PHOTO_USE_THEME_TEXT_COLOR] = enabled } }
    }

    fun setBuiltInVideoUseThemeTextColor(enabled: Boolean) {
        _builtInVideoUseThemeTextColor.value = enabled
        persist { store.edit { it[PrefKeys.KEY_VIDEO_USE_THEME_TEXT_COLOR] = enabled } }
    }

    fun setBuiltInPhotoTextColorArgb(argb: Int) {
        _builtInPhotoTextColorArgb.value = argb
        persist { store.edit { it[PrefKeys.KEY_PHOTO_TEXT_COLOR] = argb } }
    }

    fun setBuiltInVideoTextColorArgb(argb: Int) {
        _builtInVideoTextColorArgb.value = argb
        persist { store.edit { it[PrefKeys.KEY_VIDEO_TEXT_COLOR] = argb } }
    }

    fun setBuiltInPhotoMd3eAutoColors(enabled: Boolean) {
        _builtInPhotoMd3eAutoColors.value = enabled
        persist { store.edit { it[PrefKeys.KEY_PHOTO_MD3E_AUTO_COLORS] = enabled } }
    }

    fun setBuiltInVideoMd3eAutoColors(enabled: Boolean) {
        _builtInVideoMd3eAutoColors.value = enabled
        persist { store.edit { it[PrefKeys.KEY_VIDEO_MD3E_AUTO_COLORS] = enabled } }
    }

    fun setBuiltInPhotoMd3eTextColorArgb(argb: Int) {
        _builtInPhotoMd3eTextColorArgb.value = argb
        persist { store.edit { it[PrefKeys.KEY_PHOTO_MD3E_TEXT_COLOR] = argb } }
    }

    fun setBuiltInVideoMd3eTextColorArgb(argb: Int) {
        _builtInVideoMd3eTextColorArgb.value = argb
        persist { store.edit { it[PrefKeys.KEY_VIDEO_MD3E_TEXT_COLOR] = argb } }
    }

    fun setBuiltInPhotoMd3eFaceColorArgb(argb: Int) {
        _builtInPhotoMd3eFaceColorArgb.value = argb
        persist { store.edit { it[PrefKeys.KEY_PHOTO_MD3E_FACE_COLOR] = argb } }
    }

    fun setBuiltInVideoMd3eFaceColorArgb(argb: Int) {
        _builtInVideoMd3eFaceColorArgb.value = argb
        persist { store.edit { it[PrefKeys.KEY_VIDEO_MD3E_FACE_COLOR] = argb } }
    }

    fun setBuiltInPhotoMd3eHourColorArgb(argb: Int) {
        _builtInPhotoMd3eHourColorArgb.value = argb
        persist { store.edit { it[PrefKeys.KEY_PHOTO_MD3E_HOUR_COLOR] = argb } }
    }

    fun setBuiltInVideoMd3eHourColorArgb(argb: Int) {
        _builtInVideoMd3eHourColorArgb.value = argb
        persist { store.edit { it[PrefKeys.KEY_VIDEO_MD3E_HOUR_COLOR] = argb } }
    }

    fun setBuiltInPhotoMd3eMinuteColorArgb(argb: Int) {
        _builtInPhotoMd3eMinuteColorArgb.value = argb
        persist { store.edit { it[PrefKeys.KEY_PHOTO_MD3E_MINUTE_COLOR] = argb } }
    }

    fun setBuiltInVideoMd3eMinuteColorArgb(argb: Int) {
        _builtInVideoMd3eMinuteColorArgb.value = argb
        persist { store.edit { it[PrefKeys.KEY_VIDEO_MD3E_MINUTE_COLOR] = argb } }
    }

    fun setBuiltInPhotoMd3eSecondColorArgb(argb: Int) {
        _builtInPhotoMd3eSecondColorArgb.value = argb
        persist { store.edit { it[PrefKeys.KEY_PHOTO_MD3E_SECOND_COLOR] = argb } }
    }

    fun setBuiltInVideoMd3eSecondColorArgb(argb: Int) {
        _builtInVideoMd3eSecondColorArgb.value = argb
        persist { store.edit { it[PrefKeys.KEY_VIDEO_MD3E_SECOND_COLOR] = argb } }
    }

    fun setBuiltInPhotoShowSeconds(enabled: Boolean) {
        _builtInPhotoShowSeconds.value = enabled
        persist { store.edit { it[PrefKeys.KEY_PHOTO_SHOW_SECONDS] = enabled } }
    }

    fun setBuiltInVideoShowSeconds(enabled: Boolean) {
        _builtInVideoShowSeconds.value = enabled
        persist { store.edit { it[PrefKeys.KEY_VIDEO_SHOW_SECONDS] = enabled } }
    }

    fun setBuiltInPhotoCustomText(text: String) {
        _builtInPhotoCustomText.value = text
        persistDebounced("photo_custom_text") { store.edit { if (text.isBlank()) it.remove(PrefKeys.KEY_PHOTO_CUSTOM_TEXT) else it[PrefKeys.KEY_PHOTO_CUSTOM_TEXT] = text } }
    }

    fun setBuiltInVideoCustomText(text: String) {
        _builtInVideoCustomText.value = text
        persistDebounced("video_custom_text") { store.edit { if (text.isBlank()) it.remove(PrefKeys.KEY_VIDEO_CUSTOM_TEXT) else it[PrefKeys.KEY_VIDEO_CUSTOM_TEXT] = text } }
    }

    fun setBuiltInManagerThumbnails(enabled: Boolean) {
        _builtInManagerThumbnails.value = enabled
        persist { store.edit { it[PrefKeys.KEY_BUILTIN_MANAGER_THUMBNAILS] = enabled } }
    }

    fun setWatchFaceChargingPowerTextEnabled(enabled: Boolean) {
        _watchFaceChargingPowerText.value = enabled
        persist { store.edit { it[PrefKeys.KEY_WATCHFACE_CHARGING_POWER_TEXT] = enabled } }
    }

    fun setWatchFaceStatusIndicatorsEnabled(enabled: Boolean) {
        _watchFaceStatusIndicators.value = enabled
        persist { store.edit { it[PrefKeys.KEY_WATCHFACE_STATUS_INDICATORS] = enabled } }
    }

    fun setWatchFaceBottomFadeEnabled(enabled: Boolean) {
        _watchFaceBottomFadeEnabled.value = enabled
        persist { store.edit { it[PrefKeys.KEY_WATCHFACE_BOTTOM_FADE] = enabled } }
    }

    fun setDingDingCatFillScreenEnabled(enabled: Boolean) {
        _dingDingCatFillScreen.value = enabled
        persist { store.edit { it[PrefKeys.KEY_DINGDINGCAT_FILL_SCREEN] = enabled } }
    }

    fun setDingDingCatPlaybackSpeedPercent(value: Int) {
        val normalized = ((value.coerceIn(PrefKeys.DINGDINGCAT_PLAYBACK_SPEED_MIN_PERCENT, PrefKeys.DINGDINGCAT_PLAYBACK_SPEED_MAX_PERCENT) / PrefKeys.DINGDINGCAT_PLAYBACK_SPEED_STEP_PERCENT.toFloat()).roundToInt() * PrefKeys.DINGDINGCAT_PLAYBACK_SPEED_STEP_PERCENT).coerceIn(PrefKeys.DINGDINGCAT_PLAYBACK_SPEED_MIN_PERCENT, PrefKeys.DINGDINGCAT_PLAYBACK_SPEED_MAX_PERCENT)
        _dingDingCatPlaybackSpeedPercent.value = normalized
        persist { store.edit { it[PrefKeys.KEY_DINGDINGCAT_PLAYBACK_SPEED_PERCENT] = normalized } }
    }

    fun setDingDingCatImportUnlocked(unlocked: Boolean) {
        _dingDingCatImportUnlocked.value = unlocked
        persist { store.edit { it[PrefKeys.KEY_DINGDINGCAT_IMPORT_UNLOCKED] = unlocked } }
    }

    fun resetSettings() {
        _selectedWatchFaceId.value = BUILT_IN_WATCHFACE_ID
        _selectedWatchFace.value = LunchWatchFaceScanner.builtInDescriptor(BUILT_IN_WATCHFACE_ID)
        _watchFaceLastError.value = null
        _builtInPhotoPath.value = null
        _builtInVideoPath.value = null
        _builtInPhotoClockPosition.value = WatchClockPosition.CENTER
        _builtInVideoClockPosition.value = WatchClockPosition.CENTER
        _builtInPhotoClockSize.value = 64
        _builtInVideoClockSize.value = 64
        _builtInPhotoClockBold.value = false
        _builtInVideoClockBold.value = false
        _builtInVideoFillScreen.value = true
        _builtInVideoClockColorMode.value = WatchClockColorMode.AUTO
        _builtInWatchFaceFontPath.value = null
        _builtInPhotoClockStyle.value = WatchFaceClockStyle.DIGITAL
        _builtInVideoClockStyle.value = WatchFaceClockStyle.DIGITAL
        _builtInPhotoMd3eShape.value = WatchFaceMd3eShape.COOKIE
        _builtInVideoMd3eShape.value = WatchFaceMd3eShape.COOKIE
        _builtInPhotoUseThemeTextColor.value = true
        _builtInVideoUseThemeTextColor.value = true
        _builtInPhotoTextColorArgb.value = 0xFFFFFFFF.toInt()
        _builtInVideoTextColorArgb.value = 0xFFFFFFFF.toInt()
        _builtInPhotoMd3eAutoColors.value = true
        _builtInVideoMd3eAutoColors.value = true
        _builtInPhotoMd3eTextColorArgb.value = 0xFF202938.toInt()
        _builtInVideoMd3eTextColorArgb.value = 0xFF202938.toInt()
        _builtInPhotoMd3eFaceColorArgb.value = 0xFFEAF1FF.toInt()
        _builtInVideoMd3eFaceColorArgb.value = 0xFFEAF1FF.toInt()
        _builtInPhotoMd3eHourColorArgb.value = 0xFF334155.toInt()
        _builtInVideoMd3eHourColorArgb.value = 0xFF334155.toInt()
        _builtInPhotoMd3eMinuteColorArgb.value = 0xFF5F84B6.toInt()
        _builtInVideoMd3eMinuteColorArgb.value = 0xFF5F84B6.toInt()
        _builtInPhotoMd3eSecondColorArgb.value = 0xFF806EA4.toInt()
        _builtInVideoMd3eSecondColorArgb.value = 0xFF806EA4.toInt()
        _builtInPhotoShowSeconds.value = false
        _builtInVideoShowSeconds.value = false
        _builtInPhotoCustomText.value = ""
        _builtInVideoCustomText.value = ""
        _builtInManagerThumbnails.value = true
        _watchFaceChargingPowerText.value = true
        _watchFaceStatusIndicators.value = true
        _watchFaceBottomFadeEnabled.value = true
        _dingDingCatFillScreen.value = false
        _dingDingCatPlaybackSpeedPercent.value = 100
        _dingDingCatImportUnlocked.value = false
        InternalWatchFaceStorage.clearFont(getApplication())
        LunchWatchFaceRegistry.setCurrentSelectedId(BUILT_IN_WATCHFACE_ID)
        persist {
            store.edit {
                it[PrefKeys.KEY_SELECTED_WATCHFACE_ID] = BUILT_IN_WATCHFACE_ID
                it[PrefKeys.KEY_WATCHFACE_CHARGING_POWER_TEXT] = true
                it[PrefKeys.KEY_WATCHFACE_STATUS_INDICATORS] = true
                it[PrefKeys.KEY_WATCHFACE_BOTTOM_FADE] = true
                it[PrefKeys.KEY_DINGDINGCAT_FILL_SCREEN] = false
                it[PrefKeys.KEY_DINGDINGCAT_PLAYBACK_SPEED_PERCENT] = 100
                it[PrefKeys.KEY_DINGDINGCAT_IMPORT_UNLOCKED] = false
                it[PrefKeys.KEY_PHOTO_CLOCK_POSITION] = WatchClockPosition.CENTER.name
                it[PrefKeys.KEY_VIDEO_CLOCK_POSITION] = WatchClockPosition.CENTER.name
                it[PrefKeys.KEY_PHOTO_CLOCK_SIZE] = 64
                it[PrefKeys.KEY_VIDEO_CLOCK_SIZE] = 64
                it[PrefKeys.KEY_PHOTO_CLOCK_BOLD] = false
                it[PrefKeys.KEY_VIDEO_CLOCK_BOLD] = false
                it[PrefKeys.KEY_VIDEO_FILL_SCREEN] = true
                it[PrefKeys.KEY_VIDEO_CLOCK_COLOR_MODE] = WatchClockColorMode.AUTO.name
                it.remove(PrefKeys.KEY_WATCHFACE_FONT_PATH)
                it[PrefKeys.KEY_PHOTO_CLOCK_STYLE] = WatchFaceClockStyle.DIGITAL.name
                it[PrefKeys.KEY_VIDEO_CLOCK_STYLE] = WatchFaceClockStyle.DIGITAL.name
                it[PrefKeys.KEY_PHOTO_MD3E_SHAPE] = WatchFaceMd3eShape.COOKIE.name
                it[PrefKeys.KEY_VIDEO_MD3E_SHAPE] = WatchFaceMd3eShape.COOKIE.name
                it[PrefKeys.KEY_PHOTO_USE_THEME_TEXT_COLOR] = true
                it[PrefKeys.KEY_VIDEO_USE_THEME_TEXT_COLOR] = true
                it[PrefKeys.KEY_PHOTO_TEXT_COLOR] = 0xFFFFFFFF.toInt()
                it[PrefKeys.KEY_VIDEO_TEXT_COLOR] = 0xFFFFFFFF.toInt()
                it[PrefKeys.KEY_PHOTO_MD3E_AUTO_COLORS] = true
                it[PrefKeys.KEY_VIDEO_MD3E_AUTO_COLORS] = true
                it[PrefKeys.KEY_PHOTO_MD3E_TEXT_COLOR] = 0xFF202938.toInt()
                it[PrefKeys.KEY_VIDEO_MD3E_TEXT_COLOR] = 0xFF202938.toInt()
                it[PrefKeys.KEY_PHOTO_MD3E_FACE_COLOR] = 0xFFEAF1FF.toInt()
                it[PrefKeys.KEY_VIDEO_MD3E_FACE_COLOR] = 0xFFEAF1FF.toInt()
                it[PrefKeys.KEY_PHOTO_MD3E_HOUR_COLOR] = 0xFF334155.toInt()
                it[PrefKeys.KEY_VIDEO_MD3E_HOUR_COLOR] = 0xFF334155.toInt()
                it[PrefKeys.KEY_PHOTO_MD3E_MINUTE_COLOR] = 0xFF5F84B6.toInt()
                it[PrefKeys.KEY_VIDEO_MD3E_MINUTE_COLOR] = 0xFF5F84B6.toInt()
                it[PrefKeys.KEY_PHOTO_MD3E_SECOND_COLOR] = 0xFF806EA4.toInt()
                it[PrefKeys.KEY_VIDEO_MD3E_SECOND_COLOR] = 0xFF806EA4.toInt()
                it[PrefKeys.KEY_PHOTO_SHOW_SECONDS] = false
                it[PrefKeys.KEY_VIDEO_SHOW_SECONDS] = false
                it.remove(PrefKeys.KEY_PHOTO_CUSTOM_TEXT)
                it.remove(PrefKeys.KEY_VIDEO_CUSTOM_TEXT)
                it[PrefKeys.KEY_BUILTIN_MANAGER_THUMBNAILS] = true
                it.remove(PrefKeys.KEY_LAST_WATCHFACE_ERROR)
            }
        }
    }

    // ===== Persistence Helpers =====

    private fun persist(block: suspend () -> Unit) {
        viewModelScope.launch { block() }
    }

    private fun persistDebounced(tag: String, delayMs: Long = 120, block: suspend () -> Unit) {
        pendingWriteJobs[tag]?.cancel()
        pendingWriteJobs[tag] = viewModelScope.launch {
            kotlinx.coroutines.delay(delayMs)
            block()
        }
    }

    override fun onCleared() {
        watchFaceRefreshJob?.cancel()
        pendingWriteJobs.values.forEach(Job::cancel)
        pendingWriteJobs.clear()
        super.onCleared()
    }
}
