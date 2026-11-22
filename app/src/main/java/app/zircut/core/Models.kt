package app.zircut.core

import android.graphics.Color

data class Word(
    val text: String,
    val startMs: Long,
    val endMs: Long,
    val cutBefore: Boolean = false,
    val conf: Float = 1f
)

data class Phrase(val words: List<Word>) {
    val startMs: Long get() = words.first().startMs
    val endMs: Long get() = words.last().endMs
    val text: String get() = words.joinToString(" ") { it.text }
}

/** A piece [startMs, endMs) of a source. [len] is its SOURCE length; [outLen] is how long it lasts on the edited timeline (source length / [speed]). */
data class Range(
    val startMs: Long,
    val endMs: Long,
    val speed: Float = 1f,
    /** transition at the cut BEFORE this clip: -1 = the project's transition, otherwise the ordinal of a [TrKind] (0 = none) */
    val tr: Int = -1,
    /** length of that transition in ms, 0 = the project's */
    val trMs: Long = 0L,
    /** effects of THIS clip only (on top of the project-wide grade / effects) */
    val look: ClipLook = ClipLook()
) {
    val len: Long get() = endMs - startMs
    val outLen: Long get() = if (speed == 1f) len else Math.round(len / speed.toDouble())
}

/**
 * Effects of one clip. Colour (brightness, contrast, saturation, warmth) is ONE affine matrix applied after the project's grade and LUT
 * (preview ColorMatrix = export RgbMatrix); [blur] and [sharpen] (0..1) are added to the project's values for this clip.
 */
data class ClipLook(
    val brightness: Float = 0f,
    val contrast: Float = 1f,
    val saturation: Float = 1f,
    val warmth: Float = 0f,
    val blur: Float = 0f,
    val sharpen: Float = 0f,
    /** chroma key (green screen) of this clip, see core/ChromaKey.kt */
    val key: KeyLook = KeyLook()
) {
    val hasColor: Boolean get() = brightness != 0f || contrast != 1f || saturation != 1f || warmth != 0f
    val isNone: Boolean get() = !hasColor && blur <= 0f && sharpen <= 0f && !key.on
    fun affine(): Affine = ColorGrade.build(brightness, contrast, saturation, warmth, 0f, 0f)
}

data class VideoInfo(val durationMs: Long, val width: Int, val height: Int)

data class Options(
    val captions: Boolean = true,
    val removeSilence: Boolean = true,
    val removeFillers: Boolean = true,
    val zoom: Boolean = true,
    val vertical: Boolean = true,
    /** output frame shape: "" = follow [vertical] (old projects), "9:16", "16:9", "1:1", "4:5" or "orig" (the video's own shape) */
    val ratio: String = "",
    val maxGapMs: Long = 500,
    val language: String = "fa"
)

data class SubStyle(
    val id: String,
    val nameFa: String,
    val textColor: Int,
    val highlightColor: Int,
    val box: Boolean,
    val boxColor: Int,
    val outline: Float,
    val sizeFrac: Float,
    val pop: Boolean,
    val fade: Boolean = false,
    val hlBox: Int = 0,
    val glow: Int = 0,
    val font: String = "Vazirmatn-ExtraBold",
    val fontScale: Float = 1f
)

data class CapLayout(
    val x: Float = 0.5f,
    val y: Float = 0.66f,
    val mw: Float = 0.9f,
    val ls: Float = 1.5f,
    val lw: Int = 4,
    val size: Float = 1f,
    /** subtitle animation: 0 = the style's own (old behaviour), 1 none, 2 fade, 3 pop, 4 slide up, 5 zoom, 6 word bounce */
    val anim: Int = 0
)

/** What a subtitle looks like at one moment of its animation: scale, opacity and a downward offset as a fraction of the frame height. */
class CapPose(val scale: Float, val alpha: Float, val dy: Float)

/** ONE function for the subtitle animation, used by the preview and by the exporter so they match. */
object CapAnim {
    val names: List<Pair<Int, String>> = listOf(
        0 to "طبق استایل", 1 to "بدون", 2 to "محو", 3 to "پاپ", 4 to "اسلاید از پایین", 5 to "زوم", 6 to "پرش کلمه"
    )

    private fun easeOutBack(x: Float): Float {
        val c1 = 1.70158f
        val c3 = c1 + 1f
        val u = x - 1f
        return 1f + c3 * u * u * u + c1 * u * u
    }

    private fun easeOutCubic(x: Float): Float {
        val u = 1f - x
        return 1f - u * u * u
    }

