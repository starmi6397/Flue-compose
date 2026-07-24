package com.flue.launcher.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.flue.launcher.data.model.AppInfo
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.util.concurrent.ConcurrentHashMap

class AppManagementViewModel(application: Application) : AndroidViewModel(application) {

    private val store = application.dataStore
    private val repositories = com.flue.launcher.FlueApplication.repositories(application)
    private val appRepository = repositories.appRepository

    // ===== App List State =====
    val allApps: StateFlow<List<AppInfo>> = appRepository.allApps
    val allSelectableApps: StateFlow<List<AppInfo>> = appRepository.allSelectableApps
    val apps: StateFlow<List<AppInfo>> = appRepository.apps

    // ===== Folder State =====
    private val _openFolder = MutableStateFlow<AppInfo?>(null)
    val openFolder: StateFlow<AppInfo?> = _openFolder.asStateFlow()

    private val _openFolderItems = MutableStateFlow<List<AppInfo>>(emptyList())
    val openFolderItems: StateFlow<List<AppInfo>> = _openFolderItems.asStateFlow()

    // ===== Hidden Apps =====
    private val _hiddenApps = MutableStateFlow<Set<String>>(emptySet())
    val hiddenApps: StateFlow<Set<String>> = _hiddenApps.asStateFlow()

    // ===== App Order =====
    private val _appOrder = MutableStateFlow<List<String>>(emptyList())
    val appOrder: StateFlow<List<String>> = _appOrder.asStateFlow()

    // ===== Internal =====
    private val pendingWriteJobs = ConcurrentHashMap<String, Job>()

    init {
        viewModelScope.launch(Dispatchers.IO) {
            store.data.collect { prefs ->
                val loadedOrder = prefs[PrefKeys.KEY_APP_ORDER]
                    ?.takeIf { it.isNotEmpty() }
                    ?.split(",")
                    ?: emptyList()
                if (_appOrder.value != loadedOrder) {
                    _appOrder.value = loadedOrder
                    appRepository.setCustomOrder(loadedOrder)
                }

                val hidden = prefs[PrefKeys.KEY_HIDDEN_APPS]
                    ?.split(",")
                    ?.filter(String::isNotBlank)
                    ?.toSet()
                    ?: emptySet()
                if (_hiddenApps.value != hidden) {
                    _hiddenApps.value = hidden
                    appRepository.setHiddenComponents(hidden)
                }
            }
        }
    }

    // ===== Folder Management =====

    fun openFolder(folder: AppInfo) {
        if (!folder.isFolder) return
        _openFolder.value = folder
        _openFolderItems.value = appRepository.folderItems(folder.componentKey)
    }

    fun closeFolder() {
        _openFolder.value = null
        _openFolderItems.value = emptyList()
    }

    fun createFolder(fromIndex: Int, toIndex: Int) {
        val current = apps.value.toMutableList()
        if (fromIndex !in current.indices || toIndex !in current.indices || fromIndex == toIndex) return
        val source = current[fromIndex]
        val target = current[toIndex]
        val folderKey = appRepository.createFolder(source.componentKey, target.componentKey) ?: return
        val insertIndex = minOf(fromIndex, toIndex)
        val nextOrder = current
            .filterIndexed { index, _ -> index != fromIndex && index != toIndex }
            .map { it.componentKey }
            .toMutableList()
        nextOrder.add(insertIndex.coerceIn(0, nextOrder.size), folderKey)
        setAppOrder(nextOrder)
    }

    fun removeAppListShortcut(shortcut: AppInfo) {
        if (!shortcut.isAppListShortcut) return
        if (appRepository.removeShortcut(shortcut.componentKey)) {
            setAppOrder(_appOrder.value.filterNot { it == shortcut.componentKey })
            closeFolder()
        }
    }

    fun renameFolder(folder: AppInfo, name: String) {
        if (!folder.isFolder) return
        if (appRepository.renameFolder(folder.componentKey, name)) {
            val renamed = folder.copy(label = name.ifBlank { "文件夹" })
            if (_openFolder.value?.componentKey == folder.componentKey) {
                _openFolder.value = renamed
            }
        }
    }

    fun reorderFolderItems(folder: AppInfo, orderedItems: List<AppInfo>) {
        if (!folder.isFolder) return
        val nextKeys = appRepository.reorderFolderItems(folder.componentKey, orderedItems.map { it.componentKey })
        if (nextKeys.isNotEmpty()) {
            _openFolderItems.value = appRepository.itemsForKeys(nextKeys)
        }
    }

