package app.zircut.core

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.RadialGradient
import android.graphics.Shader

/**
 * Whole-video visual effects.
 * vignette / grain / bars / leak: pictures drawn over the video (same in preview and export).
 * blur .. shadows: custom GPU shader (core/GpuFx.kt), export only (blur also shows in the preview on Android 12+).
 * trKind / trMs: transition at every cut between two clips (core/GpuFx.kt).
 */
data class Fx(
    val vignette: Float = 0f,
    val grain: Float = 0f,
    /** cinema bars (black bands at top and bottom) */
    val bars: Float = 0f,
    /** warm light leak from the side */
    val leak: Float = 0f,
    val blur: Float = 0f,
    val sharpen: Float = 0f,
    /** RGB channel split (chromatic aberration) */
    val split: Float = 0f,
    val glitch: Float = 0f,
    val noise: Float = 0f,
    /** glow / bloom of bright areas */
    val glow: Float = 0f,
    /** -1..1 */
    val highlights: Float = 0f,
    /** -1..1 */
    val shadows: Float = 0f,
    /** [TrKind.id] */
    val trKind: String = "none",
    val trMs: Long = 400L,
    /** picture transform of video clips: rotation 0/90/180/270 (clockwise), zoom 1..4, pan -1..1 (of the room the zoom leaves) */
    val rot: Int = 0,
    val vzoom: Float = 1f,
    val vpanX: Float = 0f,
    val vpanY: Float = 0f,
    /** mirror of the picture (applied before the rotation, same order in preview and export) */
    val flipH: Boolean = false,
    val flipV: Boolean = false,
    /** free rotation of the whole frame in degrees (clockwise), -45..45; the picture is auto-zoomed so no empty corners show */
    val fine: Float = 0f,
    /** keyframes of zoom and pan of the video picture on the EDITED timeline (empty = the fixed vzoom / vpanX / vpanY) */
    val vkeys: List<VideoKey> = emptyList(),
    /** colour LUT (.cube) copied into the project folder (`lut_<time>.cube`, "" = none) and how strongly it is applied (0..1) */
    val lutFile: String = "",
    val lutAmt: Float = 1f
) {
    val hasLut: Boolean get() = lutFile.isNotEmpty() && lutAmt > 0.01f

    /** true when a keyframe makes the video picture less than fully visible */
    val hasFade: Boolean get() = vkeys.any { it.op < 0.999f }

    val hasFine: Boolean get() = kotlin.math.abs(fine) > 0.05f || vkeys.any { kotlin.math.abs(it.rot) > 0.05f }

    val hasTransform: Boolean get() = rot != 0 || vzoom > 1.001f || flipH || flipV || vkeys.isNotEmpty()

    /** true when no picture-overlay effect is on (the shader effects are handled separately) */
    val isNone: Boolean get() = vignette <= 0f && grain <= 0f && bars <= 0f && leak <= 0f

    val hasShader: Boolean
        get() = blur > 0f || sharpen > 0f || split > 0f || glitch > 0f || noise > 0f || glow > 0f || highlights != 0f || shadows != 0f
}

/** One effect picture: [bitmaps] shown in turn every [periodMs] (film grain moves), drawn with [alpha]. */
class FxLayer(val bitmaps: List<Bitmap>, val periodMs: Long, val alpha: Float) {
    fun at(ms: Long): Bitmap =
        if (bitmaps.size == 1 || periodMs <= 0L) bitmaps[0] else bitmaps[((ms / periodMs) % bitmaps.size).toInt()]
}

/** Zoom that keeps a frame of [w] x [h] fully covered when the picture is turned by [deg] degrees (worst case: picture exactly as big as the frame). */
fun fineCover(deg: Float, w: Float, h: Float): Float {
    val a = Math.toRadians(Math.abs(deg).toDouble())
    val r = if (w >= h) w / h else h / w
    return (Math.cos(a) + Math.sin(a) * r).toFloat()
}

object FxRender {
    /** Builds the pictures for [fx] at a frame of [w] x [h] pixels (they are stretched over the whole frame). */
    fun build(fx: Fx, w: Int, h: Int): List<FxLayer> {
        val out = ArrayList<FxLayer>()
        if (fx.isNone || w < 8 || h < 8) return out
        if (fx.leak > 0f) out.add(FxLayer(listOf(leak(w, h)), 0L, 0.75f * fx.leak))
        if (fx.vignette > 0f) out.add(FxLayer(listOf(vignette(w, h)), 0L, fx.vignette))
        if (fx.grain > 0f) {
            val gw = minOf(w, 540)
            val gh = maxOf(8, (gw.toLong() * h / w).toInt())
            out.add(FxLayer(grainFrames(gw, gh), 70L, 0.5f * fx.grain))
        }
        if (fx.bars > 0f) out.add(FxLayer(listOf(bars(w, h, 0.14f * fx.bars)), 0L, 1f))
        return out
    }

