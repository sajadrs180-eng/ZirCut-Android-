package app.zircut.i18n

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.unit.LayoutDirection
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap

/** One interface language. [cont] is the "Continue" label shown on the first-run picker in that language. */
data class AppLang(val code: String, val native: String, val rtl: Boolean, val cont: String)

/**
 * App-language layer (fix234).
 *
 * The source text of the app is Persian. For every other language a pack `assets/i18n/<code>.tsv`
 * (lines: persian-text TAB translation, "\n" written as backslash-n) is loaded; a language is offered
 * in the picker ONLY when its pack file exists, so the picker never lists a language that is not translated.
 *
 * Translation happens when text is DISPLAYED (see `Txt` in ui/Theme.kt): the Persian literals in the code are
 * untouched, so logic that compares or stores them keeps working. Persian users (code "fa") see exactly the old text.
 */
object Lang {
    private const val PREFS = "zircut_lang"
    private const val KEY = "code"
    private const val RAW = '\u2060' // marker: text that must be shown as it is (user content, language names)

    private val known = listOf(
        AppLang("fa", "فارسی", true, "ادامه"),
        AppLang("en", "English", false, "Continue"),
        AppLang("ar", "العربية", true, "متابعة"),
        AppLang("tr", "Türkçe", false, "Devam"),
        AppLang("es", "Español", false, "Continuar"),
        AppLang("ru", "Русский", false, "Продолжить"),
        AppLang("hi", "हिन्दी", false, "जारी रखें"),
        AppLang("fr", "Français", false, "Continuer"),
        AppLang("id", "Bahasa Indonesia", false, "Lanjutkan"),
        AppLang("pt", "Português", false, "Continuar"),
        AppLang("de", "Deutsch", false, "Weiter")
    )

    /** Languages that really have a pack (plus Persian, the source language). */
    var available: List<AppLang> = listOf(known[0])
        private set

    var code by mutableStateOf("fa")
        private set

    /** false = the person has not picked a language yet -> show the picker before the app. */
    var chosen by mutableStateOf(true)
        private set

    /** Language to highlight first in the picker (the phone's language when we have it). */
    var suggested: String = "fa"
        private set

    private var ready = false

    val current: AppLang get() = available.firstOrNull { it.code == code } ?: known[0]
    val dir: LayoutDirection get() = if (current.rtl) LayoutDirection.Rtl else LayoutDirection.Ltr
    val persianDigits: Boolean get() = code == "fa"

    private class Tpl(val full: Regex, val frag: Regex, val labels: IntArray, val out: String, val weight: Int)
    private class Frag(val key: String, val out: String)

    @Volatile private var exact: Map<String, String> = emptyMap()
    @Volatile private var tpls: List<Tpl> = emptyList()
    @Volatile private var frags: List<Frag> = emptyList()
    private val cache = ConcurrentHashMap<String, String>()

    fun init(context: Context) {
        if (ready) return
        ready = true
        val app = context.applicationContext
        val files = try { app.assets.list("i18n")?.toSet() ?: emptySet() } catch (e: Exception) { emptySet() }
        available = known.filter { it.code == "fa" || (it.code + ".tsv") in files }
        val saved = app.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(KEY, null)
        suggested = suggest()
        if (saved != null && available.any { it.code == saved }) {
            load(app, saved)
            code = saved
            chosen = true
        } else {
            load(app, suggested)
            code = suggested
            chosen = false
        }
    }

    private fun suggest(): String {
        var l = Locale.getDefault().language.lowercase(Locale.US)
        if (l == "in") l = "id"
        if (l == "iw") l = "he"
        return if (available.any { it.code == l }) l else if (available.any { it.code == "en" }) "en" else "fa"
    }

    /** Switch the UI language now without saving it (used while the picker is open). */
    fun preview(context: Context, newCode: String) {
        if (available.none { it.code == newCode }) return
        load(context.applicationContext, newCode)
        code = newCode
    }

    /** Save the language that is showing now and let the app start. */
    fun confirm(context: Context) {
        context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putString(KEY, code).apply()
        chosen = true
    }

    /** Switch and save in one step (settings). */
    fun choose(context: Context, newCode: String) {
        preview(context, newCode)
        confirm(context)
    }

    /** Mark text that must not be translated (project names, subtitle words, language names...). */
    fun raw(s: String): String = RAW.toString() + s

    // -------------------------------------------------------------------------------------
    //  Translation
    // -------------------------------------------------------------------------------------

    private fun load(app: Context, c: String) {
        cache.clear()
        if (c == "fa") { exact = emptyMap(); tpls = emptyList(); frags = emptyList(); return }
        val map = HashMap<String, String>()
        val tl = ArrayList<Tpl>()
        val fl = ArrayList<Frag>()
        try {
            app.assets.open("i18n/$c.tsv").bufferedReader(Charsets.UTF_8).useLines { lines ->
                for (line in lines) {
                    val t = line.indexOf('\t')
                    if (t <= 0) continue
                    val key = line.substring(0, t).replace("\\n", "\n")
                    val out = line.substring(t + 1).replace("\\n", "\n")
                    if (out.isEmpty()) continue
                    map[key] = out
                    if (PLACEHOLDER.containsMatchIn(key)) {
                        buildTemplate(key, out)?.let { tl.add(it) }
                    } else {
                        val k = key.trim()
                        if (k.isNotEmpty()) fl.add(Frag(k, out.trim()))
                    }
                }
            }
        } catch (e: Exception) {
            // a missing or broken pack leaves the Persian text visible: nothing else breaks
        }
        exact = map
        tpls = tl.sortedByDescending { it.weight }
        frags = fl.sortedByDescending { it.key.length }
    }

