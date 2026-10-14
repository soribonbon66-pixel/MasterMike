package com.michel.masteringpro.ui

import android.content.Intent
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.michel.masteringpro.audio.AudioAnalyzer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import kotlin.math.max

@Composable
fun ResultScreen(
    originalPcm: FloatArray,
    processedPcm: FloatArray,
    sampleRate: Int,
    originalAnalysis: AudioAnalyzer.AnalysisResult,
    resultAnalysis: AudioAnalyzer.AnalysisResult,
    processingLog: List<String>,
    duration: Double,
    onExportWav: () -> String?,
    onShare: () -> File?,
    onEditSettings: () -> Unit,
    onNewTrack: () -> Unit
) {
    var isPlayingOriginal by remember { mutableStateOf(false) }
    var isPlayingProcessed by remember { mutableStateOf(false) }
    var audioTrack by remember { mutableStateOf<AudioTrack?>(null) }
    var playThread by remember { mutableStateOf<Thread?>(null) }
    val context = LocalContext.current

    DisposableEffect(Unit) {
        onDispose {
            audioTrack?.stop()
            audioTrack?.release()
            playThread?.interrupt()
        }
    }

    fun stopPlayback() {
        audioTrack?.pause()
        audioTrack?.flush()
        playThread?.interrupt()
        isPlayingOriginal = false
        isPlayingProcessed = false
    }

    fun playAudio(pcm: FloatArray) {
        stopPlayback()

        val channelConfig = AudioFormat.CHANNEL_OUT_STEREO
        val encoding = AudioFormat.ENCODING_PCM_FLOAT
        val bufferSize = AudioTrack.getMinBufferSize(sampleRate, channelConfig, encoding)
            .coerceAtLeast(4096)

        val attributes = AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_MEDIA)
            .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
            .build()

        val track = AudioTrack.Builder()
            .setAudioAttributes(attributes)
            .setAudioFormat(AudioFormat.Builder()
                .setEncoding(encoding)
                .setSampleRate(sampleRate)
                .setChannelMask(channelConfig)
                .build())
            .setBufferSizeInBytes(bufferSize * 4)
            .setTransferMode(AudioTrack.MODE_STREAM)
            .build()

        audioTrack = track
        track.play()

        val thread = Thread {
            val chunkSize = 1024
            var offset = 0
            try {
                while (offset < pcm.size && !Thread.currentThread().isInterrupted) {
                    val end = minOf(offset + chunkSize, pcm.size)
                    val chunk = pcm.copyOfRange(offset, end)
                    track.write(chunk, 0, chunk.size, AudioTrack.WRITE_BLOCKING)
                    offset = end
                }
            } catch (_: InterruptedException) {
            }
            track.stop()
        }
        playThread = thread
        thread.start()
    }

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
                text = "RESULTADO - ANÁLISIS COMPARATIVO",
                style = MaterialTheme.typography.headlineMedium,
                color = MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.Bold
            )
        }

        // Before/After table
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                shape = RoundedCornerShape(12.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = "COMPARATIVA BEFORE / AFTER",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.secondary,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(12.dp))

                    ComparisonRow("Métrica", "ANTES", "DESPUÉS", isHeader = true)
                    ComparisonRow(
                        "Integrated LUFS",
                        "${String.format("%.1f", originalAnalysis.integratedLufs)}",
                        "${String.format("%.1f", resultAnalysis.integratedLufs)} LUFS"
                    )
                    ComparisonRow(
                        "True Peak",
                        "${String.format("%.1f", originalAnalysis.truePeakDbtp)}",
                        "${String.format("%.1f", resultAnalysis.truePeakDbtp)} dBTP"
                    )
                    ComparisonRow(
                        "LRA",
                        "${String.format("%.1f", originalAnalysis.lra)}",
                        "${String.format("%.1f", resultAnalysis.lra)} LU"
                    )
                    ComparisonRow(
                        "RMS",
                        "${String.format("%.1f", originalAnalysis.rmsDb)} dB",
                        "${String.format("%.1f", resultAnalysis.rmsDb)} dB"
                    )
                    ComparisonRow(
                        "Clipping",
                        "${String.format("%.2f", originalAnalysis.clippingPercent)}%",
                        "${String.format("%.2f", resultAnalysis.clippingPercent)}%",
                        success = resultAnalysis.clippingPercent == 0.0
                    )
                }
            }
        }

        // Validation
        item {
            val lufsOk = resultAnalysis.integratedLufs in -14.5..-13.5
            val tpOk = resultAnalysis.truePeakDbtp <= -1.0
            val clipOk = resultAnalysis.clippingPercent == 0.0

            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = if (lufsOk && tpOk && clipOk)
                        MaterialTheme.colorScheme.surface
                    else
                        MaterialTheme.colorScheme.surface
                ),
                shape = RoundedCornerShape(12.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = "VALIDACIÓN EBU R128",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.secondary,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(8.dp))

                    ValidationItem(
                        label = "Objetivo -14 LUFS ±0.5",
                        value = "${String.format("%.1f", resultAnalysis.integratedLufs)} LUFS",
                        passed = lufsOk
                    )
                    ValidationItem(
                        label = "True Peak < -1.0 dBTP",
                        value = "${String.format("%.1f", resultAnalysis.truePeakDbtp)} dBTP",
                        passed = tpOk
                    )
                    ValidationItem(
                        label = "Sin clipping",
                        value = "${String.format("%.2f", resultAnalysis.clippingPercent)}%",
                        passed = clipOk
                    )
                }
            }
        }

        // Waveforms comparison
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                shape = RoundedCornerShape(12.dp)
            ) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Text("WAVEFORM COMPARATIVO", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.secondary)
                    Spacer(modifier = Modifier.height(4.dp))
                    Text("Original (gris):", style = MaterialTheme.typography.labelSmall, color = Color.Gray)
                    Canvas(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(40.dp)
                    ) {
                        val w = size.width
                        val h = size.height
                        val centerY = h / 2f
                        val path = Path()
                        path.moveTo(0f, centerY)
                        val step = max(1, originalPcm.size / w.toInt())
                        var x = 0f
                        var i = 0
                        while (i < originalPcm.size && x < w) {
                            path.lineTo(x, centerY - originalPcm[i] * centerY * 0.8f)
                            x += w / originalPcm.size
                            i += step
                        }
                        drawPath(path, Color.Gray.copy(alpha = 0.6f), style = Stroke(width = 1.dp.toPx()))
                    }
                    Spacer(modifier = Modifier.height(4.dp))
                    Text("Masterizado (azul):", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
                    Canvas(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(40.dp)
                    ) {
                        val w = size.width
                        val h = size.height
                        val centerY = h / 2f
                        val path = Path()
                        path.moveTo(0f, centerY)
                        val step = max(1, processedPcm.size / w.toInt())
                        var x = 0f
                        var i = 0
                        while (i < processedPcm.size && x < w) {
                            path.lineTo(x, centerY - processedPcm[i] * centerY * 0.8f)
                            x += w / processedPcm.size
                            i += step
                        }
                        drawPath(path, MaterialTheme.colorScheme.primary, style = Stroke(width = 1.dp.toPx()))
                    }
                }
            }
        }

        // A/B Playback
        item {
            Text(
                text = "🎧 ESCUCHAR A/B",
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.secondary
            )
        }

        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // Play Original
                Button(
                    onClick = {
                        if (isPlayingOriginal) {
                            stopPlayback()
                        } else {
                            isPlayingProcessed = false
                            isPlayingOriginal = true
                            playAudio(originalPcm)
                        }
                    },
                    modifier = Modifier
                        .weight(1f)
                        .height(56.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (isPlayingOriginal) MaterialTheme.colorScheme.error else Color.Gray
                    ),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Icon(
                        imageVector = if (isPlayingOriginal) Icons.Default.Stop else Icons.Default.PlayArrow,
                        contentDescription = null
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(if (isPlayingOriginal) "STOP" else "ORIGINAL")
                }

                // Play Processed
                Button(
                    onClick = {
                        if (isPlayingProcessed) {
                            stopPlayback()
                        } else {
                            isPlayingOriginal = false
                            isPlayingProcessed = true
                            playAudio(processedPcm)
                        }
                    },
                    modifier = Modifier
                        .weight(1f)
                        .height(56.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (isPlayingProcessed) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary
                    ),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Icon(
                        imageVector = if (isPlayingProcessed) Icons.Default.Stop else Icons.Default.PlayArrow,
                        contentDescription = null
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(if (isPlayingProcessed) "STOP" else "MASTER")
                }
            }
        }

        // Processing log
        if (processingLog.isNotEmpty()) {
            item {
                Text(
                    text = "📋 LOG DE PROCESAMIENTO",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.secondary
                )
            }

            items(processingLog) { logEntry ->
                Text(
                    text = "• $logEntry",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.8f),
                    modifier = Modifier.padding(start = 8.dp)
                )
            }
        }

        // Action buttons
        item {
            Spacer(modifier = Modifier.height(8.dp))

            // Export WAV
            Button(
                onClick = {
                    stopPlayback()
                    val path = onExportWav()
                    // Could show a toast/snackbar here
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp),
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                shape = RoundedCornerShape(12.dp)
            ) {
                Icon(Icons.Default.Download, contentDescription = null)
                Spacer(modifier = Modifier.width(8.dp))
                Text("EXPORTAR WAV", style = MaterialTheme.typography.titleMedium)
            }
        }

        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // Share
                OutlinedButton(
                    onClick = {
                        stopPlayback()
                        val file = onShare()
                        if (file != null) {
                            val shareIntent = Intent(Intent.ACTION_SEND).apply {
                                type = "audio/wav"
                                val uri = androidx.core.content.FileProvider.getUriForFile(
                                    context,
                                    "${context.packageName}.fileprovider",
                                    file
                                )
                                putExtra(Intent.EXTRA_STREAM, uri)
                                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                            }
                            context.startActivity(Intent.createChooser(shareIntent, "Compartir WAV"))
                        }
                    },
                    modifier = Modifier
                        .weight(1f)
                        .height(48.dp),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Icon(Icons.Default.Share, contentDescription = null, modifier = Modifier.height(20.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("COMPARTIR", style = MaterialTheme.typography.labelLarge)
                }

                // Edit settings
                OutlinedButton(
                    onClick = {
                        stopPlayback()
                        onEditSettings()
                    },
                    modifier = Modifier
                        .weight(1f)
                        .height(48.dp),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Icon(Icons.Default.Edit, contentDescription = null, modifier = Modifier.height(20.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("EDITAR", style = MaterialTheme.typography.labelLarge)
                }
            }
        }

        item {
            // New track
            OutlinedButton(
                onClick = {
                    stopPlayback()
                    onNewTrack()
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp),
                shape = RoundedCornerShape(12.dp)
            ) {
                Text("CARGAR NUEVO TRACK", style = MaterialTheme.typography.labelLarge)
            }
            Spacer(modifier = Modifier.height(24.dp))
        }
    }
}

