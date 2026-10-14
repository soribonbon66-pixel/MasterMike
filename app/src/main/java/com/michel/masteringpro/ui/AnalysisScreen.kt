package com.michel.masteringpro.ui

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.michel.masteringpro.audio.AudioAnalyzer
import com.michel.masteringpro.audio.SuggestionEngine
import com.michel.masteringpro.viewmodel.MasteringViewModel
import kotlin.math.abs
import kotlin.math.max

// ============== LOAD SCREEN ==============

@Composable
fun LoadScreen(viewModel: MasteringViewModel) {
    val launcher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        uri?.let { viewModel.loadAudio(it) }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            // Logo / Title
            Text(
                text = "MICHEL",
                style = MaterialTheme.typography.headlineLarge.copy(
                    fontSize = 48.sp,
                    fontWeight = FontWeight.Black
                ),
                color = MaterialTheme.colorScheme.primary
            )
            Text(
                text = "MASTERING PRO",
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.secondary
            )

            Spacer(modifier = Modifier.height(48.dp))

            Text(
                text = "Mastering automatizado con IA",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.7f)
            )

            Spacer(modifier = Modifier.height(32.dp))

            // Load button
            Button(
                onClick = {
                    launcher.launch(arrayOf("audio/*"))
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(64.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.primary
                ),
                shape = RoundedCornerShape(16.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.MusicNote,
                    contentDescription = null,
                    modifier = Modifier.size(24.dp)
                )
                Spacer(modifier = Modifier.width(12.dp))
                Text(
                    text = "CARGAR AUDIO",
                    style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold)
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            Text(
                text = "MP3, WAV, AAC, OGG - Procesamiento 100% offline",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.5f)
            )
        }
    }
}

// ============== ANALYSIS SCREEN ==============