    fun dissolveFolder(folder: AppInfo) {
        if (!folder.isFolder) return
        val childKeys = appRepository.dissolveFolder(folder.componentKey)
        if (childKeys.isEmpty()) return
        val currentOrder = apps.value.map { it.componentKey }.toMutableList()
        val folderIndex = currentOrder.indexOf(folder.componentKey)
        currentOrder.remove(folder.componentKey)
        currentOrder.addAll(folderIndex.coerceIn(0, currentOrder.size), childKeys)
        setAppOrder(currentOrder)
        closeFolder()
    }

    fun moveItemOutOfFolder(folder: AppInfo, item: AppInfo) {
        if (!folder.isFolder) return
        val remainingKeys = appRepository.moveItemOutOfFolder(folder.componentKey, item.componentKey)
        val remainingItems = appRepository.itemsForKeys(remainingKeys)
        val dissolvesFolder = remainingKeys.size <= 1
        val currentOrder = apps.value.map { it.componentKey }.toMutableList()
        val folderIndex = currentOrder.indexOf(folder.componentKey).takeIf { it >= 0 } ?: currentOrder.size
        if (dissolvesFolder) {
            currentOrder.remove(folder.componentKey)
            val insertAt = folderIndex.coerceIn(0, currentOrder.size)
            remainingItems.forEachIndexed { offset, remaining ->
                if (remaining.componentKey !in currentOrder) {
                    currentOrder.add((insertAt + offset).coerceIn(0, currentOrder.size), remaining.componentKey)
                }
            }
        }
        val insertAt = folderIndex.coerceIn(0, currentOrder.size)
        if (item.componentKey !in currentOrder) {
            val offset = if (dissolvesFolder) remainingItems.size else 1
            currentOrder.add((insertAt + offset).coerceIn(0, currentOrder.size), item.componentKey)
        }
        setAppOrder(currentOrder)
        if (dissolvesFolder) closeFolder()
        else _openFolderItems.value = remainingItems
    }

    fun addItemsToFolder(folder: AppInfo, items: List<AppInfo>) {
        if (!folder.isFolder || items.isEmpty()) return
        val addedKeys = items.asSequence()
            .filterNot { it.isFolder }
            .map { it.componentKey }
            .filter { it != folder.componentKey }
            .distinct()
            .toList()
        if (addedKeys.isEmpty()) return
        val nextKeys = appRepository.addItemsToFolder(folder.componentKey, addedKeys)
        if (nextKeys.isEmpty()) return
        val addedKeySet = addedKeys.toSet()
        val currentOrder = apps.value.map { it.componentKey }.filterNot { it in addedKeySet }
        setAppOrder(currentOrder)
        _openFolderItems.value = appRepository.itemsForKeys(nextKeys)
    }