    private var grainKey = 0L
    private var grainCache: List<Bitmap> = emptyList()

    /** The moving grain does not depend on its strength, so the 4 pictures are made once per frame size (not on every slider step). */
    @Synchronized
    private fun grainFrames(w: Int, h: Int): List<Bitmap> {
        val key = w.toLong() * 100000L + h
        if (key != grainKey || grainCache.isEmpty()) {
            grainCache = List(4) { grain(w, h, 1234L + it * 77L) }
            grainKey = key
        }
        return grainCache
    }

    private fun vignette(w: Int, h: Int): Bitmap {
        val bw = minOf(w, 540)
        val bh = maxOf(8, (bw.toLong() * h / w).toInt())
        val b = Bitmap.createBitmap(bw, bh, Bitmap.Config.ARGB_8888)
        val r = Math.hypot(bw / 2.0, bh / 2.0).toFloat()
        val p = Paint(Paint.ANTI_ALIAS_FLAG)
        p.shader = RadialGradient(
            bw / 2f, bh / 2f, r, intArrayOf(Color.TRANSPARENT, Color.TRANSPARENT, Color.argb(235, 0, 0, 0)),
            floatArrayOf(0f, 0.45f, 1f), Shader.TileMode.CLAMP
        )
        Canvas(b).drawRect(0f, 0f, bw.toFloat(), bh.toFloat(), p)
        return b
    }

    private fun leak(w: Int, h: Int): Bitmap {
        val bw = minOf(w, 540)
        val bh = maxOf(8, (bw.toLong() * h / w).toInt())
        val b = Bitmap.createBitmap(bw, bh, Bitmap.Config.ARGB_8888)
        val p = Paint(Paint.ANTI_ALIAS_FLAG)
        p.shader = LinearGradient(
            bw.toFloat(), 0f, bw * 0.3f, bh * 0.4f,
            intArrayOf(Color.argb(255, 255, 120, 40), Color.argb(150, 255, 70, 110), Color.TRANSPARENT),
            floatArrayOf(0f, 0.4f, 1f), Shader.TileMode.CLAMP
        )
        Canvas(b).drawRect(0f, 0f, bw.toFloat(), bh.toFloat(), p)
        return b
    }

    /** black / white speckles: stronger deviation from mid-grey = more opaque, so the picture's average brightness stays put */
    private fun grain(w: Int, h: Int, seed: Long): Bitmap {
        val rnd = java.util.Random(seed)
        val px = IntArray(w * h)
        for (i in px.indices) {
            val g = rnd.nextInt(256)
            val a = (Math.abs(g - 128) * 2).coerceIn(0, 255)
            px[i] = Color.argb(a, if (g > 128) 255 else 0, if (g > 128) 255 else 0, if (g > 128) 255 else 0)
        }
        val b = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        b.setPixels(px, 0, w, 0, 0, w, h)
        return b
    }

    private fun bars(w: Int, h: Int, frac: Float): Bitmap {
        val bw = minOf(w, 540)
        val bh = maxOf(8, (bw.toLong() * h / w).toInt())
        val b = Bitmap.createBitmap(bw, bh, Bitmap.Config.ARGB_8888)
        val c = Canvas(b)
        val p = Paint()
        p.color = Color.BLACK
        val bar = bh * frac
        c.drawRect(0f, 0f, bw.toFloat(), bar, p)
        c.drawRect(0f, bh - bar, bw.toFloat(), bh.toFloat(), p)
        return b
    }
}

/**
 * One keyframe of the video picture: [tMs] on the edited timeline, zoom 1..4, pan -1..1 (of the room the zoom leaves),
 * [rot] = the free-angle turn in degrees (-45..45, the same turn the «چرخش آزاد» slider gives; fix230).
 */
data class VideoKey(val tMs: Long, val zoom: Float, val panX: Float, val panY: Float, val rot: Float = 0f, val op: Float = 1f,
    /** how the picture travels from THIS key to the next: 0 smooth, 1 linear, 2 hold (fix232) */
    val ease: Int = 0
)

/** [op] = opacity of the video picture 0..1 (fade towards black, fix231) */
class VideoPose(val zoom: Float, val panX: Float, val panY: Float, val rot: Float = 0f, val op: Float = 1f)

/** Keyframe maths of the video picture, shared by the preview, the exporter and the editor (smooth ease in / out between keys). */
object VideoKeys {
    const val MAX_KEYS = 40
    const val NEAR_MS = 60L
    const val EASE_SMOOTH = 0
    const val EASE_LINEAR = 1
    const val EASE_HOLD = 2

