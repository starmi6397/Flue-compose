package com.flue.launcher.viewmodel

import android.app.Application
import android.os.Build
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.flue.launcher.ui.controlcenter.MusicTextSwitchAnimations
import com.flue.launcher.ui.navigation.LayoutMode
import com.flue.launcher.ui.theme.ThemeMode
import com.flue.launcher.ui.theme.UiStyle
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import androidx.datastore.preferences.core.edit
import kotlin.math.roundToInt

class PreferencesViewModel(application: Application) : AndroidViewModel(application) {

    private val store = application.dataStore
    private val repositories = com.flue.launcher.FlueApplication.repositories(application)
    private val appRepository = repositories.appRepository

    // ===== UI Layout & Appearance =====
    private val _layoutMode = MutableStateFlow(LayoutMode.Honeycomb)
    val layoutMode: StateFlow<LayoutMode> = _layoutMode.asStateFlow()

    private val _themeMode = repositories.sharedState.themeMode
    val themeMode: StateFlow<ThemeMode> = _themeMode.asStateFlow()

    private val _uiStyle = repositories.sharedState.uiStyle
    val uiStyle: StateFlow<UiStyle> = _uiStyle.asStateFlow()

    private val _sideScreenEnabled = MutableStateFlow(true)
    val sideScreenEnabled: StateFlow<Boolean> = _sideScreenEnabled.asStateFlow()

    private val _sideScreenShortcutRows = MutableStateFlow(2)
    val sideScreenShortcutRows: StateFlow<Int> = _sideScreenShortcutRows.asStateFlow()

    private val _sideScreenShortcutCols = MutableStateFlow(3)
    val sideScreenShortcutCols: StateFlow<Int> = _sideScreenShortcutCols.asStateFlow()

    private val _sideScreenShortcuts = MutableStateFlow(List(PrefKeys.SIDE_SCREEN_SHORTCUT_SLOT_CAPACITY) { null as String? })
    val sideScreenShortcuts: StateFlow<List<String?>> = _sideScreenShortcuts.asStateFlow()

    private val _sideScreenWidgetSlots = MutableStateFlow(emptyList<String?>())
    val sideScreenWidgetSlots: StateFlow<List<String?>> = _sideScreenWidgetSlots.asStateFlow()

    // ===== Visual Effects =====
    private val _blurEnabled = MutableStateFlow(Build.VERSION.SDK_INT >= Build.VERSION_CODES.S)
    val blurEnabled: StateFlow<Boolean> = _blurEnabled.asStateFlow()

    private val _edgeBlurEnabled = MutableStateFlow(false)
    val edgeBlurEnabled: StateFlow<Boolean> = _edgeBlurEnabled.asStateFlow()

    private val _lowResIcons = MutableStateFlow(false)
    val lowResIcons: StateFlow<Boolean> = _lowResIcons.asStateFlow()

    private val _animationOverrideEnabled = MutableStateFlow(true)
    val animationOverrideEnabled: StateFlow<Boolean> = _animationOverrideEnabled.asStateFlow()

    private val _splashIcon = MutableStateFlow(true)
    val splashIcon: StateFlow<Boolean> = _splashIcon.asStateFlow()

    private val _splashDelay = MutableStateFlow(500)
    val splashDelay: StateFlow<Int> = _splashDelay.asStateFlow()

    private val _directLaunchAppListEnabled = MutableStateFlow(false)
    val directLaunchAppListEnabled: StateFlow<Boolean> = _directLaunchAppListEnabled.asStateFlow()

    // ===== Honeycomb Layout =====
    private val _honeycombCols = MutableStateFlow(3)
    val honeycombCols: StateFlow<Int> = _honeycombCols.asStateFlow()

    private val _legacyCircularIcons = MutableStateFlow(true)
    val legacyCircularIcons: StateFlow<Boolean> = _legacyCircularIcons.asStateFlow()

    private val _honeycombTopBlur = MutableStateFlow(4)
    val honeycombTopBlur: StateFlow<Int> = _honeycombTopBlur.asStateFlow()

    private val _honeycombBottomBlur = MutableStateFlow(4)
    val honeycombBottomBlur: StateFlow<Int> = _honeycombBottomBlur.asStateFlow()

    private val _honeycombEdgeBlurRadius = MutableStateFlow(4f)
    val honeycombEdgeBlurRadius: StateFlow<Float> = _honeycombEdgeBlurRadius.asStateFlow()

    private val _honeycombTopFade = MutableStateFlow(30)
    val honeycombTopFade: StateFlow<Int> = _honeycombTopFade.asStateFlow()

    private val _honeycombBottomFade = MutableStateFlow(30)
    val honeycombBottomFade: StateFlow<Int> = _honeycombBottomFade.asStateFlow()

    private val _honeycombFastScrollOptimization = MutableStateFlow(true)
    val honeycombFastScrollOptimization: StateFlow<Boolean> = _honeycombFastScrollOptimization.asStateFlow()

    private val _honeycombFastScrollOptimizationMode = MutableStateFlow(HoneycombFastScrollOptimizationMode.Standard)
    val honeycombFastScrollOptimizationMode: StateFlow<HoneycombFastScrollOptimizationMode> = _honeycombFastScrollOptimizationMode.asStateFlow()

    // ===== App List =====
    private val _appListFisheyeEnabled = MutableStateFlow(true)
    val appListFisheyeEnabled: StateFlow<Boolean> = _appListFisheyeEnabled.asStateFlow()

    private val _materialHoneycombTopFisheyeEnabled = MutableStateFlow(true)
    val materialHoneycombTopFisheyeEnabled: StateFlow<Boolean> = _materialHoneycombTopFisheyeEnabled.asStateFlow()

    private val _appListFisheyeRangeRows = MutableStateFlow(4)
    val appListFisheyeRangeRows: StateFlow<Int> = _appListFisheyeRangeRows.asStateFlow()

    private val _appListFisheyeStrengthPercent = MutableStateFlow(100)
    val appListFisheyeStrengthPercent: StateFlow<Int> = _appListFisheyeStrengthPercent.asStateFlow()

