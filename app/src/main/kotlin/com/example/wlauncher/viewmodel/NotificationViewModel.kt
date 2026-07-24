package com.flue.launcher.viewmodel

import android.app.Application
import android.content.ComponentName
import android.provider.Settings
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.flue.launcher.service.NotifData
import com.flue.launcher.service.WLauncherNotificationListener
import com.flue.launcher.ui.notification.NotificationActionUi
import com.flue.launcher.ui.notification.NotificationEntryUi
import com.flue.launcher.ui.notification.NotificationGroupUi
import com.flue.launcher.ui.notification.NotificationRevealTarget
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch

class NotificationViewModel(application: Application) : AndroidViewModel(application) {

    // ===== Access Status =====
    private val _notificationAccessGranted = MutableStateFlow(false)
    val notificationAccessGranted: StateFlow<Boolean> = _notificationAccessGranted.asStateFlow()

    // ===== Notification Groups =====
    private val _expandedNotificationGroups = MutableStateFlow<Set<String>>(emptySet())
    val expandedNotificationGroups: StateFlow<Set<String>> = _expandedNotificationGroups.asStateFlow()

    private val _sideScreenPreviewGroups = MutableStateFlow<List<NotificationGroupUi>>(emptyList())
    val sideScreenPreviewGroups: StateFlow<List<NotificationGroupUi>> = _sideScreenPreviewGroups.asStateFlow()

    private val _notificationGroups = MutableStateFlow<List<NotificationGroupUi>>(emptyList())
    val notificationGroups: StateFlow<List<NotificationGroupUi>> = _notificationGroups.asStateFlow()

    // ===== Reveal Target =====
    private val _revealedNotificationTarget = MutableStateFlow<NotificationRevealTarget?>(null)
    val revealedNotificationTarget: StateFlow<NotificationRevealTarget?> = _revealedNotificationTarget.asStateFlow()

    // ===== Pending Dismissed Keys =====
    private val _pendingDismissedNotificationKeys = MutableStateFlow<Set<String>>(emptySet())
    val pendingDismissedNotificationKeys: StateFlow<Set<String>> = _pendingDismissedNotificationKeys.asStateFlow()

    // ===== Internal =====
    var pendingNotificationClearJob: Job? = null

    /**
     * Callback used by LauncherViewModel to wire the notification combine flow
     * with the showOngoing preference from PreferencesViewModel.
     */
    fun startNotificationFlow(showOngoingFlow: StateFlow<Boolean>) {
        refreshNotificationAccess()
        viewModelScope.launch {
            combine(
                WLauncherNotificationListener.notifications,
                _expandedNotificationGroups,
                _pendingDismissedNotificationKeys,
                showOngoingFlow
            ) { notifications, expandedPackages, pendingDismissedKeys, showOngoing ->
                val visibleNotifications = notifications.filter { shouldShowNotification(it, showOngoing) }
                val filteredNotifications = visibleNotifications.filterNot { it.key in pendingDismissedKeys }
                val activeNotificationKeys = notifications.asSequence().map { it.key }.toSet()
                Triple(
                    buildNotificationGroups(filteredNotifications, emptySet()),
                    buildNotificationGroups(filteredNotifications, expandedPackages),
                    pendingDismissedKeys.intersect(activeNotificationKeys)
                )
            }.collect { (previewGroups, groups, remainingPendingDismissedKeys) ->
                updateStableNotificationGroups(previewGroups, groups)
                if (_pendingDismissedNotificationKeys.value != remainingPendingDismissedKeys) {
                    _pendingDismissedNotificationKeys.value = remainingPendingDismissedKeys
                }
                _expandedNotificationGroups.value =
                    _expandedNotificationGroups.value.intersect(groups.map(NotificationGroupUi::packageName).toSet())
                val currentRevealTarget = _revealedNotificationTarget.value
                val validRevealTarget = when (currentRevealTarget) {
                    is NotificationRevealTarget.Group -> groups.any { it.packageName == currentRevealTarget.packageName }
                    is NotificationRevealTarget.Entry -> groups.any { group -> group.entries.any { it.key == currentRevealTarget.key } }
                    null -> true
                }
                if (!validRevealTarget) {
                    _revealedNotificationTarget.value = null
                }
            }
        }
    }

    fun refreshNotificationAccess() {
        val application = getApplication<Application>()
        val component = ComponentName(application, WLauncherNotificationListener::class.java)
        val enabledListeners = Settings.Secure.getString(
            application.contentResolver,
            "enabled_notification_listeners"
        )
        val granted = enabledListeners
            ?.split(':')
            ?.mapNotNull { ComponentName.unflattenFromString(it) }
            ?.any { it.packageName == component.packageName && it.className == component.className }
            ?: false

        _notificationAccessGranted.value = granted || WLauncherNotificationListener.isConnected()
        if (granted) {
            WLauncherNotificationListener.requestRebindIfNeeded(application)
        }
    }

    fun isAccessibilityServiceEnabled(): Boolean {
        val application = getApplication<Application>()
        return com.flue.launcher.service.FlueAccessibilityService.isEnabled(application) ||
            com.flue.launcher.service.FlueAccessibilityService.isConnected()
    }

    // ===== Notification Group Management =====

    fun setRevealedNotificationTarget(target: NotificationRevealTarget?) {
        _revealedNotificationTarget.value = target
    }

    fun toggleNotificationGroup(packageName: String) {
        _expandedNotificationGroups.value = if (packageName in _expandedNotificationGroups.value) {
            _expandedNotificationGroups.value - packageName
        } else {
            _expandedNotificationGroups.value + packageName
        }
    }

