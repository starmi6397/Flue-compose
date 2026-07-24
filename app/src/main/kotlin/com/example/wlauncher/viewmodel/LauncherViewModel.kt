package com.flue.launcher.viewmodel

import android.app.Application
import android.content.Context
import android.net.Uri
import android.os.Build
import android.os.Vibrator
import android.os.VibratorManager
import android.widget.Toast
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.ImageBitmap
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.preferencesDataStore
import androidx.lifecycle.AndroidViewModel
import androidx.datastore.preferences.core.edit
import androidx.lifecycle.viewModelScope
import com.flue.launcher.backup.FlueBackupOptions
import com.flue.launcher.backup.FlueBackupPreview
import com.flue.launcher.data.model.AppInfo
import com.flue.launcher.data.model.iconForDisplay
import com.flue.launcher.iconpack.IconPackDescriptor
import com.flue.launcher.ui.navigation.LayoutMode
import com.flue.launcher.ui.navigation.ScreenState
import com.flue.launcher.ui.notification.NotificationGroupUi
import com.flue.launcher.ui.notification.NotificationRevealTarget
import com.flue.launcher.service.WLauncherNotificationListener
import com.flue.launcher.ui.theme.ThemeMode
import com.flue.launcher.ui.theme.UiStyle
import com.flue.launcher.watchface.BUILT_IN_WATCHFACE_ID
import com.flue.launcher.watchface.WatchClockPosition
import com.flue.launcher.watchface.WatchClockColorMode
import com.flue.launcher.watchface.WatchFaceClockStyle
import com.flue.launcher.watchface.WatchFaceMd3eShape
import com.flue.launcher.watchface.LunchWatchFaceDescriptor
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.delay

val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "launcher_settings")

enum class HoneycombFastScrollOptimizationMode(val id: String) {
    Standard("standard"),
    Aggressive("aggressive");
    companion object {
        fun fromId(id: String?): HoneycombFastScrollOptimizationMode =
            entries.firstOrNull { it.id == id } ?: Standard
    }
}

class LauncherViewModel(application: Application) : AndroidViewModel(application) {

    // ===== Delegated ViewModels =====
    val watchFaceVM = WatchFaceViewModel(application)
    val preferencesVM = PreferencesViewModel(application)
    val notificationVM = NotificationViewModel(application)
    val appManagementVM = AppManagementViewModel(application)
    val backupVM = BackupViewModel(application)

    // ===== Screen & Navigation State (kept in LauncherViewModel) =====
    private val _screenState = MutableStateFlow(ScreenState.Face)
    val screenState: StateFlow<ScreenState> = _screenState.asStateFlow()

    private val _launcherInteractive = MutableStateFlow(true)
    val launcherInteractive: StateFlow<Boolean> = _launcherInteractive.asStateFlow()

    private val _backPressRequests = MutableStateFlow(0L)
    val backPressRequests: StateFlow<Long> = _backPressRequests.asStateFlow()

    private val _homePressRequests = MutableStateFlow(0L)
    val homePressRequests: StateFlow<Long> = _homePressRequests.asStateFlow()

    private val _appOpenOrigin = MutableStateFlow(Offset(0.5f, 0.5f))
    val appOpenOrigin: StateFlow<Offset> = _appOpenOrigin.asStateFlow()

    private val _currentApp = MutableStateFlow<AppInfo?>(null)
    val currentApp: StateFlow<AppInfo?> = _currentApp.asStateFlow()

    private val _currentLaunchIcon = MutableStateFlow<ImageBitmap?>(null)
    val currentLaunchIcon: StateFlow<ImageBitmap?> = _currentLaunchIcon.asStateFlow()

    private val _launchSourceState = MutableStateFlow(ScreenState.Apps)
    val launchSourceState: StateFlow<ScreenState> = _launchSourceState.asStateFlow()

    private var launchingExternalApp = false
    private var returnStateAfterExternalLaunch = ScreenState.Apps
    private var launchJob: Job? = null

    private val _hideFromRecents = MutableStateFlow(true)
    val hideFromRecents: StateFlow<Boolean> = _hideFromRecents.asStateFlow()

    private val _directLaunchAppListPreferenceLoaded = MutableStateFlow(false)
    val directLaunchAppListPreferenceLoaded: StateFlow<Boolean> = _directLaunchAppListPreferenceLoaded.asStateFlow()

    init {
        // Wire notification flow with showOngoing preference
        notificationVM.startNotificationFlow(preferencesVM.showOngoingNotifications)
        // Signal that preferences have been loaded (after a short delay)
        viewModelScope.launch {
            delay(600)
            _directLaunchAppListPreferenceLoaded.value = true
        }
    }

    private val repositories = com.flue.launcher.FlueApplication.repositories(application)
    private val store = application.dataStore
    private val appRepository = repositories.appRepository

    // ========================================================================
    // Delegated Properties — Backward Compatible with Existing UI Code
    // ========================================================================

    // --- WatchFaceVM ---
    val availableWatchFaces: StateFlow<List<LunchWatchFaceDescriptor>> get() = watchFaceVM.availableWatchFaces
    val selectedWatchFaceId: StateFlow<String> get() = watchFaceVM.selectedWatchFaceId
    val selectedWatchFace: StateFlow<LunchWatchFaceDescriptor> get() = watchFaceVM.selectedWatchFace
    val watchFaceSelectionReady: StateFlow<Boolean> get() = watchFaceVM.watchFaceSelectionReady
    val watchFaceRefreshToken: StateFlow<Int> get() = watchFaceVM.watchFaceRefreshToken
    val watchFaceLastError: StateFlow<String?> get() = watchFaceVM.watchFaceLastError