    fun setFolderItems(folder: AppInfo, items: List<AppInfo>) {
        if (!folder.isFolder) return
        val originalKeys = appRepository.folderItemKeys(folder.componentKey)
        val folderSnapshot = appRepository.folderMembershipSnapshot()
        val selectedKeys = items.asSequence()
            .filterNot { it.isFolder }
            .map { it.componentKey }
            .filter { it != folder.componentKey }
            .distinct()
            .toList()
        val selectedKeySet = selectedKeys.toSet()
        val dissolvedOtherFolderItems = folderSnapshot
            .filterKeys { it != folder.componentKey }
            .mapNotNull { (otherFolderKey, otherKeys) ->
                val keptKeys = otherKeys.filterNot { it in selectedKeySet }
                if (keptKeys != otherKeys && keptKeys.size <= 1) {
                    otherFolderKey to keptKeys.filter { appRepository.itemForKey(it) != null }
                } else null
            }
        val nextKeys = appRepository.setFolderItems(folder.componentKey, selectedKeys)
        val currentTopLevelKeys = apps.value.map { it.componentKey }.toMutableList()
        val folderIndex = currentTopLevelKeys.indexOf(folder.componentKey).takeIf { it >= 0 }
        val removedKeys = originalKeys.filter { it !in selectedKeySet }.filter { appRepository.itemForKey(it) != null }
        currentTopLevelKeys.removeAll(selectedKeySet)
        dissolvedOtherFolderItems.forEach { (otherFolderKey, keptKeys) ->
            val otherFolderIndex = currentTopLevelKeys.indexOf(otherFolderKey).takeIf { it >= 0 }
            val otherInsertAt = (otherFolderIndex ?: currentTopLevelKeys.size).coerceIn(0, currentTopLevelKeys.size)
            currentTopLevelKeys.remove(otherFolderKey)
            keptKeys.forEachIndexed { offset, key ->
                if (key !in currentTopLevelKeys) {
                    currentTopLevelKeys.add((otherInsertAt + offset).coerceIn(0, currentTopLevelKeys.size), key)
                }
            }
        }
        val insertAt = currentTopLevelKeys.indexOf(folder.componentKey).takeIf { it >= 0 }
            ?: (folderIndex ?: currentTopLevelKeys.size).coerceIn(0, currentTopLevelKeys.size)
        if (nextKeys.size <= 1) {
            currentTopLevelKeys.remove(folder.componentKey)
            val restoredKeys = nextKeys + removedKeys.filter { it !in nextKeys }
            restoredKeys.forEachIndexed { offset, key ->
                if (key !in currentTopLevelKeys) {
                    currentTopLevelKeys.add((insertAt + offset).coerceIn(0, currentTopLevelKeys.size), key)
                }
            }
            setAppOrder(currentTopLevelKeys)
            closeFolder()
            return
        }
        removedKeys.forEachIndexed { offset, key ->
            if (key !in currentTopLevelKeys) {
                currentTopLevelKeys.add((insertAt + 1 + offset).coerceIn(0, currentTopLevelKeys.size), key)
            }
        }
        if (folder.componentKey !in currentTopLevelKeys) {
            currentTopLevelKeys.add(insertAt, folder.componentKey)
        }
        setAppOrder(currentTopLevelKeys)
        _openFolderItems.value = appRepository.itemsForKeys(nextKeys)
    }

    // ===== App Order =====

    fun setAppOrder(order: List<String>) {
        _appOrder.value = order
        viewModelScope.launch(Dispatchers.IO) { appRepository.setCustomOrder(order) }
        persist { store.edit { it[PrefKeys.KEY_APP_ORDER] = order.joinToString(",") } }
    }

    fun swapApps(fromIndex: Int, toIndex: Int) {
        val current = apps.value.toMutableList()
        if (fromIndex !in current.indices || toIndex !in current.indices) return
        val item = current.removeAt(fromIndex)
        current.add(toIndex, item)
        setAppOrder(current.map { it.componentKey })
    }

    // ===== Hidden Apps =====

    fun setAppHidden(componentKey: String, hidden: Boolean) {
        val next = _hiddenApps.value.toMutableSet().apply {
            if (hidden) add(componentKey) else remove(componentKey)
        }
        setHiddenApps(next)
    }

    fun setHiddenApps(components: Set<String>) {
        val next = components.filter(String::isNotBlank).toSet()
        _hiddenApps.value = next
        viewModelScope.launch(Dispatchers.IO) { appRepository.setHiddenComponents(next) }
        persist {
            store.edit {
                if (next.isEmpty()) it.remove(PrefKeys.KEY_HIDDEN_APPS)
                else it[PrefKeys.KEY_HIDDEN_APPS] = next.joinToString(",")
            }
        }
    }

    fun clearHiddenApps() {
        setHiddenApps(emptySet())
    }

    // ===== Icon Packs =====

    private val _availableIconPacks = MutableStateFlow<List<com.flue.launcher.iconpack.IconPackDescriptor>>(emptyList())
    val availableIconPacks: StateFlow<List<com.flue.launcher.iconpack.IconPackDescriptor>> = _availableIconPacks.asStateFlow()

    fun refreshIconPacks() {
        viewModelScope.launch {
            val packs = withContext(Dispatchers.IO) {
                com.flue.launcher.iconpack.IconPackScanner.scanInstalled(getApplication())
            }
            _availableIconPacks.value = packs
        }
    }

    // ===== Reset =====

    fun resetSettings() {
        _hiddenApps.value = emptySet()
        appRepository.setHiddenComponents(emptySet())
        appRepository.setIconRenderingOptions(packageName = null, legacyCircular = true, twoTone = false, iconSize = 128)
        persist {
            store.edit {
                it.remove(PrefKeys.KEY_HIDDEN_APPS)
            }
        }
    }

    // ===== Persistence =====

    private fun persist(block: suspend () -> Unit) {
        viewModelScope.launch { block() }
    }

    override fun onCleared() {
        pendingWriteJobs.values.forEach(Job::cancel)
        pendingWriteJobs.clear()
        super.onCleared()
    }
}