@Composable
fun AnalysisScreen(
    analysis: AudioAnalyzer.AnalysisResult,
    samples: FloatArray,
    sampleRate: Int,
    duration: Double,
    fileName: String,
    settings: SuggestionEngine.MasteringSettings,
    onSettingsChange: (SuggestionEngine.MasteringSettings) -> Unit,
    onResetSettings: () -> Unit,
    onApply: () -> Unit
) {
    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // Title
        item {
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "ANÁLISIS DEL TRACK CARGADO",
                style = MaterialTheme.typography.headlineMedium,
                color = MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = "$fileName • ${String.format("%.1f", duration)}s • ${sampleRate}Hz",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.7f)
            )
            Spacer(modifier = Modifier.height(4.dp))
        }

        // Waveform
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
            ) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Text("WAVEFORM", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.secondary)
                    Spacer(modifier = Modifier.height(8.dp))
                    WaveformCanvas(samples = samples, color = MaterialTheme.colorScheme.primary)
                }
            }
        }

        // Loudness Cards
        item {
            Text(
                text = "📊 LOUDNESS (EBU R128)",
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.secondary,
                modifier = Modifier.padding(top = 8.dp)
            )
        }

        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                MetricCard(
                    title = "Integrated",
                    value = "${String.format("%.1f", analysis.integratedLufs)} LUFS",
                    modifier = Modifier.weight(1f),
                    statusColor = when {
                        analysis.integratedLufs > -9.0 -> MaterialTheme.colorScheme.error
                        analysis.integratedLufs > -14.0 -> MaterialTheme.colorScheme.primary
                        else -> MaterialTheme.colorScheme.secondary
                    }
                )
                MetricCard(
                    title = "True Peak",
                    value = "${String.format("%.1f", analysis.truePeakDbtp)} dBTP",
                    modifier = Modifier.weight(1f),
                    statusColor = when {
                        analysis.truePeakDbtp > 0.0 -> MaterialTheme.colorScheme.error
                        analysis.truePeakDbtp > -1.0 -> Color(0xFFFFAA00)
                        else -> MaterialTheme.colorScheme.secondary
                    }
                )
                MetricCard(
                    title = "LRA",
                    value = "${String.format("%.1f", analysis.lra)} LU",
                    modifier = Modifier.weight(1f)
                )
            }
        }

        // Dynamics
        item {
            Text(
                text = "🎚️ DINÁMICA",
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.secondary
            )
        }

        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                MetricCard(
                    title = "RMS",
                    value = "${String.format("%.1f", analysis.rmsDb)} dB",
                    modifier = Modifier.weight(1f)
                )
                MetricCard(
                    title = "Crest Factor",
                    value = "${String.format("%.1f", analysis.crestFactorDb)} dB",
                    modifier = Modifier.weight(1f)
                )
                MetricCard(
                    title = "Clipping",
                    value = "${String.format("%.2f", analysis.clippingPercent)}%",
                    modifier = Modifier.weight(1f),
                    statusColor = if (analysis.clippingPercent > 0) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.secondary
                )
            }
        }

        // Spectrum
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
            ) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Text(
                        text = "📈 ESPECTRO",
                        style = MaterialTheme.typography.titleLarge,
                        color = MaterialTheme.colorScheme.secondary
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    SpectrumCanvas(
                        spectrumDb = analysis.spectrumDb,
                        frequencies = analysis.spectrumFreqs,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("Graves: ${String.format("%.0f", analysis.energyLow)}dB", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurface)
                        Text("Medios: ${String.format("%.0f", analysis.energyMid)}dB", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurface)
                        Text("Agudos: ${String.format("%.0f", analysis.energyHigh)}dB", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurface)
                        Text("Aire: ${String.format("%.0f", analysis.energyAir)}dB", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurface)
                    }
                }
            }
        }

        // Stereo & Phase
        item {
            Text(
                text = "🔊 ESTÉREO Y FASE",
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.secondary
            )
        }

        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                MetricCard(
                    title = "Correlación",
                    value = String.format("%.2f", analysis.correlation),
                    modifier = Modifier.weight(1f),
                    statusColor = if (analysis.correlation < 0.3) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.secondary
                )
                MetricCard(
                    title = "Ancho Estéreo",
                    value = String.format("%.2f", analysis.stereoWidth),
                    modifier = Modifier.weight(1f)
                )
                MetricCard(
                    title = "Compat. Mono",
                    value = String.format("%.0f%%", analysis.monoCompatibility * 100),
                    modifier = Modifier.weight(1f)
                )
            }
        }

        // Tonal
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                MetricCard(
                    title = "BPM",
                    value = "~${analysis.bpmEstimate}",
                    modifier = Modifier.weight(1f)
                )
                MetricCard(
                    title = "Tonalidad",
                    value = analysis.keyEstimate,
                    modifier = Modifier.weight(1f)
                )
            }
        }

        // Problems detected
        if (analysis.problems.isNotEmpty()) {
            item {
                Text(
                    text = "⚠️ PROBLEMAS DETECTADOS",
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.error
                )
            }

            items(analysis.problems) { problem ->
                val severityColor = when (problem.severity) {
                    AudioAnalyzer.Problem.Severity.CRITICAL -> MaterialTheme.colorScheme.error
                    AudioAnalyzer.Problem.Severity.WARNING -> Color(0xFFFFAA00)
                    AudioAnalyzer.Problem.Severity.INFO -> MaterialTheme.colorScheme.secondary
                }
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surface
                    ),
                    border = androidx.compose.foundation.BorderStroke(1.dp, severityColor)
                ) {
                    Text(
                        text = problem.message,
                        style = MaterialTheme.typography.bodyMedium,
                        color = severityColor,
                        modifier = Modifier.padding(12.dp)
                    )
                }
            }
        }

        // SUGGESTIONS SECTION
        item {
            Spacer(modifier = Modifier.height(16.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "🎛️ AJUSTES SUGERIDOS POR MICHEL",
                    style = MaterialTheme.typography.headlineMedium,
                    color = MaterialTheme.colorScheme.primary
                )
                Icon(
                    imageVector = Icons.Default.Refresh,
                    contentDescription = "Reset",
                    tint = MaterialTheme.colorScheme.secondary,
                    modifier = Modifier.clickable { onResetSettings() }
                )
            }
        }

        // Gain slider
        item {
            SettingsSlider(
                label = "Gain",
                value = settings.gainDb,
                range = -12f..12f,
                unit = "dB",
                onValueChange = { settings.gainDb = it; onSettingsChange(settings) }
            )
        }

        // EQ Low
        item {
            SettingsSlider(
                label = "EQ Low (${settings.eqLowFreq.toInt()}Hz Shelf)",
                value = settings.eqLowGainDb,
                range = -6f..6f,
                unit = "dB",
                onValueChange = { settings.eqLowGainDb = it; onSettingsChange(settings) }
            )
        }

        // EQ Mid
        item {
            SettingsSlider(
                label = "EQ Mid (${settings.eqMidFreq.toInt()}Hz Q=${String.format("%.1f", settings.eqMidQ)})",
                value = settings.eqMidGainDb,
                range = -6f..6f,
                unit = "dB",
                onValueChange = { settings.eqMidGainDb = it; onSettingsChange(settings) }
            )
        }

        // EQ High
        item {
            SettingsSlider(
                label = "EQ High (${settings.eqHighFreq.toInt()}Hz Shelf)",
                value = settings.eqHighGainDb,
                range = -6f..6f,
                unit = "dB",
                onValueChange = { settings.eqHighGainDb = it; onSettingsChange(settings) }
            )
        }

        // Compressor
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
            ) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "COMPRESOR",
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.secondary
                        )
                        Switch(
                            checked = settings.compEnabled,
                            onCheckedChange = { settings.compEnabled = it; onSettingsChange(settings) }
                        )
                    }
                    if (settings.compEnabled) {
                        SettingsSlider(
                            label = "Threshold",
                            value = settings.compThresholdDb,
                            range = -40f..0f,
                            unit = "dB",
                            onValueChange = { settings.compThresholdDb = it; onSettingsChange(settings) }
                        )
                        SettingsSlider(
                            label = "Ratio",
                            value = settings.compRatio,
                            range = 1f..10f,
                            unit = ":1",
                            onValueChange = { settings.compRatio = it; onSettingsChange(settings) }
                        )
                    }
                }
            }
        }

        // Limiter
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
            ) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "LIMITER",
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.secondary
                        )
                        Switch(
                            checked = settings.limiterEnabled,
                            onCheckedChange = { settings.limiterEnabled = it; onSettingsChange(settings) }
                        )
                    }
                    if (settings.limiterEnabled) {
                        SettingsSlider(
                            label = "Ceiling",
                            value = settings.limiterCeilingDb,
                            range = -3f..0f,
                            unit = "dB",
                            onValueChange = { settings.limiterCeilingDb = it; onSettingsChange(settings) }
                        )
                    }
                }
            }
        }

        // Target LUFS
        item {
            SettingsSlider(
                label = "Target LUFS",
                value = settings.targetLufs,
                range = -18f..-8f,
                unit = "LUFS",
                onValueChange = { settings.targetLufs = it; onSettingsChange(settings) }
            )
        }

        // APPLY BUTTON
        item {
            Spacer(modifier = Modifier.height(16.dp))
            Button(
                onClick = onApply,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(72.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.primary
                ),
                shape = RoundedCornerShape(16.dp)
            ) {
                Text(
                    text = "APLICAR MASTERING",
                    style = MaterialTheme.typography.titleLarge.copy(
                        fontWeight = FontWeight.Bold,
                        fontSize = 20.sp
                    )
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "(EQ + COMP + LIMITER)",
                    style = MaterialTheme.typography.bodyMedium
                )
            }
            Spacer(modifier = Modifier.height(24.dp))
        }
    }
}