    val builtInPhotoPath: StateFlow<String?> get() = watchFaceVM.builtInPhotoPath
    val builtInPhotoClockPosition: StateFlow<WatchClockPosition> get() = watchFaceVM.builtInPhotoClockPosition
    val builtInPhotoClockSize: StateFlow<Int> get() = watchFaceVM.builtInPhotoClockSize
    val builtInPhotoClockBold: StateFlow<Boolean> get() = watchFaceVM.builtInPhotoClockBold
    val builtInPhotoClockStyle: StateFlow<WatchFaceClockStyle> get() = watchFaceVM.builtInPhotoClockStyle
    val builtInPhotoMd3eShape: StateFlow<WatchFaceMd3eShape> get() = watchFaceVM.builtInPhotoMd3eShape
    val builtInPhotoUseThemeTextColor: StateFlow<Boolean> get() = watchFaceVM.builtInPhotoUseThemeTextColor
    val builtInPhotoTextColorArgb: StateFlow<Int> get() = watchFaceVM.builtInPhotoTextColorArgb
    val builtInPhotoMd3eAutoColors: StateFlow<Boolean> get() = watchFaceVM.builtInPhotoMd3eAutoColors
    val builtInPhotoMd3eTextColorArgb: StateFlow<Int> get() = watchFaceVM.builtInPhotoMd3eTextColorArgb
    val builtInPhotoMd3eFaceColorArgb: StateFlow<Int> get() = watchFaceVM.builtInPhotoMd3eFaceColorArgb
    val builtInPhotoMd3eHourColorArgb: StateFlow<Int> get() = watchFaceVM.builtInPhotoMd3eHourColorArgb
    val builtInPhotoMd3eMinuteColorArgb: StateFlow<Int> get() = watchFaceVM.builtInPhotoMd3eMinuteColorArgb
    val builtInPhotoMd3eSecondColorArgb: StateFlow<Int> get() = watchFaceVM.builtInPhotoMd3eSecondColorArgb
    val builtInPhotoShowSeconds: StateFlow<Boolean> get() = watchFaceVM.builtInPhotoShowSeconds
    val builtInPhotoCustomText: StateFlow<String> get() = watchFaceVM.builtInPhotoCustomText

    val builtInVideoPath: StateFlow<String?> get() = watchFaceVM.builtInVideoPath
    val builtInVideoClockPosition: StateFlow<WatchClockPosition> get() = watchFaceVM.builtInVideoClockPosition
    val builtInVideoClockSize: StateFlow<Int> get() = watchFaceVM.builtInVideoClockSize
    val builtInVideoClockBold: StateFlow<Boolean> get() = watchFaceVM.builtInVideoClockBold
    val builtInVideoClockStyle: StateFlow<WatchFaceClockStyle> get() = watchFaceVM.builtInVideoClockStyle
    val builtInVideoMd3eShape: StateFlow<WatchFaceMd3eShape> get() = watchFaceVM.builtInVideoMd3eShape
    val builtInVideoUseThemeTextColor: StateFlow<Boolean> get() = watchFaceVM.builtInVideoUseThemeTextColor
    val builtInVideoTextColorArgb: StateFlow<Int> get() = watchFaceVM.builtInVideoTextColorArgb
    val builtInVideoMd3eAutoColors: StateFlow<Boolean> get() = watchFaceVM.builtInVideoMd3eAutoColors
    val builtInVideoMd3eTextColorArgb: StateFlow<Int> get() = watchFaceVM.builtInVideoMd3eTextColorArgb
    val builtInVideoMd3eFaceColorArgb: StateFlow<Int> get() = watchFaceVM.builtInVideoMd3eFaceColorArgb
    val builtInVideoMd3eHourColorArgb: StateFlow<Int> get() = watchFaceVM.builtInVideoMd3eHourColorArgb
    val builtInVideoMd3eMinuteColorArgb: StateFlow<Int> get() = watchFaceVM.builtInVideoMd3eMinuteColorArgb
    val builtInVideoMd3eSecondColorArgb: StateFlow<Int> get() = watchFaceVM.builtInVideoMd3eSecondColorArgb
    val builtInVideoShowSeconds: StateFlow<Boolean> get() = watchFaceVM.builtInVideoShowSeconds
    val builtInVideoCustomText: StateFlow<String> get() = watchFaceVM.builtInVideoCustomText
    val builtInVideoFillScreen: StateFlow<Boolean> get() = watchFaceVM.builtInVideoFillScreen
    val builtInVideoClockColorMode: StateFlow<WatchClockColorMode> get() = watchFaceVM.builtInVideoClockColorMode
    val builtInWatchFaceFontPath: StateFlow<String?> get() = watchFaceVM.builtInWatchFaceFontPath

    val dingDingCatFillScreen: StateFlow<Boolean> get() = watchFaceVM.dingDingCatFillScreen
    val dingDingCatPlaybackSpeedPercent: StateFlow<Int> get() = watchFaceVM.dingDingCatPlaybackSpeedPercent
    val dingDingCatImportUnlocked: StateFlow<Boolean> get() = watchFaceVM.dingDingCatImportUnlocked

    val watchFaceChargingPowerText: StateFlow<Boolean> get() = watchFaceVM.watchFaceChargingPowerText
    val watchFaceStatusIndicators: StateFlow<Boolean> get() = watchFaceVM.watchFaceStatusIndicators
    val watchFaceBottomFadeEnabled: StateFlow<Boolean> get() = watchFaceVM.watchFaceBottomFadeEnabled
    val builtInManagerThumbnails: StateFlow<Boolean> get() = watchFaceVM.builtInManagerThumbnails
    val hideFromRecents: StateFlow<Boolean> get() = watchFaceVM.builtInManagerThumbnails  // Placeholder - see WatchFaceVM