    fun collapseNotificationGroups() {
        _expandedNotificationGroups.value = emptySet()
    }

    fun expandAllNotificationGroups() {
        _expandedNotificationGroups.value = _notificationGroups.value.map { it.packageName }.toSet()
    }

    fun expandNotificationGroup(packageName: String) {
        _expandedNotificationGroups.value = _expandedNotificationGroups.value + packageName
    }

    fun collapseNotificationGroup(packageName: String) {
        _expandedNotificationGroups.value = _expandedNotificationGroups.value - packageName
    }

    fun dismissNotificationGroup(packageName: String) {
        val keys = _notificationGroups.value
            .firstOrNull { it.packageName == packageName }
            ?.entries
            ?.map { it.key }
            ?: return
        markNotificationsDismissed(keys)
        val stillVisibleNotifications = _notificationGroups.value
            .filter { it.packageName != packageName }
            .flatMap { it.entries }
        if (stillVisibleNotifications.isEmpty()) {
            _revealedNotificationTarget.value = null
        }
    }

    fun dismissNotification(key: String) {
        markNotificationsDismissed(listOf(key))
        val stillVisible = _notificationGroups.value
            .flatMap { it.entries }
            .any { it.key != key }
        if (!stillVisible) {
            _revealedNotificationTarget.value = null
        }
    }

    fun dismissAllNotifications() {
        val allKeys = _notificationGroups.value.flatMap { it.entries }.map { it.key }
        markNotificationsDismissed(allKeys)
        _revealedNotificationTarget.value = null
    }

    private fun markNotificationsDismissed(keys: Collection<String>) {
        if (keys.isEmpty()) return
        _pendingDismissedNotificationKeys.value = _pendingDismissedNotificationKeys.value + keys
    }

    fun clearPendingDismissedNotificationKeys() {
        _pendingDismissedNotificationKeys.value = emptySet()
    }

    fun setSideScreenPreviewGroups(groups: List<NotificationGroupUi>) {
        _sideScreenPreviewGroups.value = groups
        scheduleClearSideScreenPreviewGroups()
    }

    fun setNotificationGroups(groups: List<NotificationGroupUi>) {
        _notificationGroups.value = groups
    }

    // ===== Notification Actions =====

    fun runNotificationAction(actionKey: String): Boolean {
        _revealedNotificationTarget.value = null
        return WLauncherNotificationListener.runNotificationAction(actionKey)
    }

    // ===== Helper Methods =====

    private fun scheduleClearSideScreenPreviewGroups() {
        pendingNotificationClearJob?.cancel()
        pendingNotificationClearJob = viewModelScope.launch {
            delay(220)
            _sideScreenPreviewGroups.value = emptyList()
        }
    }

    private fun updateStableNotificationGroups(
        previewGroups: List<NotificationGroupUi>,
        groups: List<NotificationGroupUi>
    ) {
        if (previewGroups.isNotEmpty() || groups.isNotEmpty()) {
            pendingNotificationClearJob?.cancel()
            pendingNotificationClearJob = null
            _sideScreenPreviewGroups.value = previewGroups
            _notificationGroups.value = groups
            return
        }
        if (_sideScreenPreviewGroups.value.isEmpty() && _notificationGroups.value.isEmpty()) {
            return
        }
        pendingNotificationClearJob?.cancel()
        pendingNotificationClearJob = viewModelScope.launch {
            delay(220)
            _sideScreenPreviewGroups.value = emptyList()
            _notificationGroups.value = emptyList()
        }
    }

    private fun buildNotificationGroups(
        notifications: List<NotifData>,
        expandedPackages: Set<String>
    ): List<NotificationGroupUi> {
        return notifications
            .groupBy { it.packageName }
            .values
            .map { group ->
                val children = group
                    .sortedByDescending { it.time }
                    .map { item ->
                        NotificationEntryUi(
                            key = item.key,
                            packageName = item.packageName,
                            groupKey = item.groupKey,
                            appLabel = item.appLabel,
                            title = item.title,
                            text = item.text,
                            time = item.time,
                            icon = item.icon,
                            isClearable = item.isClearable,
                            contentIntentAvailable = item.contentIntentAvailable,
                            isOngoing = item.isOngoing,
                            isForegroundService = item.isForegroundService,
                            actions = item.actions.map { action ->
                                NotificationActionUi(key = action.key, title = action.title)
                            }
                        )
                    }
                val latest = children.first()
                NotificationGroupUi(
                    packageName = latest.packageName,
                    appLabel = latest.appLabel,
                    headerTitle = latest.appLabel,
                    icon = latest.icon,
                    latestTime = latest.time,
                    latestSummary = latest.text.ifBlank { latest.title.ifBlank { latest.appLabel } },
                    entries = children,
                    visiblePreviewEntries = children.take(1),
                    hiddenPreviewCount = (children.size - 1).coerceAtLeast(0),
                    expanded = latest.packageName in expandedPackages
                )
            }
            .sortedByDescending { it.latestTime }
    }

    private fun shouldShowNotification(item: NotifData, showOngoing: Boolean): Boolean {
        if (item.isSystemHidden) return false
        if (item.isNoisyOngoing) return false
        return showOngoing || (!item.isOngoing && !item.isForegroundService)
    }

    // ===== Reset =====

    fun resetSettings() {
        _notificationAccessGranted.value = false
        _expandedNotificationGroups.value = emptySet()
        _revealedNotificationTarget.value = null
        clearPendingDismissedNotificationKeys()
    }

    override fun onCleared() {
        pendingNotificationClearJob?.cancel()
        super.onCleared()
    }
}
