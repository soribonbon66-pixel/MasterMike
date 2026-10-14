package com.michel.masteringpro.dsp

/**
 * Brickwall limiter implementation.
 * Uses lookahead with a ceiling to prevent clipping.
 * Implements a simple peak-limiting algorithm with release smoothing.
 */
object Limiter {

    data class LimiterSettings(
        val ceilingDb: Float = -1.0f,
        val releaseMs: Float = 50f,
        val enabled: Boolean = false
    )

    /**
     * Apply brickwall limiting to mono PCM.
     * Uses lookahead buffer for better transient handling.
     */
    fun apply(input: FloatArray, sampleRate: Int, settings: LimiterSettings): FloatArray {
        if (!settings.enabled) return input.copyOf()

        val ceilingLinear = dbToLinear(settings.ceilingDb.toDouble()).toFloat()
        val releaseCoeff = Math.exp(-1.0 / (sampleRate * settings.releaseMs / 1000.0))
        val lookaheadSamples = (sampleRate * 0.005).toInt().coerceAtLeast(1) // 5ms lookahead

        val output = FloatArray(input.size)

        // Pass 1: compute gain envelope with lookahead
        val gainEnvelope = FloatArray(input.size)
        var currentGain = 1.0f

        for (i in input.indices) {
            val sampleAbs = Math.abs(input[i])
            val targetGain = if (sampleAbs > ceilingLinear) ceilingLinear / sampleAbs else 1.0f

            // Lookahead: check upcoming samples
            var minGain = targetGain
            val lookAheadEnd = Math.min(i + lookaheadSamples, input.size)
            for (j in i until lookAheadEnd) {
                val futureAbs = Math.abs(input[j])
                if (futureAbs > ceilingLinear) {
                    val futureGain = ceilingLinear / futureAbs
                    if (futureGain < minGain) minGain = futureGain
                }
            }

            // Smooth gain (only release, gain reduction is instant for brickwall)
            if (minGain < currentGain) {
                currentGain = minGain
            } else {
                currentGain = (releaseCoeff * currentGain + (1.0f - releaseCoeff) * minGain).toFloat()
            }

            gainEnvelope[i] = currentGain
        }

        // Pass 2: apply gain envelope
        for (i in input.indices) {
            output[i] = input[i] * gainEnvelope[i]
        }

        // Final hard clamp to ceiling
        for (i in output.indices) {
            if (Math.abs(output[i]) > ceilingLinear) {
                output[i] = if (output[i] > 0) ceilingLinear else -ceilingLinear
            }
        }

        return output
    }

    /**
     * Apply brickwall limiting to interleaved stereo PCM.
     * Uses linked stereo limiting (same gain for both channels).
     */
    fun applyStereo(input: FloatArray, sampleRate: Int, settings: LimiterSettings): FloatArray {
        if (!settings.enabled) return input.copyOf()

        val ceilingLinear = dbToLinear(settings.ceilingDb.toDouble()).toFloat()
        val releaseCoeff = Math.exp(-1.0 / (sampleRate * settings.releaseMs / 1000.0))
        val lookaheadSamples = (sampleRate * 0.005).toInt().coerceAtLeast(1)

        val output = FloatArray(input.size)

        // Compute gain envelope (stereo-linked)
        val gainEnvelope = FloatArray(input.size / 2)
        var currentGain = 1.0f

        for (i in 0 until input.size step 2) {
            val maxAbs = Math.max(Math.abs(input[i]), Math.abs(input[i + 1]))
            val targetGain = if (maxAbs > ceilingLinear) ceilingLinear / maxAbs else 1.0f

            // Lookahead
            var minGain = targetGain
            val lookAheadEnd = Math.min(i + lookaheadSamples * 2, input.size - 1)
            var j = i
            while (j < lookAheadEnd) {
                val futureAbs = Math.max(Math.abs(input[j]), Math.abs(input[j + 1]))
                if (futureAbs > ceilingLinear) {
                    val futureGain = ceilingLinear / futureAbs
                    if (futureGain < minGain) minGain = futureGain
                }
                j += 2
            }

            if (minGain < currentGain) {
                currentGain = minGain
            } else {
                currentGain = (releaseCoeff * currentGain + (1.0f - releaseCoeff) * minGain).toFloat()
            }

            gainEnvelope[i / 2] = currentGain
        }

        // Apply gain
        for (i in 0 until input.size step 2) {
            val gain = gainEnvelope[i / 2]
            output[i] = input[i] * gain
            output[i + 1] = input[i + 1] * gain
        }

        // Hard clamp
        for (i in output.indices) {
            if (Math.abs(output[i]) > ceilingLinear) {
                output[i] = if (output[i] > 0) ceilingLinear else -ceilingLinear
            }
        }

        return output
    }

    private fun dbToLinear(db: Double): Double = Math.pow(10.0, db / 20.0)
}
