package app.zircut.core

import kotlin.math.max

object Planner {
    private val fillerSet = setOf(
        "اوم", "اِ", "اه", "امم", "اممم", "اووم", "اوووم", "اِمم",
        "um", "uh", "uhh", "umm", "er", "erm", "ehm", "hmm", "hm", "mm", "mmm", "ah", "eh"
    )
    private val musicWords = setOf("موسیقی", "موزیک", "music")

    /** A lone "موسیقی" with silence around it is the model's note about music, not speech: drop it. */
    private fun dropIsolatedMusic(ws: List<Word>): List<Word> = ws.filterIndexed { i, w ->
        val k = w.text.trim { !it.isLetter() }.lowercase()
        if (k !in musicWords) true else {
            val before = i == 0 || w.startMs - ws[i - 1].endMs >= 800
            val after = i == ws.size - 1 || ws[i + 1].startMs - w.endMs >= 800
            !(before && after)
        }
    }

    private val punctChars = ".,،؟?!…-\"«»:;؛()[]"

    private fun norm(t: String) = t.trim().trim { it in punctChars }.lowercase()

    /** Turns raw Whisper tokens (one word each) into clean words. */
    fun clean(rawIn: List<Word>, removeFillers: Boolean, language: String = "fa"): List<Word> {
        // order words by time and keep every word's end after its start, so later subtitles are never skipped
        val raw = dropIsolatedMusic(rawIn.sortedBy { it.startMs }.map { if (it.endMs <= it.startMs) it.copy(endMs = it.startMs + 50) else it })
        val out = ArrayList<Word>()
        var pendingCut = false
        for (w in raw) {
            val t = w.text.trim().replace('ي', 'ی').replace('ك', 'ک')
            if (t.isEmpty()) continue
            if (t.startsWith("[") || t.startsWith("(") || t.startsWith("♪") || t.startsWith("*")) continue
            if (t.all { it in punctChars } && out.isNotEmpty()) {
                val last = out.removeAt(out.size - 1)
                out.add(last.copy(text = last.text + t, endMs = max(last.endMs, w.endMs)))
                continue
            }
            if (removeFillers && norm(t) in fillerSet) {
                pendingCut = true
                continue
            }
            // meaningless Latin letters inside a Persian video are usually a recognition failure: mark them red for review
            val latinJunk = language == "fa" && t.any { it in 'a'..'z' || it in 'A'..'Z' } &&
                t.none { it.code in 0x0600..0x06FF } && t.filter { it.isLetter() }.length <= 4
            out.add(w.copy(text = t, cutBefore = pendingCut, conf = if (latinJunk) minOf(w.conf, 0.3f) else w.conf))
            pendingCut = false
        }
        // Whisper sometimes loops on one word; keep at most 3 in a row.
        val res = ArrayList<Word>()
        var run = 0
        for (w in out) {
            if (res.isNotEmpty() && norm(res.last().text) == norm(w.text)) run++ else run = 1
            if (run <= 3) res.add(w)
        }
        // also drop loops of 2-4 words repeated again and again
        var i = 0
        val fin = ArrayList<Word>()
        while (i < res.size) {
            var skipped = false
            for (l in 2..4) {
                if (i + 3 * l > res.size) break
                var reps = 1
                while (i + (reps + 1) * l <= res.size &&
                    (0 until l).all { k -> norm(res[i + k].text) == norm(res[i + reps * l + k].text) }) reps++
                if (reps >= 3) {
                    for (k in 0 until 2 * l) fin.add(res[i + k])
                    i += reps * l
                    skipped = true
                    break
                }
            }
            if (!skipped) { fin.add(res[i]); i++ }
        }
        return if (language == "fa") joinPersian(fin).map { splitMi(it) } else fin
    }

    private val prefixWords = setOf("می", "نمی")
    private val suffixWords = setOf("ها", "های", "هایی", "ام", "ات", "اش", "ایم", "اید", "اند", "تر", "ترین", "تان", "شان", "مان")
    private const val ZW = "\u200c"

    /** Verb stems that follow «می» / «نمی» (a whitelist, so names like «مینا» or words like «میز» are never touched). */
    private val miStems = listOf(
        "کن", "کرد", "خوا", "خور", "خون", "خند", "گم", "گی", "گه", "گو", "گذ", "گیر", "گرد", "دون", "دید", "ده",
        "ذار", "ذاش", "شه", "شم", "شی", "شن", "شد", "شو", "رم", "ری", "ره", "رن", "رید", "رفت", "رود", "رس", "رق",
        "فهم", "فرست", "فروش", "پرس", "پذیر", "پز", "پوش", "نویس", "نوش", "بین", "بخش", "تون", "ترس", "ساز", "شناس",
        "شنو", "نش", "مون", "ماند", "افت", "خواب", "چسب", "ریز", "کش", "برد", "بر", "زن", "زد"
    )

    /** Whisper often writes «میفهمی» / «نمیکنن» without the half-space: «می‌فهمی» / «نمی‌کنن». */
    private fun splitMi(w: Word): Word {
        val t = w.text
        val pre = if (t.startsWith("نمی")) "نمی" else if (t.startsWith("می")) "می" else return w
        if (t.length <= pre.length + 1 || t.contains(ZW) || !isFa(t)) return w
        val rest = t.substring(pre.length)
        if (rest.first() == ' ' || miStems.none { rest.startsWith(it) }) return w
        return w.copy(text = pre + ZW + rest)
    }

    private fun isFa(s: String) = s.isNotEmpty() && s.first().code in 0x0600..0x06FF