// ============== HELPER COMPONENTS ==============

@Composable
fun MetricCard(
    title: String,
    value: String,
    modifier: Modifier = Modifier,
    statusColor: Color = MaterialTheme.colorScheme.secondary
) {
    Card(
        modifier = modifier,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        shape = RoundedCornerShape(12.dp)
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
            )
            Text(
                text = value,
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                color = statusColor
            )
        }
    }
}

@Composable
fun SettingsSlider(
    label: String,
    value: Float,
    range: ClosedFloatingPointRange<Float>,
    unit: String,
    onValueChange: (Float) -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        shape = RoundedCornerShape(12.dp)
    ) {
        Column(modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = label,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = "${String.format("%.1f", value)} $unit",
                    style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold),
                    color = MaterialTheme.colorScheme.primary
                )
            }
            Slider(
                value = value,
                onValueChange = onValueChange,
                valueRange = range,
                modifier = Modifier.fillMaxWidth(),
                colors = SliderDefaults.colors(
                    thumbColor = MaterialTheme.colorScheme.primary,
                    activeTrackColor = MaterialTheme.colorScheme.primary,
                    inactiveTrackColor = MaterialTheme.colorScheme.surfaceVariant
                )
            )
        }
    }
}

@Composable
fun WaveformCanvas(samples: FloatArray, color: Color, modifier: Modifier = Modifier) {
    Canvas(
        modifier = modifier
            .fillMaxWidth()
            .height(80.dp)
            .background(Color(0xFF0A0A1A), RoundedCornerShape(8.dp))
    ) {
        val w = size.width
        val h = size.height
        val centerY = h / 2f
        val step = max(1, samples.size / w.toInt())

        val path = Path()
        path.moveTo(0f, centerY)

        var x = 0f
        var i = 0
        while (i < samples.size && x < w) {
            val sample = samples[i]
            val y = centerY - sample * centerY * 0.9f
            path.lineTo(x, y)
            x += w / samples.size
            i += step
        }

        drawPath(
            path = path,
            color = color.copy(alpha = 0.8f),
            style = Stroke(width = 1.dp.toPx())
        )
    }
}

