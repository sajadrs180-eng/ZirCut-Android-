package app.zircut.timeline

import kotlin.math.abs

/**
 * Magnetic alignment ("snap") for the timeline, added in 0.1.246. Pure maths with no Android classes, so it is unit-tested.
 *
 * All times are positions on the EDITED timeline (the same time line as the playhead).
 * - [windowMs] turns a fixed finger distance (10 dp) into milliseconds for the current zoom, so the magnet feels the same
 *   zoomed out or zoomed in, and a clip is never pulled by more than [MAX_WINDOW_MS].
 * - [nearest] picks the closest landmark inside that window. A landmark the edge is already stuck to is kept until the
 *   finger is [RELEASE_FACTOR] windows away, so the clip does not flicker or jump when the finger hovers near the border.
 * - [trim] / [move] always return a result that is inside the limits they were given (minimum clip length, project bounds),
 *   and they never snap to a landmark that would break those limits.
 */
object TimelineSnap {
    /** how close (in dp on screen) an edge must come to a landmark to be pulled onto it */
    const val THRESHOLD_DP = 10f
    const val MIN_WINDOW_MS = 25L
    const val MAX_WINDOW_MS = 400L
    /** a snapped edge lets go only when the finger is this many windows away */
    const val RELEASE_FACTOR = 1.6f

    /** landmark kinds; the order is the priority when two landmarks sit at the same time */
    enum class Kind { PLAYHEAD, PROJECT, VIDEO, LAYER, AUDIO }

    data class Target(val ms: Long, val kind: Kind)
    data class Hit(val ms: Long, val kind: Kind)

    /** [scale] is the timeline zoom in dp per second (the same number the timeline uses). */
    fun windowMs(scale: Float): Long =
        (THRESHOLD_DP * 1000f / scale.coerceAtLeast(1f)).toLong().coerceIn(MIN_WINDOW_MS, MAX_WINDOW_MS)

    /**
     * Every landmark an edge can stick to: the playhead, the start and the end of the project, both edges of every video
     * clip ([videoEdges]) and both edges of every other layer and audio clip. The item being dragged is left out
     * ([skipLayer] / [skipAudio]). One landmark per time, sorted by time.
     */
    fun targets(
        total: Long,
        playheadMs: Long,
        videoEdges: List<Long>,
        layers: List<Layer>,
        audio: List<AudioClip>,
        skipLayer: Int = -1,
        skipAudio: Int = -1
    ): List<Target> {
        val out = ArrayList<Target>(videoEdges.size + 2 * (layers.size + audio.size) + 3)
        out.add(Target(playheadMs.coerceIn(0L, maxOf(0L, total)), Kind.PLAYHEAD))
        out.add(Target(0L, Kind.PROJECT))
        out.add(Target(maxOf(0L, total), Kind.PROJECT))
        for (e in videoEdges) out.add(Target(e, Kind.VIDEO))
        for (l in layers) if (l.id != skipLayer) {
            out.add(Target(l.startMs, Kind.LAYER)); out.add(Target(l.endMs, Kind.LAYER))
        }
        for (c in audio) if (c.id != skipAudio) {
            out.add(Target(c.startMs, Kind.AUDIO)); out.add(Target(c.endMs, Kind.AUDIO))
        }
        return out.sortedWith(compareBy<Target>({ it.ms }, { it.kind.ordinal })).distinctBy { it.ms }
    }

    /** the landmark closest to [raw] inside [window] (null = none). [stuckMs] is the landmark the edge is stuck to right now. */
    fun nearest(raw: Long, targets: List<Target>, window: Long, stuckMs: Long? = null): Hit? {
        var best: Target? = null
        var bestD = Long.MAX_VALUE
        for (t in targets) {
            val d = abs(t.ms - raw)
            if (d <= window && d < bestD) { best = t; bestD = d }
        }
        if (stuckMs != null) {
            val s = targets.firstOrNull { it.ms == stuckMs }
            if (s != null) {
                val ds = abs(s.ms - raw)
                if (ds <= (window * RELEASE_FACTOR).toLong() && (best == null || bestD >= ds)) return Hit(s.ms, s.kind)
            }
        }
        return best?.let { Hit(it.ms, it.kind) }
    }

    class TrimResult(val ms: Long, val hit: Hit?)

    /**
     * One dragged edge. [raw] is where the finger wants the edge; [lo]..[hi] are the allowed times (they carry the minimum
     * clip length and the project bounds). The result is always inside [lo]..[hi]; landmarks outside it are ignored.
     */
    fun trim(raw: Long, lo: Long, hi: Long, targets: List<Target>, window: Long, stuckMs: Long? = null): TrimResult {
        val top = maxOf(lo, hi)
        val clamped = raw.coerceIn(lo, top)
        val usable = if (targets.isEmpty()) targets else targets.filter { it.ms in lo..top }
        val hit = nearest(clamped, usable, window, stuckMs)
        return if (hit != null) TrimResult(hit.ms, hit) else TrimResult(clamped, null)
    }

