package com.michel.masteringpro.audio

import android.content.Context
import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import android.net.Uri
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * Loads audio files and decodes them to PCM FloatArray using MediaExtractor + MediaCodec.
 * Supports MP3, WAV, AAC, OGG and other formats supported by Android codecs.
 */
object AudioLoader {

    data class DecodedAudio(
        val samples: FloatArray, // Interleaved stereo (L, R, L, R, ...)
        val sampleRate: Int,
        val channelCount: Int,
        val duration: Double // seconds
    )

    /**
     * Load and decode an audio file from URI.
     * Copies to cacheDir first, then decodes to PCM float.
     */
    fun loadFromUri(context: Context, uri: Uri): DecodedAudio {
        val inputStream = context.contentResolver.openInputStream(uri)
            ?: throw IllegalArgumentException("Cannot open URI: $uri")

        // Copy to cache
        val cacheFile = File(context.cacheDir, "input_audio_${System.currentTimeMillis()}")
        cacheFile.outputStream().use { output ->
            inputStream.copyTo(output)
        }
        inputStream.close()

        return decodeToPCM(cacheFile)
    }

    /**
     * Decode a file to PCM FloatArray using MediaExtractor + MediaCodec.
     */
    private fun decodeToPCM(file: File): DecodedAudio {
        val extractor = MediaExtractor()
        extractor.setDataSource(file.absolutePath)

        val trackIndex = selectAudioTrack(extractor)
        if (trackIndex < 0) throw IllegalStateException("No audio track found")

        extractor.selectTrack(trackIndex)
        val format = extractor.getTrackFormat(trackIndex)
        val sampleRate = format.getInteger(MediaFormat.KEY_SAMPLE_RATE)
        val channelCount = format.getInteger(MediaFormat.KEY_CHANNEL_COUNT)
        val mime = format.getString(MediaFormat.KEY_MIME) ?: throw IllegalStateException("No MIME type")

        // Decode
        val decoder = MediaCodec.createDecoderByType(mime)
        decoder.configure(format, null, null, 0)
        decoder.start()

        val outputBuffer = AudioBuffer()
        val bufferInfo = MediaCodec.BufferInfo()
        var inputDone = false
        var outputDone = false

        while (!outputDone) {
            // Feed input
            if (!inputDone) {
                val inputIndex = decoder.dequeueInputBuffer(10000)
                if (inputIndex >= 0) {
                    val inputBuffer = decoder.getInputBuffer(inputIndex)!!
                    val sampleSize = extractor.readSampleData(inputBuffer, 0)
                    if (sampleSize < 0) {
                        decoder.queueInputBuffer(inputIndex, 0, 0, 0, MediaCodec.BUFFER_FLAG_END_OF_STREAM)
                        inputDone = true
                    } else {
                        decoder.queueInputBuffer(inputIndex, 0, sampleSize, extractor.sampleTime, 0)
                        extractor.advance()
                    }
                }
            }

            // Read output
            val outputIndex = decoder.dequeueOutputBuffer(bufferInfo, 10000)
            when {
                outputIndex >= 0 -> {
                    val outBuffer = decoder.getOutputBuffer(outputIndex)!!
                    val bytes = ByteArray(bufferInfo.size)
                    outBuffer.get(bytes)
                    outputBuffer.writeBytes(bytes)
                    decoder.releaseOutputBuffer(outputIndex, false)

                    if (bufferInfo.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0) {
                        outputDone = true
                    }
                }
                outputIndex == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED -> {
                    // Format changed, continue
                }
                outputIndex == MediaCodec.INFO_TRY_AGAIN_LATER -> {
                    // Timeout, continue
                }
            }
        }

        decoder.stop()
        decoder.release()
        extractor.release()
        file.delete()

        // Convert bytes to FloatArray
        val rawBytes = outputBuffer.toByteArray()
        val samples = bytesToFloatArray(rawBytes, format)
        val duration = samples.size.toDouble() / (sampleRate * channelCount)

        // If mono, duplicate to stereo interleaved
        val stereoSamples = if (channelCount == 1) {
            val stereo = FloatArray(samples.size * 2)
            for (i in samples.indices) {
                stereo[i * 2] = samples[i]
                stereo[i * 2 + 1] = samples[i]
            }
            stereo
        } else {
            samples
        }

        return DecodedAudio(
            samples = stereoSamples,
            sampleRate = sampleRate,
            channelCount = 2, // Always output stereo
            duration = duration
        )
    }

    private fun selectAudioTrack(extractor: MediaExtractor): Int {
        for (i in 0 until extractor.trackCount) {
            val mime = extractor.getTrackFormat(i).getString(MediaFormat.KEY_MIME)
            if (mime != null && mime.startsWith("audio/")) return i
        }
        return -1
    }

    /**
     * Convert raw bytes to FloatArray based on sample format.
     */
    private fun bytesToFloatArray(bytes: ByteArray, format: MediaFormat): FloatArray {
        val pcmEncoding = if (format.containsKey(MediaFormat.KEY_PCM_ENCODING)) {
            format.getInteger(MediaFormat.KEY_PCM_ENCODING)
        } else {
            android.media.AudioFormat.ENCODING_PCM_16BIT
        }

        return when (pcmEncoding) {
            android.media.AudioFormat.ENCODING_PCM_16BIT -> {
                val samples = FloatArray(bytes.size / 2)
                val bb = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN)
                for (i in samples.indices) {
                    samples[i] = bb.short / 32768f
                }
                samples
            }
            android.media.AudioFormat.ENCODING_PCM_24BITPACKED -> {
                val samples = FloatArray(bytes.size / 3)
                for (i in samples.indices) {
                    val b0 = bytes[i * 3].toInt() and 0xFF
                    val b1 = bytes[i * 3 + 1].toInt() and 0xFF
                    val b2 = bytes[i * 3 + 2].toInt()
                    val value = (b0 or (b1 shl 8) or (b2 shl 16))
                    samples[i] = value / 8388608f
                }
                samples
            }
            android.media.AudioFormat.ENCODING_PCM_32BIT,
            android.media.AudioFormat.ENCODING_PCM_FLOAT -> {
                val samples = FloatArray(bytes.size / 4)
                val bb = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN)
                for (i in samples.indices) {
                    samples[i] = bb.float
                }
                samples
            }
            else -> {
                // Default to 16-bit
                val samples = FloatArray(bytes.size / 2)
                val bb = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN)
                for (i in samples.indices) {
                    samples[i] = bb.short / 32768f
                }
                samples
            }
        }
    }
}

/**
 * Simple expandable byte buffer for decoded audio data.
 */
class AudioBuffer(private var buffer: ByteArray = ByteArray(8192), private var count: Int = 0) {

    fun writeBytes(data: ByteArray) {
        ensureCapacity(count + data.size)
        System.arraycopy(data, 0, buffer, count, data.size)
        count += data.size
    }

    private fun ensureCapacity(minCapacity: Int) {
        if (minCapacity > buffer.size) {
            var newCapacity = buffer.size * 2
            while (newCapacity < minCapacity) newCapacity *= 2
            buffer = buffer.copyOf(newCapacity)
        }
    }

    fun toByteArray(): ByteArray = buffer.copyOf(count)
}