@Composable
fun SpectrumCanvas(
    spectrumDb: DoubleArray,
    frequencies: DoubleArray,
    color: Color,
    modifier: Modifier = Modifier
) {
    Canvas(
        modifier = modifier
            .fillMaxWidth()
            .height(120.dp)
            .background(Color(0xFF0A0A1A), RoundedCornerShape(8.dp))
    ) {
        val w = size.width
        val h = size.height
        val padding = 4.dp.toPx()
        val drawH = h - padding * 2

        if (spectrumDb.isEmpty()) return@Canvas

        // Map frequency to x using log scale (20Hz - 20kHz)
        val minFreq = 20.0
        val maxFreq = 20000.0
        val logMin = kotlin.math.ln(minFreq)
        val logMax = kotlin.math.ln(maxFreq)

        val numBars = 200
        val barWidth = w / numBars

        for (bar in 0 until numBars) {
            val xStart = bar * barWidth
            val logFreqStart = logMin + (bar.toDouble() / numBars) * (logMax - logMin)
            val logFreqEnd = logMin + ((bar + 1).toDouble() / numBars) * (logMax - logMin)
            val freqStart = kotlin.math.exp(logFreqStart)
            val freqEnd = kotlin.math.exp(logFreqEnd)

            // Find average dB in this frequency range
            var sumDb = 0.0
            var count = 0
            for (i in frequencies.indices) {
                if (frequencies[i] in freqStart..freqEnd) {
                    sumDb += spectrumDb[i]
                    count++
                }
            }
            val avgDb = if (count > 0) sumDb / count else -120.0

            // Map dB to height (-100 to 0 dB range)
            val normalizedHeight = ((avgDb + 100.0) / 100.0).coerceIn(0.0, 1.0)
            val barHeight = (normalizedHeight * drawH).toFloat()

            val gradient = Brush.verticalGradient(
                colors = listOf(
                    color,
                    color.copy(alpha = 0.3f)
                )
            )

            drawRect(
                brush = gradient,
                topLeft = Offset(xStart, h - padding - barHeight),
                size = androidx.compose.ui.geometry.Size(barWidth.coerceAtLeast(1f), barHeight)
            )
        }
    }
}