    private val _appListEdgeSpacingCompressionEnabled = MutableStateFlow(true)
    val appListEdgeSpacingCompressionEnabled: StateFlow<Boolean> = _appListEdgeSpacingCompressionEnabled.asStateFlow()

    private val _appListLeftSafeInsetPercent = MutableStateFlow(0)
    val appListLeftSafeInsetPercent: StateFlow<Int> = _appListLeftSafeInsetPercent.asStateFlow()

    private val _appListScalePercent = MutableStateFlow(100)
    val appListScalePercent: StateFlow<Int> = _appListScalePercent.asStateFlow()

    private val _globalUiScalePercent = MutableStateFlow(100)
    val globalUiScalePercent: StateFlow<Int> = _globalUiScalePercent.asStateFlow()

    private val _appListWatchFaceColors = MutableStateFlow(false)
    val appListWatchFaceColors: StateFlow<Boolean> = _appListWatchFaceColors.asStateFlow()

    private val _appListRowBorderEnabled = MutableStateFlow(false)
    val appListRowBorderEnabled: StateFlow<Boolean> = _appListRowBorderEnabled.asStateFlow()

    private val _appListFoldersEnabled = MutableStateFlow(false)
    val appListFoldersEnabled: StateFlow<Boolean> = _appListFoldersEnabled.asStateFlow()

    private val _fastFlowAnimationEnabled = MutableStateFlow(false)
    val fastFlowAnimationEnabled: StateFlow<Boolean> = _fastFlowAnimationEnabled.asStateFlow()

    private val _musicTextSwitchAnimation = MutableStateFlow(MusicTextSwitchAnimations.DEFAULT_ID)
    val musicTextSwitchAnimation: StateFlow<String> = _musicTextSwitchAnimation.asStateFlow()

    // ===== Icon =====
    private val _twoToneIconsEnabled = MutableStateFlow(false)
    val twoToneIconsEnabled: StateFlow<Boolean> = _twoToneIconsEnabled.asStateFlow()

    private val _iconShadowEnabled = MutableStateFlow(true)
    val iconShadowEnabled: StateFlow<Boolean> = _iconShadowEnabled.asStateFlow()

    private val _classicReturnAnimationEnabled = MutableStateFlow(false)
    val classicReturnAnimationEnabled: StateFlow<Boolean> = _classicReturnAnimationEnabled.asStateFlow()

    private val _selectedIconPackPackage = MutableStateFlow<String?>(null)
    val selectedIconPackPackage: StateFlow<String?> = _selectedIconPackPackage.asStateFlow()

    // ===== Feature Toggles =====
    private val _showStepCount = MutableStateFlow(true)
    val showStepCount: StateFlow<Boolean> = _showStepCount.asStateFlow()

    private val _showNotification = MutableStateFlow(true)
    val showNotification: StateFlow<Boolean> = _showNotification.asStateFlow()

    private val _showOngoingNotifications = MutableStateFlow(false)
    val showOngoingNotifications: StateFlow<Boolean> = _showOngoingNotifications.asStateFlow()

    private val _rotaryHapticsEnabled = MutableStateFlow(true)
    val rotaryHapticsEnabled: StateFlow<Boolean> = _rotaryHapticsEnabled.asStateFlow()

    private val _showWidgetPage = MutableStateFlow(true)
    val showWidgetPage: StateFlow<Boolean> = _showWidgetPage.asStateFlow()

    private val _showControlCenter = MutableStateFlow(true)
    val showControlCenter: StateFlow<Boolean> = _showControlCenter.asStateFlow()

    private val _showMusicControls = MutableStateFlow(true)
    val showMusicControls: StateFlow<Boolean> = _showMusicControls.asStateFlow()

    private val _showMediaCustomActions = MutableStateFlow(true)
    val showMediaCustomActions: StateFlow<Boolean> = _showMediaCustomActions.asStateFlow()

    private val _swapMusicNotificationComponents = MutableStateFlow(false)
    val swapMusicNotificationComponents: StateFlow<Boolean> = _swapMusicNotificationComponents.asStateFlow()

    private val _doubleTapLockScreenEnabled = MutableStateFlow(false)
    val doubleTapLockScreenEnabled: StateFlow<Boolean> = _doubleTapLockScreenEnabled.asStateFlow()

    private val _powerMenuButtonEnabled = MutableStateFlow(false)
    val powerMenuButtonEnabled: StateFlow<Boolean> = _powerMenuButtonEnabled.asStateFlow()

    // ===== Internal =====
    private var directLaunchPreferenceLoaded = false
    private val pendingWriteJobs = java.util.concurrent.ConcurrentHashMap<String, Job>()
    private var refreshIconsJob: Job? = null

    private fun normalizeEdgeBlurTenths(value: Int): Int =
        ((value.coerceIn(5, 50) / 5f).roundToInt() * 5).coerceIn(5, 50)

    private fun normalizeLeftSafeInsetPercent(value: Int): Int =
        ((value.coerceIn(0, PrefKeys.APP_LIST_LEFT_SAFE_INSET_MAX_PERCENT) / 5f).roundToInt() * 5)
            .coerceIn(0, PrefKeys.APP_LIST_LEFT_SAFE_INSET_MAX_PERCENT)

    private fun normalizeAppListScalePercent(value: Int): Int {
        val clamped = value.coerceIn(50, 200)
        if (clamped == 200) return 200
        return ((clamped / 10) * 10).coerceIn(50, 200)
    }

    private fun normalizeGlobalUiScalePercent(value: Int): Int =
        ((value.coerceIn(50, 150) / 5f).roundToInt() * 5).coerceIn(50, 150)

