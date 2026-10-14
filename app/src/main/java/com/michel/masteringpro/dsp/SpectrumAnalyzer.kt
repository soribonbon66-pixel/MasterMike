package com.michel.masteringpro.dsp

/**
 * FFT-based spectrum analyzer.
 * Implements radix-2 Cooley-Tukey FFT in pure Kotlin.
 * Computes magnitude spectrum and provides spectral analysis.
 */
object SpectrumAnalyzer {

    data class SpectrumResult(
        val magnitudes: DoubleArray, // magnitude per frequency bin
        val frequencies: DoubleArray, // frequency per bin
        val peakFrequency: Double,
        val energyLow: Double,      // energy below 80Hz
        val energyMid: Double,      // energy 80Hz-2kHz
        val energyHigh: Double,     // energy 2kHz-12kHz
        val energyAir: Double,      // energy above 12kHz
        val spectrumDb: DoubleArray  // magnitudes in dB
    )

    /**
     * Compute average spectrum of mono PCM using overlapping FFT windows.
     * @param samples Mono PCM FloatArray
     * @param sampleRate Sample rate in Hz
     * @param fftSize FFT window size (must be power of 2)
     */
    fun analyze(samples: FloatArray, sampleRate: Int, fftSize: Int = 4096): SpectrumResult {
        val numBins = fftSize / 2
        val avgMagnitudes = DoubleArray(numBins)
        var windowCount = 0

        // Hann window for better spectral estimation
        val window = DoubleArray(fftSize) { i ->
            0.5 * (1.0 - Math.cos(2.0 * Math.PI * i / (fftSize - 1)))
        }

        val hopSize = fftSize / 2 // 50% overlap

        var offset = 0
        while (offset + fftSize <= samples.size) {
            val real = DoubleArray(fftSize)
            val imag = DoubleArray(fftSize)

            for (i in 0 until fftSize) {
                real[i] = samples[offset + i].toDouble() * window[i]
            }

            fft(real, imag)

            for (i in 0 until numBins) {
                val mag = Math.sqrt(real[i] * real[i] + imag[i] * imag[i])
                avgMagnitudes[i] += mag
            }

            windowCount++
            offset += hopSize
        }

        // Average the magnitudes
        if (windowCount > 0) {
            for (i in 0 until numBins) {
                avgMagnitudes[i] /= windowCount
            }
        }

        // Compute frequency per bin
        val frequencies = DoubleArray(numBins) { i ->
            i.toDouble() * sampleRate / fftSize
        }

        // Convert to dB
        val spectrumDb = DoubleArray(numBins) { i ->
            if (avgMagnitudes[i] > 1e-10) 20.0 * Math.log10(avgMagnitudes[i]) else -120.0
        }

        // Find peak frequency (skip DC bin)
        var peakIdx = 1
        for (i in 2 until numBins) {
            if (avgMagnitudes[i] > avgMagnitudes[peakIdx]) peakIdx = i
        }

        // Compute energy in bands
        val energyLow = bandEnergy(spectrumDb, frequencies, 0.0, 80.0)
        val energyMid = bandEnergy(spectrumDb, frequencies, 80.0, 2000.0)
        val energyHigh = bandEnergy(spectrumDb, frequencies, 2000.0, 12000.0)
        val energyAir = bandEnergy(spectrumDb, frequencies, 12000.0, sampleRate / 2.0)

        return SpectrumResult(
            magnitudes = avgMagnitudes,
            frequencies = frequencies,
            peakFrequency = frequencies[peakIdx],
            energyLow = energyLow,
            energyMid = energyMid,
            energyHigh = energyHigh,
            energyAir = energyAir,
            spectrumDb = spectrumDb
        )
    }

    /**
     * Compute average energy in a frequency band (in dB).
     */
    fun bandEnergy(spectrumDb: DoubleArray, frequencies: DoubleArray, lowFreq: Double, highFreq: Double): Double {
        var sum = 0.0
        var count = 0
        for (i in spectrumDb.indices) {
            if (frequencies[i] in lowFreq..highFreq) {
                sum += spectrumDb[i]
                count++
            }
        }
        return if (count > 0) sum / count else -120.0
    }

    /**
     * Radix-2 Cooley-Tukey FFT (in-place).
     * Input arrays must have length that is a power of 2.
     */
    fun fft(real: DoubleArray, imag: DoubleArray) {
        val n = real.size
        require(n and (n - 1) == 0) { "FFT size must be power of 2, got $n" }

        // Bit-reversal permutation
        var j = 0
        for (i in 0 until n - 1) {
            if (i < j) {
                var temp = real[i]; real[i] = real[j]; real[j] = temp
                temp = imag[i]; imag[i] = imag[j]; imag[j] = temp
            }
            var k = n >> 1
            while (k <= j) {
                j -= k
                k >>= 1
            }
            j += k
        }

        // Cooley-Tukey iterative FFT
        var len = 2
        while (len <= n) {
            val angle = -2.0 * Math.PI / len
            val wReal = Math.cos(angle)
            val wImag = Math.sin(angle)

            var i = 0
            while (i < n) {
                var curReal = 1.0
                var curImag = 0.0

                for (k in 0 until len / 2) {
                    val idx1 = i + k
                    val idx2 = i + k + len / 2

                    val tReal = curReal * real[idx2] - curImag * imag[idx2]
                    val tImag = curReal * imag[idx2] + curImag * real[idx2]

                    real[idx2] = real[idx1] - tReal
                    imag[idx2] = imag[idx1] - tImag
                    real[idx1] = real[idx1] + tReal
                    imag[idx1] = imag[idx1] + tImag

                    val newCurReal = curReal * wReal - curImag * wImag
                    curImag = curReal * wImag + curImag * wReal
                    curReal = newCurReal
                }

                i += len
            }
            len <<= 1
        }
    }
}