@Composable
fun ComparisonRow(metric: String, before: String, after: String, isHeader: Boolean = false, success: Boolean? = null) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            text = metric,
            style = if (isHeader) MaterialTheme.typography.labelMedium else MaterialTheme.typography.bodyMedium,
            color = if (isHeader) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.onSurface,
            fontWeight = if (isHeader) FontWeight.Bold else FontWeight.Normal,
            modifier = Modifier.weight(1.5f)
        )
        Text(
            text = before,
            style = if (isHeader) MaterialTheme.typography.labelMedium else MaterialTheme.typography.bodyMedium,
            color = if (isHeader) MaterialTheme.colorScheme.secondary else Color.Gray,
            fontWeight = if (isHeader) FontWeight.Bold else FontWeight.Normal,
            modifier = Modifier.weight(1f),
            textAlign = androidx.compose.ui.text.style.TextAlign.Center
        )
        Row(
            modifier = Modifier.weight(1f),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center
        ) {
            if (success != null) {
                Icon(
                    imageVector = if (success) Icons.Default.Check else Icons.Default.Close,
                    contentDescription = null,
                    tint = if (success) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.error,
                    modifier = Modifier.height(16.dp).width(16.dp)
                )
                Spacer(modifier = Modifier.width(4.dp))
            }
            Text(
                text = after,
                style = if (isHeader) MaterialTheme.typography.labelMedium else MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold),
                color = if (isHeader) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.primary,
            )
        }
    }
}

@Composable
fun ValidationItem(label: String, value: String, passed: Boolean) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = if (passed) Icons.Default.Check else Icons.Default.Close,
            contentDescription = null,
            tint = if (passed) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.error,
            modifier = Modifier.height(20.dp).width(20.dp)
        )
        Spacer(modifier = Modifier.width(8.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.weight(1f)
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold),
            color = if (passed) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.error
        )
    }
}
