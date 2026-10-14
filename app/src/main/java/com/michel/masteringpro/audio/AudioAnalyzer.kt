package com.michel.masteringpro.audio

import com.michel.masteringpro.dsp.LoudnessMeter
import com.michel.masteringpro.dsp.SpectrumAnalyzer
import kotlin.math.abs
import kotlin.math.log10
import kotlin.math.sqrt

/**
 * Analyzes loaded audio and produces a comprehensive AnalysisResult.
 * Computes loudness (EBU R128), dynamics, spectral analysis, stereo/phase, BPM, and key.
 */
object AudioAnalyzer {

    data class AnalysisResult(
        // Loudness (EBU R128)
        val integratedLufs: Double,
        val truePeakDbtp: Double,
        val lra: Double,
        // Dynamics
        val rmsDb: Double,
        val crestFactorDb: Double,
        val dynamicRangeDb: Double,
        val clippingCount: Int,
        val clippingPercent: Double,
        // Spectral
        val spectrumDb: DoubleArray,
        val spectrumFreqs: DoubleArray,
        val energyLow: Double,
        val energyMid: Double,
        val energyHigh: Double,
        val energyAir: Double,
        // Stereo & Phase
        val correlation: Double,
        val stereoWidth: Double,
        val monoCompatibility: Double,
        // Tonal
        val bpmEstimate: Int,
        val keyEstimate: String,
        // Problems
        val problems: List<Problem>
    )

    data class Problem(
        val message: String,
        val severity: Severity
    ) {
        enum class Severity { INFO, WARNING, CRITICAL }
    }

    /**
     * Analyze interleaved stereo PCM audio.
     */
    fun analyze(samples: FloatArray, sampleRate: Int): AnalysisResult {
        // Extract L and R channels
        val left = FloatArray(samples.size / 2) { samples[it * 2] }
        val right = FloatArray(samples.size / 2) { samples[it * 2 + 1] }

        // 1. Loudness (EBU R128)
        val loudness = LoudnessMeter.measureStereo(samples, sampleRate)

        // 2. Dynamics
        val rmsDb = computeRmsDb(samples)
        val peakDb = computePeakDb(samples)
        val crestFactor = peakDb - rmsDb
        val dynamicRange = computeDynamicRange(left, sampleRate)
        val (clipCount, clipPercent) = computeClipping(samples)

        // 3. Spectral analysis (analyze left channel for mono reference)
        val spectrum = SpectrumAnalyzer.analyze(left, sampleRate, 4096)

        // 4. Stereo & Phase
        val correlation = computeCorrelation(left, right)
        val stereoWidth = computeStereoWidth(left, right)
        val monoCompatibility = computeMonoCompatibility(left, right)

        // 5. BPM estimation
        val bpm = estimateBpm(left, sampleRate)

        // 6. Key estimation
        val key = estimateKey(left, sampleRate)

        // 7. Detect problems
        val problems = detectProblems(loudness, spectrum, correlation, clipPercent, left, right)

        return AnalysisResult(
            integratedLufs = loudness.integratedLufs,
            truePeakDbtp = loudness.truePeakDbtp,
            lra = loudness.lra,
            rmsDb = rmsDb,
            crestFactorDb = crestFactor,
            dynamicRangeDb = dynamicRange,
            clippingCount = clipCount,
            clippingPercent = clipPercent,
            spectrumDb = spectrum.spectrumDb,
            spectrumFreqs = spectrum.frequencies,
            energyLow = spectrum.energyLow,
            energyMid = spectrum.energyMid,
            energyHigh = spectrum.energyHigh,
            energyAir = spectrum.energyAir,
            correlation = correlation,
            stereoWidth = stereoWidth,
            monoCompatibility = monoCompatibility,
            bpmEstimate = bpm,
            keyEstimate = key,
            problems = problems
        )
    }

    private fun computeRmsDb(samples: FloatArray): Double {
        var sumSq = 0.0
        for (s in samples) sumSq += s.toDouble() * s.toDouble()
        val rms = sqrt(sumSq / samples.size)
        return if (rms > 1e-10) 20.0 * log10(rms) else -100.0
    }

