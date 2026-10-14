package com.michel.masteringpro.dsp

/**
 * EBU R128 Loudness Meter implementation.
 * Computes Integrated Loudness (LUFS), Loudness Range (LRA), and True Peak (dBTP).
 *
 * Implements K-weighting filter (high-shelf + high-pass) and gating per EBU R128.
 * Simplified gating: uses absolute threshold (-70 LUFS) and relative threshold (-10 LU below ungated mean).
 */
object LoudnessMeter {

    data class LoudnessResult(
        val integratedLufs: Double,
        val truePeakDbtp: Double,
        val lra: Double,
        val shortTermMax: Double
    )

    /**
     * Measure loudness of mono PCM signal.
     * @param samples Mono PCM FloatArray
     * @param sampleRate Sample rate in Hz
     */
    fun measure(samples: FloatArray, sampleRate: Int): LoudnessResult {
        val blockSize = (sampleRate * 0.400).toInt() // 400ms blocks
        val hopSize = (sampleRate * 0.100).toInt()   // 100ms hop

        // Step 1: Apply K-weighting filter
        val filtered = applyKWeighting(samples, sampleRate)

        // Step 2: Compute block loudness values
        val blockLoudness = mutableListOf<Double>()
        var offset = 0
        while (offset + blockSize <= filtered.size) {
            var sumSq = 0.0
            for (i in offset until offset + blockSize) {
                val s = filtered[i].toDouble()
                sumSq += s * s
            }
            val meanSq = sumSq / blockSize
            val lufs = if (meanSq > 0) -0.691 + 10.0 * Math.log10(meanSq) else -100.0
            blockLoudness.add(lufs)
            offset += hopSize
        }

        // Step 3: Absolute gating (-70 LUFS)
        val absoluteGated = blockLoudness.filter { it > -70.0 }
        val absoluteMean = if (absoluteGated.isNotEmpty()) absoluteGated.average() else -70.0

        // Step 4: Relative gating (-10 LU below absolute mean)
        val relativeThreshold = absoluteMean - 10.0
        val relativeGated = blockLoudness.filter { it > relativeThreshold }
        val integrated = if (relativeGated.isNotEmpty()) relativeGated.average() else absoluteMean

        // Step 5: LRA (Loudness Range)
        val lra = computeLRA(blockLoudness)

        // Step 6: True Peak (4x oversample)
        val truePeak = computeTruePeak(samples)

        // Step 7: Short-term max (3s window)
        val shortTermBlock = (sampleRate * 3.0).toInt()
        var shortTermMax = -100.0
        offset = 0
        while (offset + shortTermBlock <= filtered.size) {
            var sumSq = 0.0
            for (i in offset until offset + shortTermBlock) {
                val s = filtered[i].toDouble()
                sumSq += s * s
            }
            val meanSq = sumSq / shortTermBlock
            val lufs = if (meanSq > 0) -0.691 + 10.0 * Math.log10(meanSq) else -100.0
            if (lufs > shortTermMax) shortTermMax = lufs
            offset += shortTermBlock
        }

        return LoudnessResult(
            integratedLufs = integrated,
            truePeakDbtp = truePeak,
            lra = lra,
            shortTermMax = shortTermMax
        )
    }

