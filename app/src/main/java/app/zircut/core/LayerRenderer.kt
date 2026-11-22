package app.zircut.core

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import android.text.Layout
import android.text.StaticLayout
import android.text.TextDirectionHeuristics
import android.text.TextPaint
import app.zircut.timeline.Layer
import java.io.File
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sin

/**
 * Draws a text or picture layer into a bitmap. The preview (frame 540 px wide) and the export (frame 1080 px wide or more)
 * both use this, so a layer looks the same in both: sizes are fractions of the frame's short side.
 * The bitmap is drawn at [bs] times its natural size and shown at layer.scale / bs, which keeps big layers sharp.
 */
object LayerRenderer {
    /** the resolution a layer is drawn at: its own scale, but never more than 2x (memory) and never less than 0.25x */
    fun bakeScale(scale: Float): Float = ((min(scale, 2f) * 4f).roundToInt() / 4f).coerceAtLeast(0.25f)

    /** Unrotated bitmap of the layer, or null when there is nothing to draw (empty text, missing picture). */
    fun base(l: Layer, frameW: Int, frameH: Int, tf: Typeface, dir: File?, bs: Float): Bitmap? =
        try {
            if (l.isText) text(l, frameW, frameH, tf, bs) else image(l, frameW, frameH, dir, bs)
        } catch (_: Throwable) {
            null
        }

    /** The same bitmap turned by [deg] degrees clockwise on a bigger transparent canvas (used by the exporter). */
    fun bakeRotation(src: Bitmap, deg: Float): Bitmap {
        if (abs(deg) < 0.01f) return src
        val rad = Math.toRadians(deg.toDouble())
        val c = abs(cos(rad))
        val s = abs(sin(rad))
        val nw = ceil(src.width * c + src.height * s).toInt().coerceAtLeast(2)
        val nh = ceil(src.width * s + src.height * c).toInt().coerceAtLeast(2)
        val out = Bitmap.createBitmap(nw, nh, Bitmap.Config.ARGB_8888)
        val cv = Canvas(out)
        cv.translate(nw / 2f, nh / 2f)
        cv.rotate(deg)
        cv.drawBitmap(src, -src.width / 2f, -src.height / 2f, Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG))
        return out
    }

    private fun makeLayout(t: CharSequence, paint: TextPaint, w: Int, align: Layout.Alignment, lineMult: Float): StaticLayout =
        StaticLayout.Builder.obtain(t, 0, t.length, paint, w)
            .setAlignment(align)
            .setTextDirection(TextDirectionHeuristics.ANYRTL_LTR)
            .setLineSpacing(0f, lineMult)
            .build()

    private fun hasArabicScript(t: String): Boolean = t.any { it.code in 0x0600..0x06FF || it.code in 0x0750..0x077F || it.code in 0xFB50..0xFEFF }

    private fun text(l: Layer, fw: Int, fh: Int, tf: Typeface, bs: Float): Bitmap? {
        val t = l.text.trim()
        if (t.isEmpty()) return null
        val unit = min(fw, fh).toFloat()
        val size = unit * 0.075f * bs
        val maxW = (fw * 0.84f * bs).toInt().coerceAtLeast(40)
        val paint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            typeface = tf
            textSize = size
            color = l.color
            isFakeBoldText = l.bold
            if (l.italic) textSkewX = -0.22f
            // letter spacing would break the joined letters of Persian/Arabic, so it only applies to other scripts
            if (l.letterSpacing > 0f && !hasArabicScript(t)) letterSpacing = l.letterSpacing
        }
        val lineMult = l.lineSpacing.coerceIn(0.8f, 2.5f)
        val rtl = makeLayout(t, paint, maxW, Layout.Alignment.ALIGN_NORMAL, lineMult).getParagraphDirection(0) == Layout.DIR_RIGHT_TO_LEFT
        // visual alignment: 1 = left, 2 = right, otherwise centred (NORMAL means "right" for right-to-left text)
        val layoutAlign = when (l.align) {
            1 -> if (rtl) Layout.Alignment.ALIGN_OPPOSITE else Layout.Alignment.ALIGN_NORMAL
            2 -> if (rtl) Layout.Alignment.ALIGN_NORMAL else Layout.Alignment.ALIGN_OPPOSITE
            else -> Layout.Alignment.ALIGN_CENTER
        }
        val probe = makeLayout(t, paint, maxW, layoutAlign, lineMult)
        var lineMax = 0f
        for (i in 0 until probe.lineCount) lineMax = max(lineMax, probe.getLineWidth(i))
        val pad = (size * 0.35f).toInt()
        val w = (ceil(lineMax).toInt() + 2 * pad).coerceAtLeast(2)
        val h = (probe.height + 2 * pad).coerceAtLeast(2)
        val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val c = Canvas(bmp)
        if (l.bgColor != 0) {
            val bg = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = l.bgColor }
            c.drawRoundRect(RectF(0f, 0f, w.toFloat(), h.toFloat()), size * 0.3f, size * 0.3f, bg)
        }
        c.save()
        val shiftX = when (l.align) {
            1 -> 0f
            2 -> (maxW - lineMax)
            else -> (maxW - lineMax) / 2f
        }
        c.translate(pad - shiftX, pad.toFloat())
        // shadow (mode 0 keeps the old look: a small shadow only when there is no outline and no box)
        fun applyShadow(p: TextPaint) {
            when (l.shadowMode) {
                2 -> p.setShadowLayer(size * 0.10f, 0f, size * 0.05f, 0x99000000.toInt())
                3 -> p.setShadowLayer(size * 0.16f, size * 0.03f, size * 0.09f, 0xCC000000.toInt())
                4 -> p.setShadowLayer(size * 0.30f, 0f, 0f, l.color or 0xFF000000.toInt())
            }
        }
        if (l.stroke) {
            val sp = TextPaint(paint).apply {
                style = Paint.Style.STROKE
                strokeWidth = size * 0.14f
                strokeJoin = Paint.Join.ROUND
                color = l.outlineColor
            }
            applyShadow(sp)
            makeLayout(t, sp, maxW, layoutAlign, lineMult).draw(c)
        } else if (l.shadowMode == 0) {
            if (l.bgColor == 0) paint.setShadowLayer(size * 0.08f, 0f, size * 0.04f, 0x99000000.toInt())
        } else {
            applyShadow(paint)
        }
        makeLayout(t, paint, maxW, layoutAlign, lineMult).draw(c)
        c.restore()
        return bmp
    }

    private fun image(l: Layer, fw: Int, fh: Int, dir: File?, bs: Float): Bitmap? {
        if (dir == null || l.file.isEmpty()) return null
        val f = File(dir, l.file)
        if (!f.exists()) return null
        val o = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(f.absolutePath, o)
        if (o.outWidth <= 0 || o.outHeight <= 0) return null
        // at scale 1 a picture fits inside half of the frame
        val k = min(fw * 0.5f / o.outWidth, fh * 0.5f / o.outHeight) * bs
        val tw = max(2, (o.outWidth * k).roundToInt())
        val th = max(2, (o.outHeight * k).roundToInt())
        var sample = 1
        while (o.outWidth / (sample * 2) >= tw && o.outHeight / (sample * 2) >= th) sample *= 2
        val src = BitmapFactory.decodeFile(f.absolutePath, BitmapFactory.Options().apply { inSampleSize = sample }) ?: return null
        val res = if (src.width == tw && src.height == th) src else {
            val scaled = Bitmap.createScaledBitmap(src, tw, th, true)
            if (scaled !== src) src.recycle()
            scaled
        }
        return if (l.keyMode != 0) ChromaKey.apply(res, l.keyMode, l.keyTol) else res
    }
}

