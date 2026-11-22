package app.zircut.timeline

/**
 * A text or picture laid over the video (part 4). Times are positions on the EDITED timeline
 * (the same time line as the playhead), x/y are the layer centre as a fraction of the output frame,
 * rotation is in degrees clockwise. Layers are drawn in list order (later = on top).
 */
data class Layer(
    val id: Int,
    val kind: Int,
    val startMs: Long,
    val endMs: Long,
    val x: Float = 0.5f,
    val y: Float = 0.5f,
    val scale: Float = 1f,
    val rotation: Float = 0f,
    val opacity: Float = 1f,
    /** text layers */
    val text: String = "",
    val color: Int = 0xFFFFFFFF.toInt(),
    /** 0 = no background box */
    val bgColor: Int = 0,
    val stroke: Boolean = true,
    /** "" = the project's subtitle font */
    val fontId: String = "",
    /** picture layers: file inside the project folder */
    val file: String = "",
    val imgW: Int = 0,
    val imgH: Int = 0,
    /** text layers: faux bold / italic, alignment (0 centre, 1 left, 2 right), letter spacing (fraction of the text size, ignored for Persian/Arabic script), line height factor, outline colour */
    val bold: Boolean = false,
    val italic: Boolean = false,
    val align: Int = 0,
    val letterSpacing: Float = 0f,
    val lineSpacing: Float = 1.2f,
    val outlineColor: Int = 0xFF000000.toInt(),
    /** keyframes of position, size and opacity (time is relative to startMs); empty = the layer simply keeps x/y/scale/opacity */
    val keys: List<Keyframe> = emptyList(),
    /** entrance / exit animation: 0 none, 1 fade, 2 from bottom, 3 from top, 4 from left, 5 from right, 6 zoom; animMs = length of each */
    val animIn: Int = 0,
    val animOut: Int = 0,
    val animMs: Long = 400L,
    /** locked: cannot be moved, edited, split, moved in order or deleted until unlocked */
    val locked: Boolean = false,
    /** hidden: kept on the timeline but not drawn in the preview or the exported video */
    val hidden: Boolean = false,
    /** picture layers: colour key (green screen). 0 off, 1 green, 2 blue, 3 white, 4 black; [keyTol] 0..1 = how much counts as the key colour */
    val keyMode: Int = 0,
    val keyTol: Float = 0.4f,
    /** text layers: shadow. 0 = automatic (the old look: a small shadow only when there is no outline and no box), 1 none, 2 soft, 3 strong, 4 glow (in the text colour) */
    val shadowMode: Int = 0
) {
    val isText: Boolean get() = kind == KIND_TEXT
    val len: Long get() = endMs - startMs
    fun visibleAt(ms: Long): Boolean = ms >= startMs && ms < endMs

    companion object {
        const val KIND_TEXT = 0
        const val KIND_IMAGE = 1
        const val MIN_MS = 200L
    }
}

/**
 * One keyframe: where the layer is, how big, how turned and how opaque, [tMs] after the layer's start.
 * [ease] is how the layer moves from THIS key to the next one: 0 smooth (ease in and out), 1 linear (constant speed), 2 hold (jumps at the next key).
 */
data class Keyframe(
    val tMs: Long, val x: Float, val y: Float, val scale: Float, val opacity: Float,
    val rotation: Float = 0f, val ease: Int = 0
)

class LayerPose(val x: Float, val y: Float, val scale: Float, val opacity: Float, val rotation: Float = 0f)

/** Keyframe maths, shared by the preview, the exporter and the editor. Movement between two keys is smooth (ease in and out). */
object LayerKeys {
    const val MAX_KEYS = 40
    const val EASE_SMOOTH = 0
    const val EASE_LINEAR = 1
    const val EASE_HOLD = 2
    /** a keyframe turns the layer at most this far either way (the slider shows -180..180) */
    const val ROT_MAX = 720f
    /** a keyframe within this many ms of the playhead counts as "the keyframe here" */
    const val NEAR_MS = 60L

