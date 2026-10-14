package com.michel.masteringpro.dsp

/**
 * Feed-forward dynamic range compressor.
 * Uses RMS-based level detection with attack/release envelope following.
 */
object Compressor {

    data class CompressorSettings(
        val thresholdDb: Float = -18f,
        val ratio: Float = 1.5f,
        val attackMs: Float = 30f,
        val releaseMs: Float = 100f,
        val makeupGainDb: Float = 0f,
        val enabled: Boolean = false
    )

    /**
     * Apply compression to a mono PCM signal.
     * @param input Mono PCM samples as FloatArray
     * @param sampleRate Sample rate in Hz
     * @param settings Compressor parameters
     * @return Compressed audio as FloatArray
     */
    fun apply(input: FloatArray, sampleRate: Int, settings: CompressorSettings): FloatArray {
        if (!settings.enabled || settings.ratio <= 1.0f) return input.copyOf()

        val output = FloatArray(input.size)
        val threshold = settings.thresholdDb
        val ratio = settings.ratio
        val makeupGain = dbToLinear(settings.makeupGainDb)

        val attackCoeff = Math.exp(-1.0 / (sampleRate * settings.attackMs / 1000.0))
        val releaseCoeff = Math.exp(-1.0 / (sampleRate * settings.releaseMs / 1000.0))

        var envelopeDb = -96.0

        for (i in input.indices) {
            val sample = input[i].toDouble()

            // Convert sample to dB
            val sampleDb = if (Math.abs(sample) > 1e-10) 20.0 * Math.log10(Math.abs(sample)) else -96.0

            // Envelope follower (attack/release)
            if (sampleDb > envelopeDb) {
                envelopeDb = attackCoeff * envelopeDb + (1.0 - attackCoeff) * sampleDb
            } else {
                envelopeDb = releaseCoeff * envelopeDb + (1.0 - releaseCoeff) * sampleDb
            }

            // Gain computation
            var gainReductionDb = 0.0
            if (envelopeDb > threshold) {
                gainReductionDb = (envelopeDb - threshold) * (1.0 - 1.0 / ratio)
            }

            val gainLinear = dbToLinear(-gainReductionDb) * makeupGain
            output[i] = (sample * gainLinear).toFloat()
        }

        return output
    }

    /**
     * Apply compression to interleaved stereo PCM.
     * Uses linked stereo (same envelope for both channels based on max level).
     */
    fun applyStereo(input: FloatArray, sampleRate: Int, settings: CompressorSettings): FloatArray {
        if (!settings.enabled || settings.ratio <= 1.0f) return input.copyOf()

        val output = FloatArray(input.size)
        val threshold = settings.thresholdDb
        val ratio = settings.ratio
        val makeupGain = dbToLinear(settings.makeupGainDb)

        val attackCoeff = Math.exp(-1.0 / (sampleRate * settings.attackMs / 1000.0))
        val releaseCoeff = Math.exp(-1.0 / (sampleRate * settings.releaseMs / 1000.0))

        var envelopeDb = -96.0

        var i = 0
        while (i < input.size - 1) {
            val sampleL = input[i].toDouble()
            val sampleR = input[i + 1].toDouble()

            // Use max of both channels for stereo linking
            val maxSample = Math.max(Math.abs(sampleL), Math.abs(sampleR))
            val sampleDb = if (maxSample > 1e-10) 20.0 * Math.log10(maxSample) else -96.0

            // Envelope follower
            if (sampleDb > envelopeDb) {
                envelopeDb = attackCoeff * envelopeDb + (1.0 - attackCoeff) * sampleDb
            } else {
                envelopeDb = releaseCoeff * envelopeDb + (1.0 - releaseCoeff) * sampleDb
            }

            // Gain computation
            var gainReductionDb = 0.0
            if (envelopeDb > threshold) {
                gainReductionDb = (envelopeDb - threshold) * (1.0 - 1.0 / ratio)
            }

            val gainLinear = dbToLinear(-gainReductionDb) * makeupGain
            output[i] = (sampleL * gainLinear).toFloat()
            output[i + 1] = (sampleR * gainLinear).toFloat()

            i += 2
        }

        return output
    }

    private fun dbToLinear(db: Double): Double = Math.pow(10.0, db / 20.0)
    private fun dbToLinear(db: Float): Double = Math.pow(10.0, db.toDouble() / 20.0)
}
