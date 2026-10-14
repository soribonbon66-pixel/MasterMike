package com.michel.masteringpro.viewmodel

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.michel.masteringpro.audio.AudioAnalyzer
import com.michel.masteringpro.audio.AudioLoader
import com.michel.masteringpro.audio.AudioProcessor
import com.michel.masteringpro.audio.SuggestionEngine
import com.michel.masteringpro.audio.WavWriter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Main ViewModel for Michel Mastering PRO.
 * Manages the state flow: Load -> Analyze -> Suggest -> Process -> Compare.
 */
class MasteringViewModel(application: Application) : AndroidViewModel(application) {

    private val _uiState = MutableStateFlow<UiState>(UiState.Idle)
    val uiState: StateFlow<UiState> = _uiState.asStateFlow()

    private val _settings = MutableStateFlow(SuggestionEngine.MasteringSettings())
    val settings: StateFlow<SuggestionEngine.MasteringSettings> = _settings.asStateFlow()

    private val _processingProgress = MutableStateFlow(0f)
    val processingProgress: StateFlow<Float> = _processingProgress.asStateFlow()

    private val _processingLog = MutableStateFlow<List<String>>(emptyList())
    val processingLog: StateFlow<List<String>> = _processingLog.asStateFlow()

    private var originalPcm: FloatArray? = null
    private var originalSampleRate: Int = 44100
    private var originalDuration: Double = 0.0
    private var originalFileName: String = ""

    /**
     * Load audio from URI.
     */
    fun loadAudio(uri: Uri) {
        viewModelScope.launch {
            _uiState.value = UiState.Loading("Cargando audio...")
            try {
                val context = getApplication<Application>()
                val decoded = withContext(Dispatchers.IO) {
                    AudioLoader.loadFromUri(context, uri)
                }

                originalPcm = decoded.samples
                originalSampleRate = decoded.sampleRate
                originalDuration = decoded.duration

                // Extract filename
                originalFileName = uri.lastPathSegment?.substringAfterLast('/') ?: "audio"

                _uiState.value = UiState.Loaded(
                    samples = decoded.samples,
                    sampleRate = decoded.sampleRate,
                    duration = decoded.duration,
                    channelCount = decoded.channelCount,
                    fileName = originalFileName
                )

                // Automatically start analysis
                analyzeAudio(decoded.samples, decoded.sampleRate)
            } catch (e: Exception) {
                _uiState.value = UiState.Error("Error cargando audio: ${e.message}")
            }
        }
    }

    /**
     * Analyze loaded audio (EBU R128, dynamics, spectral, etc).
     */
    fun analyzeAudio(samples: FloatArray, sampleRate: Int) {
        viewModelScope.launch {
            _uiState.value = UiState.Analyzing("Analizando track...")
            try {
                val analysis = withContext(Dispatchers.IO) {
                    AudioAnalyzer.analyze(samples, sampleRate)
                }

                // Generate suggestions
                val suggestions = SuggestionEngine.suggest(analysis)
                _settings.value = suggestions

                _uiState.value = UiState.Analyzed(
                    analysis = analysis,
                    samples = samples,
                    sampleRate = sampleRate,
                    duration = originalDuration,
                    fileName = originalFileName
                )
            } catch (e: Exception) {
                _uiState.value = UiState.Error("Error en análisis: ${e.message}")
            }
        }
    }

    /**
     * Update a specific setting.
     */
    fun updateSettings(update: (SuggestionEngine.MasteringSettings) -> Unit) {
        val current = _settings.value
        update(current)
        _settings.value = current
    }

    /**
     * Reset settings to AI suggestions based on original analysis.
     */
    fun resetSettings() {
        viewModelScope.launch {
            val pcm = originalPcm ?: return@launch
            val analysis = withContext(Dispatchers.IO) {
                AudioAnalyzer.analyze(pcm, originalSampleRate)
            }
            _settings.value = SuggestionEngine.suggest(analysis)
        }
    }

