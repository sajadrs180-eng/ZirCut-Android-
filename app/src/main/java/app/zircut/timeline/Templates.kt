package app.zircut.timeline

import app.zircut.core.ColorGrade

/** The title text a template puts on the video (position, entrance animation and one of the ready text styles by name). */
class TemplateTitle(val text: String, val y: Float, val animIn: Int, val styleName: String, val scale: Float = 1f)

/**
 * A ready-made video recipe: frame shape, subtitle style, colour look (with strength) and a title.
 * Applying one is a handful of normal edits done in one step; nothing is hidden: every part can be changed afterwards.
 */
class VideoTemplate(
    val id: String,
    val name: String,
    val note: String,
    /** frame ratio id as in Frame (9:16, 16:9, 1:1, 4:5, orig) or "c:2.35" */
    val ratio: String,
    val styleId: String,
    val lookId: String,
    val lookStrength: Float,
    val title: TemplateTitle
)

object VideoTemplates {
    val all = listOf(
        VideoTemplate("reels", "ریلز و شورتس", "عمودی ۹:۱۶ · زیرنویس ضربتی · رنگ زنده", "9:16", "zarbati", "vivid", 0.6f,
            TemplateTitle("عنوان ویدیو", 0.18f, 3, "تیتر زرد")),
        VideoTemplate("youtube", "یوتیوب", "افقی ۱۶:۹ · زیرنویس کادر · نام پایین تصویر", "16:9", "box", "none", 1f,
            TemplateTitle("نام شما", 0.84f, 4, "جعبه تیره", 0.8f)),
        VideoTemplate("cinema", "سینمایی", "عریض ۲٫۳۵ · رنگ سینمایی · عنوان وسط", "c:2.35", "cinema", "cinema", 1f,
            TemplateTitle("عنوان فیلم", 0.5f, 1, "ساده", 1.2f)),
        VideoTemplate("square", "پست مربعی", "۱:۱ · زیرنویس تمیز · رنگ ملایم", "1:1", "clean", "soft", 0.8f,
            TemplateTitle("عنوان پست", 0.16f, 6, "تیتر زرد")),
        VideoTemplate("portrait", "پست ۴:۵", "۴:۵ · هایلایت · رنگ گرم", "4:5", "highlight", "warm", 0.7f,
            TemplateTitle("عنوان پست", 0.15f, 2, "برچسب قرمز", 0.9f))
    )

    fun byId(id: String): VideoTemplate? = all.firstOrNull { it.id == id }

    fun grade(t: VideoTemplate, cur: ColorGrade): ColorGrade = cur.copy(lookId = t.lookId, strength = t.lookStrength)
}

/** The title text of a user-saved template (a copy of the look of one text layer). */
class SavedTitle(
    val text: String, val y: Float, val scale: Float, val animIn: Int, val color: Int, val bgColor: Int,
    val stroke: Boolean, val bold: Boolean, val italic: Boolean, val outlineColor: Int, val fontId: String
)

/** A template the user saved from the current project: frame shape, subtitle style, look and (optionally) one title. */
class MyTemplate(val name: String, val ratio: String, val styleId: String, val lookId: String, val lookStrength: Float, val title: SavedTitle?) {
    fun toJson(): org.json.JSONObject = org.json.JSONObject()
        .put("n", name).put("r", ratio).put("s", styleId).put("l", lookId).put("ls", lookStrength.toDouble())
        .also { o ->
            title?.let { t ->
                o.put("t", org.json.JSONObject().put("x", t.text).put("y", t.y.toDouble()).put("sc", t.scale.toDouble()).put("ai", t.animIn)
                    .put("c", t.color).put("bg", t.bgColor).put("st", t.stroke).put("b", t.bold).put("i", t.italic).put("oc", t.outlineColor).put("f", t.fontId))
            }
        }

    companion object {
        fun fromJson(o: org.json.JSONObject): MyTemplate? = try {
            val t = o.optJSONObject("t")?.let {
                SavedTitle(
                    it.optString("x", ""), it.optDouble("y", 0.5).toFloat(), it.optDouble("sc", 1.0).toFloat(), it.optInt("ai", 0).coerceIn(0, 6),
                    it.optInt("c", 0xFFFFFFFF.toInt()), it.optInt("bg", 0), it.optBoolean("st", true), it.optBoolean("b", false),
                    it.optBoolean("i", false), it.optInt("oc", 0xFF000000.toInt()), it.optString("f", "")
                )
            }
            MyTemplate(o.getString("n"), o.optString("r", ""), o.optString("s", "zarbati"), o.optString("l", "none"), o.optDouble("ls", 1.0).toFloat().coerceIn(0f, 1f), t)
        } catch (e: Exception) {
            null
        }
    }
}
