package app.zircut.timeline

import app.zircut.core.Range
import app.zircut.core.VideoKey

/**
 * Ripple editing (fix219): when the clips change length, layers and audio clips that come AFTER the change move with it.
 * Pure functions on the clip list; [MainViewModel.setSegments] applies them when the ripple switch is on.
 */
object Ripple {
    const val MIN_LAYER_MS = 100L

    /**
     * What changed on the edited timeline: the old stretch [from, oldEnd) was replaced by [from, newEnd).
     * Everything at or after [oldEnd] moves by [delta] (new total - old total).
     */
    class Change(val from: Long, val oldEnd: Long, val newEnd: Long, val delta: Long)

    /** the common beginning and ending of both lists are kept; null when the total length did not change */
    fun diff(old: List<Range>, new: List<Range>): Change? {
        val oldTotal = old.sumOf { it.outLen }
        val newTotal = new.sumOf { it.outLen }
        val delta = newTotal - oldTotal
        if (delta == 0L) return null
        var i = 0
        var from = 0L
        while (i < old.size && i < new.size && old[i] == new[i]) {
            from += old[i].outLen
            i++
        }
        var j = 0
        var tail = 0L
        while (old.size - 1 - j >= i && new.size - 1 - j >= i && old[old.size - 1 - j] == new[new.size - 1 - j]) {
            tail += old[old.size - 1 - j].outLen
            j++
        }
        return Change(from, oldTotal - tail, newTotal - tail, delta)
    }

    /** new position of a time on the timeline */
    fun shift(t: Long, c: Change): Long = when {
        t >= c.oldEnd -> (t + c.delta).coerceAtLeast(0L)
        t > c.from -> minOf(t, maxOf(c.from, c.newEnd))   // inside the changed stretch: never beyond its new end
        else -> t
    }

    /** locked layers stay where they are */
    fun layers(list: List<Layer>, c: Change): List<Layer> = list.map { l ->
        if (l.locked) l else {
            val s = shift(l.startMs, c)
            var e = shift(l.endMs, c)
            if (e - s < MIN_LAYER_MS) e = s + MIN_LAYER_MS
            if (s == l.startMs && e == l.endMs) l else l.copy(startMs = s, endMs = e)
        }
    }

    /** keys of the video picture (fix232) follow the change like layers do; keys that fall on the same time collapse into the first one */
    fun videoKeys(list: List<VideoKey>, c: Change): List<VideoKey> =
        list.map { k -> val t = shift(k.tMs, c); if (t == k.tMs) k else k.copy(tMs = t) }
            .sortedBy { it.tMs }.distinctBy { it.tMs }

    /** only the start moves; a clip that is playing across the change keeps playing */
    fun audio(list: List<AudioClip>, c: Change): List<AudioClip> = list.map { a ->
        val s = shift(a.startMs, c)
        if (s == a.startMs) a else a.copy(startMs = s)
    }
}