    private fun computePeakDb(samples: FloatArray): Double {
        var peak = 0.0
        for (s in samples) {
            val abs = abs(s.toDouble())
            if (abs > peak) peak = abs
        }
        return if (peak > 1e-10) 20.0 * log10(peak) else -100.0
    }

    private fun computeDynamicRange(mono: FloatArray, sampleRate: Int): Double {
        val blockSize = (sampleRate * 0.400).toInt()
        val rmsValues = mutableListOf<Double>()
        var offset = 0
        while (offset + blockSize <= mono.size) {
            var sumSq = 0.0
            for (i in offset until offset + blockSize) {
                sumSq += mono[i].toDouble() * mono[i].toDouble()
            }
            val rms = sqrt(sumSq / blockSize)
            val rmsDb = if (rms > 1e-10) 20.0 * log10(rms) else -100.0
            rmsValues.add(rmsDb)
            offset += blockSize
        }
        if (rmsValues.size < 10) return 0.0
        val sorted = rmsValues.sorted()
        val p10 = sorted[(sorted.size * 0.10).toInt()]
        val p95 = sorted[(sorted.size * 0.95).toInt().coerceAtMost(sorted.size - 1)]
        return p95 - p10
    }

    private fun computeClipping(samples: FloatArray): Pair<Int, Double> {
        val threshold = 0.999f // -0.1dB approx
        var count = 0
        for (s in samples) {
            if (abs(s) > threshold) count++
        }
        val percent = count.toDouble() / samples.size * 100.0
        return Pair(count, percent)
    }

    private fun computeCorrelation(left: FloatArray, right: FloatArray): Double {
        val n = minOf(left.size, right.size)
        var sumLR = 0.0
        var sumLL = 0.0
        var sumRR = 0.0
        for (i in 0 until n) {
            val l = left[i].toDouble()
            val r = right[i].toDouble()
            sumLR += l * r
            sumLL += l * l
            sumRR += r * r
        }
        val denom = sqrt(sumLL * sumRR)
        return if (denom > 1e-10) sumLR / denom else 0.0
    }

    private fun computeStereoWidth(left: FloatArray, right: FloatArray): Double {
        val n = minOf(left.size, right.size)
        var sumDiff = 0.0
        var sumSum = 0.0
        for (i in 0 until n) {
            val diff = (left[i] - right[i]).toDouble()
            val sum = (left[i] + right[i]).toDouble()
            sumDiff += diff * diff
            sumSum += sum * sum
        }
        val rmsDiff = sqrt(sumDiff / n)
        val rmsSum = sqrt(sumSum / n)
        return if (rmsSum > 1e-10) rmsDiff / rmsSum else 0.0
    }

    private fun computeMonoCompatibility(left: FloatArray, right: FloatArray): Double {
        // Check for phase cancellation when summing to mono
        val n = minOf(left.size, right.size)
        var monoSum = 0.0
        var leftSum = 0.0
        var rightSum = 0.0
        for (i in 0 until n) {
            monoSum += abs(left[i] + right[i])
            leftSum += abs(left[i])
            rightSum += abs(right[i])
        }
        val expectedMono = (leftSum + rightSum) / 2.0
        return if (expectedMono > 1e-10) (monoSum / n) / expectedMono * 2.0 else 0.0
    }