    fun pose(l: Layer, ms: Long): LayerPose {
        val k = l.keys
        if (k.isEmpty()) return LayerPose(l.x, l.y, l.scale, l.opacity, l.rotation)
        val t = ms - l.startMs
        if (t <= k.first().tMs) return k.first().let { LayerPose(it.x, it.y, it.scale, it.opacity, it.rotation) }
        if (t >= k.last().tMs) return k.last().let { LayerPose(it.x, it.y, it.scale, it.opacity, it.rotation) }
        var i = 0
        while (i < k.size - 2 && k[i + 1].tMs <= t) i++
        val a = k[i]
        val b = k[i + 1]
        val span = (b.tMs - a.tMs).coerceAtLeast(1L).toFloat()
        val u = ((t - a.tMs) / span).coerceIn(0f, 1f)
        val e = when (a.ease) {
            EASE_LINEAR -> u
            EASE_HOLD -> 0f
            else -> u * u * (3f - 2f * u)
        }
        fun mix(p: Float, q: Float) = p + (q - p) * e
        // rotation is mixed as plain numbers (not the short way round), so a key at -150 and one at +150 turn 300 degrees through 0, like the slider shows
        return LayerPose(mix(a.x, b.x), mix(a.y, b.y), mix(a.scale, b.scale), mix(a.opacity, b.opacity), mix(a.rotation, b.rotation))
    }

    /** the layer as it looks at [ms] (x, y, scale and opacity taken from the keyframes) */
    fun withPose(l: Layer, ms: Long): Layer {
        if (l.keys.isEmpty()) return l
        val p = pose(l, ms)
        return l.copy(x = p.x, y = p.y, scale = p.scale, opacity = p.opacity, rotation = p.rotation)
    }

    /** true when the turn of the layer changes between its keyframes: only then the exporter has to rotate frame by frame */
    fun rotationAnimated(l: Layer): Boolean =
        l.keys.size >= 2 && l.keys.any { kotlin.math.abs(it.rotation - l.keys.first().rotation) > 0.01f }

    /** puts a keyframe with this look at [ms]; a keyframe already there is replaced. Unchanged when it would be too many. */
    fun upsert(l: Layer, ms: Long, p: LayerPose): Layer {
        val rel = ms - l.startMs
        val kept = l.keys.filter { kotlin.math.abs(it.tMs - rel) > NEAR_MS }
        if (kept.size >= MAX_KEYS) return l
        // the way to the next key of a replaced key is kept
        val ease = l.keys.firstOrNull { kotlin.math.abs(it.tMs - rel) <= NEAR_MS }?.ease ?: 0
        val n = (kept + Keyframe(
            rel, p.x.coerceIn(0f, 1f), p.y.coerceIn(0f, 1f), p.scale.coerceIn(0.1f, 6f), p.opacity.coerceIn(0.05f, 1f),
            p.rotation.coerceIn(-ROT_MAX, ROT_MAX), ease
        )).sortedBy { it.tMs }
        return l.copy(keys = n)
    }

    /** sets how the layer moves from the keyframe at the playhead to the next one (EASE_*); unchanged when there is no key here */
    fun setEase(l: Layer, ms: Long, ease: Int): Layer {
        val k = nearest(l, ms) ?: return l
        return l.copy(keys = l.keys.map { if (it === k) it.copy(ease = ease.coerceIn(EASE_SMOOTH, EASE_HOLD)) else it })
    }

    /**
     * Moves the keyframe at the playhead by [deltaMs] (stays inside the layer, keeps its distance to the other keys, never lands on one).
     * Unchanged when there is no key here or the move is impossible.
     */
    fun nudge(l: Layer, ms: Long, deltaMs: Long): Layer {
        val k = nearest(l, ms) ?: return l
        val t = (k.tMs + deltaMs).coerceIn(0L, l.len)
        if (t == k.tMs) return l
        if (l.keys.any { it !== k && kotlin.math.abs(it.tMs - t) <= NEAR_MS }) return l
        return l.copy(keys = l.keys.map { if (it === k) it.copy(tMs = t) else it }.sortedBy { it.tMs })
    }

    /** a normal edit of the look (move, size, opacity) on a layer that has keyframes becomes a keyframe at the playhead */
    fun edited(cur: Layer, ms: Long, change: (Layer) -> Layer): Layer {
        val ch = change(withPose(cur, ms))
        return upsert(cur, ms, LayerPose(ch.x, ch.y, ch.scale, ch.opacity, ch.rotation))
    }

    fun nearest(l: Layer, ms: Long): Keyframe? {
        val rel = ms - l.startMs
        return l.keys.minByOrNull { kotlin.math.abs(it.tMs - rel) }?.takeIf { kotlin.math.abs(it.tMs - rel) <= 100L }
    }