    init {
        viewModelScope.launch(Dispatchers.IO) {
            store.data.collect { prefs ->
                _layoutMode.value = try { prefs[PrefKeys.KEY_LAYOUT]?.let { LayoutMode.valueOf(it) } ?: LayoutMode.Honeycomb } catch (_: Exception) { LayoutMode.Honeycomb }
                _sideScreenEnabled.value = prefs[PrefKeys.KEY_SIDE_SCREEN_ENABLED] ?: true
                _sideScreenShortcutRows.value = (prefs[PrefKeys.KEY_SIDE_SCREEN_SHORTCUT_ROWS] ?: 2).coerceIn(1, 3)
                _sideScreenShortcutCols.value = (prefs[PrefKeys.KEY_SIDE_SCREEN_SHORTCUT_COLS] ?: 3).coerceIn(2, 4)
                _sideScreenShortcuts.value = parseSideScreenShortcuts(prefs[PrefKeys.KEY_SIDE_SCREEN_SHORTCUTS])
                _sideScreenWidgetSlots.value = parseSideScreenWidgets(prefs[PrefKeys.KEY_SIDE_SCREEN_WIDGETS])
                _blurEnabled.value = prefs[PrefKeys.KEY_BLUR] ?: (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S)
                _edgeBlurEnabled.value = prefs[PrefKeys.KEY_EDGE_BLUR] ?: false
                val loadedLowRes = prefs[PrefKeys.KEY_LOW_RES] ?: false
                if (_lowResIcons.value != loadedLowRes) { _lowResIcons.value = loadedLowRes; refreshIcons() }
                _legacyCircularIcons.value = prefs[PrefKeys.KEY_LEGACY_CIRCULAR_ICONS] ?: true
                _animationOverrideEnabled.value = prefs[PrefKeys.KEY_ANIMATION_OVERRIDE] ?: true
                _splashIcon.value = prefs[PrefKeys.KEY_SPLASH_ICON] ?: true
                _splashDelay.value = (prefs[PrefKeys.KEY_SPLASH_DELAY] ?: 500).coerceIn(300, 1500)
                val loadedDirectLaunch = prefs[PrefKeys.KEY_DIRECT_LAUNCH_APP_LIST] ?: false
                _directLaunchAppListEnabled.value = loadedDirectLaunch
                directLaunchPreferenceLoaded = true

                val shouldApplyDevicePreset = !(prefs[PrefKeys.KEY_DEVICE_PRESET_APPLIED] ?: false) && isXiaomi17ProSeriesDevice()
                val hasSavedHoneycombCols = prefs[PrefKeys.KEY_HONEYCOMB_COLS] != null
                _honeycombCols.value = (prefs[PrefKeys.KEY_HONEYCOMB_COLS] ?: (if (shouldApplyDevicePreset && !hasSavedHoneycombCols) 2 else 3)).coerceIn(1, 5)
                _honeycombTopBlur.value = (prefs[PrefKeys.KEY_HONEYCOMB_TOP_BLUR] ?: 4).coerceIn(0, 48)
                _honeycombBottomBlur.value = (prefs[PrefKeys.KEY_HONEYCOMB_BOTTOM_BLUR] ?: 4).coerceIn(0, 48)
                val loadedEdgeBlurTenths = (prefs[PrefKeys.KEY_HONEYCOMB_EDGE_BLUR_TENTHS] ?: (((_honeycombTopBlur.value + _honeycombBottomBlur.value) / 2f) * 10f).roundToInt()).let(::normalizeEdgeBlurTenths)
                _honeycombEdgeBlurRadius.value = loadedEdgeBlurTenths / 10f
                _honeycombTopFade.value = (prefs[PrefKeys.KEY_HONEYCOMB_TOP_FADE] ?: 30).coerceIn(0, 160)
                _honeycombBottomFade.value = (prefs[PrefKeys.KEY_HONEYCOMB_BOTTOM_FADE] ?: 30).coerceIn(0, 160)
                _honeycombFastScrollOptimization.value = prefs[PrefKeys.KEY_HONEYCOMB_FAST_SCROLL_OPTIMIZATION] ?: true
                _honeycombFastScrollOptimizationMode.value = HoneycombFastScrollOptimizationMode.fromId(prefs[PrefKeys.KEY_HONEYCOMB_FAST_SCROLL_OPTIMIZATION_MODE])
                _appListFisheyeEnabled.value = prefs[PrefKeys.KEY_APP_LIST_FISHEYE] ?: true
                _materialHoneycombTopFisheyeEnabled.value = prefs[PrefKeys.KEY_MATERIAL_HONEYCOMB_TOP_FISHEYE] ?: true
                _appListFisheyeRangeRows.value = (prefs[PrefKeys.KEY_APP_LIST_FISHEYE_RANGE_ROWS] ?: 4).coerceIn(1, 8)
                _appListFisheyeStrengthPercent.value = (prefs[PrefKeys.KEY_APP_LIST_FISHEYE_STRENGTH_PERCENT] ?: 100).coerceIn(0, 200)
                val hasSavedLeftSafeInset = prefs[PrefKeys.KEY_APP_LIST_LEFT_SAFE_INSET_PERCENT] != null
                _appListLeftSafeInsetPercent.value = normalizeLeftSafeInsetPercent(prefs[PrefKeys.KEY_APP_LIST_LEFT_SAFE_INSET_PERCENT] ?: (if (shouldApplyDevicePreset && !hasSavedLeftSafeInset && isXiaomi17ProSeriesDevice()) 20 else 0))
                _appListEdgeSpacingCompressionEnabled.value = prefs[PrefKeys.KEY_APP_LIST_EDGE_SPACING_COMPRESSION] ?: true
                _appListScalePercent.value = normalizeAppListScalePercent(prefs[PrefKeys.KEY_APP_LIST_SCALE_PERCENT] ?: 100)
                _globalUiScalePercent.value = normalizeGlobalUiScalePercent(prefs[PrefKeys.KEY_GLOBAL_UI_SCALE_PERCENT] ?: 100)
                _appListWatchFaceColors.value = prefs[PrefKeys.KEY_APP_LIST_WATCHFACE_COLORS] ?: false
                _appListRowBorderEnabled.value = prefs[PrefKeys.KEY_APP_LIST_ROW_BORDER] ?: false
                _appListFoldersEnabled.value = prefs[PrefKeys.KEY_APP_LIST_FOLDERS_ENABLED] ?: false
                _fastFlowAnimationEnabled.value = prefs[PrefKeys.KEY_FAST_FLOW_ANIMATION] ?: false
                _musicTextSwitchAnimation.value = MusicTextSwitchAnimations.normalizeId(prefs[PrefKeys.KEY_MUSIC_TEXT_SWITCH_ANIMATION])
                _twoToneIconsEnabled.value = prefs[PrefKeys.KEY_TWO_TONE_ICONS] ?: false
                _iconShadowEnabled.value = prefs[PrefKeys.KEY_ICON_SHADOW] ?: true
                _classicReturnAnimationEnabled.value = prefs[PrefKeys.KEY_CLASSIC_RETURN_ANIMATION] ?: false
                _showStepCount.value = prefs[PrefKeys.KEY_SHOW_STEP_COUNT] ?: true
                _showNotification.value = prefs[PrefKeys.KEY_SHOW_NOTIFICATION] ?: true
                _showOngoingNotifications.value = prefs[PrefKeys.KEY_SHOW_ONGOING_NOTIFICATIONS] ?: false
                _rotaryHapticsEnabled.value = prefs[PrefKeys.KEY_ROTARY_HAPTICS_ENABLED] ?: true
                _showWidgetPage.value = prefs[PrefKeys.KEY_SHOW_WIDGET_PAGE] ?: true
                _showControlCenter.value = prefs[PrefKeys.KEY_SHOW_CONTROL_CENTER] ?: true
                _showMusicControls.value = prefs[PrefKeys.KEY_SHOW_MUSIC_CONTROLS] ?: true
                _showMediaCustomActions.value = prefs[PrefKeys.KEY_SHOW_MEDIA_CUSTOM_ACTIONS] ?: true
                _swapMusicNotificationComponents.value = prefs[PrefKeys.KEY_SWAP_MUSIC_NOTIFICATION_COMPONENTS] ?: false
                _doubleTapLockScreenEnabled.value = prefs[PrefKeys.KEY_DOUBLE_TAP_LOCK_SCREEN] ?: false
                _powerMenuButtonEnabled.value = prefs[PrefKeys.KEY_POWER_MENU_BUTTON] ?: false
                _selectedIconPackPackage.value = prefs[PrefKeys.KEY_ICON_PACK_PACKAGE]

                if (!(prefs[PrefKeys.KEY_LEGACY_CIRCULAR_ICONS] ?: false)) {
                    // Migration: ensure legacy circular icons defaults to true
                }

                if (shouldApplyDevicePreset) {
                    store.edit {
                        if (!hasSavedHoneycombCols) it[PrefKeys.KEY_HONEYCOMB_COLS] = 2
                        if (!hasSavedLeftSafeInset) it[PrefKeys.KEY_APP_LIST_LEFT_SAFE_INSET_PERCENT] = 20
                        it[PrefKeys.KEY_DEVICE_PRESET_APPLIED] = true
                    }
                }
            }
        }
    }

