package com.michel.masteringpro.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.michel.masteringpro.viewmodel.MasteringViewModel
import com.michel.masteringpro.viewmodel.UiState

/**
 * Main app composable. Routes to different screens based on UiState.
 * Flow: Idle -> Load -> Analyze -> Suggest -> Process -> Result
 */
@Composable
fun MichelApp(viewModel: MasteringViewModel = viewModel()) {
    val uiState by viewModel.uiState.collectAsState()
    val settings by viewModel.settings.collectAsState()
    val processingProgress by viewModel.processingProgress.collectAsState()
    val processingLog by viewModel.processingLog.collectAsState()

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            when (val state = uiState) {
                is UiState.Idle -> {
                    LoadScreen(viewModel = viewModel)
                }

                is UiState.Loading -> {
                    LoadingOverlay(message = state.message)
                }

                is UiState.Analyzing -> {
                    LoadingOverlay(message = state.message)
                }

                is UiState.Loaded -> {
                    LoadingOverlay(message = "Iniciando análisis...")
                }

                is UiState.Analyzed -> {
                    AnalysisScreen(
                        analysis = state.analysis,
                        samples = state.samples,
                        sampleRate = state.sampleRate,
                        duration = state.duration,
                        fileName = state.fileName,
                        settings = settings,
                        onSettingsChange = { update -> viewModel.updateSettings(update) },
                        onResetSettings = { viewModel.resetSettings() },
                        onApply = { viewModel.applyMastering() }
                    )
                }

                is UiState.Processing -> {
                    LoadingOverlay(message = state.message, progress = processingProgress)
                }

                is UiState.Processed -> {
                    ResultScreen(
                        originalPcm = state.originalPcm,
                        processedPcm = state.processedPcm,
                        sampleRate = state.sampleRate,
                        originalAnalysis = state.originalAnalysis,
                        resultAnalysis = state.resultAnalysis,
                        processingLog = processingLog,
                        duration = state.duration,
                        onExportWav = { viewModel.exportWav() },
                        onShare = { viewModel.getProcessedFileForShare() },
                        onEditSettings = { viewModel.editSettings() },
                        onNewTrack = { viewModel.reset() }
                    )
                }

                is UiState.Error -> {
                    ErrorScreen(
                        message = state.message,
                        onRetry = { viewModel.reset() }
                    )
                }
            }
        }
    }
}

@Composable
fun LoadingOverlay(message: String, progress: Float = 0f) {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            CircularProgressIndicator(
                color = MaterialTheme.colorScheme.primary
            )
            Text(
                text = message,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onBackground,
                modifier = Modifier.padding(top = 16.dp)
            )
            if (progress > 0f) {
                LinearProgressIndicator(
                    progress = { progress },
                    modifier = Modifier
                        .padding(top = 8.dp)
                        .fillMaxWidth()
                        .padding(horizontal = 32.dp),
                    color = MaterialTheme.colorScheme.primary,
                )
            }
        }
    }
}

@Composable
fun ErrorScreen(message: String, onRetry: () -> Unit) {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.padding(24.dp)
        ) {
            Text(
                text = "⚠️ Error",
                style = MaterialTheme.typography.headlineMedium,
                color = MaterialTheme.colorScheme.error
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = message,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onBackground
            )
            Spacer(modifier = Modifier.height(16.dp))
            Button(onClick = onRetry) {
                Text("Volver al inicio")
            }
        }
    }
}