    private val PLACEHOLDER = Regex("\\{(\\d+)\\}")

    private fun buildTemplate(key: String, out: String): Tpl? {
        val parts = ArrayList<String>()
        val labels = ArrayList<Int>()
        var last = 0
        for (m in PLACEHOLDER.findAll(key)) {
            parts.add(key.substring(last, m.range.first))
            labels.add(m.groupValues[1].toInt())
            last = m.range.last + 1
        }
        parts.add(key.substring(last))
        if (labels.isEmpty()) return null
        val full = StringBuilder("^")
        val frag = StringBuilder()
        for (i in parts.indices) {
            full.append(Regex.escape(parts[i]))
            frag.append(Regex.escape(parts[i]))
            if (i < labels.size) {
                full.append("(.+?)")
                val edge = (i == 0 && parts[0].isEmpty()) || (i == labels.size - 1 && parts[i + 1].isEmpty())
                frag.append(if (edge) "(\\S+)" else "(.+?)")
            }
        }
        full.append("$")
        return try {
            Tpl(
                Regex(full.toString(), RegexOption.DOT_MATCHES_ALL),
                Regex(frag.toString(), RegexOption.DOT_MATCHES_ALL),
                labels.toIntArray(), out, key.length - labels.size * 3
            )
        } catch (e: Exception) { null }
    }

    /** Text for display. Persian ("fa") returns the text unchanged. */
    fun tr(s: String): String {
        if (s.isEmpty()) return s
        if (s[0] == RAW) return s.substring(1)
        if (code == "fa") return s
        if (!needs(s)) return s
        cache[s]?.let { return it }
        val r = latinize(translate(s, true))
        if (cache.size > 3000) cache.clear()
        cache[s] = r
        return r
    }

    private fun needs(s: String): Boolean {
        for (ch in s) if (ch in '\u0600'..'\u06FF') return true
        return false
    }

    private fun translate(s: String, fragments: Boolean): String {
        val ex = exact
        ex[s]?.let { return it }
        val t = s.trim()
        if (t.isEmpty()) return s
        if (t.length != s.length) {
            ex[t]?.let { return s.substring(0, s.indexOf(t)) + it + s.substring(s.indexOf(t) + t.length) }
        }
        for (tp in tpls) {
            val m = tp.full.matchEntire(t) ?: continue
            return fill(tp, m)
        }
        if (!fragments) return s
        var r = s
        for (tp in tpls) {
            var guard = 0
            while (guard++ < 4) {
                val m = tp.frag.find(r) ?: break
                if (!boundaryOk(r, m.range.first, m.range.last + 1)) break
                r = r.substring(0, m.range.first) + fill(tp, m) + r.substring(m.range.last + 1)
            }
        }
        for (f in frags) {
            var from = 0
            while (true) {
                val i = r.indexOf(f.key, from)
                if (i < 0) break
                val j = i + f.key.length
                if (boundaryOk(r, i, j)) {
                    r = r.substring(0, i) + f.out + r.substring(j)
                    from = i + f.out.length
                } else from = i + 1
            }
        }
        return r
    }

    private fun fill(tp: Tpl, m: MatchResult): String {
        var out = tp.out
        for (g in tp.labels.indices) {
            val v = translate(m.groupValues[g + 1], false)
            out = out.replace("{" + tp.labels[g] + "}", v)
        }
        return out
    }

    private fun isWordChar(c: Char): Boolean = c == '\u200c' || (c in '\u0600'..'\u06FF' && Character.isLetter(c))

    /** A match must not start or end in the middle of a word (checked only on a side where the match itself edges on a letter). */
    private fun boundaryOk(r: String, i: Int, j: Int): Boolean {
        if (j <= i) return false
        val before = if (i > 0) r[i - 1] else ' '
        val after = if (j < r.length) r[j] else ' '
        if (isWordChar(r[i]) && isWordChar(before)) return false
        if (isWordChar(r[j - 1]) && isWordChar(after)) return false
        return true
    }

    /** Persian/Arabic digits and punctuation -> the usual ones, for every language except Persian. */
    private fun latinize(s: String): String {
        val keepMarks = code == "ar" // Arabic keeps its own comma / question mark / semicolon
        var hit = false
        for (ch in s) if (ch in '\u0660'..'\u066C' || ch in '\u06F0'..'\u06F9' || (!keepMarks && (ch == '،' || ch == '؟' || ch == '؛'))) { hit = true; break }
        if (!hit) return s
        val sb = StringBuilder(s.length)
        for (ch in s) {
            sb.append(
                when (ch) {
                    in '\u0660'..'\u0669' -> '0' + (ch - '\u0660')
                    in '\u06F0'..'\u06F9' -> '0' + (ch - '\u06F0')
                    '\u066B' -> '.'
                    '\u066C' -> ','
                    '\u066A' -> '%'
                    '،' -> if (keepMarks) ch else ','
                    '؟' -> if (keepMarks) ch else '?'
                    '؛' -> if (keepMarks) ch else ';'
                    else -> ch
                }
            )
        }
        return sb.toString()
    }
}
