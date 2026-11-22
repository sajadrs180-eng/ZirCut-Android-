package app.zircut.core

import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.log10
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Gains of the five-band equaliser in dB (fix233): bass shelf 120 Hz, low-mid bell 350 Hz, mid bell 1 kHz, high-mid bell 3.5 kHz, treble shelf 6 kHz.
 * Every gain is -12..+12. Pure maths (no Android classes), so it can be tested on the JVM.
 */
data class EqBands(
    val bass: Float = 0f,
    val lowMid: Float = 0f,
    val mid: Float = 0f,
    val highMid: Float = 0f,
    val treble: Float = 0f
) {
    val isFlat: Boolean get() = listOf(bass, lowMid, mid, highMid, treble).all { kotlin.math.abs(it) < 0.05f }

    fun coerced(): EqBands = EqBands(bass.c(), lowMid.c(), mid.c(), highMid.c(), treble.c())
    private fun Float.c() = coerceIn(-12f, 12f)
}

object EqMath {
    /** One band: filter type (0 low shelf, 1 bell, 2 high shelf), centre frequency, gain, Q. */
    class Spec(val type: Int, val f0: Double, val gainDb: Double, val q: Double)

    /** Direct form II transposed biquad. */
    class Bq(val b0: Float, val b1: Float, val b2: Float, val a1: Float, val a2: Float) {
        var z1 = 0f
        var z2 = 0f
        fun p(x: Float): Float {
            val y = b0 * x + z1
            z1 = b1 * x - a1 * y + z2
            z2 = b2 * x - a2 * y
            return y
        }
    }

    /** The bands that really change the sound: a band with (almost) no gain is left out, so a flat setting is an exact bypass. */
    fun specs(b: EqBands): List<Spec> {
        val c = b.coerced()
        val all = listOf(
            Spec(0, 120.0, c.bass.toDouble(), 0.8),
            Spec(1, 350.0, c.lowMid.toDouble(), 1.0),
            Spec(1, 1000.0, c.mid.toDouble(), 0.8),
            Spec(1, 3500.0, c.highMid.toDouble(), 1.0),
            Spec(2, 6000.0, c.treble.toDouble(), 0.8)
        )
        return all.filter { kotlin.math.abs(it.gainDb) >= 0.05 }
    }

    /** RBJ cookbook coefficients. */
    fun make(s: Spec, sr: Double): Bq {
        val a = 10.0.pow(s.gainDb / 40.0)
        val w = 2 * PI * s.f0 / sr
        val cw = cos(w)
        val sw = sin(w)
        val al = sw / (2 * s.q)
        val b0: Double; val b1: Double; val b2: Double; val a0: Double; val a1: Double; val a2: Double
        when (s.type) {
            0 -> {
                val t = 2 * sqrt(a) * al
                b0 = a * ((a + 1) - (a - 1) * cw + t); b1 = 2 * a * ((a - 1) - (a + 1) * cw); b2 = a * ((a + 1) - (a - 1) * cw - t)
                a0 = (a + 1) + (a - 1) * cw + t; a1 = -2 * ((a - 1) + (a + 1) * cw); a2 = (a + 1) + (a - 1) * cw - t
            }
            2 -> {
                val t = 2 * sqrt(a) * al
                b0 = a * ((a + 1) + (a - 1) * cw + t); b1 = -2 * a * ((a - 1) + (a + 1) * cw); b2 = a * ((a + 1) + (a - 1) * cw - t)
                a0 = (a + 1) - (a - 1) * cw + t; a1 = 2 * ((a - 1) - (a + 1) * cw); a2 = (a + 1) - (a - 1) * cw - t
            }
            else -> {
                b0 = 1 + al * a; b1 = -2 * cw; b2 = 1 - al * a
                a0 = 1 + al / a; a1 = -2 * cw; a2 = 1 - al / a
            }
        }
        return Bq((b0 / a0).toFloat(), (b1 / a0).toFloat(), (b2 / a0).toFloat(), (a1 / a0).toFloat(), (a2 / a0).toFloat())
    }

    /** Combined magnitude response in dB at [freq] Hz (used by tests and could drive a curve drawing). */
    fun responseDb(b: EqBands, freq: Double, sr: Double): Double {
        var db = 0.0
        val w = 2 * PI * freq / sr
        for (s in specs(b)) {
            val q = make(s, sr)
            // |H(e^jw)| with the transposed coefficients
            val c1 = cos(w); val s1 = sin(w); val c2 = cos(2 * w); val s2 = sin(2 * w)
            val nr = q.b0 + q.b1 * c1 + q.b2 * c2
            val ni = -(q.b1 * s1 + q.b2 * s2)
            val dr = 1.0 + q.a1 * c1 + q.a2 * c2
            val di = -(q.a1 * s1 + q.a2 * s2)
            val mag = sqrt((nr * nr + ni * ni) / (dr * dr + di * di))
            db += 20 * log10(mag)
        }
        return db
    }
}