    /**
     * Estimate BPM using autocorrelation of onset detection function.
     * Simplified: uses energy-based onset detection then autocorrelation.
     */
    private fun estimateBpm(mono: FloatArray, sampleRate: Int): Int {
        val blockSize = 1024
        val hopSize = 512
        val numBlocks = (mono.size - blockSize) / hopSize
        if (numBlocks < 10) return 120

        // Energy per block
        val energy = DoubleArray(numBlocks)
        for (b in 0 until numBlocks) {
            val offset = b * hopSize
            var sum = 0.0
            for (i in offset until offset + blockSize) {
                sum += mono[i].toDouble() * mono[i].toDouble()
            }
            energy[b] = sum / blockSize
        }

        // Onset detection (first derivative)
        val onset = DoubleArray(energy.size - 1)
        for (i in 1 until energy.size) {
            onset[i - 1] = maxOf(0.0, energy[i] - energy[i - 1])
        }

        // Autocorrelation for BPM detection (20-220 BPM)
        val hopRate = sampleRate.toDouble() / hopSize
        val minLag = (hopRate * 60.0 / 220.0).toInt()
        val maxLag = (hopRate * 60.0 / 20.0).toInt().coerceAtMost(onset.size - 1)

        var bestLag = minLag
        var bestCorr = -1.0
        for (lag in minLag..maxLag) {
            var corr = 0.0
            var count = 0
            for (i in 0 until onset.size - lag) {
                corr += onset[i] * onset[i + lag]
                count++
            }
            if (count > 0) corr /= count
            if (corr > bestCorr) {
                bestCorr = corr
                bestLag = lag
            }
        }

        val bpm = (hopRate * 60.0 / bestLag).toInt()
        return bpm.coerceIn(40, 220)
    }

    /**
     * Estimate musical key using chromagram (simplified).
     * Maps energy to 12 pitch classes and correlates with major/minor profiles.
     */
    private fun estimateKey(mono: FloatArray, sampleRate: Int): String {
        val fftSize = 8192
        if (mono.size < fftSize) return "N/A"

        val chroma = DoubleArray(12)
        val hopSize = fftSize / 2
        var windowCount = 0

        var offset = 0
        while (offset + fftSize <= mono.size && windowCount < 50) {
            val real = DoubleArray(fftSize)
            val imag = DoubleArray(fftSize)
            for (i in 0 until fftSize) {
                real[i] = mono[offset + i].toDouble() *
                    (0.5 * (1.0 - kotlin.math.cos(2.0 * Math.PI * i / (fftSize - 1))))
            }
            com.michel.masteringpro.dsp.SpectrumAnalyzer.fft(real, imag)

            for (i in 1 until fftSize / 2) {
                val freq = i.toDouble() * sampleRate / fftSize
                if (freq in 65.0..2000.0) {
                    val mag = kotlin.math.sqrt(real[i] * real[i] + imag[i] * imag[i])
                    val midiNote = 69.0 + 12.0 * kotlin.math.log10(freq / 440.0)
                    val pitchClass = ((midiNote % 12.0) + 12.0).toInt() % 12
                    chroma[pitchClass] += mag * mag
                }
            }

            windowCount++
            offset += hopSize
        }

        if (windowCount == 0) return "N/A"

        // Normalize chroma
        val maxChroma = chroma.maxOrNull() ?: 1.0
        if (maxChroma < 1e-10) return "N/A"
        for (i in chroma.indices) chroma[i] /= maxChroma

        // Correlate with major and minor profiles (Krumhansl-Schmuckler)
        val majorProfile = doubleArrayOf(6.35, 2.23, 3.48, 2.33, 4.38, 4.09, 2.52, 5.19, 2.39, 3.66, 2.29, 2.88)
        val minorProfile = doubleArrayOf(6.33, 2.68, 3.52, 5.38, 2.60, 3.53, 2.54, 4.75, 3.98, 2.69, 3.34, 3.17)
        val noteNames = arrayOf("C", "C#", "D", "D#", "E", "F", "F#", "G", "G#", "A", "A#", "B")

        var bestKey = "C"
        var bestCorr = -1.0
        var bestMode = "Major"

        for (shift in 0 until 12) {
            val shifted = DoubleArray(12) { chroma[(it + shift) % 12] }

            val corrMajor = correlation(shifted, majorProfile)
            val corrMinor = correlation(shifted, minorProfile)

            if (corrMajor > bestCorr) {
                bestCorr = corrMajor
                bestKey = noteNames[shift]
                bestMode = "Major"
            }
            if (corrMinor > bestCorr) {
                bestCorr = corrMinor
                bestKey = noteNames[shift]
                bestMode = "Minor"
            }
        }

        return "$bestKey $bestMode"
    }

