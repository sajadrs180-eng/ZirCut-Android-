package app.zircut.core

import app.zircut.timeline.EdlMap
import app.zircut.timeline.Src
import app.zircut.timeline.outPhrasesOf

/** SRT subtitles. Export uses the EDITED timeline (what the viewer sees); import maps cue times back onto the main video's clips. */
object Srt {
    private fun stamp(ms: Long): String {
        val t = maxOf(0L, ms)
        return String.format(java.util.Locale.US, "%02d:%02d:%02d,%03d", t / 3_600_000, t / 60_000 % 60, t / 1000 % 60, t % 1000)
    }

    fun build(phrases: List<Phrase>, map: EdlMap): String {
        val sb = StringBuilder()
        var n = 0
        for (o in outPhrasesOf(phrases, map)) {
            val text = o.text.trim()
            if (text.isEmpty()) continue
            n++
            sb.append(n).append('\n')
            sb.append(stamp(o.startMs)).append(" --> ").append(stamp(maxOf(o.endMs, o.startMs + 200L))).append('\n')
            sb.append(text).append("\n\n")
        }
        return sb.toString()
    }

    class Cue(val startMs: Long, val endMs: Long, val text: String)

    private fun parseTime(s: String): Long? {
        val m = Regex("""(\d+):(\d{1,2}):(\d{1,2})[,.](\d{1,3})""").find(s.trim()) ?: return null
        val (h, mi, se, fr) = m.destructured
        val ms = fr.padEnd(3, '0').take(3).toLong()
        return h.toLong() * 3_600_000 + mi.toLong() * 60_000 + se.toLong() * 1000 + ms
    }

    fun parse(raw: String): List<Cue> {
        val text = raw.removePrefix("\uFEFF").replace("\r\n", "\n").replace('\r', '\n')
        val out = ArrayList<Cue>()
        for (block in text.split(Regex("\n\\s*\n"))) {
            val lines = block.split('\n').map { it.trimEnd() }.filter { it.isNotBlank() }
            val ti = lines.indexOfFirst { it.contains("-->") }
            if (ti < 0) continue
            val parts = lines[ti].split("-->")
            if (parts.size < 2) continue
            val s = parseTime(parts[0]) ?: continue
            val e = parseTime(parts[1]) ?: continue
            val body = lines.drop(ti + 1).joinToString(" ") { it.replace(Regex("<[^>]*>"), "").replace(Regex("\\{[^}]*\\}"), "").trim() }.trim()
            if (body.isEmpty() || e <= s) continue
            out.add(Cue(s, e, body))
        }
        return out.sortedBy { it.startMs }
    }

    /**
     * Cues are on the edited timeline. Each one is placed on the main-video clip that plays at its start;
     * words share the cue's time by their length. Cues that fall on photos / extra videos are skipped (returned as [skipped]).
     */
    class Imported(val phrases: List<Phrase>, val skipped: Int)

    fun toPhrases(cues: List<Cue>, map: EdlMap): Imported {
        val res = ArrayList<Phrase>()
        var skipped = 0
        for (c in cues) {
            if (map.segs.isEmpty() || c.startMs >= map.total) { skipped++; continue }
            val (ci, srcStart) = map.locate(c.startMs)
            val clip = map.segs[ci]
            if (Src.idx(clip.startMs) != 0) { skipped++; continue }
            val clipOutEnd = map.outStart(ci) + clip.outLen
            val e = minOf(c.endMs, clipOutEnd)
            val len = e - c.startMs
            val srcLen = Math.round(len * clip.speed.toDouble())
            if (len < 100L) { skipped++; continue }
            val words = c.text.split(Regex("\\s+")).filter { it.isNotEmpty() }
            if (words.isEmpty()) { skipped++; continue }
            val total = words.sumOf { it.length.coerceAtLeast(1) }.toFloat()
            var at = 0f
            val list = ArrayList<Word>()
            words.forEachIndexed { i, w ->
                val share = w.length.coerceAtLeast(1) / total
                val ws = srcStart + (srcLen * at).toLong()
                at += share
                val we = if (i == words.size - 1) srcStart + srcLen else srcStart + (srcLen * at).toLong()
                list.add(Word(w, ws, maxOf(we, ws + 1L)))
            }
            res.add(Phrase(list))
        }
        return Imported(res, skipped)
    }
}