    // ===== Side Screen Widgets =====

    fun setWidgetSlot(slotIndex: Int, slotValue: String?) {
        if (slotIndex < 0) return
        val normalizedValue = slotValue?.takeIf { it.isNotBlank() }
        val next = _sideScreenWidgetSlots.value.toMutableList()
        when {
            slotIndex < next.size && normalizedValue == null -> next.removeAt(slotIndex)
            slotIndex < next.size -> next[slotIndex] = normalizedValue
            slotIndex == next.size && normalizedValue != null -> next.add(normalizedValue)
            else -> return
        }
        updateSideScreenWidgets(next)
    }

    fun removeWidgetSlot(slotIndex: Int) {
        if (slotIndex !in _sideScreenWidgetSlots.value.indices) return
        val next = _sideScreenWidgetSlots.value.toMutableList().apply { removeAt(slotIndex) }
        updateSideScreenWidgets(next)
    }

    fun swapWidgetSlots(fromIndex: Int, toIndex: Int) {
        if (fromIndex == toIndex) return
        val current = _sideScreenWidgetSlots.value
        if (fromIndex !in current.indices || toIndex !in 0..current.size) return
        val next = current.toMutableList()
        val item = next.removeAt(fromIndex)
        val insertIndex = if (toIndex > fromIndex) toIndex - 1 else toIndex
        next.add(insertIndex.coerceIn(0, next.size), item)
        updateSideScreenWidgets(next)
    }

    private fun updateSideScreenWidgets(widgets: List<String?>) {
        val normalized = widgets.mapNotNull { value -> value?.takeIf { it.isNotBlank() } }
        _sideScreenWidgetSlots.value = normalized
        persist { store.edit { it[PrefKeys.KEY_SIDE_SCREEN_WIDGETS] = serializeSideScreenWidgets(normalized) } }
    }

    fun setSideScreenShortcutRows(rows: Int) {
        _sideScreenShortcutRows.value = rows.coerceIn(1, 3)
        persist { store.edit { it[PrefKeys.KEY_SIDE_SCREEN_SHORTCUT_ROWS] = _sideScreenShortcutRows.value } }
    }

    fun setSideScreenShortcutCols(cols: Int) {
        _sideScreenShortcutCols.value = cols.coerceIn(2, 4)
        persist { store.edit { it[PrefKeys.KEY_SIDE_SCREEN_SHORTCUT_COLS] = _sideScreenShortcutCols.value } }
    }

    fun setSideScreenShortcut(slotIndex: Int, componentKey: String?) {
        if (slotIndex !in _sideScreenShortcuts.value.indices) return
        val current = _sideScreenShortcuts.value.toMutableList()
        current[slotIndex] = componentKey?.takeIf { it.isNotBlank() }
        _sideScreenShortcuts.value = current
        persistDebounced("side_shortcuts") {
            store.edit { it[PrefKeys.KEY_SIDE_SCREEN_SHORTCUTS] = serializeSideScreenShortcuts(_sideScreenShortcuts.value) }
        }
    }

