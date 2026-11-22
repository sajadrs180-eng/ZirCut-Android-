package app.zircut.timeline

import app.zircut.core.Phrase
import app.zircut.core.Range

/**
 * The edited timeline: an ordered list of clips, each one a [start, end) piece of the source video.
 * Clips can be split, trimmed, deleted, duplicated and re-ordered, so the same source time may appear
 * twice (duplicate) or out of order (move). "out" time = position on the edited timeline.
 */
class EdlMap(val segs: List<Range>) {
    private val starts = LongArray(segs.size)
    val total: Long

    init {
        var acc = 0L
        segs.forEachIndexed { i, r ->
            starts[i] = acc
            acc += r.outLen
        }
        total = acc
    }

    fun outStart(i: Int): Long = if (segs.isEmpty()) 0L else starts[i.coerceIn(0, segs.size - 1)]

    /** clip index and source position for a position on the edited timeline */
    fun locate(out: Long): Pair<Int, Long> {
        if (segs.isEmpty()) return 0 to 0L
        var i = segs.size - 1
        while (i > 0 && starts[i] > out) i--
        val r = segs[i]
        return i to (r.startMs + Math.round((out - starts[i]).coerceIn(0L, r.outLen) * r.speed.toDouble()))
    }

    /** first place on the edited timeline where this source time is played, or null if it was cut away */
    fun srcToOut(src: Long): Long? {
        for (i in segs.indices) {
            val r = segs[i]
            if (src >= r.startMs && src < r.endMs) return starts[i] + Math.round((src - r.startMs) / r.speed.toDouble())
        }
        return null
    }

    /** first clip that overlaps a source interval */
    fun overlapIndex(s: Long, e: Long): Int? {
        for (i in segs.indices) {
            val r = segs[i]
            if (s < r.endMs && e > r.startMs) return i
        }
        return null
    }
}

/**
 * Which source a clip comes from is encoded in its Range so that every clip operation stays plain Range arithmetic:
 * source 0 is the project's main video (times are plain milliseconds), source k >= 1 is the k-th extra media
 * (times are k * BASE + milliseconds inside that media).
 */
object Src {
    const val BASE = 10_000_000_000L
    fun idx(ms: Long): Int = if (ms < 0L) 0 else (ms / BASE).toInt()
    fun local(ms: Long): Long = if (ms < 0L) ms else ms % BASE
    fun at(idx: Int, ms: Long): Long = idx * BASE + ms
}

/**
 * The same clip list, but clips that are not from the main video get impossible (negative) source times,
 * so the subtitle timeline (which only knows the main video's words) shows nothing over them.
 */
fun captionRanges(segs: List<Range>): List<Range> =
    segs.map { if (Src.idx(it.startMs) == 0) it else Range(-100_000_000_000L, -100_000_000_000L + it.outLen) }

class EdlResult(val segs: List<Range>, val playhead: Long)

/** All clip edits. They work on the clip under the playhead and return null when the edit is not possible. */
object EdlOps {
    private const val MIN = 100L

    fun split(segs: List<Range>, out: Long): EdlResult? {
        if (segs.isEmpty()) return null
        val m = EdlMap(segs)
        val (i, src) = m.locate(out)
        val r = segs[i]
        if (src - r.startMs < MIN || r.endMs - src < MIN) return null
        val n = ArrayList(segs)
        n[i] = r.copy(endMs = src)
        n.add(i + 1, r.copy(startMs = src, tr = -1, trMs = 0L))
        return EdlResult(n, out)
    }

    /** remove everything before the playhead inside this clip */
    fun trimStart(segs: List<Range>, out: Long): EdlResult? {
        if (segs.isEmpty()) return null
        val m = EdlMap(segs)
        val (i, src) = m.locate(out)
        val r = segs[i]
        if (src - r.startMs < 1L || r.endMs - src < MIN) return null
        val n = ArrayList(segs)
        n[i] = r.copy(startMs = src)
        return EdlResult(n, m.outStart(i))
    }