/**
 * Colour key for picture layers (green / blue screen, white or black background). Green and blue are keyed by chroma distance
 * (so shadows and brightness changes of the screen still go away) with a little spill removal on the edge; white and black by brightness.
 * It is applied to the layer bitmap itself, so the preview and the exported video show exactly the same result.
 */
object ChromaKey {
    fun apply(src: Bitmap, mode: Int, tol: Float): Bitmap {
        val w = src.width
        val h = src.height
        val px = IntArray(w * h)
        src.getPixels(px, 0, w, 0, 0, w, h)
        val t = tol.coerceIn(0f, 1f)
        val kb = if (mode == 2) 0.5f else -0.331f
        val kr = if (mode == 2) -0.081f else -0.419f
        val thr = 0.04f + t * 0.5f
        val edge = 0.1f
        for (i in px.indices) {
            val p = px[i]
            val a0 = (p ushr 24) and 0xFF
            if (a0 == 0) continue
            var r = ((p shr 16) and 0xFF) / 255f
            var g = ((p shr 8) and 0xFF) / 255f
            var b = (p and 0xFF) / 255f
            val keep: Float = when (mode) {
                1, 2 -> {
                    val cb = -0.169f * r - 0.331f * g + 0.5f * b
                    val cr = 0.5f * r - 0.419f * g - 0.081f * b
                    val d = Math.hypot((cb - kb).toDouble(), (cr - kr).toDouble()).toFloat()
                    ((d - thr) / edge).coerceIn(0f, 1f)
                }
                3 -> {
                    val l = 0.299f * r + 0.587f * g + 0.114f * b
                    ((1f - l - t * 0.5f) / edge).coerceIn(0f, 1f)
                }
                else -> {
                    val l = 0.299f * r + 0.587f * g + 0.114f * b
                    ((l - t * 0.5f) / edge).coerceIn(0f, 1f)
                }
            }
            if (keep < 1f) {
                // spill: the key colour that leaks into the edge pixels is pulled back
                if (mode == 1) g = minOf(g, maxOf(r, b)) else if (mode == 2) b = minOf(b, maxOf(r, g))
            }
            val a = (a0 * keep).toInt().coerceIn(0, 255)
            px[i] = (a shl 24) or ((r * 255f).toInt().coerceIn(0, 255) shl 16) or ((g * 255f).toInt().coerceIn(0, 255) shl 8) or (b * 255f).toInt().coerceIn(0, 255)
        }
        val out = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        out.setPixels(px, 0, w, 0, 0, w, h)
        if (out !== src) src.recycle()
        return out
    }
}

/** Keeps the bitmaps of the layers drawn in the preview so they are not redrawn on every frame. */
class LayerBitmapCache {
    private val map = HashMap<String, Bitmap?>()

    fun get(l: Layer, fw: Int, fh: Int, tf: Typeface, fontKey: String, dir: File?, bs: Float): Bitmap? {
        val key = "${fw}x$fh|$bs|$fontKey|${l.kind}|${l.text}|${l.color}|${l.bgColor}|${l.stroke}|${l.file}|${l.keyMode}|${l.keyTol}|${l.bold}|${l.italic}|${l.align}|${l.letterSpacing}|${l.lineSpacing}|${l.outlineColor}|${l.shadowMode}"
        if (map.containsKey(key)) return map[key]
        if (map.size > 30) map.clear()
        val b = LayerRenderer.base(l, fw, fh, tf, dir, bs)
        map[key] = b
        return b
    }
}