    // --- PreferencesVM ---
    val layoutMode: StateFlow<LayoutMode> get() = preferencesVM.layoutMode
    val themeMode: StateFlow<ThemeMode> get() = preferencesVM.themeMode
    val uiStyle: StateFlow<UiStyle> get() = preferencesVM.uiStyle
    val sideScreenEnabled: StateFlow<Boolean> get() = preferencesVM.sideScreenEnabled
    val sideScreenShortcutRows: StateFlow<Int> get() = preferencesVM.sideScreenShortcutRows
    val sideScreenShortcutCols: StateFlow<Int> get() = preferencesVM.sideScreenShortcutCols
    val sideScreenShortcuts: StateFlow<List<String?>> get() = preferencesVM.sideScreenShortcuts
    val sideScreenWidgetSlots: StateFlow<List<String?>> get() = preferencesVM.sideScreenWidgetSlots

    val blurEnabled: StateFlow<Boolean> get() = preferencesVM.blurEnabled
    val edgeBlurEnabled: StateFlow<Boolean> get() = preferencesVM.edgeBlurEnabled
    val lowResIcons: StateFlow<Boolean> get() = preferencesVM.lowResIcons
    val animationOverrideEnabled: StateFlow<Boolean> get() = preferencesVM.animationOverrideEnabled
    val splashIcon: StateFlow<Boolean> get() = preferencesVM.splashIcon
    val splashDelay: StateFlow<Int> get() = preferencesVM.splashDelay
    val directLaunchAppListEnabled: StateFlow<Boolean> get() = preferencesVM.directLaunchAppListEnabled

    val honeycombCols: StateFlow<Int> get() = preferencesVM.honeycombCols
    val legacyCircularIcons: StateFlow<Boolean> get() = preferencesVM.legacyCircularIcons
    val honeycombTopBlur: StateFlow<Int> get() = preferencesVM.honeycombTopBlur
    val honeycombBottomBlur: StateFlow<Int> get() = preferencesVM.honeycombBottomBlur
    val honeycombEdgeBlurRadius: StateFlow<Float> get() = preferencesVM.honeycombEdgeBlurRadius
    val honeycombTopFade: StateFlow<Int> get() = preferencesVM.honeycombTopFade
    val honeycombBottomFade: StateFlow<Int> get() = preferencesVM.honeycombBottomFade
    val honeycombFastScrollOptimization: StateFlow<Boolean> get() = preferencesVM.honeycombFastScrollOptimization
    val honeycombFastScrollOptimizationMode: StateFlow<HoneycombFastScrollOptimizationMode> get() = preferencesVM.honeycombFastScrollOptimizationMode

    val appListFisheyeEnabled: StateFlow<Boolean> get() = preferencesVM.appListFisheyeEnabled
    val materialHoneycombTopFisheyeEnabled: StateFlow<Boolean> get() = preferencesVM.materialHoneycombTopFisheyeEnabled
    val appListFisheyeRangeRows: StateFlow<Int> get() = preferencesVM.appListFisheyeRangeRows
    val appListFisheyeStrengthPercent: StateFlow<Int> get() = preferencesVM.appListFisheyeStrengthPercent
    val appListEdgeSpacingCompressionEnabled: StateFlow<Boolean> get() = preferencesVM.appListEdgeSpacingCompressionEnabled
    val appListLeftSafeInsetPercent: StateFlow<Int> get() = preferencesVM.appListLeftSafeInsetPercent
    val appListScalePercent: StateFlow<Int> get() = preferencesVM.appListScalePercent
    val globalUiScalePercent: StateFlow<Int> get() = preferencesVM.globalUiScalePercent
    val appListWatchFaceColors: StateFlow<Boolean> get() = preferencesVM.appListWatchFaceColors
    val appListRowBorderEnabled: StateFlow<Boolean> get() = preferencesVM.appListRowBorderEnabled
    val appListFoldersEnabled: StateFlow<Boolean> get() = preferencesVM.appListFoldersEnabled
    val fastFlowAnimationEnabled: StateFlow<Boolean> get() = preferencesVM.fastFlowAnimationEnabled
    val musicTextSwitchAnimation: StateFlow<String> get() = preferencesVM.musicTextSwitchAnimation

    val twoToneIconsEnabled: StateFlow<Boolean> get() = preferencesVM.twoToneIconsEnabled
    val iconShadowEnabled: StateFlow<Boolean> get() = preferencesVM.iconShadowEnabled
    val classicReturnAnimationEnabled: StateFlow<Boolean> get() = preferencesVM.classicReturnAnimationEnabled
    val selectedIconPackPackage: StateFlow<String?> get() = preferencesVM.selectedIconPackPackage

    val showStepCount: StateFlow<Boolean> get() = preferencesVM.showStepCount
    val showNotification: StateFlow<Boolean> get() = preferencesVM.showNotification
    val showOngoingNotifications: StateFlow<Boolean> get() = preferencesVM.showOngoingNotifications
    val rotaryHapticsEnabled: StateFlow<Boolean> get() = preferencesVM.rotaryHapticsEnabled
    val showWidgetPage: StateFlow<Boolean> get() = preferencesVM.showWidgetPage
    val showControlCenter: StateFlow<Boolean> get() = preferencesVM.showControlCenter
    val showMusicControls: StateFlow<Boolean> get() = preferencesVM.showMusicControls
    val showMediaCustomActions: StateFlow<Boolean> get() = preferencesVM.showMediaCustomActions
    val swapMusicNotificationComponents: StateFlow<Boolean> get() = preferencesVM.swapMusicNotificationComponents
    val doubleTapLockScreenEnabled: StateFlow<Boolean> get() = preferencesVM.doubleTapLockScreenEnabled
    val powerMenuButtonEnabled: StateFlow<Boolean> get() = preferencesVM.powerMenuButtonEnabled

