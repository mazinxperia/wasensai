package com.mazin.wasensai.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mazin.wasensai.export.ExportManager
import com.mazin.wasensai.root.RootFileAccess
import com.mazin.wasensai.root.ScanResult
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.io.File
import javax.inject.Inject

sealed class ExtractState {
    object Idle : ExtractState()
    object Loading : ExtractState()
    data class Success(val file: File) : ExtractState()
    data class Error(val message: String) : ExtractState()
}

@HiltViewModel
class ExtractViewModel @Inject constructor(
    private val rootFileAccess: RootFileAccess,
    private val exportManager: ExportManager
) : ViewModel() {

    private val exportLogBuffer = ArrayDeque<String>()

    private val _rootGranted = MutableStateFlow<Boolean?>(null)
    val rootGranted: StateFlow<Boolean?> = _rootGranted.asStateFlow()

    private val _scanResult = MutableStateFlow<ScanResult?>(null)
    val scanResult: StateFlow<ScanResult?> = _scanResult.asStateFlow()

    private val _isScanning = MutableStateFlow(false)
    val isScanning: StateFlow<Boolean> = _isScanning.asStateFlow()

    private val _exportState = MutableStateFlow<ExtractState>(ExtractState.Idle)
    val exportState: StateFlow<ExtractState> = _exportState.asStateFlow()

    private val _exportProgress = MutableStateFlow(0f)
    val exportProgress: StateFlow<Float> = _exportProgress.asStateFlow()

    private val _exportLog = MutableStateFlow<List<String>>(emptyList())
    val exportLog: StateFlow<List<String>> = _exportLog.asStateFlow()

    private val _includeImages = MutableStateFlow(true)
    val includeImages: StateFlow<Boolean> = _includeImages.asStateFlow()

    private val _includeVideos = MutableStateFlow(true)
    val includeVideos: StateFlow<Boolean> = _includeVideos.asStateFlow()

    private val _includeAudio = MutableStateFlow(true)
    val includeAudio: StateFlow<Boolean> = _includeAudio.asStateFlow()

    private val _includeDocs = MutableStateFlow(true)
    val includeDocs: StateFlow<Boolean> = _includeDocs.asStateFlow()

    private val _includeAvatars = MutableStateFlow(true)
    val includeAvatars: StateFlow<Boolean> = _includeAvatars.asStateFlow()

    private val _includeOthers = MutableStateFlow(true)
    val includeOthers: StateFlow<Boolean> = _includeOthers.asStateFlow()

    private val _includeThumbnails = MutableStateFlow(false)
    val includeThumbnails: StateFlow<Boolean> = _includeThumbnails.asStateFlow()

    fun setIncludeImages(value: Boolean)     { _includeImages.value = value }
    fun setIncludeVideos(value: Boolean)     { _includeVideos.value = value }
    fun setIncludeAudio(value: Boolean)      { _includeAudio.value = value }
    fun setIncludeDocs(value: Boolean)       { _includeDocs.value = value }
    fun setIncludeAvatars(value: Boolean)    { _includeAvatars.value = value }
    fun setIncludeOthers(value: Boolean)     { _includeOthers.value = value }
    fun setIncludeThumbnails(value: Boolean) { _includeThumbnails.value = value }

    fun requestRootAccess() {
        viewModelScope.launch {
            _rootGranted.value = null
            val hasRoot = rootFileAccess.checkRootAccess()
            if (hasRoot) {
                val hasAccess = rootFileAccess.verifyWhatsAppAccess()
                _rootGranted.value = hasAccess
            } else {
                _rootGranted.value = false
            }
        }
    }

    fun scanFiles() {
        viewModelScope.launch {
            _isScanning.value = true
            try {
                val result = rootFileAccess.scanFiles()
                _scanResult.value = result
            } catch (e: Exception) {
                _scanResult.value = null
            } finally {
                _isScanning.value = false
            }
        }
    }

    fun startExport() {
        viewModelScope.launch {
            _exportState.value = ExtractState.Loading
            _exportProgress.value = 0f
            exportLogBuffer.clear()
            _exportLog.value = emptyList()

            exportManager.runExport(
                includeAvatars     = _includeAvatars.value,
                includeImages      = _includeImages.value,
                includeVideos      = _includeVideos.value,
                includeAudio       = _includeAudio.value,
                includeDocs        = _includeDocs.value,
                includeOthers      = _includeOthers.value,
                includeThumbnails  = _includeThumbnails.value,
                onProgress = { progress, message ->
                    _exportProgress.value = progress
                    appendExportLog(message)
                }
            ).onSuccess { file ->
                _exportState.value = ExtractState.Success(file)
            }.onFailure { e ->
                _exportState.value = ExtractState.Error(e.message ?: "Export failed")
                appendExportLog("Error: ${e.message}")
            }
        }
    }

    fun resetExport() {
        _exportState.value = ExtractState.Idle
        _exportProgress.value = 0f
        exportLogBuffer.clear()
        _exportLog.value = emptyList()
    }

    private fun appendExportLog(message: String) {
        exportLogBuffer.addLast(message)
        _exportLog.value = exportLogBuffer.toList()
    }
}
