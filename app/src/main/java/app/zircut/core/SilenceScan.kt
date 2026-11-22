package app.zircut.core

import kotlin.math.log10
import kotlin.math.sqrt

/**
 * Finds where the video has sound by measuring loudness (no model, no network).
 * The result is a list of [Word]s with empty text: only their start/end are used by [Planner.ranges], which joins
 * sounds that are close together and cuts the pauses in between.
 */
object SilenceScan {
    private const val FRAME = 320   // 20 ms at 16 kHz
    private const val FRAME_MS = 20L
    private const val HANG = 8      // keep 160 ms of tail so word endings are not clipped
    private const val MIN_FRAMES = 4 // shorter than 80 ms = a click, not sound

    fun spans(audio: FloatArray): List<Word> {
        val n = audio.size / FRAME
        if (n < 10) return emptyList()
        val db = FloatArray(n)
        for (i in 0 until n) {
            var sum = 0.0
            val o = i * FRAME
            for (k in 0 until FRAME) {
                val v = audio[o + k]
                sum += v * v
            }
            db[i] = (20.0 * log10(sqrt(sum / FRAME) + 1e-7)).toFloat()
        }
        val sorted = db.copyOf()
        sorted.sort()
        val floor = sorted[(n * 0.10).toInt().coerceIn(0, n - 1)]
        val peak = sorted[(n * 0.95).toInt().coerceIn(0, n - 1)]
        // flat sound (no clear quiet parts): nothing can be cut
        if (peak - floor < 6f) return listOf(Word("", 0L, n * FRAME_MS))
        val thr = (floor + (peak - floor) * 0.30f).coerceAtLeast(floor + 6f).coerceAtMost(floor + 18f)
        val out = ArrayList<Word>()
        var start = -1
        var last = -1
        for (i in 0 until n) {
            if (db[i] >= thr) {
                if (start < 0) start = i
                last = i
            } else if (start >= 0 && i - last > HANG) {
                add(out, start, last)
                start = -1
            }
        }
        if (start >= 0) add(out, start, last)
        return out
    }

    private fun add(out: ArrayList<Word>, s: Int, e: Int) {
        if (e - s + 1 < MIN_FRAMES) return
        out.add(Word("", s * FRAME_MS, (e + 1) * FRAME_MS))
    }
}