    // --- NotificationVM ---
    val notificationAccessGranted: StateFlow<Boolean> get() = notificationVM.notificationAccessGranted
    val expandedNotificationGroups: StateFlow<Set<String>> get() = notificationVM.expandedNotificationGroups
    val sideScreenPreviewGroups: StateFlow<List<NotificationGroupUi>> get() = notificationVM.sideScreenPreviewGroups
    val notificationGroups: StateFlow<List<NotificationGroupUi>> get() = notificationVM.notificationGroups
    val revealedNotificationTarget: StateFlow<NotificationRevealTarget?> get() = notificationVM.revealedNotificationTarget
    val pendingDismissedNotificationKeys: StateFlow<Set<String>> get() = notificationVM.pendingDismissedNotificationKeys

    // --- AppManagementVM ---
    val allApps: StateFlow<List<AppInfo>> get() = appManagementVM.allApps
    val allSelectableApps: StateFlow<List<AppInfo>> get() = appManagementVM.allSelectableApps
    val apps: StateFlow<List<AppInfo>> get() = appManagementVM.apps
    val openFolder: StateFlow<AppInfo?> get() = appManagementVM.openFolder
    val openFolderItems: StateFlow<List<AppInfo>> get() = appManagementVM.openFolderItems
    val hiddenApps: StateFlow<Set<String>> get() = appManagementVM.hiddenApps
    val appOrder: StateFlow<List<String>> get() = appManagementVM.appOrder

    // --- BackupVM ---
    val isExporting: StateFlow<Boolean> get() = backupVM.isExporting
    val isImporting: StateFlow<Boolean> get() = backupVM.isImporting
    val exportError: StateFlow<String?> get() = backupVM.exportError
    val importError: StateFlow<String?> get() = backupVM.importError
    val backupPreview: StateFlow<FlueBackupPreview?> get() = backupVM.backupPreview

    // ========================================================================
    // Delegated Methods
    // ========================================================================

    // --- App Management ---
    fun openFolder(folder: AppInfo) = appManagementVM.openFolder(folder)
    fun closeFolder() = appManagementVM.closeFolder()
    fun createFolder(fromIndex: Int, toIndex: Int) = appManagementVM.createFolder(fromIndex, toIndex)
    fun removeAppListShortcut(shortcut: AppInfo) = appManagementVM.removeAppListShortcut(shortcut)
    fun renameFolder(folder: AppInfo, name: String) = appManagementVM.renameFolder(folder, name)
    fun reorderFolderItems(folder: AppInfo, orderedItems: List<AppInfo>) = appManagementVM.reorderFolderItems(folder, orderedItems)
    fun dissolveFolder(folder: AppInfo) = appManagementVM.dissolveFolder(folder)
    fun moveItemOutOfFolder(folder: AppInfo, item: AppInfo) = appManagementVM.moveItemOutOfFolder(folder, item)
    fun addItemsToFolder(folder: AppInfo, items: List<AppInfo>) = appManagementVM.addItemsToFolder(folder, items)
    fun setFolderItems(folder: AppInfo, items: List<AppInfo>) = appManagementVM.setFolderItems(folder, items)
    fun setAppOrder(order: List<String>) = appManagementVM.setAppOrder(order)
    fun swapApps(fromIndex: Int, toIndex: Int) = appManagementVM.swapApps(fromIndex, toIndex)
    fun setAppHidden(componentKey: String, hidden: Boolean) = appManagementVM.setAppHidden(componentKey, hidden)
    fun setHiddenApps(components: Set<String>) = appManagementVM.setHiddenApps(components)
    fun clearHiddenApps() = appManagementVM.clearHiddenApps()
    fun refreshIconPacks() = appManagementVM.refreshIconPacks()
    val availableIconPacks: StateFlow<List<IconPackDescriptor>> get() = appManagementVM.availableIconPacks

    // --- Preferences ---
    fun setWidgetSlot(slotIndex: Int, slotValue: String?) = preferencesVM.setWidgetSlot(slotIndex, slotValue)
    fun removeWidgetSlot(slotIndex: Int) = preferencesVM.removeWidgetSlot(slotIndex)
    fun swapWidgetSlots(fromIndex: Int, toIndex: Int) = preferencesVM.swapWidgetSlots(fromIndex, toIndex)
    fun setSideScreenShortcutRows(rows: Int) = preferencesVM.setSideScreenShortcutRows(rows)
    fun setSideScreenShortcutCols(cols: Int) = preferencesVM.setSideScreenShortcutCols(cols)
    fun setSideScreenShortcut(slotIndex: Int, componentKey: String?) = preferencesVM.setSideScreenShortcut(slotIndex, componentKey)
    fun removeSideScreenShortcut(slotIndex: Int) = preferencesVM.removeSideScreenShortcut(slotIndex)
    fun swapSideScreenShortcuts(fromIndex: Int, toIndex: Int) = preferencesVM.swapSideScreenShortcuts(fromIndex, toIndex)
    fun setLayoutMode(mode: LayoutMode) = preferencesVM.setLayoutMode(mode)
    fun setThemeMode(mode: ThemeMode) = preferencesVM.setThemeMode(mode)
    fun setUiStyle(style: UiStyle) = preferencesVM.setUiStyle(style)

