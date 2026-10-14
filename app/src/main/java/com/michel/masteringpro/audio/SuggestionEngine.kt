package com.michel.masteringpro.audio

import kotlin.math.abs

/**
 * Generates mastering suggestions based on AudioAnalyzer results.
 * Implements rule-based reasoning for EQ, compression, limiting, and gain.
 */
object SuggestionEngine {

    data class MasteringSettings(
        var gainDb: Float = 0f,
        var eqLowGainDb: Float = 0f,
        var eqLowFreq: Float = 100f,
        var eqMidGainDb: Float = 0f,
        var eqMidFreq: Float = 1000f,
        var eqMidQ: Float = 1.2f,
        var eqHighGainDb: Float = 0f,
        var eqHighFreq: Float = 12000f,
        var compEnabled: Boolean = false,
        var compThresholdDb: Float = -18f,
        var compRatio: Float = 1.5f,
        var compAttackMs: Float = 30f,
        var compReleaseMs: Float = 100f,
        var compMakeupDb: Float = 0f,
        var limiterEnabled: Boolean = false,
        var limiterCeilingDb: Float = -1.0f,
        var limiterReleaseMs: Float = 50f,
        var targetLufs: Float = -14f
    )

    /**
     * Generate suggested mastering settings based on analysis.
     */
    fun suggest(analysis: AudioAnalyzer.AnalysisResult): MasteringSettings {
        val settings = MasteringSettings()

        // 1. GAIN: Target -14 LUFS
        if (analysis.integratedLufs > -12.0) {
            // Too loud, reduce gain
            settings.gainDb = (-14.0 - analysis.integratedLufs).toFloat()
        } else if (analysis.integratedLufs < -18.0) {
            // Too quiet, increase gain
            settings.gainDb = (-14.0 - analysis.integratedLufs).toFloat()
        } else {
            // In acceptable range, minor adjustment
            settings.gainDb = (-14.0 - analysis.integratedLufs).toFloat()
        }

        // 2. LIMITER: If true peak too high or clipping detected
        if (analysis.truePeakDbtp > -1.0 || analysis.clippingPercent > 0.0) {
            settings.limiterEnabled = true
            settings.limiterCeilingDb = -1.0f
            settings.limiterReleaseMs = 50f
        }

        // 3. EQ - Low band (sub-bass/bass region)
        if (analysis.energyLow < -60.0) {
            // Lack of low end, boost
            settings.eqLowGainDb = 1.5f
            settings.eqLowFreq = 80f
        } else if (analysis.energyLow > analysis.energyMid + 6.0) {
            // Too much bass, cut
            settings.eqLowGainDb = -1.5f
            settings.eqLowFreq = 100f
        }

        // 4. EQ - Mid band (200-400Hz mud, 2-5kHz harshness)
        val midLowEnergy = getBandEnergyDb(analysis, 200.0, 400.0)
        val midHighEnergy = getBandEnergyDb(analysis, 2000.0, 5000.0)
        val overallMidEnergy = analysis.energyMid

        if (midLowEnergy > overallMidEnergy + 3.0) {
            // Mud in 200-400Hz
            settings.eqMidGainDb = -2.0f
            settings.eqMidFreq = 300f
            settings.eqMidQ = 1.2f
        } else if (midHighEnergy > overallMidEnergy + 3.0) {
            // Harshness in 2-5kHz
            settings.eqMidGainDb = -1.5f
            settings.eqMidFreq = 3500f
            settings.eqMidQ = 1.0f
        }

        // 5. EQ - High band (air > 12kHz)
        if (analysis.energyAir < analysis.energyMid - 12.0) {
            // Lack of air
            settings.eqHighGainDb = 1.5f
            settings.eqHighFreq = 12000f
        } else if (analysis.energyAir > analysis.energyMid + 3.0) {
            // Too much air/sibilance
            settings.eqHighGainDb = -1.0f
            settings.eqHighFreq = 10000f
        }

        // 6. COMPRESSOR: If dynamic range too high
        if (analysis.lra > 9.0) {
            settings.compEnabled = true
            settings.compThresholdDb = -18f
            settings.compRatio = 1.5f
            settings.compAttackMs = 30f
            settings.compReleaseMs = 100f
            // Makeup gain to compensate for compression
            settings.compMakeupDb = (analysis.lra / 4.0).toFloat().coerceIn(0f, 4f)
        } else if (analysis.dynamicRangeDb > 15.0) {
            settings.compEnabled = true
            settings.compThresholdDb = -15f
            settings.compRatio = 2.0f
            settings.compAttackMs = 20f
            settings.compReleaseMs = 80f
            settings.compMakeupDb = 2.0f
        }

        // 7. Phase warning
        if (analysis.correlation < 0.3) {
            // Can't fix phase with mastering, but note it
            // Reduce stereo width by making more mono-compatible
        }

        return settings
    }

    /**
     * Get average energy in a specific frequency band from spectrum analysis.
     */
    private fun getBandEnergyDb(analysis: AudioAnalyzer.AnalysisResult, lowFreq: Double, highFreq: Double): Double {
        var sum = 0.0
        var count = 0
        for (i in analysis.spectrumFreqs.indices) {
            if (analysis.spectrumFreqs[i] in lowFreq..highFreq) {
                sum += analysis.spectrumDb[i]
                count++
            }
        }
        return if (count > 0) sum / count else -120.0
    }
}