    /** remove everything after the playhead inside this clip */
    fun trimEnd(segs: List<Range>, out: Long): EdlResult? {
        if (segs.isEmpty()) return null
        val m = EdlMap(segs)
        val (i, src) = m.locate(out)
        val r = segs[i]
        if (src - r.startMs < MIN || r.endMs - src < 1L) return null
        val n = ArrayList(segs)
        n[i] = r.copy(endMs = src)
        return EdlResult(n, m.outStart(i) + n[i].outLen)
    }

    fun delete(segs: List<Range>, out: Long): EdlResult? {
        if (segs.size <= 1) return null
        val m = EdlMap(segs)
        val i = m.locate(out).first
        val n = ArrayList(segs)
        n.removeAt(i)
        val nm = EdlMap(n)
        return EdlResult(n, minOf(nm.outStart(i.coerceAtMost(n.size - 1)), nm.total))
    }

    /** Puts a new clip at the playhead: before/after the clip under it, or in the middle (the clip is split). */
    fun insertAt(segs: List<Range>, out: Long, clip: Range): EdlResult {
        if (segs.isEmpty()) return EdlResult(listOf(clip), 0L)
        val m = EdlMap(segs)
        val (i, src) = m.locate(out)
        val r = segs[i]
        val n = ArrayList<Range>()
        val at: Int
        if (src - r.startMs < MIN) {
            n.addAll(segs); n.add(i, clip); at = i
        } else if (r.endMs - src < MIN) {
            n.addAll(segs); n.add(i + 1, clip); at = i + 1
        } else {
            for (k in segs.indices) {
                if (k == i) {
                    n.add(r.copy(endMs = src)); n.add(clip); n.add(r.copy(startMs = src, tr = -1, trMs = 0L))
                } else n.add(segs[k])
            }
            at = i + 1
        }
        return EdlResult(n, EdlMap(n).outStart(at))
    }

    fun duplicate(segs: List<Range>, out: Long): EdlResult? {
        if (segs.isEmpty()) return null
        val m = EdlMap(segs)
        val i = m.locate(out).first
        val n = ArrayList(segs)
        n.add(i + 1, segs[i])
        return EdlResult(n, m.outStart(i) + segs[i].outLen)
    }

    fun moveEarlier(segs: List<Range>, out: Long): EdlResult? {
        if (segs.size < 2) return null
        val i = EdlMap(segs).locate(out).first
        if (i == 0) return null
        val n = ArrayList(segs)
        val t = n[i]; n[i] = n[i - 1]; n[i - 1] = t
        return EdlResult(n, EdlMap(n).outStart(i - 1))
    }

    fun moveLater(segs: List<Range>, out: Long): EdlResult? {
        if (segs.size < 2) return null
        val i = EdlMap(segs).locate(out).first
        if (i >= segs.size - 1) return null
        val n = ArrayList(segs)
        val t = n[i]; n[i] = n[i + 1]; n[i + 1] = t
        return EdlResult(n, EdlMap(n).outStart(i + 1))
    }

    /** Reverse the order of timeline clips (this does not reverse motion inside each source clip). */
    fun reverseOrder(segs: List<Range>, out: Long): EdlResult? {
        if (segs.size < 2) return null
        val oldMap = EdlMap(segs)
        val safeOut = out.coerceIn(0L, oldMap.total)
        val n = segs.asReversed().toList()
        return EdlResult(n, (oldMap.total - safeOut).coerceIn(0L, EdlMap(n).total))
    }

    /** Duplicate the complete edit after itself, preserving the current playhead in the first copy. */
    fun duplicateTimeline(segs: List<Range>, out: Long): EdlResult? {
        if (segs.isEmpty()) return null
        val total = EdlMap(segs).total
        if (total <= 0L || total > Long.MAX_VALUE / 2L) return null
        return EdlResult(segs + segs, out.coerceIn(0L, total))
    }

    /** Merge only source-contiguous pieces from the same source; rendered timing remains unchanged. */
    fun mergeAdjacent(segs: List<Range>, out: Long): EdlResult? {
        if (segs.size < 2) return null
        val merged = ArrayList<Range>()
        var changed = false
        for (clip in segs) {
            val prev = merged.lastOrNull()
            if (prev != null && Src.idx(prev.startMs) == Src.idx(clip.startMs) && prev.endMs == clip.startMs && prev.speed == clip.speed && prev.look == clip.look) {
                merged[merged.lastIndex] = prev.copy(endMs = clip.endMs)
                changed = true
            } else merged.add(clip)
        }
        return if (changed) EdlResult(merged, out.coerceIn(0L, EdlMap(merged).total)) else null
    }

