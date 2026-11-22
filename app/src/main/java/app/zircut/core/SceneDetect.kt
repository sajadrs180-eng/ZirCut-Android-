package app.zircut.core

import android.content.Context
import android.graphics.Bitmap
import android.media.MediaMetadataRetriever
import android.net.Uri
import kotlinx.coroutines.CancellationException

/**
 * Real scene-change detection: small frames are taken every [stepMs], their colour histograms are compared,
 * and a jump is refined by bisection. No model is involved; it finds hard cuts, not slow dissolves.
 */
object SceneDetect {
    private const val SIZE = 32
    private const val THRESHOLD = 0.30f

    private fun hist(b: Bitmap): FloatArray {
        val h = FloatArray(24)
        val px = IntArray(b.width * b.height)
        b.getPixels(px, 0, b.width, 0, 0, b.width, b.height)
        for (c in px) {
            h[((c shr 16) and 0xFF) / 32]++
            h[8 + ((c shr 8) and 0xFF) / 32]++
            h[16 + (c and 0xFF) / 32]++
        }
        val n = px.size.toFloat().coerceAtLeast(1f)
        for (i in h.indices) h[i] /= n
        return h
    }

    private fun dist(a: FloatArray, b: FloatArray): Float {
        var s = 0f
        for (i in a.indices) s += Math.abs(a[i] - b[i])
        return s / 6f
    }

    private fun frame(r: MediaMetadataRetriever, ms: Long, exact: Boolean): FloatArray? {
        val opt = if (exact) MediaMetadataRetriever.OPTION_CLOSEST else MediaMetadataRetriever.OPTION_CLOSEST_SYNC
        val b = r.getScaledFrameAtTime(ms * 1000L, opt, SIZE, SIZE) ?: return null
        val h = hist(b)
        b.recycle()
        return h
    }

    /** Cut times in ms inside the video; blocking, call off the main thread. [onProgress] gets 0..1. */
    fun detect(ctx: Context, uri: Uri, durationMs: Long, alive: () -> Boolean = { true }, onProgress: (Float) -> Unit = {}): List<Long> {
        // short videos are scanned with exact frames (slower, precise); long ones with the nearest key frames
        val exact = durationMs <= 180_000L
        val stepMs = if (exact) 700L else 1000L
        val r = MediaMetadataRetriever()
        val cuts = ArrayList<Long>()
        try {
            r.setDataSource(ctx, uri)
            var prevMs = 0L
            var prev = frame(r, 0L, exact) ?: return emptyList()
            var t = stepMs
            while (t < durationMs) {
                if (!alive()) throw CancellationException()
                val cur = frame(r, t, exact)
                if (cur != null) {
                    if (dist(prev, cur) > THRESHOLD) {
                        var lo = prevMs
                        var hi = t
                        val ref = prev
                        for (k in 0 until 4) {
                            val mid = (lo + hi) / 2
                            val m = frame(r, mid, true)
                            if (m != null && dist(ref, m) > THRESHOLD) hi = mid else lo = mid
                        }
                        if (cuts.isEmpty() || hi - cuts.last() >= 1500L) cuts.add(hi)
                    }
                    prev = cur
                    prevMs = t
                }
                onProgress(t.toFloat() / durationMs)
                t += stepMs
            }
        } finally {
            try { r.release() } catch (_: Exception) { }
        }
        return cuts
    }
}