    fun setSideScreenEnabled(enabled: Boolean) {
        preferencesVM.setSideScreenEnabled(enabled)
        if (!enabled && (_screenState.value == ScreenState.Stack || _screenState.value == ScreenState.Notifications)) {
            _screenState.value = ScreenState.Face
        }
    }

    fun setBlurEnabled(enabled: Boolean) = preferencesVM.setBlurEnabled(enabled)
    fun setEdgeBlurEnabled(enabled: Boolean) = preferencesVM.setEdgeBlurEnabled(enabled)
    fun setLowResIcons(enabled: Boolean) = preferencesVM.setLowResIcons(enabled)
    fun setLegacyCircularIconsEnabled(enabled: Boolean) = preferencesVM.setLegacyCircularIconsEnabled(enabled)
    fun setAnimationOverrideEnabled(enabled: Boolean) = preferencesVM.setAnimationOverrideEnabled(enabled)
    fun setSplashIcon(enabled: Boolean) = preferencesVM.setSplashIcon(enabled)
    fun setSplashDelay(ms: Int) = preferencesVM.setSplashDelay(ms)
    fun setDirectLaunchAppListEnabled(enabled: Boolean) = preferencesVM.setDirectLaunchAppListEnabled(enabled)
    fun setHoneycombCols(cols: Int) = preferencesVM.setHoneycombCols(cols)
    fun setHoneycombTopBlur(value: Int) = preferencesVM.setHoneycombTopBlur(value)
    fun setHoneycombBottomBlur(value: Int) = preferencesVM.setHoneycombBottomBlur(value)
    fun setHoneycombEdgeBlurRadius(value: Float) = preferencesVM.setHoneycombEdgeBlurRadius(value)
    fun setHoneycombTopFade(value: Int) = preferencesVM.setHoneycombTopFade(value)
    fun setHoneycombBottomFade(value: Int) = preferencesVM.setHoneycombBottomFade(value)
    fun setHoneycombFastScrollOptimization(enabled: Boolean) = preferencesVM.setHoneycombFastScrollOptimization(enabled)
    fun setHoneycombFastScrollOptimizationMode(mode: HoneycombFastScrollOptimizationMode) = preferencesVM.setHoneycombFastScrollOptimizationMode(mode)
    fun setAppListFisheyeEnabled(enabled: Boolean) = preferencesVM.setAppListFisheyeEnabled(enabled)
    fun setMaterialHoneycombTopFisheyeEnabled(enabled: Boolean) = preferencesVM.setMaterialHoneycombTopFisheyeEnabled(enabled)
    fun setAppListFisheyeRangeRows(value: Int) = preferencesVM.setAppListFisheyeRangeRows(value)
    fun setAppListFisheyeStrengthPercent(value: Int) = preferencesVM.setAppListFisheyeStrengthPercent(value)
    fun setAppListEdgeSpacingCompressionEnabled(enabled: Boolean) = preferencesVM.setAppListEdgeSpacingCompressionEnabled(enabled)
    fun setAppListLeftSafeInsetPercent(value: Int) = preferencesVM.setAppListLeftSafeInsetPercent(value)
    fun setAppListScalePercent(value: Int) = preferencesVM.setAppListScalePercent(value)
    fun setGlobalUiScalePercent(value: Int) = preferencesVM.setGlobalUiScalePercent(value)
    fun setAppListWatchFaceColors(enabled: Boolean) = preferencesVM.setAppListWatchFaceColors(enabled)
    fun setAppListRowBorderEnabled(enabled: Boolean) = preferencesVM.setAppListRowBorderEnabled(enabled)
    fun setAppListFoldersEnabled(enabled: Boolean) = preferencesVM.setAppListFoldersEnabled(enabled)
    fun setFastFlowAnimationEnabled(enabled: Boolean) = preferencesVM.setFastFlowAnimationEnabled(enabled)
    fun setMusicTextSwitchAnimation(id: String) = preferencesVM.setMusicTextSwitchAnimation(id)
    fun setTwoToneIconsEnabled(enabled: Boolean) = preferencesVM.setTwoToneIconsEnabled(enabled)
    fun setIconShadowEnabled(enabled: Boolean) = preferencesVM.setIconShadowEnabled(enabled)
    fun setClassicReturnAnimationEnabled(enabled: Boolean) = preferencesVM.setClassicReturnAnimationEnabled(enabled)
    fun setIconPackPackage(packageName: String?) = preferencesVM.setIconPackPackage(packageName)
    fun setShowStepCountEnabled(enabled: Boolean) = preferencesVM.setShowStepCountEnabled(enabled)

    fun setShowNotification(show: Boolean) {
        preferencesVM.setShowNotification(show)
        if (!show && _screenState.value == ScreenState.Notifications) {
            _screenState.value = if (preferencesVM.sideScreenEnabled.value) ScreenState.Stack else ScreenState.Face
        }
    }

    fun setShowOngoingNotifications(show: Boolean) = preferencesVM.setShowOngoingNotifications(show)
    fun setRotaryHapticsEnabled(enabled: Boolean) = preferencesVM.setRotaryHapticsEnabled(enabled)

    fun setShowWidgetPage(show: Boolean) {
        preferencesVM.setShowWidgetPage(show)
        if (!show && _screenState.value == ScreenState.Widgets) {
            _screenState.value = ScreenState.Face
        }
    }

    fun setShowControlCenter(show: Boolean) {
        preferencesVM.setShowControlCenter(show)
        if (!show && _screenState.value == ScreenState.ControlCenter) {
            _screenState.value = ScreenState.Face
        }
    }