    /** Trim one edge of a clip using an edited-timeline position. edge=0 trims the start, edge=1 trims the end. */
    fun trimBoundary(segs: List<Range>, index: Int, edge: Int, outMs: Long): EdlResult? {
        if (index !in segs.indices || edge !in 0..1) return null
        val map = EdlMap(segs)
        val clip = segs[index]
        val outStart = map.outStart(index)
        val minLen = MIN
        if (clip.len <= minLen) return null
        // the drag position is on the edited timeline; the clip's own times are source times (differ when the clip has a speed)
        val offSrc = Math.round((outMs - outStart) * clip.speed.toDouble())
        val next = when (edge) {
            0 -> {
                val source = clip.startMs + offSrc.coerceIn(0L, clip.len - minLen)
                if (source <= clip.startMs) return null
                clip.copy(startMs = source)
            }
            else -> {
                val source = clip.startMs + offSrc.coerceIn(minLen, clip.len)
                if (source >= clip.endMs) return null
                clip.copy(endMs = source)
            }
        }
        if (next.len < minLen || next == clip) return null
        val updated = segs.toMutableList().also { it[index] = next }
        val newMap = EdlMap(updated)
        val playhead = if (edge == 0) newMap.outStart(index) else minOf(map.total, newMap.outStart(index) + next.outLen)
        return EdlResult(updated, playhead)
    }

    /** Sets the speed of the clip under the playhead (1 = normal). The playhead keeps its place inside the clip. */
    fun setSpeed(segs: List<Range>, out: Long, speed: Float): EdlResult? {
        if (segs.isEmpty()) return null
        val s = speed.coerceIn(0.1f, 8f)
        val m = EdlMap(segs)
        val i = m.locate(out).first
        val r = segs[i]
        if (kotlin.math.abs(r.speed - s) < 0.001f) return null
        val n = ArrayList(segs)
        n[i] = r.copy(speed = s)
        val nm = EdlMap(n)
        val rel = ((out - m.outStart(i)).coerceAtLeast(0L) * (r.speed / s).toDouble()).toLong()
        return EdlResult(n, minOf(nm.outStart(i) + rel, nm.outStart(i) + n[i].outLen))
    }

    /** Speed ramp: the clip under the playhead is cut into equal parts with these speeds (each part can be edited afterwards). */
    fun ramp(segs: List<Range>, out: Long, speeds: List<Float>): EdlResult? {
        if (segs.isEmpty() || speeds.size < 2) return null
        val m = EdlMap(segs)
        val i = m.locate(out).first
        val r = segs[i]
        if (r.len < 400L * speeds.size) return null
        val step = r.len / speeds.size
        val pieces = speeds.mapIndexed { k, sp ->
            val a = r.startMs + k * step
            val b = if (k == speeds.size - 1) r.endMs else a + step
            Range(a, b, sp, if (k == 0) r.tr else -1, if (k == 0) r.trMs else 0L, r.look)
        }
        val n = ArrayList<Range>()
        for (k in segs.indices) if (k == i) n.addAll(pieces) else n.add(segs[k])
        return EdlResult(n, m.outStart(i))
    }

    // ---- several clips at once (clip numbers = visible timeline index) ----

    /** Deletes the chosen clips (at least one clip must stay). */
    fun deleteMany(segs: List<Range>, picked: Set<Int>): EdlResult? {
        val del = picked.filter { it in segs.indices }.toSet()
        if (del.isEmpty() || del.size >= segs.size) return null
        val n = segs.filterIndexed { i, _ -> i !in del }
        val first = del.min().coerceAtMost(n.size - 1)
        return EdlResult(n, EdlMap(n).outStart(first))
    }