    /** [pop] and [tMs] come from CaptionTimeline (time since the subtitle appeared), [wordMs] = time since the active word started. */
    fun pose(mode: Int, style: SubStyle, pop: Float, tMs: Long, wordMs: Long): CapPose {
        val t = tMs.coerceAtLeast(0L).toFloat()
        return when (mode) {
            1 -> CapPose(1f, 1f, 0f)
            2 -> CapPose(1f, minOf(1f, t / 250f), 0f)
            3 -> {
                val x = minOf(1f, t / 220f)
                CapPose(0.6f + 0.4f * easeOutBack(x), minOf(1f, t / 120f), 0f)
            }
            4 -> {
                val e = easeOutCubic(minOf(1f, t / 260f))
                CapPose(1f, e, (1f - e) * 0.05f)
            }
            5 -> {
                val e = easeOutCubic(minOf(1f, t / 260f))
                CapPose(1.35f - 0.35f * e, minOf(1f, t / 150f), 0f)
            }
            6 -> {
                val w = wordMs.coerceAtLeast(0L).toFloat()
                val bump = if (w < 160f) 0.14f * Math.sin(Math.PI * w / 160.0).toFloat() else 0f
                CapPose(1f + bump, minOf(1f, t / 120f), 0f)
            }
            else -> CapPose(
                if (style.pop) pop else 1f,
                if (style.fade) minOf(1f, t / 220f) else 1f,
                0f
            )
        }
    }
}

object Styles {
    private fun c(h: String) = Color.parseColor(h)

    val all = listOf(
        SubStyle("zarbati", "ضربتی", Color.WHITE, c("#FFD83A"), false, 0, 4f, 0.078f, true, font = "Lalezar-Regular", fontScale = 1.12f),
        SubStyle("clean", "تمیز", Color.WHITE, Color.WHITE, false, 0, 2f, 0.070f, false, fade = true),
        SubStyle("box", "کادر", Color.WHITE, Color.WHITE, true, c("#CC000000"), 0f, 0.066f, false),
        SubStyle("highlight", "هایلایت", Color.WHITE, Color.WHITE, false, 0, 2f, 0.068f, false, hlBox = c("#FFD83A"), font = "Cairo", fontScale = 0.96f),
        SubStyle("vlog", "ویلاگ", Color.WHITE, c("#5CF2C0"), false, 0, 3f, 0.072f, true, font = "Changa"),
        SubStyle("news", "خبری", Color.WHITE, Color.WHITE, true, c("#DD111111"), 0f, 0.062f, false, font = "Vazirmatn-Bold"),
        SubStyle("neon", "نئون", c("#EFE9FF"), c("#C4A8FF"), false, 0, 0f, 0.070f, false, fade = true, glow = c("#8B5CFF"), font = "Cairo", fontScale = 0.96f),
        SubStyle("cinema", "سینمایی", c("#F2F2F2"), Color.WHITE, false, 0, 1.5f, 0.060f, false, fade = true, font = "Vazirmatn-Regular"),
        SubStyle("estedad", "استعداد", Color.WHITE, c("#FFD83A"), false, 0, 3.5f, 0.072f, true, font = "Estedad"),
        SubStyle("sahel", "ساحل", Color.WHITE, c("#5CF2C0"), false, 0, 3.5f, 0.070f, false, fade = true, font = "Sahel-Black"),
        SubStyle("mikhak", "میخک", Color.WHITE, c("#FF8AD8"), false, 0, 3f, 0.072f, true, font = "Mikhak-Black")
    )

    fun byId(id: String): SubStyle = all.firstOrNull { it.id == id } ?: all.first()
}

/** Output frame shape: ONE place used by the preview, the export sheet and the exporter. */
object Frame {
    val IDS = listOf("9:16", "16:9", "1:1", "4:5", "orig")

    /** free (custom) ratio: stored as "c:<width/height>", for example "c:1.333" */
    const val CUSTOM = "c:"
    const val MIN_FREE = 0.4f
    const val MAX_FREE = 2.5f

    fun customId(a: Float): String = CUSTOM + "%.2f".format(java.util.Locale.US, a.coerceIn(MIN_FREE, MAX_FREE))

    private fun customValue(r: String): Float? =
        if (r.startsWith(CUSTOM)) r.substring(2).toFloatOrNull()?.coerceIn(MIN_FREE, MAX_FREE) else null

    /** the number behind a custom ratio, or null when the ratio is a fixed one */
    fun free(o: Options): Float? = customValue(o.ratio)

    fun id(o: Options): String = if (o.ratio in IDS || customValue(o.ratio) != null) o.ratio else if (o.vertical) "9:16" else "orig"

    /** width / height of the output frame, 0 = the shape of the video itself */
    fun aspect(o: Options): Float = customValue(o.ratio) ?: when (id(o)) {
        "9:16" -> 9f / 16f
        "16:9" -> 16f / 9f
        "1:1" -> 1f
        "4:5" -> 4f / 5f
        else -> 0f
    }

    fun label(o: Options): String = free(o)?.let { "آزاد " + fa("%.2f".format(java.util.Locale.US, it)) }
        ?: if (id(o) == "orig") "اندازه اصلی" else fa(id(o))

    private fun fa(s: String): String = if (!app.zircut.i18n.Lang.persianDigits) s else s.map { c -> if (c in '0'..'9') ('\u06F0' + (c - '0')) else c }.joinToString("")

    /** biggest frame of this shape that fits inside the source (the rest is cropped, centred) */
    fun crop(aspect: Float, srcW: Int, srcH: Int): Pair<Int, Int> {
        if (aspect <= 0f || srcW <= 0 || srcH <= 0) return srcW to srcH
        return if (srcW.toFloat() / srcH > aspect) {
            Math.round(srcH * aspect) to srcH
        } else {
            srcW to Math.round(srcW / aspect)
        }
    }
}
