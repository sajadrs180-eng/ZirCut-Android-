package app.zircut.timeline

import app.zircut.core.Waveform

/**
 * One piece of extra audio on the edited timeline (part 5): imported music/sound or a recorded voice-over.
 * The file lives inside the project folder. [startMs] is the position on the EDITED timeline (same time line as the
 * playhead), [inMs] is where inside the file playback begins, [lenMs] is how long it plays.
 * The original sound of the video is not a clip: it has its own volume/mute in the project (see UiState.mainVolume).
 */
data class AudioClip(
    val id: Int,
    val kind: Int,
    val file: String,
    val name: String,
    val startMs: Long,
    val inMs: Long,
    val lenMs: Long,
    /** total length of the file */
    val fileMs: Long,
    /** 0..1 (the phone's player cannot go above 100 % in the preview, so the export does not either) */
    val volume: Float = 1f,
    val fadeInMs: Long = 0L,
    val fadeOutMs: Long = 0L,
    val muted: Boolean = false
) {
    val endMs: Long get() = startMs + lenMs
    val isVoice: Boolean get() = kind == KIND_VOICE
    /** the clip made by "extract the video's sound": it IS the speech, so speech must never turn it down */
    val isVideoSound: Boolean = name.startsWith("صدای ویدیو")
    fun activeAt(ms: Long): Boolean = ms >= startMs && ms < endMs

    companion object {
        const val KIND_MUSIC = 0
        const val KIND_VOICE = 1
        const val MIN_MS = 200L
    }
}

object AudioOps {
    /** the timeline shows at most this many rows of audio; more overlapping clips share the last row */
    const val MAX_ROWS = 3

    fun nextId(clips: List<AudioClip>): Int = (clips.maxOfOrNull { it.id } ?: 0) + 1

    /**
     * Volume of a clip [tMs] milliseconds after its own start, fades included.
     * The preview and the export mixer both use this one function, so they sound the same.
     */
    fun gainAt(c: AudioClip, tMs: Double): Float {
        if (c.muted) return 0f
        var g = c.volume.coerceIn(0f, 1f)
        if (c.fadeInMs > 0L && tMs < c.fadeInMs) g *= (tMs / c.fadeInMs).coerceIn(0.0, 1.0).toFloat()
        val left = c.lenMs - tMs
        if (c.fadeOutMs > 0L && left < c.fadeOutMs) g *= (left / c.fadeOutMs).coerceIn(0.0, 1.0).toFloat()
        return g
    }

    /** music level while a voice-over plays (fraction of its own volume) and the time it takes to go down / come back */
    const val DUCK_LEVEL = 0.3f
    const val DUCK_RAMP_MS = 350.0

    /**
     * Automatic turning down of MUSIC clips under voice-over clips: 1 = no change, [DUCK_LEVEL] = fully ducked.
     * [absMs] is a position on the edited timeline. The level eases down before a voice starts and back up after it ends.
     * Preview and export both call this, so they sound the same.
     */
    fun duckFactor(clips: List<AudioClip>, c: AudioClip, absMs: Double, speech: SpeechMap? = null): Float {
        if (c.isVoice) return 1f
        var f = 1f
        for (v in clips) {
            if (!v.isVoice || v.muted || v.volume <= 0f) continue
            val d = maxOf(v.startMs - absMs, absMs - v.endMs, 0.0)
            if (d >= DUCK_RAMP_MS) continue
            val k = duckRamp(d)
            if (k < f) f = k
        }
        // the video's own speech (subtitle phrases on the edited timeline) turns music down the same way
        if (speech != null && !c.isVideoSound) {
            val d = speech.distance(absMs)
            if (d < DUCK_RAMP_MS) {
                val k = duckRamp(d)
                if (k < f) f = k
            }
        }
        return f
    }

    private fun duckRamp(distanceMs: Double): Float =
        1f - (1f - DUCK_LEVEL) * (1.0 - distanceMs / DUCK_RAMP_MS).toFloat()