    fun removeSideScreenShortcut(slotIndex: Int) {
        if (slotIndex !in _sideScreenShortcuts.value.indices) return
        val current = _sideScreenShortcuts.value.toMutableList()
        current[slotIndex] = null
        _sideScreenShortcuts.value = current
        persistDebounced("side_shortcuts") {
            store.edit { it[PrefKeys.KEY_SIDE_SCREEN_SHORTCUTS] = serializeSideScreenShortcuts(_sideScreenShortcuts.value) }
        }
    }

    fun swapSideScreenShortcuts(fromIndex: Int, toIndex: Int) {
        if (fromIndex == toIndex) return
        val current = _sideScreenShortcuts.value
        if (fromIndex !in current.indices || toIndex !in current.indices) return
        val next = current.toMutableList()
        val item = next[fromIndex]
        next[fromIndex] = next[toIndex]
        next[toIndex] = item
        _sideScreenShortcuts.value = next
        persistDebounced("side_shortcuts") {
            store.edit { it[PrefKeys.KEY_SIDE_SCREEN_SHORTCUTS] = serializeSideScreenShortcuts(next) }
        }
    }

    // ===== Layout =====

    fun setLayoutMode(mode: LayoutMode) {
        _layoutMode.value = mode
        persist { store.edit { it[PrefKeys.KEY_LAYOUT] = mode.name } }
    }

    fun setThemeMode(mode: ThemeMode) {
        if (_themeMode.value == mode) return
        // Note: themeMode is from shared repository state; persist via app scope
        repositories.applicationScope.launch { store.edit { it[PrefKeys.KEY_THEME_MODE] = mode.name } }
    }

    fun setUiStyle(style: UiStyle) {
        if (_uiStyle.value == style) return
        repositories.applicationScope.launch { store.edit { it[PrefKeys.KEY_UI_STYLE] = style.name } }
    }

    fun setSideScreenEnabled(enabled: Boolean) {
        _sideScreenEnabled.value = enabled
        persist { store.edit { it[PrefKeys.KEY_SIDE_SCREEN_ENABLED] = enabled } }
    }

    // ===== Visual Effects =====

    fun setBlurEnabled(enabled: Boolean) {
        _blurEnabled.value = enabled
        persist { store.edit { it[PrefKeys.KEY_BLUR] = enabled } }
    }

    fun setEdgeBlurEnabled(enabled: Boolean) {
        _edgeBlurEnabled.value = enabled
        persist { store.edit { it[PrefKeys.KEY_EDGE_BLUR] = enabled } }
    }

    fun setLowResIcons(enabled: Boolean) {
        _lowResIcons.value = enabled
        refreshIcons()
        persist { store.edit { it[PrefKeys.KEY_LOW_RES] = enabled } }
    }

    fun setLegacyCircularIconsEnabled(enabled: Boolean) {
        _legacyCircularIcons.value = enabled
        appRepository.setLegacyCircularIconsEnabled(enabled)
        persist { store.edit { it[PrefKeys.KEY_LEGACY_CIRCULAR_ICONS] = enabled } }
    }

    fun setAnimationOverrideEnabled(enabled: Boolean) {
        _animationOverrideEnabled.value = enabled
        persist { store.edit { it[PrefKeys.KEY_ANIMATION_OVERRIDE] = enabled } }
    }

    fun setSplashIcon(enabled: Boolean) {
        _splashIcon.value = enabled
        persist { store.edit { it[PrefKeys.KEY_SPLASH_ICON] = enabled } }
    }

    fun setSplashDelay(ms: Int) {
        _splashDelay.value = ms.coerceIn(300, 1500)
        val value = _splashDelay.value
        persistDebounced("splash_delay") { store.edit { it[PrefKeys.KEY_SPLASH_DELAY] = value } }
    }

    fun setDirectLaunchAppListEnabled(enabled: Boolean) {
        _directLaunchAppListEnabled.value = enabled
        directLaunchPreferenceLoaded = true
        persist { store.edit { it[PrefKeys.KEY_DIRECT_LAUNCH_APP_LIST] = enabled } }
    }

    // ===== Honeycomb =====

    fun setHoneycombCols(cols: Int) {
        _honeycombCols.value = cols.coerceIn(1, 5)
        persistDebounced("honeycomb_cols") { store.edit { it[PrefKeys.KEY_HONEYCOMB_COLS] = _honeycombCols.value } }
    }

    fun setHoneycombTopBlur(value: Int) {
        _honeycombTopBlur.value = value.coerceIn(0, 48)
        persistDebounced("honeycomb_top_blur") { store.edit { it[PrefKeys.KEY_HONEYCOMB_TOP_BLUR] = _honeycombTopBlur.value } }
    }

    fun setHoneycombBottomBlur(value: Int) {
        _honeycombBottomBlur.value = value.coerceIn(0, 48)
        persistDebounced("honeycomb_bottom_blur") { store.edit { it[PrefKeys.KEY_HONEYCOMB_BOTTOM_BLUR] = _honeycombBottomBlur.value } }
    }

    fun setHoneycombEdgeBlurRadius(value: Float) {
        val savedTenths = normalizeEdgeBlurTenths((value * 10f).roundToInt())
        _honeycombEdgeBlurRadius.value = savedTenths / 10f
        persistDebounced("honeycomb_edge_blur") { store.edit { it[PrefKeys.KEY_HONEYCOMB_EDGE_BLUR_TENTHS] = savedTenths } }
    }

    fun setHoneycombTopFade(value: Int) {
        _honeycombTopFade.value = value.coerceIn(0, 160)
        persistDebounced("honeycomb_top_fade") { store.edit { it[PrefKeys.KEY_HONEYCOMB_TOP_FADE] = _honeycombTopFade.value } }
    }

    fun setHoneycombBottomFade(value: Int) {
        _honeycombBottomFade.value = value.coerceIn(0, 160)
        persistDebounced("honeycomb_bottom_fade") { store.edit { it[PrefKeys.KEY_HONEYCOMB_BOTTOM_FADE] = _honeycombBottomFade.value } }
    }

    fun setHoneycombFastScrollOptimization(enabled: Boolean) {
        _honeycombFastScrollOptimization.value = enabled
        persist { store.edit { it[PrefKeys.KEY_HONEYCOMB_FAST_SCROLL_OPTIMIZATION] = enabled } }
    }