    /**
     * Apply mastering processing chain.
     */
    fun applyMastering() {
        viewModelScope.launch {
            val pcm = originalPcm ?: return@launch
            _uiState.value = UiState.Processing("Aplicando mastering...")
            _processingProgress.value = 0.1f

            try {
                val result = withContext(Dispatchers.IO) {
                    _processingProgress.value = 0.3f
                    val processed = AudioProcessor.process(pcm, originalSampleRate, _settings.value)
                    _processingProgress.value = 0.7f

                    // Analyze result for comparison
                    val resultAnalysis = AudioAnalyzer.analyze(processed.processedSamples, originalSampleRate)
                    _processingProgress.value = 0.9f

                    Triple(processed.processedSamples, resultAnalysis, processed.processingLog)
                }

                val (processedPcm, resultAnalysis, log) = result
                _processingLog.value = log
                _processingProgress.value = 1.0f

                _uiState.value = UiState.Processed(
                    originalPcm = pcm,
                    processedPcm = processedPcm,
                    sampleRate = originalSampleRate,
                    originalAnalysis = (_uiState.value as? UiState.Analyzed)?.analysis!!,
                    resultAnalysis = resultAnalysis,
                    appliedSettings = _settings.value,
                    duration = originalDuration,
                    fileName = originalFileName
                )
            } catch (e: Exception) {
                _uiState.value = UiState.Error("Error procesando: ${e.message}")
            }
        }
    }

    /**
     * Export processed audio as WAV.
     */
    fun exportWav(): String? {
        val state = _uiState.value
        if (state is UiState.Processed) {
            val context = getApplication<Application>()
            return try {
                WavWriter.write(context, state.processedPcm, state.sampleRate)
            } catch (e: Exception) {
                null
            }
        }
        return null
    }

    /**
     * Get processed samples as temporary file for sharing.
     */
    fun getProcessedFileForShare(): java.io.File? {
        val state = _uiState.value
        if (state is UiState.Processed) {
            val context = getApplication<Application>()
            return try {
                WavWriter.exportToShare(context, state.processedPcm, state.sampleRate)
            } catch (e: Exception) {
                null
            }
        }
        return null
    }

    /**
     * Go back to edit settings from processed state.
     */
    fun editSettings() {
        val state = _uiState.value
        if (state is UiState.Processed) {
            _uiState.value = UiState.Analyzed(
                analysis = state.originalAnalysis,
                samples = state.originalPcm,
                sampleRate = state.sampleRate,
                duration = state.duration,
                fileName = state.fileName
            )
        }
    }

    /**
     * Reset everything.
     */
    fun reset() {
        _uiState.value = UiState.Idle
        originalPcm = null
        _processingProgress.value = 0f
        _processingLog.value = emptyList()
    }
}

/**
 * UI States representing the mastering workflow.
 */
sealed class UiState {
    data object Idle : UiState()

    data class Loading(val message: String) : UiState()

    data class Analyzing(val message: String) : UiState()

    data class Loaded(
        val samples: FloatArray,
        val sampleRate: Int,
        val duration: Double,
        val channelCount: Int,
        val fileName: String
    ) : UiState()

    data class Analyzed(
        val analysis: AudioAnalyzer.AnalysisResult,
        val samples: FloatArray,
        val sampleRate: Int,
        val duration: Double,
        val fileName: String
    ) : UiState()

    data class Processing(val message: String) : UiState()

    data class Processed(
        val originalPcm: FloatArray,
        val processedPcm: FloatArray,
        val sampleRate: Int,
        val originalAnalysis: AudioAnalyzer.AnalysisResult,
        val resultAnalysis: AudioAnalyzer.AnalysisResult,
        val appliedSettings: SuggestionEngine.MasteringSettings,
        val duration: Double,
        val fileName: String
    ) : UiState()

    data class Error(val message: String) : UiState()
}