    fun remove(l: Layer, ms: Long): Layer {
        val k = nearest(l, ms) ?: return l
        return l.copy(keys = l.keys.filter { it !== k })
    }

    /** biggest size a layer reaches: pictures and text are drawn sharp for it */
    fun maxScale(l: Layer): Float = if (l.keys.isEmpty()) l.scale else maxOf(l.scale, l.keys.maxOf { it.scale })
}

object LayerOps {
    /** the timeline shows at most this many rows of layers; more overlapping layers share the last row */
    const val MAX_ROWS = 3

    fun nextId(layers: List<Layer>): Int = (layers.maxOfOrNull { it.id } ?: 0) + 1

    /** Row of every layer on the timeline: layers that overlap in time go to different rows. */
    fun rows(layers: List<Layer>): IntArray {
        val order = layers.indices.sortedBy { layers[it].startMs }
        val rowEnd = ArrayList<Long>()
        val res = IntArray(layers.size)
        for (i in order) {
            val l = layers[i]
            var r = rowEnd.indexOfFirst { it <= l.startMs }
            if (r < 0) {
                rowEnd.add(l.endMs)
                r = rowEnd.size - 1
            } else {
                rowEnd[r] = l.endMs
            }
            res[i] = minOf(r, MAX_ROWS - 1)
        }
        return res
    }

    fun rowCount(rows: IntArray): Int {
        if (rows.isEmpty()) return 0
        var m = 0
        for (r in rows) if (r > m) m = r
        return m + 1
    }

    /** Cuts one layer into two at [at]. Null when a piece would be shorter than the minimum. */
    fun split(layers: List<Layer>, id: Int, at: Long): List<Layer>? {
        val i = layers.indexOfFirst { it.id == id }
        if (i < 0) return null
        val l = layers[i]
        if (at - l.startMs < Layer.MIN_MS || l.endMs - at < Layer.MIN_MS) return null
        val n = ArrayList(layers)
        n[i] = l.copy(endMs = at)
        val shift = at - l.startMs
        n.add(i + 1, l.copy(id = nextId(layers), startMs = at, keys = l.keys.map { it.copy(tMs = it.tMs - shift) }))
        return n
    }

    /** Moves a layer one step up (+1, drawn later = on top) or down (-1) in the drawing order. */
    fun move(layers: List<Layer>, id: Int, dir: Int): List<Layer>? {
        val i = layers.indexOfFirst { it.id == id }
        val j = i + dir
        if (i < 0 || j < 0 || j >= layers.size) return null
        val n = ArrayList(layers)
        val t = n[i]; n[i] = n[j]; n[j] = t
        return n
    }

    fun normDeg(v: Float): Float {
        var r = v % 360f
        if (r > 180f) r -= 360f
        if (r < -180f) r += 360f
        return r
    }
}

/** How a layer is shifted / faded / scaled at one moment of its entrance or exit (shared by the preview and the exporter). */
class LayerAnimState(val alpha: Float, val dx: Float, val dy: Float, val scale: Float)

object LayerAnim {
    const val NONE = 0
    const val SLIDE = 0.12f
    private val IDENTITY = LayerAnimState(1f, 0f, 0f, 1f)

    private fun ease(p: Float): Float {
        val q = 1f - p.coerceIn(0f, 1f)
        return 1f - q * q * q
    }

    fun at(l: Layer, ms: Long): LayerAnimState {
        if (l.animIn == NONE && l.animOut == NONE) return IDENTITY
        // each animation takes at most half of the layer's time, so they never fight each other
        val d = l.animMs.coerceIn(100L, maxOf(100L, l.len / 2L)).toFloat()
        var alpha = 1f
        var dx = 0f
        var dy = 0f
        var sc = 1f
        fun part(kind: Int, p: Float) {
            if (kind == NONE) return
            val f = ease(p)
            alpha *= f
            when (kind) {
                2 -> dy += (1f - f) * SLIDE
                3 -> dy -= (1f - f) * SLIDE
                4 -> dx -= (1f - f) * SLIDE
                5 -> dx += (1f - f) * SLIDE
                6 -> sc *= 0.6f + 0.4f * f
            }
        }
        part(l.animIn, (ms - l.startMs) / d)
        part(l.animOut, (l.endMs - ms) / d)
        return LayerAnimState(alpha.coerceIn(0f, 1f), dx, dy, sc)
    }
}