    /** [edge]: 0 = the start of the item snapped, 1 = its end, -1 = nothing snapped */
    class MoveResult(val delta: Long, val hit: Hit?, val edge: Int) {
        /** Timeline position shown for the edge that actually snapped (or the start edge when none snapped). */
        fun anchorMs(start: Long, end: Long): Long = if (edge == 1) end + delta else start + delta
    }

    /**
     * A whole item dragged sideways. [rawDelta] is how far the finger moved it. Either edge may snap, the closer one wins.
     * [minDelta]..[maxDelta] keep the item inside the project; the result never leaves them, and a snap that the limits
     * would undo is dropped, so the indicator never shows a snap that did not happen.
     */
    fun move(
        start: Long, end: Long, rawDelta: Long, minDelta: Long, maxDelta: Long,
        targets: List<Target>, window: Long, stuckMs: Long? = null
    ): MoveResult {
        val maxD = maxOf(minDelta, maxDelta)
        val plain = rawDelta.coerceIn(minDelta, maxD)
        if (targets.isEmpty()) return MoveResult(plain, null, -1)
        val hs = nearest(start + plain, targets, window, stuckMs)
        val he = nearest(end + plain, targets, window, stuckMs)
        var edge = -1
        var hit: Hit? = null
        var d = plain
        if (hs != null && (he == null || abs(hs.ms - (start + plain)) <= abs(he.ms - (end + plain)))) {
            edge = 0; hit = hs; d = hs.ms - start
        } else if (he != null) {
            edge = 1; hit = he; d = he.ms - end
        }
        d = d.coerceIn(minDelta, maxD)
        if (hit != null) {
            val actual = if (edge == 0) start + d else end + d
            if (actual != hit.ms) return MoveResult(plain, null, -1)
        }
        return MoveResult(d, hit, edge)
    }
}

/**
 * Precise edge trimming of audio clips and layers (0.1.246). Pure functions: the timeline gesture asks for the allowed
 * range ([audioBounds] / [layerBounds]), snaps inside it, and the commit goes through [audio] / [layer], which clamp again.
 * So the preview during the drag and the saved result are always the same.
 */
object TrimOps {
    /** allowed times for the dragged edge of an audio clip: never shorter than [AudioClip.MIN_MS], never before the start of the file, never past its end or the end of the project */
    fun audioBounds(c: AudioClip, edge: Int, total: Long): Pair<Long, Long> =
        if (edge == 0) {
            Pair((c.startMs - c.inMs).coerceAtLeast(0L), c.endMs - AudioClip.MIN_MS)
        } else {
            val sourceEnd = c.startMs + (c.fileMs - c.inMs)
            val hi = maxOf(c.endMs, minOf(sourceEnd, total))
            Pair(c.startMs + AudioClip.MIN_MS, hi)
        }

    /** the clip after its [edge] (0 start, 1 end) is dragged to [target]. A start trim moves the in-point of the file, so the sound stays where it was. */
    fun audio(c: AudioClip, edge: Int, target: Long, total: Long): AudioClip {
        val (lo, hi) = audioBounds(c, edge, total)
        val t = target.coerceIn(lo, maxOf(lo, hi))
        return if (edge == 0) {
            val cut = t - c.startMs
            c.copy(startMs = t, inMs = c.inMs + cut, lenMs = c.lenMs - cut)
        } else c.copy(lenMs = t - c.startMs)
    }

    fun layerBounds(l: Layer, edge: Int, total: Long): Pair<Long, Long> =
        if (edge == 0) Pair(0L, l.endMs - Layer.MIN_MS)
        else {
            val lo = l.startMs + Layer.MIN_MS
            Pair(lo, maxOf(lo, total))
        }

    /**
     * The layer after its [edge] is dragged to [target]. Keyframes are stored relative to the start, so a start trim shifts them
     * to keep every animation at the same time on the timeline (see [shiftKeys]).
     */
    fun layer(l: Layer, edge: Int, target: Long, total: Long): Layer {
        val (lo, hi) = layerBounds(l, edge, total)
        val t = target.coerceIn(lo, maxOf(lo, hi))
        if (edge == 1) return l.copy(endMs = t)
        val cut = t - l.startMs
        if (cut == 0L) return l
        return l.copy(startMs = t, keys = shiftKeys(l, cut))
    }

    /**
     * Keyframes after the start of [l] moves by [cut] ms (positive = later). Moving earlier: every key keeps its time on the
     * timeline. Moving later: keys before the new start go away and the pose at the new start becomes the first key.
     */
    fun shiftKeys(l: Layer, cut: Long): List<Keyframe> {
        if (l.keys.isEmpty() || cut == 0L) return l.keys
        if (cut < 0L) return l.keys.map { it.copy(tMs = it.tMs - cut) }
        val pose = LayerKeys.pose(l, l.startMs + cut)
        val seg = l.keys.lastOrNull { it.tMs <= cut }
        val first = Keyframe(0L, pose.x, pose.y, pose.scale, pose.opacity, pose.rotation, seg?.ease ?: 0)
        val after = l.keys.filter { it.tMs > cut }.map { it.copy(tMs = it.tMs - cut) }
        return listOf(first) + after
    }
}
