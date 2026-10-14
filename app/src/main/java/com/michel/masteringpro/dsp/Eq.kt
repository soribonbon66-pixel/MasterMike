package com.michel.masteringpro.dsp

/**
 * Biquad filter implementation based on Audio EQ Cookbook by Robert Bristow-Johnson.
 * https://webaudio.github.io/Audio-EQ-Cookbook/audio-eq-cookbook.html
 *
 * Implements peaking EQ, low shelf, high shelf, high pass, and low pass filters.
 */
object Eq {

    data class BiquadCoeffs(
        val b0: Double, val b1: Double, val b2: Double,
        val a0: Double, val a1: Double, val a2: Double
    )

    data class BiquadState(
        var x1: Double = 0.0, var x2: Double = 0.0,
        var y1: Double = 0.0, var y2: Double = 0.0
    )

    /** Compute biquad coefficients for a peaking (bell) EQ filter */
    fun peakingEQ(sampleRate: Double, frequency: Double, gainDb: Double, q: Double): BiquadCoeffs {
        val a = Math.pow(10.0, gainDb / 40.0)
        val w0 = 2.0 * Math.PI * frequency / sampleRate
        val alpha = Math.sin(w0) / (2.0 * q)

        val b0 = 1.0 + alpha * a
        val b1 = -2.0 * Math.cos(w0)
        val b2 = 1.0 - alpha * a
        val a0 = 1.0 + alpha / a
        val a1 = -2.0 * Math.cos(w0)
        val a2 = 1.0 - alpha / a

        return BiquadCoeffs(b0, b1, b2, a0, a1, a2)
    }

    /** Compute biquad coefficients for a low shelf filter */
    fun lowShelf(sampleRate: Double, frequency: Double, gainDb: Double, q: Double): BiquadCoeffs {
        val a = Math.pow(10.0, gainDb / 40.0)
        val w0 = 2.0 * Math.PI * frequency / sampleRate
        val alpha = Math.sin(w0) / (2.0 * q)
        val cosW0 = Math.cos(w0)
        val twoSqrtAAlpha = 2.0 * Math.sqrt(a) * alpha

        val b0 = a * ((a + 1) - (a - 1) * cosW0 + twoSqrtAAlpha)
        val b1 = 2.0 * a * ((a - 1) - (a + 1) * cosW0)
        val b2 = a * ((a + 1) - (a - 1) * cosW0 - twoSqrtAAlpha)
        val a0 = (a + 1) + (a - 1) * cosW0 + twoSqrtAAlpha
        val a1 = -2.0 * ((a - 1) + (a + 1) * cosW0)
        val a2 = (a + 1) + (a - 1) * cosW0 - twoSqrtAAlpha

        return BiquadCoeffs(b0, b1, b2, a0, a1, a2)
    }

    /** Compute biquad coefficients for a high shelf filter */
    fun highShelf(sampleRate: Double, frequency: Double, gainDb: Double, q: Double): BiquadCoeffs {
        val a = Math.pow(10.0, gainDb / 40.0)
        val w0 = 2.0 * Math.PI * frequency / sampleRate
        val alpha = Math.sin(w0) / (2.0 * q)
        val cosW0 = Math.cos(w0)
        val twoSqrtAAlpha = 2.0 * Math.sqrt(a) * alpha

        val b0 = a * ((a + 1) + (a - 1) * cosW0 + twoSqrtAAlpha)
        val b1 = -2.0 * a * ((a - 1) + (a + 1) * cosW0)
        val b2 = a * ((a + 1) + (a - 1) * cosW0 - twoSqrtAAlpha)
        val a0 = (a + 1) - (a - 1) * cosW0 + twoSqrtAAlpha
        val a1 = 2.0 * ((a - 1) - (a + 1) * cosW0)
        val a2 = (a + 1) - (a - 1) * cosW0 - twoSqrtAAlpha

        return BiquadCoeffs(b0, b1, b2, a0, a1, a2)
    }

    /** Compute biquad coefficients for a high-pass filter */
    fun highPass(sampleRate: Double, frequency: Double, q: Double): BiquadCoeffs {
        val w0 = 2.0 * Math.PI * frequency / sampleRate
        val alpha = Math.sin(w0) / (2.0 * q)
        val cosW0 = Math.cos(w0)

        val b0 = (1.0 + cosW0) / 2.0
        val b1 = -(1.0 + cosW0)
        val b2 = (1.0 + cosW0) / 2.0
        val a0 = 1.0 + alpha
        val a1 = -2.0 * cosW0
        val a2 = 1.0 - alpha

        return BiquadCoeffs(b0, b1, b2, a0, a1, a2)
    }

    /** Apply a biquad filter to a FloatArray (in-place or return new) */
    fun applyBiquad(input: FloatArray, coeffs: BiquadCoeffs, state: BiquadState = BiquadState()): FloatArray {
        val output = FloatArray(input.size)
        val nb0 = coeffs.b0 / coeffs.a0
        val nb1 = coeffs.b1 / coeffs.a0
        val nb2 = coeffs.b2 / coeffs.a0
        val na1 = coeffs.a1 / coeffs.a0
        val na2 = coeffs.a2 / coeffs.a0

        for (i in input.indices) {
            val x0 = input[i].toDouble()
            val y0 = nb0 * x0 + nb1 * state.x1 + nb2 * state.x2 - na1 * state.y1 - na2 * state.y2
            output[i] = y0.toFloat()
            state.x2 = state.x1
            state.x1 = x0
            state.y2 = state.y1
            state.y1 = y0
        }
        return output
    }

    /** Apply biquad filter to interleaved stereo PCM (every 2 samples = L, R) */
    fun applyBiquadStereo(input: FloatArray, coeffs: BiquadCoeffs, stateL: BiquadState = BiquadState(), stateR: BiquadState = BiquadState()): FloatArray {
        val output = FloatArray(input.size)
        val nb0 = coeffs.b0 / coeffs.a0
        val nb1 = coeffs.b1 / coeffs.a0
        val nb2 = coeffs.b2 / coeffs.a0
        val na1 = coeffs.a1 / coeffs.a0
        val na2 = coeffs.a2 / coeffs.a0

        var i = 0
        while (i < input.size - 1) {
            val xL = input[i].toDouble()
            val yL = nb0 * xL + nb1 * stateL.x1 + nb2 * stateL.x2 - na1 * stateL.y1 - na2 * stateL.y2
            output[i] = yL.toFloat()
            stateL.x2 = stateL.x1; stateL.x1 = xL; stateL.y2 = stateL.y1; stateL.y1 = yL

            val xR = input[i + 1].toDouble()
            val yR = nb0 * xR + nb1 * stateR.x1 + nb2 * stateR.x2 - na1 * stateR.y1 - na2 * stateR.y2
            output[i + 1] = yR.toFloat()
            stateR.x2 = stateR.x1; stateR.x1 = xR; stateR.y2 = stateR.y1; stateR.y1 = yR

            i += 2
        }
        return output
    }
}