    fun setHoneycombFastScrollOptimizationMode(mode: HoneycombFastScrollOptimizationMode) {
        _honeycombFastScrollOptimizationMode.value = mode
        persist { store.edit { it[PrefKeys.KEY_HONEYCOMB_FAST_SCROLL_OPTIMIZATION_MODE] = mode.id } }
    }

    // ===== App List =====

    fun setAppListFisheyeEnabled(enabled: Boolean) {
        _appListFisheyeEnabled.value = enabled
        persist { store.edit { it[PrefKeys.KEY_APP_LIST_FISHEYE] = enabled } }
    }

    fun setMaterialHoneycombTopFisheyeEnabled(enabled: Boolean) {
        _materialHoneycombTopFisheyeEnabled.value = enabled
        persist { store.edit { it[PrefKeys.KEY_MATERIAL_HONEYCOMB_TOP_FISHEYE] = enabled } }
    }

    fun setAppListFisheyeRangeRows(value: Int) {
        _appListFisheyeRangeRows.value = value.coerceIn(1, 8)
        persistDebounced("fisheye_rows") { store.edit { it[PrefKeys.KEY_APP_LIST_FISHEYE_RANGE_ROWS] = _appListFisheyeRangeRows.value } }
    }

    fun setAppListFisheyeStrengthPercent(value: Int) {
        _appListFisheyeStrengthPercent.value = value.coerceIn(0, 200)
        persistDebounced("fisheye_strength") { store.edit { it[PrefKeys.KEY_APP_LIST_FISHEYE_STRENGTH_PERCENT] = _appListFisheyeStrengthPercent.value } }
    }

    fun setAppListEdgeSpacingCompressionEnabled(enabled: Boolean) {
        _appListEdgeSpacingCompressionEnabled.value = enabled
        persist { store.edit { it[PrefKeys.KEY_APP_LIST_EDGE_SPACING_COMPRESSION] = enabled } }
    }

    fun setAppListLeftSafeInsetPercent(value: Int) {
        _appListLeftSafeInsetPercent.value = normalizeLeftSafeInsetPercent(value)
        persistDebounced("left_safe_inset") { store.edit { it[PrefKeys.KEY_APP_LIST_LEFT_SAFE_INSET_PERCENT] = _appListLeftSafeInsetPercent.value } }
    }

    fun setAppListScalePercent(value: Int) {
        _appListScalePercent.value = normalizeAppListScalePercent(value)
        persistDebounced("app_list_scale") { store.edit { it[PrefKeys.KEY_APP_LIST_SCALE_PERCENT] = _appListScalePercent.value } }
    }

    fun setGlobalUiScalePercent(value: Int) {
        _globalUiScalePercent.value = normalizeGlobalUiScalePercent(value)
        persistDebounced("global_ui_scale") { store.edit { it[PrefKeys.KEY_GLOBAL_UI_SCALE_PERCENT] = _globalUiScalePercent.value } }
    }

    fun setAppListWatchFaceColors(enabled: Boolean) {
        _appListWatchFaceColors.value = enabled
        persist { store.edit { it[PrefKeys.KEY_APP_LIST_WATCHFACE_COLORS] = enabled } }
    }

    fun setAppListRowBorderEnabled(enabled: Boolean) {
        _appListRowBorderEnabled.value = enabled
        persist { store.edit { it[PrefKeys.KEY_APP_LIST_ROW_BORDER] = enabled } }
    }

    fun setAppListFoldersEnabled(enabled: Boolean) {
        _appListFoldersEnabled.value = enabled
        persist { store.edit { it[PrefKeys.KEY_APP_LIST_FOLDERS_ENABLED] = enabled } }
    }

    fun setFastFlowAnimationEnabled(enabled: Boolean) {
        _fastFlowAnimationEnabled.value = enabled
        persist { store.edit { it[PrefKeys.KEY_FAST_FLOW_ANIMATION] = enabled } }
    }

    fun setMusicTextSwitchAnimation(id: String) {
        val normalized = MusicTextSwitchAnimations.normalizeId(id)
        _musicTextSwitchAnimation.value = normalized
        persist { store.edit { it[PrefKeys.KEY_MUSIC_TEXT_SWITCH_ANIMATION] = normalized } }
    }

    // ===== Icon =====

    fun setTwoToneIconsEnabled(enabled: Boolean) {
        if (_twoToneIconsEnabled.value == enabled) return
        _twoToneIconsEnabled.value = enabled
        appRepository.setTwoToneIconsEnabled(enabled)
        persist { store.edit { it[PrefKeys.KEY_TWO_TONE_ICONS] = enabled } }
    }

    fun setIconShadowEnabled(enabled: Boolean) {
        _iconShadowEnabled.value = enabled
        persist { store.edit { it[PrefKeys.KEY_ICON_SHADOW] = enabled } }
    }

    fun setClassicReturnAnimationEnabled(enabled: Boolean) {
        _classicReturnAnimationEnabled.value = enabled
        persist { store.edit { it[PrefKeys.KEY_CLASSIC_RETURN_ANIMATION] = enabled } }
    }

    fun setIconPackPackage(packageName: String?) {
        val normalized = packageName?.takeIf { it.isNotBlank() }
        if (_selectedIconPackPackage.value == normalized) return
        _selectedIconPackPackage.value = normalized
        viewModelScope.launch(Dispatchers.IO) { appRepository.setIconPackPackage(_selectedIconPackPackage.value) }
        persist { store.edit { if (_selectedIconPackPackage.value.isNullOrBlank()) it.remove(PrefKeys.KEY_ICON_PACK_PACKAGE) else it[PrefKeys.KEY_ICON_PACK_PACKAGE] = _selectedIconPackPackage.value!! } }
    }

    // ===== Feature Toggles =====

    fun setShowStepCountEnabled(enabled: Boolean) {
        _showStepCount.value = enabled
        persist { store.edit { it[PrefKeys.KEY_SHOW_STEP_COUNT] = enabled } }
    }

