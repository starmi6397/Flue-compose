package com.flue.launcher.viewmodel

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.flue.launcher.backup.FlueBackupManager
import com.flue.launcher.backup.FlueBackupOptions
import com.flue.launcher.backup.FlueBackupPreview
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class BackupViewModel(application: Application) : AndroidViewModel(application) {

    private val repositories = com.flue.launcher.FlueApplication.repositories(application)

    private val _isExporting = MutableStateFlow(false)
    val isExporting: StateFlow<Boolean> = _isExporting.asStateFlow()

    private val _isImporting = MutableStateFlow(false)
    val isImporting: StateFlow<Boolean> = _isImporting.asStateFlow()

    private val _exportError = MutableStateFlow<String?>(null)
    val exportError: StateFlow<String?> = _exportError.asStateFlow()

    private val _importError = MutableStateFlow<String?>(null)
    val importError: StateFlow<String?> = _importError.asStateFlow()

    private val _backupPreview = MutableStateFlow<FlueBackupPreview?>(null)
    val backupPreview: StateFlow<FlueBackupPreview?> = _backupPreview.asStateFlow()

    private var onImportComplete: (() -> Unit)? = null
    private var onImportError: ((String) -> Unit)? = null

    // ===== Methods =====

    fun suggestedBackupFileName(): String = FlueBackupManager.suggestedFileName(getApplication())

    suspend fun exportBackup(uri: Uri, options: FlueBackupOptions): Result<Unit> {
        return try {
            _isExporting.value = true
            _exportError.value = null
            withContext(Dispatchers.IO) {
                FlueBackupManager.exportBackup(getApplication(), uri, options)
            }
            _isExporting.value = false
            Result.success(Unit)
        } catch (e: Exception) {
            _exportError.value = e.message
            _isExporting.value = false
            Result.failure(e)
        }
    }

    suspend fun readBackupPreview(uri: Uri): Result<FlueBackupPreview> {
        return try {
            val preview = withContext(Dispatchers.IO) {
                FlueBackupManager.readPreview(getApplication(), uri)
            }
            _backupPreview.value = preview
            Result.success(preview)
        } catch (e: Exception) {
            _importError.value = e.message
            Result.failure(e)
        }
    }

    suspend fun importBackup(
        uri: Uri,
        options: FlueBackupOptions,
        availableAppKeys: Set<String>? = null,
        onComplete: (() -> Unit)? = null,
        onError: ((String) -> Unit)? = null
    ): Result<Unit> {
        return try {
            _isImporting.value = true
            _importError.value = null
            this.onImportComplete = onComplete
            this.onImportError = onError
            val resolvedAppKeys = availableAppKeys ?: emptySet()
            withContext(Dispatchers.IO) {
                FlueBackupManager.importBackup(
                    context = getApplication(),
                    sourceUri = uri,
                    options = options,
                    availableAppKeys = resolvedAppKeys,
                    widgetRepository = repositories.widgetRepository
                )
            }
            _isImporting.value = false
            onComplete?.invoke()
            Result.success(Unit)
        } catch (e: Exception) {
            _importError.value = e.message
            _isImporting.value = false
            onError?.invoke(e.message ?: "导入失败")
            Result.failure(e)
        }
    }

    fun clearExportError() {
        _exportError.value = null
    }

    fun clearImportError() {
        _importError.value = null
    }

    fun clearBackupPreview() {
        _backupPreview.value = null
    }

    override fun onCleared() {
        super.onCleared()
    }
}
