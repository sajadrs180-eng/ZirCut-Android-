package app.zircut.core

import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

/** Small audio helpers (16 kHz mono floats). */
object Dsp {
    private const val SR = 16000.0

    private class Biquad(val b0: Float, val b1: Float, val b2: Float, val a1: Float, val a2: Float) {
        var z1 = 0f
        var z2 = 0f
        fun p(x: Float): Float {
            val y = b0 * x + z1
            z1 = b1 * x - a1 * y + z2
            z2 = b2 * x - a2 * y
            return y
        }
    }

    private fun lowpass(fc: Double, q: Double = 0.707): Biquad {
        val w = 2 * PI * fc / SR
        val cw = cos(w)
        val al = sin(w) / (2 * q)
        val a0 = 1 + al
        return Biquad(((1 - cw) / 2 / a0).toFloat(), ((1 - cw) / a0).toFloat(), ((1 - cw) / 2 / a0).toFloat(), (-2 * cw / a0).toFloat(), ((1 - al) / a0).toFloat())
    }

    private fun highpass(fc: Double, q: Double = 0.707): Biquad {
        val w = 2 * PI * fc / SR
        val cw = cos(w)
        val al = sin(w) / (2 * q)
        val a0 = 1 + al
        return Biquad(((1 + cw) / 2 / a0).toFloat(), (-(1 + cw) / a0).toFloat(), ((1 + cw) / 2 / a0).toFloat(), (-2 * cw / a0).toFloat(), ((1 - al) / a0).toFloat())
    }

    /** Keeps the band where the human voice lives (about 150 Hz to 3.8 kHz) and brings it to a good level; this helps when music plays under the speech. */
    fun speechBand(x: FloatArray): FloatArray {
        val hp1 = highpass(150.0)
        val hp2 = highpass(150.0)
        val lp1 = lowpass(3800.0)
        val lp2 = lowpass(3800.0)
        val y = FloatArray(x.size)
        var peak = 0f
        for (i in x.indices) {
            val v = lp2.p(lp1.p(hp2.p(hp1.p(x[i]))))
            y[i] = v
            if (abs(v) > peak) peak = abs(v)
        }
        if (peak > 1e-4f) {
            val g = minOf(8f, 0.9f / peak)
            for (i in y.indices) y[i] = (y[i] * g).coerceIn(-1f, 1f)
        }
        return y
    }

    fun rms(x: FloatArray): Float {
        if (x.isEmpty()) return 0f
        var s = 0.0
        for (v in x) s += v * v
        return sqrt(s / x.size).toFloat()
    }
}