    fun setShowNotification(show: Boolean) {
        _showNotification.value = show
        persist { store.edit { it[PrefKeys.KEY_SHOW_NOTIFICATION] = show; it[PrefKeys.KEY_NOTIFICATION_SETTING_MIGRATED] = true } }
    }

    fun setShowOngoingNotifications(show: Boolean) {
        _showOngoingNotifications.value = show
        persist { store.edit { it[PrefKeys.KEY_SHOW_ONGOING_NOTIFICATIONS] = show } }
    }

    fun setRotaryHapticsEnabled(enabled: Boolean) {
        _rotaryHapticsEnabled.value = enabled
        persist { store.edit { it[PrefKeys.KEY_ROTARY_HAPTICS_ENABLED] = enabled } }
    }

    fun setShowWidgetPage(show: Boolean) {
        _showWidgetPage.value = show
        persist { store.edit { it[PrefKeys.KEY_SHOW_WIDGET_PAGE] = show } }
    }

    fun setShowControlCenter(show: Boolean) {
        _showControlCenter.value = show
        persist { store.edit { it[PrefKeys.KEY_SHOW_CONTROL_CENTER] = show } }
    }

    fun setShowMusicControls(show: Boolean) {
        _showMusicControls.value = show
        persist { store.edit { it[PrefKeys.KEY_SHOW_MUSIC_CONTROLS] = show } }
    }

    fun setShowMediaCustomActions(show: Boolean) {
        _showMediaCustomActions.value = show
        persist { store.edit { it[PrefKeys.KEY_SHOW_MEDIA_CUSTOM_ACTIONS] = show } }
    }

    fun setSwapMusicNotificationComponents(enabled: Boolean) {
        _swapMusicNotificationComponents.value = enabled
        persist { store.edit { it[PrefKeys.KEY_SWAP_MUSIC_NOTIFICATION_COMPONENTS] = enabled } }
    }

    fun setDoubleTapLockScreenEnabled(enabled: Boolean) {
        _doubleTapLockScreenEnabled.value = enabled
        persist { store.edit { it[PrefKeys.KEY_DOUBLE_TAP_LOCK_SCREEN] = enabled } }
    }

    fun setPowerMenuButtonEnabled(enabled: Boolean) {
        _powerMenuButtonEnabled.value = enabled
        persist { store.edit { it[PrefKeys.KEY_POWER_MENU_BUTTON] = enabled } }
    }

    // ===== Refresh Icons =====

    private fun refreshIcons() {
        refreshIconsJob?.cancel()
        refreshIconsJob = viewModelScope.launch {
            delay(200)
            appRepository.requestRefresh(if (_lowResIcons.value) 64 else 128)
        }
    }

    // ===== Serialization =====

    private fun parseSideScreenShortcuts(raw: String?): List<String?> {
        if (raw.isNullOrBlank()) return List(PrefKeys.SIDE_SCREEN_SHORTCUT_SLOT_CAPACITY) { null }
        return raw.split("|").map { it.takeIf(String::isNotBlank) }
    }

    private fun serializeSideScreenShortcuts(shortcuts: List<String?>): String {
        return shortcuts.joinToString("|") { it.orEmpty() }
    }

    private fun parseSideScreenWidgets(raw: String?): List<String?> {
        if (raw.isNullOrBlank()) return emptyList()
        return raw.split("|").map { it.takeIf(String::isNotBlank) }
    }

    private fun serializeSideScreenWidgets(widgets: List<String?>): String {
        return widgets.mapNotNull { value -> value?.takeIf { it.isNotBlank() } }.joinToString("|")
    }

    // ===== Device Detection =====

    private fun isXiaomi17ProSeriesDevice(): Boolean {
        val maker = listOf(Build.MANUFACTURER, Build.BRAND).joinToString(" ").lowercase(java.util.Locale.ROOT)
        if ("xiaomi" !in maker) return false
        return listOf(Build.MODEL, Build.DEVICE, Build.PRODUCT).any { value ->
            value.lowercase(java.util.Locale.ROOT).replace(Regex("[\\s_\\-]+"), "")
                .let { it.contains("25098pn5ac") || it.contains("2509fpn0bc") || it.contains("xiaomi17pro") || it.contains("xiaomi17promax") }
        }
    }

    // ===== Reset =====