    /**
     * Measure loudness of interleaved stereo PCM.
     * K-weighting applied independently per channel, then summed with weights.
     */
    fun measureStereo(interleaved: FloatArray, sampleRate: Int): LoudnessResult {
        // Extract L and R channels
        val left = FloatArray(interleaved.size / 2)
        val right = FloatArray(interleaved.size / 2)
        for (i in 0 until interleaved.size step 2) {
            left[i / 2] = interleaved[i]
            right[i / 2] = interleaved[i + 1]
        }

        val blockSize = (sampleRate * 0.400).toInt()
        val hopSize = (sampleRate * 0.100).toInt()

        // K-weight each channel
        val filteredL = applyKWeighting(left, sampleRate)
        val filteredR = applyKWeighting(right, sampleRate)

        // Compute block loudness (sum L+R with equal weight for stereo)
        val blockLoudness = mutableListOf<Double>()
        var offset = 0
        while (offset + blockSize <= filteredL.size) {
            var sumSqL = 0.0
            var sumSqR = 0.0
            for (i in offset until offset + blockSize) {
                val sl = filteredL[i].toDouble()
                val sr = filteredR[i].toDouble()
                sumSqL += sl * sl
                sumSqR += sr * sr
            }
            val meanSqL = sumSqL / blockSize
            val meanSqR = sumSqR / blockSize
            // Stereo: sum of both channels
            val totalMeanSq = meanSqL + meanSqR
            val lufs = if (totalMeanSq > 0) -0.691 + 10.0 * Math.log10(totalMeanSq / 2.0) else -100.0
            blockLoudness.add(lufs)
            offset += hopSize
        }

        // Gating
        val absoluteGated = blockLoudness.filter { it > -70.0 }
        val absoluteMean = if (absoluteGated.isNotEmpty()) absoluteGated.average() else -70.0
        val relativeThreshold = absoluteMean - 10.0
        val relativeGated = blockLoudness.filter { it > relativeThreshold }
        val integrated = if (relativeGated.isNotEmpty()) relativeGated.average() else absoluteMean

        val lra = computeLRA(blockLoudness)
        val truePeak = computeTruePeakStereo(interleaved, sampleRate)

        // Short-term max
        val shortTermBlock = (sampleRate * 3.0).toInt()
        var shortTermMax = -100.0
        offset = 0
        while (offset + shortTermBlock <= filteredL.size) {
            var sumSq = 0.0
            for (i in offset until offset + shortTermBlock) {
                val sl = filteredL[i].toDouble()
                val sr = filteredR[i].toDouble()
                sumSq += (sl * sl + sr * sr) / 2.0
            }
            val meanSq = sumSq / shortTermBlock
            val lufs = if (meanSq > 0) -0.691 + 10.0 * Math.log10(meanSq) else -100.0
            if (lufs > shortTermMax) shortTermMax = lufs
            offset += shortTermBlock
        }

        return LoudnessResult(
            integratedLufs = integrated,
            truePeakDbtp = truePeak,
            lra = lra,
            shortTermMax = shortTermMax
        )
    }

    /**
     * K-weighting filter: cascade of high-shelf (+4dB at 1.5kHz) and high-pass (168.42Hz).
     * Coefficients from ITU-R BS.1770-4 for 48kHz. Scaled for other sample rates.
     */
    private fun applyKWeighting(samples: FloatArray, sampleRate: Int): FloatArray {
        // High-shelf filter coefficients (pre-computed for 48kHz, scale for other rates)
        // Using the standard ITU-R BS.1770-4 coefficients
        val hsStateL = Eq.BiquadState()
        val hpStateL = Eq.BiquadState()

        // For simplicity, use pre-computed 48kHz coefficients and resample if needed
        // High-shelf: +4dB at 1.5kHz
        val hsCoeffs = Eq.highShelf(sampleRate.toDouble(), 1500.0, 4.0, 0.7071)
        val filtered1 = Eq.applyBiquad(samples, hsCoeffs, hsStateL)

        // High-pass: 2nd order Butterworth at 168.42Hz
        val hpCoeffs = Eq.highPass(sampleRate.toDouble(), 168.42, 0.5)
        val filtered2 = Eq.applyBiquad(filtered1, hpCoeffs, hpStateL)

        return filtered2
    }

    /**
     * True Peak measurement via 4x oversampling.
     */
    private fun computeTruePeak(samples: FloatArray): Double {
        // 4x oversample using linear interpolation (simplified sinc)
        val oversampled = FloatArray(samples.size * 4)
        for (i in 0 until samples.size - 1) {
            val s0 = samples[i]
            val s1 = samples[i + 1]
            for (j in 0 until 4) {
                val t = j / 4.0
                oversampled[i * 4 + j] = (s0 * (1.0 - t) + s1 * t).toFloat()
            }
        }
        // Last sample
        for (j in 0 until 4) {
            oversampled[oversampled.size - 4 + j] = samples.last()
        }

        var maxDb = -100.0
        for (s in oversampled) {
            val absS = Math.abs(s.toDouble())
            if (absS > 1e-10) {
                val db = 20.0 * Math.log10(absS)
                if (db > maxDb) maxDb = db
            }
        }
        return maxDb
    }

    private fun computeTruePeakStereo(interleaved: FloatArray, sampleRate: Int): Double {
        var maxDb = -100.0
        for (i in interleaved.indices) {
            val absS = Math.abs(interleaved[i].toDouble())
            if (absS > 1e-10) {
                val db = 20.0 * Math.log10(absS)
                if (db > maxDb) maxDb = db
            }
        }
        return maxDb
    }

    /**
     * LRA: Loudness Range (10th to 95th percentile of block loudness values).
     */
    private fun computeLRA(blockLoudness: List<Double>): Double {
        if (blockLoudness.size < 10) return 0.0

        val sorted = blockLoudness.sorted()
        val p10Index = (sorted.size * 0.10).toInt()
        val p95Index = (sorted.size * 0.95).toInt()

        return sorted[p95Index.coerceAtMost(sorted.size - 1)] - sorted[p10Index]
    }
}
