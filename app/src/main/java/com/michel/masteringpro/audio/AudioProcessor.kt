package com.michel.masteringpro.audio

import com.michel.masteringpro.dsp.Compressor
import com.michel.masteringpro.dsp.Eq
import com.michel.masteringpro.dsp.Limiter
import com.michel.masteringpro.dsp.LoudnessMeter
import kotlin.math.pow
import kotlin.math.sqrt

/**
 * Applies mastering processing chain to audio.
 * Order: Gain -> EQ -> Compressor -> Limiter -> Loudness Normalization
 */
object AudioProcessor {

    data class ProcessingResult(
        val processedSamples: FloatArray,
        val processingLog: List<String>
    )

    /**
     * Apply full mastering chain to interleaved stereo PCM.
     */
    fun process(
        input: FloatArray,
        sampleRate: Int,
        settings: SuggestionEngine.MasteringSettings
    ): ProcessingResult {
        val log = mutableListOf<String>()
        var current = input.copyOf()

        // Step 1: Apply gain
        if (settings.gainDb != 0f) {
            val gainLinear = 10.0.pow(settings.gainDb.toDouble() / 20.0)
            for (i in current.indices) {
                current[i] = (current[i] * gainLinear).toFloat()
            }
            log.add("Gain: ${String.format("%.1f", settings.gainDb)} dB")
        }

        // Step 2: Apply EQ (3 bands)
        current = applyEQ(current, sampleRate, settings, log)

        // Step 3: Apply Compressor
        if (settings.compEnabled) {
            val compSettings = Compressor.CompressorSettings(
                thresholdDb = settings.compThresholdDb,
                ratio = settings.compRatio,
                attackMs = settings.compAttackMs,
                releaseMs = settings.compReleaseMs,
                makeupGainDb = settings.compMakeupDb,
                enabled = true
            )
            current = Compressor.applyStereo(current, sampleRate, compSettings)
            log.add("Compresor: threshold=${compSettings.thresholdDb}dB, ratio=${compSettings.ratio}:1")
        }

        // Step 4: Apply Limiter
        if (settings.limiterEnabled) {
            val limiterSettings = Limiter.LimiterSettings(
                ceilingDb = settings.limiterCeilingDb,
                releaseMs = settings.limiterReleaseMs,
                enabled = true
            )
            current = Limiter.applyStereo(current, sampleRate, limiterSettings)
            log.add("Limiter: ceiling=${limiterSettings.ceilingDb}dB, release=${limiterSettings.releaseMs}ms")
        }

        // Step 5: Loudness normalization to target LUFS
        current = normalizeLoudness(current, sampleRate, settings.targetLufs, log)

        return ProcessingResult(processedSamples = current, processingLog = log)
    }

    /**
     * Apply 3-band EQ to interleaved stereo.
     */
    private fun applyEQ(
        input: FloatArray,
        sampleRate: Int,
        settings: SuggestionEngine.MasteringSettings,
        log: MutableList<String>
    ): FloatArray {
        var current = input
        val sr = sampleRate.toDouble()

        // Low band: low shelf
        if (settings.eqLowGainDb != 0f) {
            val coeffs = Eq.lowShelf(sr, settings.eqLowFreq.toDouble(), settings.eqLowGainDb.toDouble(), 0.7071)
            val stateL = Eq.BiquadState()
            val stateR = Eq.BiquadState()
            current = Eq.applyBiquadStereo(current, coeffs, stateL, stateR)
            log.add("EQ Low: ${String.format("%.1f", settings.eqLowGainDb)}dB @ ${settings.eqLowFreq.toInt()}Hz (shelf)")
        }

        // Mid band: peaking (bell)
        if (settings.eqMidGainDb != 0f) {
            val coeffs = Eq.peakingEQ(sr, settings.eqMidFreq.toDouble(), settings.eqMidGainDb.toDouble(), settings.eqMidQ.toDouble())
            val stateL = Eq.BiquadState()
            val stateR = Eq.BiquadState()
            current = Eq.applyBiquadStereo(current, coeffs, stateL, stateR)
            log.add("EQ Mid: ${String.format("%.1f", settings.eqMidGainDb)}dB @ ${settings.eqMidFreq.toInt()}Hz Q=${String.format("%.1f", settings.eqMidQ)}")
        }

        // High band: high shelf
        if (settings.eqHighGainDb != 0f) {
            val coeffs = Eq.highShelf(sr, settings.eqHighFreq.toDouble(), settings.eqHighGainDb.toDouble(), 0.7071)
            val stateL = Eq.BiquadState()
            val stateR = Eq.BiquadState()
            current = Eq.applyBiquadStereo(current, coeffs, stateL, stateR)
            log.add("EQ High: ${String.format("%.1f", settings.eqHighGainDb)}dB @ ${settings.eqHighFreq.toInt()}Hz (shelf)")
        }

        return current
    }

    /**
     * Normalize loudness to target LUFS using gain adjustment.
     * Measures current loudness, computes needed gain, applies it.
     */
    private fun normalizeLoudness(
        input: FloatArray,
        sampleRate: Int,
        targetLufs: Float,
        log: MutableList<String>
    ): FloatArray {
        val measurement = LoudnessMeter.measureStereo(input, sampleRate)
        val currentLufs = measurement.integratedLufs
        val gainNeeded = targetLufs - currentLufs

        // Only normalize if gain needed is reasonable (< 12dB)
        if (kotlin.math.abs(gainNeeded) > 12.0) {
            log.add("Normalización: Omitida (diferencia > 12dB)")
            return input
        }

        if (kotlin.math.abs(gainNeeded) < 0.1) {
            log.add("Normalización: Ya en objetivo (${String.format("%.1f", currentLufs)} LUFS)")
            return input
        }

        val gainLinear = 10.0.pow(gainNeeded / 20.0)
        val output = FloatArray(input.size)
        for (i in input.indices) {
            output[i] = (input[i] * gainLinear).toFloat()
        }

        // Final true peak check and limiting if needed
        var truePeak = -100.0
        for (s in output) {
            val tp = 20.0 * kotlin.math.log10(kotlin.math.abs(s.toDouble()).coerceAtLeast(1e-10))
            if (tp > truePeak) truePeak = tp
        }

        if (truePeak > -1.0) {
            // Apply final safety limiter
            val ceiling = 10.0.pow(-1.0 / 20.0)
            for (i in output.indices) {
                if (kotlin.math.abs(output[i]) > ceiling) {
                    output[i] = if (output[i] > 0) ceiling.toFloat() else (-ceiling).toFloat()
                }
            }
            log.add("Safety limiter aplicado (TP excedía -1dBTP)")
        }

        log.add("Normalización: ${String.format("%.1f", gainNeeded)}dB → objetivo ${targetLufs} LUFS")
        return output
    }
}