    fun setShowMusicControls(show: Boolean) = preferencesVM.setShowMusicControls(show)
    fun setShowMediaCustomActions(show: Boolean) = preferencesVM.setShowMediaCustomActions(show)
    fun setSwapMusicNotificationComponents(enabled: Boolean) = preferencesVM.setSwapMusicNotificationComponents(enabled)
    fun setDoubleTapLockScreenEnabled(enabled: Boolean) = preferencesVM.setDoubleTapLockScreenEnabled(enabled)
    fun setPowerMenuButtonEnabled(enabled: Boolean) = preferencesVM.setPowerMenuButtonEnabled(enabled)

    // --- Watch Face ---
    fun refreshWatchFaces(force: Boolean = false) = watchFaceVM.refreshWatchFaces(force)
    fun selectWatchFace(id: String) = watchFaceVM.setSelectedWatchFace(id)
    fun fallbackToBuiltIn(error: String? = null) = watchFaceVM.fallbackToBuiltIn(error)
    fun clearWatchFaceError() = watchFaceVM.clearWatchFaceError()
    fun requestWatchFaceRefresh() = watchFaceVM.requestWatchFaceRefresh()
    suspend fun importJbWatchFace(uri: Uri) = watchFaceVM.importJbWatchFace(uri)
    suspend fun importDingDingCatWatchFace(uri: Uri): Result<LunchWatchFaceDescriptor> {
        return Result.failure(UnsupportedOperationException("公开版已移除旧版叮叮猫表盘导入"))
    }
    suspend fun importWatchFaceArchive(uri: Uri, allowDingDingCat: Boolean = false) = watchFaceVM.importWatchFaceArchive(uri, allowDingDingCat)
    suspend fun deleteImportedWatchFace(id: String) = watchFaceVM.deleteImportedWatchFace(id)

    fun setBuiltInPhotoPath(path: String?) = watchFaceVM.setBuiltInPhotoPath(path)
    fun setBuiltInVideoPath(path: String?) = watchFaceVM.setBuiltInVideoPath(path)
    fun setBuiltInPhotoClockPosition(position: WatchClockPosition) = watchFaceVM.setBuiltInPhotoClockPosition(position)
    fun setBuiltInVideoClockPosition(position: WatchClockPosition) = watchFaceVM.setBuiltInVideoClockPosition(position)
    fun setBuiltInPhotoClockSize(sizeSp: Int) = watchFaceVM.setBuiltInPhotoClockSize(sizeSp)
    fun setBuiltInVideoClockSize(sizeSp: Int) = watchFaceVM.setBuiltInVideoClockSize(sizeSp)
    fun setBuiltInPhotoClockBold(enabled: Boolean) = watchFaceVM.setBuiltInPhotoClockBold(enabled)
    fun setBuiltInVideoClockBold(enabled: Boolean) = watchFaceVM.setBuiltInVideoClockBold(enabled)
    fun setBuiltInVideoFillScreen(fillScreen: Boolean) = watchFaceVM.setBuiltInVideoFillScreen(fillScreen)
    fun setBuiltInVideoClockColorMode(mode: WatchClockColorMode) = watchFaceVM.setBuiltInVideoClockColorMode(mode)
    fun setBuiltInWatchFaceFontPath(path: String?) = watchFaceVM.setBuiltInWatchFaceFontPath(path)
    fun setBuiltInPhotoClockStyle(style: WatchFaceClockStyle) = watchFaceVM.setBuiltInPhotoClockStyle(style)
    fun setBuiltInVideoClockStyle(style: WatchFaceClockStyle) = watchFaceVM.setBuiltInVideoClockStyle(style)
    fun setBuiltInPhotoMd3eShape(shape: WatchFaceMd3eShape) = watchFaceVM.setBuiltInPhotoMd3eShape(shape)
    fun setBuiltInVideoMd3eShape(shape: WatchFaceMd3eShape) = watchFaceVM.setBuiltInVideoMd3eShape(shape)
    fun setBuiltInPhotoUseThemeTextColor(enabled: Boolean) = watchFaceVM.setBuiltInPhotoUseThemeTextColor(enabled)
    fun setBuiltInVideoUseThemeTextColor(enabled: Boolean) = watchFaceVM.setBuiltInVideoUseThemeTextColor(enabled)
    fun setBuiltInPhotoTextColorArgb(argb: Int) = watchFaceVM.setBuiltInPhotoTextColorArgb(argb)
    fun setBuiltInVideoTextColorArgb(argb: Int) = watchFaceVM.setBuiltInVideoTextColorArgb(argb)
    fun setBuiltInPhotoMd3eAutoColors(enabled: Boolean) = watchFaceVM.setBuiltInPhotoMd3eAutoColors(enabled)
    fun setBuiltInVideoMd3eAutoColors(enabled: Boolean) = watchFaceVM.setBuiltInVideoMd3eAutoColors(enabled)
    fun setBuiltInPhotoMd3eTextColorArgb(argb: Int) = watchFaceVM.setBuiltInPhotoMd3eTextColorArgb(argb)
    fun setBuiltInVideoMd3eTextColorArgb(argb: Int) = watchFaceVM.setBuiltInVideoMd3eTextColorArgb(argb)
    fun setBuiltInPhotoMd3eFaceColorArgb(argb: Int) = watchFaceVM.setBuiltInPhotoMd3eFaceColorArgb(argb)
    fun setBuiltInVideoMd3eFaceColorArgb(argb: Int) = watchFaceVM.setBuiltInVideoMd3eFaceColorArgb(argb)
    fun setBuiltInPhotoMd3eHourColorArgb(argb: Int) = watchFaceVM.setBuiltInPhotoMd3eHourColorArgb(argb)
    fun setBuiltInVideoMd3eHourColorArgb(argb: Int) = watchFaceVM.setBuiltInVideoMd3eHourColorArgb(argb)
    fun setBuiltInPhotoMd3eMinuteColorArgb(argb: Int) = watchFaceVM.setBuiltInPhotoMd3eMinuteColorArgb(argb)
    fun setBuiltInVideoMd3eMinuteColorArgb(argb: Int) = watchFaceVM.setBuiltInVideoMd3eMinuteColorArgb(argb)
    fun setBuiltInPhotoMd3eSecondColorArgb(argb: Int) = watchFaceVM.setBuiltInPhotoMd3eSecondColorArgb(argb)
    fun setBuiltInVideoMd3eSecondColorArgb(argb: Int) = watchFaceVM.setBuiltInVideoMd3eSecondColorArgb(argb)
    fun setBuiltInPhotoShowSeconds(enabled: Boolean) = watchFaceVM.setBuiltInPhotoShowSeconds(enabled)
    fun setBuiltInVideoShowSeconds(enabled: Boolean) = watchFaceVM.setBuiltInVideoShowSeconds(enabled)
    fun setBuiltInPhotoCustomText(text: String) = watchFaceVM.setBuiltInPhotoCustomText(text)
    fun setBuiltInVideoCustomText(text: String) = watchFaceVM.setBuiltInVideoCustomText(text)
    fun setBuiltInManagerThumbnails(enabled: Boolean) = watchFaceVM.setBuiltInManagerThumbnails(enabled)
    fun setWatchFaceChargingPowerTextEnabled(enabled: Boolean) = watchFaceVM.setWatchFaceChargingPowerTextEnabled(enabled)
    fun setWatchFaceStatusIndicatorsEnabled(enabled: Boolean) = watchFaceVM.setWatchFaceStatusIndicatorsEnabled(enabled)
    fun setWatchFaceBottomFadeEnabled(enabled: Boolean) = watchFaceVM.setWatchFaceBottomFadeEnabled(enabled)
    fun setDingDingCatFillScreenEnabled(enabled: Boolean) = watchFaceVM.setDingDingCatFillScreenEnabled(enabled)
    fun setDingDingCatPlaybackSpeedPercent(value: Int) = watchFaceVM.setDingDingCatPlaybackSpeedPercent(value)
    fun setDingDingCatImportUnlocked(unlocked: Boolean) = watchFaceVM.setDingDingCatImportUnlocked(unlocked)

