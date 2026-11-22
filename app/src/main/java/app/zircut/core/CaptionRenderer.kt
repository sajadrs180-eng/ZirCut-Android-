package app.zircut.core

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Typeface
import android.text.Layout
import android.text.SpannableString
import android.text.SpannableStringBuilder
import android.text.Spanned
import android.text.StaticLayout
import android.text.TextDirectionHeuristics
import android.text.TextPaint
import android.text.style.ForegroundColorSpan
import android.text.style.RelativeSizeSpan
import kotlin.math.min

/** Draws one caption (a phrase with the active word highlighted) into a bitmap. */
class CaptionRenderer(
    frameW: Int,
    frameH: Int,
    private val subStyle: SubStyle,
    private val typeface: Typeface,
    private val layout: CapLayout = CapLayout()
) {
    private val base = min(frameW, frameH).toFloat()
    private val textSize = base * subStyle.sizeFrac * layout.size * subStyle.fontScale
    private val maxW = min(frameW * layout.mw, base * 1.4f).toInt().coerceAtLeast(120)
    private val pad = (textSize * 0.4f).toInt()
    private val perLine = layout.lw.coerceAtLeast(1)

    fun render(p: Phrase, active: Int): Bitmap {
        val sb = SpannableStringBuilder()
        var aStart = -1
        var aEnd = -1
        val emphasis = ArrayList<IntArray>()
        p.words.forEachIndexed { i, w ->
            if (i > 0) sb.append(if (i % perLine == 0) '\n' else ' ')
            val em = w.text.startsWith("*")
            val t = if (em) w.text.substring(1) else w.text
            val st = sb.length
            sb.append(t)
            if (em) emphasis.add(intArrayOf(st, sb.length))
            if (i == active) {
                aStart = st
                aEnd = sb.length
            }
        }
        val plain = sb.toString()
        val hasBox = subStyle.hlBox != 0

        val fillPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            typeface = this@CaptionRenderer.typeface
            textSize = this@CaptionRenderer.textSize
            color = subStyle.textColor
        }
        val spannable = SpannableString(plain)
        for (e in emphasis) {
            spannable.setSpan(ForegroundColorSpan(EM_COLOR), e[0], e[1], Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
            spannable.setSpan(RelativeSizeSpan(1.1f), e[0], e[1], Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
        }
        if (aStart >= 0) {
            val col = if (hasBox) 0xFF111111.toInt() else subStyle.highlightColor
            if (col != 0) {
                spannable.setSpan(ForegroundColorSpan(col), aStart, aEnd, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
            }
        }
        val fillLayout = makeLayout(spannable, fillPaint)

        var lineMax = 0f
        for (i in 0 until fillLayout.lineCount) lineMax = maxOf(lineMax, fillLayout.getLineWidth(i))

        val w = maxW + 2 * pad
        val h = fillLayout.height + 2 * pad
        val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val c = Canvas(bmp)

        if (subStyle.box) {
            val boxPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = subStyle.boxColor }
            val bw = lineMax + 2 * pad
            val left = (w - bw) / 2f
            c.drawRoundRect(RectF(left, 0f, left + bw, h.toFloat()), textSize * 0.35f, textSize * 0.35f, boxPaint)
        }

        c.save()
        c.translate(pad.toFloat(), pad.toFloat())

        if (hasBox && aStart >= 0) {
            val path = Path()
            fillLayout.getSelectionPath(aStart, aEnd, path)
            val rf = RectF()
            path.computeBounds(rf, true)
            rf.inset(-textSize * 0.08f, -textSize * 0.02f)
            val hp = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = subStyle.hlBox }
            c.drawRoundRect(rf, textSize * 0.25f, textSize * 0.25f, hp)
        }

        val soft = 0x99000000.toInt()
        if (subStyle.outline > 0f) {
            val strokePaint = TextPaint(fillPaint).apply {
                style = Paint.Style.STROKE
                strokeWidth = textSize * subStyle.outline * 0.044f
                strokeJoin = Paint.Join.ROUND
                strokeMiter = 2f
                color = 0xFF000000.toInt()
                setShadowLayer(textSize * 0.09f, 0f, textSize * 0.045f, soft)
            }
            makeLayout(SpannableString(plain), strokePaint).draw(c)
        } else if (subStyle.glow != 0) {
            fillPaint.setShadowLayer(textSize * 0.35f, 0f, 0f, subStyle.glow)
        } else {
            fillPaint.setShadowLayer(textSize * 0.08f, 0f, textSize * 0.04f, soft)
        }
        fillLayout.draw(c)
        c.restore()
        return bmp
    }

    private fun makeLayout(text: CharSequence, paint: TextPaint): StaticLayout =
        StaticLayout.Builder.obtain(text, 0, text.length, paint, maxW)
            .setAlignment(Layout.Alignment.ALIGN_CENTER)
            .setTextDirection(TextDirectionHeuristics.ANYRTL_LTR)
            .setLineSpacing(0f, layout.ls / 1.5f)
            .build()

    private companion object {
        val EM_COLOR = 0xFFFFD83A.toInt()
    }
}
