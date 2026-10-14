package com.michel.masteringpro.audio

import android.content.ContentValues
import android.content.Context
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import java.io.File
import java.io.FileOutputStream
import java.io.OutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Writes PCM FloatArray to WAV file format.
 * Supports 24-bit and 32-bit float PCM output.
 * Saves to MediaStore (scoped storage) or legacy path for older devices.
 */
object WavWriter {

    enum class BitDepth(val bitsPerSample: Int) {
        PCM_24BIT(24),
        PCM_32BIT_FLOAT(32)
    }

    /**
     * Write interleaved stereo PCM to WAV file.
     * @return File path of written WAV
     */
    fun write(
        context: Context,
        samples: FloatArray,
        sampleRate: Int,
        channelCount: Int = 2,
        bitDepth: BitDepth = BitDepth.PCM_24BIT,
        filename: String? = null
    ): String {
        val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
        val name = filename ?: "michel_mastered_$timestamp.wav"

        val wavData = encodeWav(samples, sampleRate, channelCount, bitDepth)

        // Try to save to MediaStore (Android 10+)
        val savedPath = try {
            saveToMediaStore(context, wavData, name)
        } catch (e: Exception) {
            // Fallback: save to app-specific directory
            saveToAppDir(context, wavData, name)
        }

        return savedPath
    }

    /**
     * Encode PCM FloatArray to WAV byte array.
     */
    private fun encodeWav(samples: FloatArray, sampleRate: Int, channelCount: Int, bitDepth: BitDepth): ByteArray {
        val bitsPerSample = bitDepth.bitsPerSample
        val bytesPerSample = bitsPerSample / 8
        val blockAlign = channelCount * bytesPerSample
        val dataSize = samples.size * bytesPerSample
        val byteRate = sampleRate * blockAlign
        val totalSize = 44 + dataSize // 44 byte header + data

        val buffer = ByteBuffer.allocate(totalSize).order(ByteOrder.LITTLE_ENDIAN)

        // RIFF header
        buffer.put("RIFF".toByteArray())
        buffer.putInt(totalSize - 8) // file size - 8
        buffer.put("WAVE".toByteArray())

        // fmt chunk
        buffer.put("fmt ".toByteArray())
        buffer.putInt(16) // chunk size
        buffer.putShort(when (bitDepth) {
            BitDepth.PCM_24BIT -> 1 // PCM
            BitDepth.PCM_32BIT_FLOAT -> 3 // IEEE Float
        })
        buffer.putShort(channelCount.toShort())
        buffer.putInt(sampleRate)
        buffer.putInt(byteRate)
        buffer.putShort(blockAlign.toShort())
        buffer.putShort(bitsPerSample.toShort())

        // data chunk
        buffer.put("data".toByteArray())
        buffer.putInt(dataSize)

        // Write samples
        when (bitDepth) {
            BitDepth.PCM_24BIT -> {
                for (sample in samples) {
                    val clamped = sample.coerceIn(-1f, 1f)
                    val int24 = (clamped * 8388607f).toInt()
                    buffer.put((int24 and 0xFF).toByte())
                    buffer.put(((int24 >> 8) and 0xFF).toByte())
                    buffer.put(((int24 >> 16) and 0xFF).toByte())
                }
            }
            BitDepth.PCM_32BIT_FLOAT -> {
                for (sample in samples) {
                    buffer.putFloat(sample.coerceIn(-1f, 1f))
                }
            }
        }

        return buffer.array()
    }

    /**
     * Save WAV data to MediaStore (for sharing in Music folder).
     */
    private fun saveToMediaStore(context: Context, wavData: ByteArray, filename: String): String {
        val contentValues = ContentValues().apply {
            put(MediaStore.Audio.Media.DISPLAY_NAME, filename)
            put(MediaStore.Audio.Media.MIME_TYPE, "audio/wav")
            put(MediaStore.Audio.Media.RELATIVE_PATH, "Music/Michel/")
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                put(MediaStore.Audio.Media.IS_PENDING, 1)
            }
        }

        val resolver = context.contentResolver
        val collection = MediaStore.Audio.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
        val uri = resolver.insert(collection, contentValues)
            ?: throw IllegalStateException("Cannot create MediaStore entry")

        resolver.openOutputStream(uri)?.use { outputStream ->
            outputStream.write(wavData)
        } ?: throw IllegalStateException("Cannot open output stream")

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            contentValues.clear()
            contentValues.put(MediaStore.Audio.Media.IS_PENDING, 0)
            resolver.update(uri, contentValues, null, null)
        }

        return "Music/Michel/$filename"
    }

    /**
     * Fallback: Save WAV to app-specific directory.
     */
    private fun saveToAppDir(context: Context, wavData: ByteArray, filename: String): String {
        val dir = File(context.getExternalFilesDir(Environment.DIRECTORY_MUSIC), "Michel")
        dir.mkdirs()
        val file = File(dir, filename)
        FileOutputStream(file).use { it.write(wavData) }
        return file.absolutePath
    }

    /**
     * Export to a temporary file for sharing.
     * @return File object for sharing
     */
    fun exportToShare(context: Context, samples: FloatArray, sampleRate: Int, channelCount: Int = 2): File {
        val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
        val filename = "michel_mastered_$timestamp.wav"
        val wavData = encodeWav(samples, sampleRate, channelCount, BitDepth.PCM_24BIT)

        val cacheFile = File(context.cacheDir, filename)
        FileOutputStream(cacheFile).use { it.write(wavData) }
        return cacheFile
    }
}