    // --- Notification ---
    fun refreshNotificationAccess() = notificationVM.refreshNotificationAccess()
    fun isAccessibilityServiceEnabled(): Boolean = notificationVM.isAccessibilityServiceEnabled()
    fun setRevealedNotificationTarget(target: NotificationRevealTarget?) = notificationVM.setRevealedNotificationTarget(target)
    fun toggleNotificationGroup(packageName: String) = notificationVM.toggleNotificationGroup(packageName)
    fun collapseNotificationGroups() = notificationVM.collapseNotificationGroups()
    fun expandAllNotificationGroups() = notificationVM.expandAllNotificationGroups()
    fun dismissNotificationGroup(packageName: String) = notificationVM.dismissNotificationGroup(packageName)
    fun dismissNotification(key: String) = notificationVM.dismissNotification(key)
    fun dismissAllNotifications() = notificationVM.dismissAllNotifications()
    fun runNotificationAction(actionKey: String): Boolean = notificationVM.runNotificationAction(actionKey)

    // --- Backup ---
    fun suggestedBackupFileName(): String = backupVM.suggestedBackupFileName()
    suspend fun exportBackup(uri: Uri, options: FlueBackupOptions) = backupVM.exportBackup(uri, options)
    suspend fun readBackupPreview(uri: Uri) = backupVM.readBackupPreview(uri)

    suspend fun importBackup(uri: Uri, options: FlueBackupOptions): Result<Unit> {
        val availableAppKeys = allApps.value.ifEmpty { apps.value }.map { it.componentKey }.toSet()
        return backupVM.importBackup(uri, options, availableAppKeys,
            onComplete = {
                refreshWatchFaces(force = true)
                refreshIconPacks()
                requestWatchFaceRefresh()
            },
            onError = { /* errors handled by BackupVM */ }
        )
    }

    fun clearExportError() = backupVM.clearExportError()
    fun clearImportError() = backupVM.clearImportError()
    fun clearBackupPreview() = backupVM.clearBackupPreview()

    // ========================================================================
    // Screen & Navigation (kept local)
    // ========================================================================

    fun setHideFromRecents(enabled: Boolean) {
        _hideFromRecents.value = enabled
        viewModelScope.launch {
            store.edit { it[PrefKeys.KEY_HIDE_FROM_RECENTS] = enabled }
        }
    }
    fun setState(state: ScreenState) {
        _screenState.value = when {
            state == ScreenState.Notifications && !preferencesVM.sideScreenEnabled.value -> ScreenState.Face
            state == ScreenState.Notifications && !preferencesVM.showNotification.value -> ScreenState.Stack
            state == ScreenState.Widgets && !preferencesVM.showWidgetPage.value -> ScreenState.Face
            state == ScreenState.ControlCenter && !preferencesVM.showControlCenter.value -> ScreenState.Face
            else -> state
        }
        if (_screenState.value != ScreenState.Apps) {
            closeFolder()
        }
        notificationVM.setRevealedNotificationTarget(null)
    }

