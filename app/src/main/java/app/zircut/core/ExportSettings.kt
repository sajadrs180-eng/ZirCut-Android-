package app.zircut.core

import kotlin.math.min

/**
 * What the user chose for the export (part 7). 0 means "automatic": the behaviour the app had before part 7.
 * [shortEdge] is the length of the shorter side in pixels (720, 1080, 1440, 2160).
 */
data class ExportSettings(
    val shortEdge: Int = 0,
    val fps: Int = 0,
    val hevc: Boolean = false,
    /** video bitrate in Mbit/s, 0 = automatic */
    val videoMbps: Int = 0
)

object ExportPlan {
    /** The audio encoder of Media3 1.4.1 has a fixed rate (128 kbit/s) that cannot be changed from outside. */
    const val AUDIO_BPS = 128 * 1024

    /** Output frame size (width, height), always even. [level] 2 is the emergency fallback (2/3 of the size). */
    fun size(st: ExportSettings, aspect: Float, srcW: Int, srcH: Int, level: Int = 0): Pair<Int, Int> {
        var outW = srcW
        var outH = srcH
        if (aspect > 0f) {
            val c = Frame.crop(aspect, srcW, srcH)
            outW = c.first
            outH = c.second
        }
        outW = maxOf(2, outW)
        outH = maxOf(2, outH)
        // portrait and square frames are sized by their width, landscape ones by their height
        val portrait = if (aspect > 0f) aspect <= 1f else outW <= outH
        val baseW: Int
        val baseH: Int
        if (st.shortEdge > 0) {
            val s = st.shortEdge and 1.inv()
            if (aspect > 0f) {
                if (portrait) {
                    baseW = s
                    baseH = Math.round(s / aspect) and 1.inv()
                } else {
                    baseH = s
                    baseW = Math.round(s * aspect) and 1.inv()
                }
            } else if (outW <= outH) {
                baseW = s
                baseH = (outH.toLong() * s / outW).toInt() and 1.inv()
            } else {
                baseH = s
                baseW = (outW.toLong() * s / outH).toInt() and 1.inv()
            }
        } else if (aspect > 0f) {
            // automatic: short side 1080 (9:16 stays 1080x1920 like before)
            if (portrait) {
                baseW = 1080
                baseH = Math.round(1080f / aspect) and 1.inv()
            } else {
                baseH = 1080
                baseW = Math.round(1080f * aspect) and 1.inv()
            }
        } else {
            // automatic: full HD or better, as before
            val autoH = (maxOf(1080, min(outH, 1920)) and 1.inv())
            baseH = autoH
            baseW = ((outW.toLong() * autoH / outH).toInt() and 1.inv())
        }
        if (level >= 2) return ((baseW * 2 / 3) and 1.inv()) to ((baseH * 2 / 3) and 1.inv())
        return baseW to baseH
    }

    /** Bitrate used when the user left it on automatic (the values the app used before part 7, extended for bigger sizes). */
    fun autoBitrate(w: Int, h: Int, hevc: Boolean): Int {
        val px = w.toLong() * h
        val base = when {
            px < 1_200_000L -> 6_000_000
            px < 3_000_000L -> if (h >= 1500) 12_000_000 else 10_000_000
            px < 7_000_000L -> 20_000_000
            else -> 35_000_000
        }
        return if (hevc) base * 7 / 10 else base
    }

    fun videoBitrate(st: ExportSettings, w: Int, h: Int, hevc: Boolean): Int =
        if (st.videoMbps > 0) st.videoMbps * 1_000_000 else autoBitrate(w, h, hevc)

    fun estimateBytes(videoBps: Int, durationMs: Long): Long =
        ((videoBps + AUDIO_BPS).toDouble() * (durationMs / 1000.0) / 8.0).toLong()
}