    /** Row of every clip on the timeline: clips that overlap in time go to different rows. */
    fun rows(clips: List<AudioClip>): IntArray {
        val order = clips.indices.sortedBy { clips[it].startMs }
        val rowEnd = ArrayList<Long>()
        val res = IntArray(clips.size)
        for (i in order) {
            val c = clips[i]
            var r = rowEnd.indexOfFirst { it <= c.startMs }
            if (r < 0) {
                rowEnd.add(c.endMs)
                r = rowEnd.size - 1
            } else {
                rowEnd[r] = c.endMs
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

    /** Keeps a clip inside its file: it cannot start before 0 or play past the end of the file. */
    fun fit(c: AudioClip): AudioClip {
        val inMs = c.inMs.coerceIn(0L, maxOf(0L, c.fileMs - AudioClip.MIN_MS))
        val len = c.lenMs.coerceIn(AudioClip.MIN_MS, maxOf(AudioClip.MIN_MS, c.fileMs - inMs))
        val fi = c.fadeInMs.coerceIn(0L, len)
        val fo = c.fadeOutMs.coerceIn(0L, len)
        return c.copy(startMs = maxOf(0L, c.startMs), inMs = inMs, lenMs = len, fadeInMs = fi, fadeOutMs = fo)
    }

    /** Cuts one clip into two at [at] (position on the edited timeline). Null when a piece would be too short. */
    fun split(clips: List<AudioClip>, id: Int, at: Long): List<AudioClip>? {
        val i = clips.indexOfFirst { it.id == id }
        if (i < 0) return null
        val c = clips[i]
        if (at - c.startMs < AudioClip.MIN_MS || c.endMs - at < AudioClip.MIN_MS) return null
        val cut = at - c.startMs
        val left = c.copy(lenMs = cut, fadeOutMs = 0L)
        val right = c.copy(
            id = nextId(clips), startMs = at, inMs = c.inMs + cut, lenMs = c.lenMs - cut, fadeInMs = 0L
        )
        val n = ArrayList(clips)
        n[i] = fit(left)
        n.add(i + 1, fit(right))
        return n
    }

    /**
     * Removes the silent stretches of one clip (found from its waveform peaks, 50 per second) and closes the gaps:
     * the loud parts become consecutive clips starting where the original clip started. Silence shorter than [minSilenceMs]
     * is kept, and [padMs] of quiet is left on both sides of every cut so words do not sound chopped.
     * [thrByte] is the peak level (0..255, square-root scale like the waveform). Null when there is nothing to remove.
     * Other clips are NOT moved.
     */
    fun removeSilence(clips: List<AudioClip>, id: Int, peaks: ByteArray, thrByte: Int, minSilenceMs: Long = 500L, padMs: Long = 120L): List<AudioClip>? {
        val idx = clips.indexOfFirst { it.id == id }
        if (idx < 0 || peaks.isEmpty()) return null
        val c = clips[idx]
        val step = 1000L / Waveform.PER_SEC
        val from = (c.inMs / step).toInt().coerceIn(0, peaks.size)
        val to = ((c.inMs + c.lenMs) / step).toInt().coerceIn(from, peaks.size)
        if (to - from < 4) return null
        // runs of silence inside the clip, in file ms
        val keep = ArrayList<LongArray>()
        var segStart = c.inMs
        var i = from
        var removed = 0L
        while (i < to) {
            if ((peaks[i].toInt() and 0xFF) < thrByte) {
                var j = i
                while (j < to && (peaks[j].toInt() and 0xFF) < thrByte) j++
                val sMs = i * step
                val eMs = j * step
                if (eMs - sMs >= minSilenceMs && i > from && j < to) {
                    val cutA = sMs + padMs
                    val cutB = eMs - padMs
                    if (cutB - cutA >= 100L) {
                        keep.add(longArrayOf(segStart, cutA))
                        segStart = cutB
                        removed += cutB - cutA
                    }
                }
                i = j
            } else i++
        }
        keep.add(longArrayOf(segStart, c.inMs + c.lenMs))
        if (removed < 200L) return null
        val out = ArrayList<AudioClip>()
        var at = c.startMs
        var nid = nextId(clips)
        keep.forEachIndexed { k, seg ->
            val len = seg[1] - seg[0]
            if (len < AudioClip.MIN_MS) return@forEachIndexed
            val first = out.isEmpty()
            val last = k == keep.size - 1
            val piece = c.copy(
                id = if (first) c.id else nid++, startMs = at, inMs = seg[0], lenMs = len,
                fadeInMs = if (first) c.fadeInMs else 0L, fadeOutMs = if (last) c.fadeOutMs else 0L
            )
            out.add(fit(piece))
            at += len
        }
        if (out.isEmpty()) return null
        val n = ArrayList(clips)
        n.removeAt(idx)
        n.addAll(idx, out)
        return n
    }
}

/**
 * Where the video's own speech is on the EDITED timeline (one sorted list of non-overlapping intervals).
 * Phrases closer than [gapMs] are joined so the music does not pump up and down between words.
 * Used by the preview and the export mixer for the music auto-duck (fix218).
 */
class SpeechMap(private val starts: LongArray, private val ends: LongArray) {
    val isEmpty: Boolean get() = starts.isEmpty()

    /** 0 inside speech, otherwise the distance in ms to the nearest speech; huge when there is none */
    fun distance(ms: Double): Double {
        val n = starts.size
        if (n == 0) return Double.MAX_VALUE
        // last interval that starts at or before ms
        var lo = 0
        var hi = n - 1
        var idx = -1
        while (lo <= hi) {
            val mid = (lo + hi) ushr 1
            if (starts[mid] <= ms) { idx = mid; lo = mid + 1 } else hi = mid - 1
        }
        var best = Double.MAX_VALUE
        if (idx >= 0) {
            if (ms <= ends[idx]) return 0.0
            best = ms - ends[idx]
        }
        if (idx + 1 < n) best = minOf(best, starts[idx + 1] - ms)
        return best
    }

    companion object {
        const val JOIN_GAP_MS = 600L

        fun of(intervals: List<Pair<Long, Long>>, gapMs: Long = JOIN_GAP_MS): SpeechMap {
            val sorted = intervals.filter { it.second > it.first }.sortedBy { it.first }
            val s = ArrayList<Long>()
            val e = ArrayList<Long>()
            for ((a, b) in sorted) {
                if (e.isNotEmpty() && a - e[e.size - 1] <= gapMs) {
                    if (b > e[e.size - 1]) e[e.size - 1] = b
                } else {
                    s.add(a); e.add(b)
                }
            }
            return SpeechMap(s.toLongArray(), e.toLongArray())
        }
    }
}