    /** Sets one speed on the chosen clips; clips in [skip] (photos) are left alone. */
    fun setSpeedMany(segs: List<Range>, picked: Set<Int>, speed: Float, skip: Set<Int>): EdlResult? {
        val s = speed.coerceIn(0.1f, 8f)
        val use = picked.filter { it in segs.indices && it !in skip }.toSet()
        if (use.isEmpty()) return null
        var changed = false
        val n = segs.mapIndexed { i, r ->
            if (i in use && kotlin.math.abs(r.speed - s) >= 0.001f) { changed = true; r.copy(speed = s) } else r
        }
        if (!changed) return null
        return EdlResult(n, EdlMap(n).outStart(use.min()))
    }

    /** Result of moving several clips: the new clip list and where the moved clips are now. */
    class MoveResult(val result: EdlResult, val picked: Set<Int>)

    /**
     * Moves the chosen clips together. mode: 0 = one place earlier, 1 = one place later, 2 = to the start, 3 = to the end.
     * The chosen clips keep their order among themselves. Null when nothing would change.
     */
    fun moveMany(segs: List<Range>, picked: Set<Int>, mode: Int): MoveResult? {
        val n = segs.size
        val sel = picked.filter { it in 0 until n }.toSet()
        if (sel.isEmpty() || sel.size >= n) return null
        val order = (0 until n).toMutableList()   // order[newPos] = old index
        when (mode) {
            0 -> for (p in 1 until n) if (order[p] in sel && order[p - 1] !in sel) { val t = order[p]; order[p] = order[p - 1]; order[p - 1] = t }
            1 -> for (p in n - 2 downTo 0) if (order[p] in sel && order[p + 1] !in sel) { val t = order[p]; order[p] = order[p + 1]; order[p + 1] = t }
            2 -> { val a = order.filter { it in sel }; val b = order.filter { it !in sel }; order.clear(); order.addAll(a + b) }
            else -> { val a = order.filter { it !in sel }; val b = order.filter { it in sel }; order.clear(); order.addAll(a + b) }
        }
        if (order == (0 until n).toList()) return null
        val ns = order.map { segs[it] }
        val newPicked = order.indices.filter { order[it] in sel }.toSet()
        return MoveResult(EdlResult(ns, EdlMap(ns).outStart(newPicked.min())), newPicked)
    }

    /** Puts a copy of each chosen clip right after itself. */
    fun duplicateMany(segs: List<Range>, picked: Set<Int>): EdlResult? {
        val dup = picked.filter { it in segs.indices }.toSet()
        if (dup.isEmpty()) return null
        val n = ArrayList<Range>()
        segs.forEachIndexed { i, r -> n.add(r); if (i in dup) n.add(r) }
        return EdlResult(n, EdlMap(n).outStart(dup.min() + 1))
    }

    /** Reorders a clip by its visible timeline index (used by long-press drag). */
    fun moveIndex(segs: List<Range>, from: Int, to: Int): EdlResult? {
        if (segs.size < 2 || from !in segs.indices || to !in segs.indices || from == to) return null
        val n = segs.toMutableList()
        val clip = n.removeAt(from)
        n.add(to.coerceIn(0, n.size), clip)
        return EdlResult(n, EdlMap(n).outStart(to.coerceIn(0, n.lastIndex)))
    }
}

/** A subtitle placed on the edited timeline (first time it is played). */
class OutPhrase(val idx: Int, val startMs: Long, val endMs: Long, val text: String, val low: Boolean)

fun outPhrasesOf(phrases: List<Phrase>, map: EdlMap): List<OutPhrase> {
    val res = ArrayList<OutPhrase>()
    phrases.forEachIndexed { i, p ->
        val si = map.overlapIndex(p.startMs, p.endMs) ?: return@forEachIndexed
        val r = map.segs[si]
        val s = maxOf(p.startMs, r.startMs)
        val e = minOf(p.endMs, r.endMs)
        val o = map.outStart(si) + Math.round((s - r.startMs) / r.speed.toDouble())
        res.add(OutPhrase(i, o, o + Math.round((e - s) / r.speed.toDouble()), p.text.replace("*", ""), p.words.any { it.conf < 0.5f }))
    }
    return res
}