    private fun correlation(a: DoubleArray, b: DoubleArray): Double {
        val n = a.size
        val meanA = a.average()
        val meanB = b.average()
        var num = 0.0
        var denA = 0.0
        var denB = 0.0
        for (i in 0 until n) {
            val da = a[i] - meanA
            val db = b[i] - meanB
            num += da * db
            denA += da * da
            denB += db * db
        }
        val denom = sqrt(denA * denB)
        return if (denom > 1e-10) num / denom else 0.0
    }

    private fun detectProblems(
        loudness: LoudnessMeter.LoudnessResult,
        spectrum: SpectrumAnalyzer.SpectrumResult,
        correlation: Double,
        clipPercent: Double,
        left: FloatArray,
        right: FloatArray
    ): List<Problem> {
        val problems = mutableListOf<Problem>()

        // True Peak check
        if (loudness.truePeakDbtp > 0.0) {
            problems.add(Problem("True Peak a +${String.format("%.1f", loudness.truePeakDbtp)} dBTP - CLIPPING SEVERO", Problem.Severity.CRITICAL))
        } else if (loudness.truePeakDbtp > -1.0) {
            problems.add(Problem("True Peak a ${String.format("%.1f", loudness.truePeakDbtp)} dBTP - RIESGO DE CLIPPING", Problem.Severity.WARNING))
        }

        // Integrated loudness check
        if (loudness.integratedLufs > -9.0) {
            problems.add(Problem("LUFS integrado a ${String.format("%.1f", loudness.integratedLufs)} - DEMASIADO ALTO", Problem.Severity.CRITICAL))
        } else if (loudness.integratedLufs > -12.0) {
            problems.add(Problem("LUFS integrado a ${String.format("%.1f", loudness.integratedLufs)} - Puede ser demasiado alto para streaming", Problem.Severity.WARNING))
        } else if (loudness.integratedLufs < -24.0) {
            problems.add(Problem("LUFS integrado a ${String.format("%.1f", loudness.integratedLufs)} - DEMASIADO BAJO", Problem.Severity.WARNING))
        }

        // Clipping
        if (clipPercent > 1.0) {
            problems.add(Problem("Clipping: ${String.format("%.2f", clipPercent)}% muestras saturadas", Problem.Severity.CRITICAL))
        } else if (clipPercent > 0.0) {
            problems.add(Problem("Clipping: ${String.format("%.2f", clipPercent)}% muestras cercanas al límite", Problem.Severity.WARNING))
        }

        // LRA
        if (loudness.lra > 12.0) {
            problems.add(Problem("Rango dinámico muy amplio: LRA ${String.format("%.1f", loudness.lra)} LU", Problem.Severity.WARNING))
        }

        // Spectral issues
        if (spectrum.energyLow < -60.0) {
            problems.add(Problem("Falta de graves (< 80 Hz) - sonido delgado", Problem.Severity.WARNING))
        }
        if (spectrum.energyHigh - spectrum.energyMid > 6.0) {
            problems.add(Problem("Exceso en 2-5 kHz - sonido estridente", Problem.Severity.WARNING))
        }
        if (spectrum.energyAir < spectrum.energyMid - 15.0) {
            problems.add(Problem("Falta de aire (> 12 kHz) - sonido opaco", Problem.Severity.WARNING))
        }

        // Stereo/Phase
        if (correlation < 0.3) {
            problems.add(Problem("PROBLEMA DE FASE: correlación ${String.format("%.2f", correlation)}", Problem.Severity.CRITICAL))
        } else if (correlation < 0.5) {
            problems.add(Problem("Correlación estéreo baja: ${String.format("%.2f", correlation)}", Problem.Severity.WARNING))
        }

        // L/R balance
        val rmsL = kotlin.math.sqrt(left.sumOf { it.toDouble() * it.toDouble() } / left.size)
        val rmsR = kotlin.math.sqrt(right.sumOf { it.toDouble() * it.toDouble() } / right.size)
        if (rmsL > 1e-10 && rmsR > 1e-10) {
            val diffDb = 20.0 * kotlin.math.log10(rmsL / rmsR)
            if (kotlin.math.abs(diffDb) > 1.5) {
                problems.add(Problem("Desbalance L/R: ${String.format("%.1f", diffDb)} dB", Problem.Severity.WARNING))
            }
        }

        return problems
    }
}