    /** Fixes pieces that belong together: «می خوام» → «می‌خوام», «کتاب ها» → «کتاب‌ها», «رفته ام» → «رفته‌ام». */
    private fun joinPersian(ws: List<Word>): List<Word> {
        val out = ArrayList<Word>()
        var i = 0
        while (i < ws.size) {
            val w = ws[i]
            if (w.text in prefixWords && i + 1 < ws.size) {
                val n = ws[i + 1]
                if (isFa(n.text) && !n.cutBefore && n.startMs - w.endMs < 500) {
                    out.add(w.copy(text = w.text + ZW + n.text, endMs = max(w.endMs, n.endMs), conf = minOf(w.conf, n.conf)))
                    i += 2
                    continue
                }
            }
            if (w.text in suffixWords && out.isNotEmpty() && !w.cutBefore) {
                val p = out.last()
                if (isFa(p.text) && p.text.last().isLetter() && w.startMs - p.endMs < 500) {
                    out[out.size - 1] = p.copy(text = p.text + ZW + w.text, endMs = max(p.endMs, w.endMs), conf = minOf(p.conf, w.conf))
                    i++
                    continue
                }
            }
            out.add(w)
            i++
        }
        return out
    }

    fun phrases(words: List<Word>, maxWords: Int = 4): List<Phrase> {
        val res = ArrayList<Phrase>()
        var cur = ArrayList<Word>()
        for (w in words) {
            if (cur.isNotEmpty()) {
                val gap = w.startMs - cur.last().endMs
                val endsSentence = cur.last().text.last() in ".?!؟…،,"
                if (cur.size >= maxWords || gap > 450 || endsSentence) {
                    res.add(Phrase(cur))
                    cur = ArrayList()
                }
            }
            cur.add(w)
        }
        if (cur.isNotEmpty()) res.add(Phrase(cur))
        return res
    }

    /** Applies a user's text edit to a phrase while keeping its timing. */
    fun rebuild(old: Phrase, newText: String): Phrase {
        val parts = newText.trim().split(Regex("\\s+")).filter { it.isNotEmpty() }
        if (parts.isEmpty()) return old
        if (parts.size == old.words.size) {
            return Phrase(old.words.mapIndexed { i, w -> w.copy(text = parts[i], conf = if (parts[i] == w.text) w.conf else 1f) })
        }
        val s = old.startMs
        val e = old.endMs
        val total = parts.sumOf { it.length }.coerceAtLeast(1)
        var cursor = s
        val words = parts.mapIndexed { i, p ->
            val dur = (e - s) * p.length / total
            val w = Word(p, cursor, if (i == parts.size - 1) e else cursor + dur, i == 0 && old.words.first().cutBefore)
            cursor += dur
            w
        }
        return Phrase(words)
    }

    /** Segments of the original video that are kept in the final cut. */
    fun ranges(words: List<Word>, totalMs: Long, opt: Options): List<Range> {
        if (!opt.removeSilence || words.isEmpty()) return listOf(Range(0, totalMs))
        val pad = 120L
        val maxGap = opt.maxGapMs.coerceAtLeast(2 * pad + 60)
        val groups = ArrayList<Range>()
        var s = words.first().startMs
        var e = words.first().endMs
        for (i in 1 until words.size) {
            val w = words[i]
            val gap = w.startMs - e
            val limit = if (w.cutBefore) 120L else maxGap
            if (gap > limit) {
                groups.add(Range(s, e))
                s = w.startMs
                e = w.endMs
            } else {
                e = max(e, w.endMs)
            }
        }
        groups.add(Range(s, e))
        val padded = groups.map {
            Range((it.startMs - pad).coerceAtLeast(0), (it.endMs + pad).coerceAtMost(totalMs))
        }.toMutableList()
        for (i in 0 until padded.size - 1) {
            if (padded[i + 1].startMs < padded[i].endMs) {
                val mid = (padded[i + 1].startMs + padded[i].endMs) / 2
                padded[i] = padded[i].copy(endMs = mid)
                padded[i + 1] = padded[i + 1].copy(startMs = mid)
            }
        }
        val kept = padded.filter { it.len >= 200 }
        return if (kept.isEmpty()) listOf(Range(0, totalMs)) else kept
    }
}

/** Maps the final (cut) timeline back to the source video to decide which caption to show. */
class CaptionTimeline(private val phrases: List<Phrase>, private val ranges: List<Range>) {
    private val outStart = LongArray(ranges.size)

    init {
        var acc = 0L
        ranges.forEachIndexed { i, r ->
            outStart[i] = acc
            acc += r.outLen
        }
    }

    data class State(val phraseIdx: Int, val wordIdx: Int, val pop: Float, val tMs: Long = 0, val wordMs: Long = 0)

    private fun srcAt(outMs: Long): Long {
        var i = ranges.size - 1
        while (i > 0 && outStart[i] > outMs) i--
        return ranges[i].startMs + Math.round((outMs - outStart[i]) * ranges[i].speed.toDouble())
    }

    fun stateAt(outMs: Long): State? {
        if (ranges.isEmpty()) return null
        val src = srcAt(outMs)
        var best: State? = null
        for ((pi, p) in phrases.withIndex()) {
            if (src < p.startMs - 40) continue
            if (src <= p.endMs + 250) {
                var wi = 0
                for ((k, w) in p.words.withIndex()) if (src >= w.startMs) wi = k
                val t = (src - p.startMs).coerceAtLeast(0)
                val pop = if (t < 150) 0.88f + 0.12f * (t / 150f) else 1f
                val wt = (src - (p.words.getOrNull(wi)?.startMs ?: p.startMs)).coerceAtLeast(0)
                best = State(pi, wi, pop, t, wt)
            }
        }
        return best
    }
}