    fun pose(fx: Fx, ms: Long): VideoPose {
        val k = fx.vkeys
        if (k.isEmpty()) return VideoPose(fx.vzoom, fx.vpanX, fx.vpanY, fx.fine, 1f)
        if (ms <= k.first().tMs) return k.first().let { VideoPose(it.zoom, it.panX, it.panY, it.rot, it.op) }
        if (ms >= k.last().tMs) return k.last().let { VideoPose(it.zoom, it.panX, it.panY, it.rot, it.op) }
        var i = 0
        while (i < k.size - 2 && k[i + 1].tMs <= ms) i++
        val a = k[i]
        val b = k[i + 1]
        val u = ((ms - a.tMs) / (b.tMs - a.tMs).coerceAtLeast(1L).toFloat()).coerceIn(0f, 1f)
        val e = when (a.ease) {
            EASE_LINEAR -> u
            EASE_HOLD -> 0f
            else -> u * u * (3f - 2f * u)
        }
        fun mix(p: Float, q: Float) = p + (q - p) * e
        return VideoPose(mix(a.zoom, b.zoom), mix(a.panX, b.panX), mix(a.panY, b.panY), mix(a.rot, b.rot), mix(a.op, b.op))
    }

    /** puts a keyframe with this look at [ms]; a keyframe already there is replaced; unchanged when there would be too many */
    fun upsert(fx: Fx, ms: Long, p: VideoPose): Fx {
        val kept = fx.vkeys.filter { kotlin.math.abs(it.tMs - ms) > NEAR_MS }
        if (kept.size >= MAX_KEYS) return fx
        // a replaced key keeps its way to the next key
        val ease = fx.vkeys.firstOrNull { kotlin.math.abs(it.tMs - ms) <= NEAR_MS }?.ease ?: 0
        val n = (kept + VideoKey(ms.coerceAtLeast(0L), p.zoom.coerceIn(1f, 4f), p.panX.coerceIn(-1f, 1f), p.panY.coerceIn(-1f, 1f), p.rot.coerceIn(-45f, 45f), p.op.coerceIn(0f, 1f), ease)).sortedBy { it.tMs }
        return fx.copy(vkeys = n)
    }

    fun nearest(fx: Fx, ms: Long): VideoKey? =
        fx.vkeys.minByOrNull { kotlin.math.abs(it.tMs - ms) }?.takeIf { kotlin.math.abs(it.tMs - ms) <= 100L }

    fun remove(fx: Fx, ms: Long): Fx {
        val k = nearest(fx, ms) ?: return fx
        return fx.copy(vkeys = fx.vkeys.filter { it !== k })
    }

    /** sets the way from the key at the playhead to the next one (EASE_*); unchanged when there is no key here */
    fun setEase(fx: Fx, ms: Long, ease: Int): Fx {
        val k = nearest(fx, ms) ?: return fx
        return fx.copy(vkeys = fx.vkeys.map { if (it === k) it.copy(ease = ease.coerceIn(EASE_SMOOTH, EASE_HOLD)) else it })
    }

    /**
     * Moves the key at the playhead by [deltaMs] inside 0..[total]. Unchanged when there is no key here, nothing would move,
     * or it would land within NEAR_MS of another key.
     */
    fun nudge(fx: Fx, ms: Long, deltaMs: Long, total: Long): Fx {
        val k = nearest(fx, ms) ?: return fx
        val t = (k.tMs + deltaMs).coerceIn(0L, maxOf(0L, total))
        if (t == k.tMs) return fx
        if (fx.vkeys.any { it !== k && kotlin.math.abs(it.tMs - t) <= NEAR_MS }) return fx
        return fx.copy(vkeys = fx.vkeys.map { if (it === k) it.copy(tMs = t) else it }.sortedBy { it.tMs })
    }

    fun previous(fx: Fx, ms: Long): VideoKey? = fx.vkeys.lastOrNull { it.tMs < ms - NEAR_MS }
    fun next(fx: Fx, ms: Long): VideoKey? = fx.vkeys.firstOrNull { it.tMs > ms + NEAR_MS }

    /**
     * Centre (fractions of the rotated picture, from the top-left) of the window the frame shows for this zoom / pan.
     * The same numbers the static crop in the exporter uses. [vw] x [vh] = shape of the rotated picture, [outW] x [outH] = frame.
     */
    fun centre(zoom: Float, panX: Float, panY: Float, vw: Int, vh: Int, outW: Int, outH: Int): FloatArray {
        val va = vw.toFloat() / vh
        val fa = outW.toFloat() / outH
        val visW = if (va >= fa) fa / va else 1f
        val visH = if (va >= fa) 1f else va / fa
        val z = zoom.coerceIn(1f, 4f)
        val wf = visW / z
        val hf = visH / z
        val tx = panX.coerceIn(-1f, 1f) * (z - 1f) / 2f
        val ty = panY.coerceIn(-1f, 1f) * (z - 1f) / 2f
        return floatArrayOf(0.5f - tx * wf, 0.5f - ty * hf)
    }
}