            closeFolder()
        }
        notificationVM.setRevealedNotificationTarget(null)
    }

    fun setLauncherInteractive(interactive: Boolean) {
        _launcherInteractive.value = interactive
    }

    fun openApp(
        appInfo: AppInfo,
        origin: Offset = Offset(0.5f, 0.5f),
        launchDelayMs: Long = preferencesVM.splashDelay.value.toLong(),
        returnState: ScreenState = ScreenState.Apps
    ) {
        if (appInfo.isFolder) {
            openFolder(appInfo)
            return
        }
        if (!appInfo.isAppListShortcut && appInfo.packageName == getApplication<Application>().packageName) {
            Toast.makeText(getApplication(), "已阻止启动自身应用", Toast.LENGTH_SHORT).show()
            return
        }
        returnStateAfterExternalLaunch = returnState
        _launchSourceState.value = returnState
        _currentApp.value = appInfo
        _currentLaunchIcon.value = appInfo.iconForDisplay(useTwoTone = preferencesVM.twoToneIconsEnabled.value)
        _appOpenOrigin.value = origin
        _screenState.value = ScreenState.App
        notificationVM.setRevealedNotificationTarget(null)

        launchJob?.cancel()
        launchJob = viewModelScope.launch {
            delay(launchDelayMs)
            val launched = appRepository.launchApp(appInfo)
            if (launched) {
                launchingExternalApp = true
            } else {
                launchingExternalApp = false
                _currentApp.value = null
                _currentLaunchIcon.value = null
                _screenState.value = returnStateAfterExternalLaunch
                Toast.makeText(getApplication(), "应用无法启动，已刷新列表", Toast.LENGTH_SHORT).show()
            }
        }
    }

    fun openNotification(
        key: String,
        origin: Offset = Offset(0.5f, 0.5f),
        returnState: ScreenState = ScreenState.Stack,
        launchDelayMs: Long = preferencesVM.splashDelay.value.toLong()
    ): Boolean {
        val targetEntry = notificationVM.notificationGroups.value
            .asSequence()
            .flatMap { it.entries.asSequence() }
            .firstOrNull { it.key == key }
            ?: return false
        returnStateAfterExternalLaunch = returnState
        _launchSourceState.value = returnState
        _currentApp.value = null
        _currentLaunchIcon.value = targetEntry.icon
        _appOpenOrigin.value = origin
        _screenState.value = ScreenState.App
        notificationVM.setRevealedNotificationTarget(null)

        launchJob?.cancel()
        launchJob = viewModelScope.launch {
            delay(launchDelayMs)
            val opened = WLauncherNotificationListener.openNotification(key)
            if (opened) {
                launchingExternalApp = true
            } else {
                launchingExternalApp = false
                _currentLaunchIcon.value = null
                _screenState.value = returnStateAfterExternalLaunch
            }
        }
        return true
    }

    fun onReturnToLauncher() {
        if (launchingExternalApp) {
            launchingExternalApp = false
            _currentLaunchIcon.value = null
            _screenState.value = returnStateAfterExternalLaunch
            notificationVM.setRevealedNotificationTarget(null)
        }
    }

    fun hasPendingExternalLaunchReturn(): Boolean = launchingExternalApp

    fun handleHomePress() {
        when (_screenState.value) {
            ScreenState.Face -> _screenState.value = ScreenState.Apps
            ScreenState.Apps -> _screenState.value = ScreenState.Face
            ScreenState.App -> {
                launchJob?.cancel()
                launchJob = null
                launchingExternalApp = false
                _currentLaunchIcon.value = null
                _screenState.value = returnStateAfterExternalLaunch
                notificationVM.setRevealedNotificationTarget(null)
            }
            else -> _screenState.value = ScreenState.Face
        }
    }

    fun requestHomePress() {
        _homePressRequests.value = _homePressRequests.value + 1L
    }

    fun requestBackPress() {
        _backPressRequests.value = _backPressRequests.value + 1L
    }

    fun handleBackPress() {
        when (_screenState.value) {
            ScreenState.Face -> Unit
            ScreenState.Apps -> {
                if (appManagementVM.openFolder.value != null) {
                    closeFolder()
                } else {
                    _screenState.value = ScreenState.Face
                }
            }
            ScreenState.App -> {
                launchJob?.cancel()
                launchJob = null
                launchingExternalApp = false
                _currentLaunchIcon.value = null
                _screenState.value = returnStateAfterExternalLaunch
                notificationVM.setRevealedNotificationTarget(null)
            }
            ScreenState.Settings -> _screenState.value = ScreenState.Apps
            ScreenState.Stack -> _screenState.value = ScreenState.Face
            ScreenState.Notifications -> _screenState.value = if (preferencesVM.sideScreenEnabled.value) ScreenState.Stack else ScreenState.Face
            ScreenState.Widgets -> _screenState.value = ScreenState.Face
            ScreenState.ControlCenter -> _screenState.value = ScreenState.Face
        }
    }

    // ========================================================================
    // Vibrator
    // ========================================================================

    val defaultVibrator: Vibrator by lazy {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val vm = getApplication<Application>().getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as VibratorManager
            vm.defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            getApplication<Application>().getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
        }
    }

    // ========================================================================

    // ========================================================================
    // Reset Settings
    // ========================================================================

    fun resetSettings() {
        watchFaceVM.resetSettings()
        preferencesVM.resetSettings()
        notificationVM.resetSettings()
        appManagementVM.resetSettings()
    }

    // ========================================================================
    // Lifecycle
    // ========================================================================

    override fun onCleared() {
        launchJob?.cancel()
        super.onCleared()
    }
}
