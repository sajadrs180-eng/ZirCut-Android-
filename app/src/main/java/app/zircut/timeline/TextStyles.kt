package app.zircut.timeline

import org.json.JSONArray
import org.json.JSONObject

/** Everything that makes a text layer look the way it does, without its words, position, size and timing. */
data class TextStyle(
    val color: Int = 0xFFFFFFFF.toInt(),
    val bgColor: Int = 0,
    val stroke: Boolean = true,
    val outlineColor: Int = 0xFF000000.toInt(),
    val bold: Boolean = false,
    val italic: Boolean = false,
    val align: Int = 0,
    val letterSpacing: Float = 0f,
    val lineSpacing: Float = 1.2f,
    val fontId: String = "",
    val shadowMode: Int = 0
)

class NamedStyle(val name: String, val style: TextStyle)

object TextStyles {
    private const val MAX_CUSTOM = 20

    val presets: List<NamedStyle> = listOf(
        NamedStyle("ساده", TextStyle()),
        NamedStyle("تیتر زرد", TextStyle(color = 0xFFFFD83A.toInt(), bold = true)),
        NamedStyle("برچسب قرمز", TextStyle(color = 0xFFFFFFFF.toInt(), bgColor = 0xFFFF4D4D.toInt(), stroke = false, bold = true)),
        NamedStyle("جعبه تیره", TextStyle(color = 0xFFFFFFFF.toInt(), bgColor = 0xCC000000.toInt(), stroke = false)),
        NamedStyle("نئون", TextStyle(color = 0xFF3DD6F5.toInt(), outlineColor = 0xFF4D8DFF.toInt(), bold = true)),
        NamedStyle("طلایی", TextStyle(color = 0xFFFFD83A.toInt(), outlineColor = 0xFFFF9F43.toInt(), bold = true)),
        NamedStyle("سینمایی", TextStyle(color = 0xFFFFFFFF.toInt(), stroke = false, italic = true, lineSpacing = 1.45f))
    )

    fun of(l: Layer): TextStyle = TextStyle(
        l.color, l.bgColor, l.stroke, l.outlineColor, l.bold, l.italic, l.align, l.letterSpacing, l.lineSpacing, l.fontId, l.shadowMode
    )

    /** Applies the look to a text layer; words, position, size, rotation, opacity and timing stay as they are. */
    fun apply(l: Layer, s: TextStyle): Layer = l.copy(
        color = s.color, bgColor = s.bgColor, stroke = s.stroke, outlineColor = s.outlineColor,
        bold = s.bold, italic = s.italic, align = s.align, letterSpacing = s.letterSpacing,
        lineSpacing = s.lineSpacing, fontId = s.fontId, shadowMode = s.shadowMode
    )

    fun matches(l: Layer, s: TextStyle): Boolean = of(l) == s

    fun add(list: List<NamedStyle>, s: TextStyle): List<NamedStyle> {
        // the same look is not saved twice
        if (list.any { it.style == s }) return list
        var n = list.size + 1
        while (list.any { it.name == "استایل $n" }) n++
        return (list + NamedStyle("استایل $n", s)).takeLast(MAX_CUSTOM)
    }

    fun toJson(list: List<NamedStyle>): String {
        val a = JSONArray()
        list.forEach { n ->
            val s = n.style
            a.put(
                JSONObject().put("name", n.name).put("color", s.color).put("bg", s.bgColor).put("stroke", s.stroke)
                    .put("oc", s.outlineColor).put("b", s.bold).put("i", s.italic).put("al", s.align)
                    .put("ls", s.letterSpacing.toDouble()).put("lh", s.lineSpacing.toDouble()).put("font", s.fontId).put("sd", s.shadowMode)
            )
        }
        return a.toString()
    }

    fun fromJson(json: String?): List<NamedStyle> {
        if (json.isNullOrEmpty()) return emptyList()
        return try {
            val a = JSONArray(json)
            val out = ArrayList<NamedStyle>()
            for (i in 0 until a.length()) {
                val o = a.optJSONObject(i) ?: continue
                out.add(
                    NamedStyle(
                        o.optString("name", "استایل ${i + 1}"),
                        TextStyle(
                            color = o.optInt("color", 0xFFFFFFFF.toInt()), bgColor = o.optInt("bg", 0),
                            stroke = o.optBoolean("stroke", true), outlineColor = o.optInt("oc", 0xFF000000.toInt()),
                            bold = o.optBoolean("b", false), italic = o.optBoolean("i", false),
                            align = o.optInt("al", 0).coerceIn(0, 2),
                            letterSpacing = o.optDouble("ls", 0.0).toFloat().coerceIn(0f, 0.5f),
                            lineSpacing = o.optDouble("lh", 1.2).toFloat().coerceIn(0.8f, 2.5f),
                            fontId = o.optString("font", ""),
                            shadowMode = o.optInt("sd", 0).coerceIn(0, 4)
                        )
                    )
                )
            }
            out.takeLast(MAX_CUSTOM)
        } catch (_: Exception) {
            emptyList()
        }
    }
}