    fun resetSettings() {
        val defaultBlurEnabled = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S
        _layoutMode.value = LayoutMode.Honeycomb
        _sideScreenEnabled.value = true
        _sideScreenShortcutRows.value = 2
        _sideScreenShortcutCols.value = 3
        _sideScreenShortcuts.value = List(PrefKeys.SIDE_SCREEN_SHORTCUT_SLOT_CAPACITY) { null }
        _sideScreenWidgetSlots.value = emptyList()
        _blurEnabled.value = defaultBlurEnabled
        _edgeBlurEnabled.value = false
        _lowResIcons.value = false
        _animationOverrideEnabled.value = true
        _splashIcon.value = true
        _splashDelay.value = 500
        _directLaunchAppListEnabled.value = false
        _honeycombCols.value = 3
        _legacyCircularIcons.value = true
        _honeycombTopBlur.value = 4
        _honeycombBottomBlur.value = 4
        _honeycombEdgeBlurRadius.value = 4f
        _honeycombTopFade.value = 30
        _honeycombBottomFade.value = 30
        _honeycombFastScrollOptimization.value = true
        _honeycombFastScrollOptimizationMode.value = HoneycombFastScrollOptimizationMode.Standard
        _appListFisheyeEnabled.value = true
        _materialHoneycombTopFisheyeEnabled.value = true
        _appListFisheyeRangeRows.value = 4
        _appListFisheyeStrengthPercent.value = 100
        _appListEdgeSpacingCompressionEnabled.value = true
        _appListLeftSafeInsetPercent.value = 0
        _appListScalePercent.value = 100
        _globalUiScalePercent.value = 100
        _appListWatchFaceColors.value = false
        _appListRowBorderEnabled.value = false
        _appListFoldersEnabled.value = false
        _fastFlowAnimationEnabled.value = false
        _musicTextSwitchAnimation.value = MusicTextSwitchAnimations.DEFAULT_ID
        _twoToneIconsEnabled.value = false
        _iconShadowEnabled.value = true
        _classicReturnAnimationEnabled.value = false
        _showStepCount.value = true
        _showNotification.value = true
        _showOngoingNotifications.value = false
        _rotaryHapticsEnabled.value = true
        _showWidgetPage.value = true
        _showControlCenter.value = true
        _showMusicControls.value = true
        _showMediaCustomActions.value = true
        _swapMusicNotificationComponents.value = false
        _doubleTapLockScreenEnabled.value = false
        _powerMenuButtonEnabled.value = false
        _selectedIconPackPackage.value = null
        persist {
            store.edit {
                it[PrefKeys.KEY_LAYOUT] = LayoutMode.Honeycomb.name
                it[PrefKeys.KEY_SIDE_SCREEN_ENABLED] = true
                it[PrefKeys.KEY_SIDE_SCREEN_SHORTCUT_ROWS] = 2
                it[PrefKeys.KEY_SIDE_SCREEN_SHORTCUT_COLS] = 3
                it[PrefKeys.KEY_BLUR] = defaultBlurEnabled
                it[PrefKeys.KEY_EDGE_BLUR] = false
                it[PrefKeys.KEY_LOW_RES] = false
                it[PrefKeys.KEY_ANIMATION_OVERRIDE] = true
                it[PrefKeys.KEY_SPLASH_ICON] = true
                it[PrefKeys.KEY_SPLASH_DELAY] = 500
                it[PrefKeys.KEY_DIRECT_LAUNCH_APP_LIST] = false
                it[PrefKeys.KEY_HONEYCOMB_COLS] = 3
                it[PrefKeys.KEY_HONEYCOMB_TOP_BLUR] = 4
                it[PrefKeys.KEY_HONEYCOMB_BOTTOM_BLUR] = 4
                it[PrefKeys.KEY_HONEYCOMB_EDGE_BLUR_TENTHS] = 40
                it[PrefKeys.KEY_HONEYCOMB_TOP_FADE] = 30
                it[PrefKeys.KEY_HONEYCOMB_BOTTOM_FADE] = 30
                it[PrefKeys.KEY_HONEYCOMB_FAST_SCROLL_OPTIMIZATION] = true
                it[PrefKeys.KEY_HONEYCOMB_FAST_SCROLL_OPTIMIZATION_MODE] = HoneycombFastScrollOptimizationMode.Standard.id
                it[PrefKeys.KEY_APP_LIST_FISHEYE] = true
                it[PrefKeys.KEY_MATERIAL_HONEYCOMB_TOP_FISHEYE] = true
                it[PrefKeys.KEY_APP_LIST_FISHEYE_RANGE_ROWS] = 4
                it[PrefKeys.KEY_APP_LIST_FISHEYE_STRENGTH_PERCENT] = 100
                it[PrefKeys.KEY_APP_LIST_EDGE_SPACING_COMPRESSION] = true
                it[PrefKeys.KEY_APP_LIST_LEFT_SAFE_INSET_PERCENT] = 0
                it[PrefKeys.KEY_APP_LIST_SCALE_PERCENT] = 100
                it[PrefKeys.KEY_GLOBAL_UI_SCALE_PERCENT] = 100
                it[PrefKeys.KEY_APP_LIST_WATCHFACE_COLORS] = false
                it[PrefKeys.KEY_APP_LIST_ROW_BORDER] = false
                it[PrefKeys.KEY_APP_LIST_FOLDERS_ENABLED] = false
                it[PrefKeys.KEY_FAST_FLOW_ANIMATION] = false
                it[PrefKeys.KEY_MUSIC_TEXT_SWITCH_ANIMATION] = MusicTextSwitchAnimations.DEFAULT_ID
                it[PrefKeys.KEY_TWO_TONE_ICONS] = false
                it[PrefKeys.KEY_ICON_SHADOW] = true
                it[PrefKeys.KEY_CLASSIC_RETURN_ANIMATION] = false
                it[PrefKeys.KEY_SHOW_STEP_COUNT] = true
                it[PrefKeys.KEY_LEGACY_CIRCULAR_ICONS] = true
                it[PrefKeys.KEY_SHOW_NOTIFICATION] = true
                it[PrefKeys.KEY_SHOW_ONGOING_NOTIFICATIONS] = false
                it[PrefKeys.KEY_ROTARY_HAPTICS_ENABLED] = true
                it[PrefKeys.KEY_SHOW_WIDGET_PAGE] = true
                it[PrefKeys.KEY_SHOW_CONTROL_CENTER] = true
                it[PrefKeys.KEY_SHOW_MUSIC_CONTROLS] = true
                it[PrefKeys.KEY_SHOW_MEDIA_CUSTOM_ACTIONS] = true
                it[PrefKeys.KEY_SWAP_MUSIC_NOTIFICATION_COMPONENTS] = false
                it[PrefKeys.KEY_DOUBLE_TAP_LOCK_SCREEN] = false
                it[PrefKeys.KEY_POWER_MENU_BUTTON] = false
                it[PrefKeys.KEY_NOTIFICATION_SETTING_MIGRATED] = true
                it.remove(PrefKeys.KEY_SIDE_SCREEN_SHORTCUTS)
                it.remove(PrefKeys.KEY_SIDE_SCREEN_WIDGETS)
                it.remove(PrefKeys.KEY_ICON_PACK_PACKAGE)
            }
        }
    }

    // ===== Persistence =====

    private fun persist(block: suspend () -> Unit) {
        viewModelScope.launch { block() }
    }

    private fun persistDebounced(tag: String, delayMs: Long = 120, block: suspend () -> Unit) {
        pendingWriteJobs[tag]?.cancel()
        pendingWriteJobs[tag] = viewModelScope.launch {
            delay(delayMs)
            block()
        }
    }

    override fun onCleared() {
        refreshIconsJob?.cancel()
        pendingWriteJobs.values.forEach(Job::cancel)
        pendingWriteJobs.clear()
        super.onCleared()
    }
}
