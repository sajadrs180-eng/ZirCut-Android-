package app.zircut.core

/**
 * Color grading as ONE affine RGB transform: out = L * in + o  (values 0..1).
 * The same numbers drive the live preview (Android ColorMatrix) and the export (Media3 RgbMatrix),
 * so what you see in the editor is what gets rendered.
 */
class Affine(val l: FloatArray, val o: FloatArray) {
    /** Applies [this] after [first]. */
    fun after(first: Affine): Affine {
        val nl = FloatArray(9)
        val no = FloatArray(3)
        for (r in 0 until 3) {
            for (c in 0 until 3) {
                var s = 0f
                for (k in 0 until 3) s += l[r * 3 + k] * first.l[k * 3 + c]
                nl[r * 3 + c] = s
            }
            var s = o[r]
            for (k in 0 until 3) s += l[r * 3 + k] * first.o[k]
            no[r] = s
        }
        return Affine(nl, no)
    }

    fun apply(rgb: FloatArray): FloatArray = FloatArray(3) { r ->
        (l[r * 3] * rgb[0] + l[r * 3 + 1] * rgb[1] + l[r * 3 + 2] * rgb[2] + o[r]).coerceIn(0f, 1f)
    }

    /** Android ColorMatrix layout (4x5, row-major, offsets in 0..255). */
    fun toColorMatrix(): FloatArray = floatArrayOf(
        l[0], l[1], l[2], 0f, o[0] * 255f,
        l[3], l[4], l[5], 0f, o[1] * 255f,
        l[6], l[7], l[8], 0f, o[2] * 255f,
        0f, 0f, 0f, 1f, 0f
    )

    /** Media3 RgbMatrix layout (4x4, column-major). The offset rides on the alpha column because alpha is always 1 for video. */
    fun toRgbMatrix(): FloatArray {
        val m = FloatArray(16)
        for (r in 0 until 3) {
            for (c in 0 until 3) m[c * 4 + r] = l[r * 3 + c]
            m[3 * 4 + r] = o[r]
        }
        m[3] = 0f; m[7] = 0f; m[11] = 0f; m[15] = 1f
        return m
    }

    companion object {
        val IDENTITY get() = Affine(floatArrayOf(1f, 0f, 0f, 0f, 1f, 0f, 0f, 0f, 1f), floatArrayOf(0f, 0f, 0f))

        fun diag(r: Float, g: Float, b: Float, off: Float = 0f) =
            Affine(floatArrayOf(r, 0f, 0f, 0f, g, 0f, 0f, 0f, b), floatArrayOf(off, off, off))

        fun saturation(s: Float): Affine {
            val lr = 0.2126f
            val lg = 0.7152f
            val lb = 0.0722f
            val i = 1f - s
            return Affine(
                floatArrayOf(
                    lr * i + s, lg * i, lb * i,
                    lr * i, lg * i + s, lb * i,
                    lr * i, lg * i, lb * i + s
                ),
                floatArrayOf(0f, 0f, 0f)
            )
        }

        fun contrast(c: Float) = diag(c, c, c, 0.5f * (1f - c))

        /** Rotates all hues by [deg] degrees (the standard hue-rotate matrix). */
        fun hue(deg: Float): Affine {
            val r = Math.toRadians(deg.toDouble())
            val c = Math.cos(r).toFloat()
            val s = Math.sin(r).toFloat()
            val lr = 0.213f
            val lg = 0.715f
            val lb = 0.072f
            return Affine(
                floatArrayOf(
                    lr + c * (1 - lr) - s * lr, lg - c * lg - s * lg, lb - c * lb + s * (1 - lb),
                    lr - c * lr + s * 0.143f, lg + c * (1 - lg) + s * 0.140f, lb - c * lb - s * 0.283f,
                    lr - c * lr - s * (1 - lr), lg - c * lg + s * lg, lb + c * (1 - lb) + s * lb
                ),
                floatArrayOf(0f, 0f, 0f)
            )
        }

        fun lerp(a: Affine, b: Affine, t: Float) = Affine(
            FloatArray(9) { a.l[it] + (b.l[it] - a.l[it]) * t },
            FloatArray(3) { a.o[it] + (b.o[it] - a.o[it]) * t }
        )
    }
}

/** One look (parameters, not pixels). */
data class Look(
    val id: String,
    val nameFa: String,
    val brightness: Float = 0f,
    val contrast: Float = 1f,
    val saturation: Float = 1f,
    val warmth: Float = 0f,
    val tint: Float = 0f,
    val fade: Float = 0f
)

object Looks {
    val all = listOf(
        Look("none", "اصلی"),
        Look("vivid", "زنده", contrast = 1.16f, saturation = 1.55f, brightness = 0.01f),
        Look("warm", "گرم", warmth = 0.45f, saturation = 1.08f, brightness = 0.015f),
        Look("cool", "سرد", warmth = -0.45f, saturation = 1.03f),
        Look("bw", "سیاه‌وسفید", contrast = 1.18f, saturation = 0f),
        Look("cinema", "سینمایی", contrast = 1.16f, saturation = 0.86f, warmth = 0.18f, tint = -0.1f, brightness = -0.02f),
        Look("vintage", "قدیمی", contrast = 1.0f, saturation = 0.78f, warmth = 0.6f, tint = 0.1f, fade = 0.07f),
        Look("fade", "محو", contrast = 0.86f, saturation = 0.82f, fade = 0.11f, brightness = 0.02f),
        Look("drama", "دراماتیک", contrast = 1.34f, saturation = 0.88f, brightness = -0.05f, warmth = -0.06f),
        Look("soft", "ملایم", contrast = 0.92f, saturation = 1.1f, brightness = 0.06f, warmth = 0.1f)
    )

    fun byId(id: String): Look = all.firstOrNull { it.id == id } ?: all.first()
}

/** The user's grade: a look (with strength) plus manual sliders applied on top. */
data class ColorGrade(
    val lookId: String = "none",
    val strength: Float = 1f,
    val brightness: Float = 0f,
    val contrast: Float = 1f,
    val saturation: Float = 1f,
    val warmth: Float = 0f,
    val tint: Float = 0f,
    val fade: Float = 0f,
    /** exposure in stops (-1..1): every value is multiplied by 2^exposure */
    val exposure: Float = 0f,
    /** white point (-0.2..0.2): positive makes the brightest values brighter */
    val whites: Float = 0f,
    /** black point (-0.15..0.15): positive deepens the darkest values */
    val blacks: Float = 0f,
    /** hue rotation in degrees (-180..180) */
    val hue: Float = 0f
) {
    val isIdentity: Boolean
        get() = lookId == "none" && brightness == 0f && contrast == 1f && saturation == 1f && warmth == 0f && tint == 0f && fade == 0f &&
            exposure == 0f && whites == 0f && blacks == 0f && hue == 0f

    fun affine(): Affine {
        val look = Looks.byId(lookId)
        val full = build(look.brightness, look.contrast, look.saturation, look.warmth, look.tint, look.fade)
        val base = Affine.lerp(Affine.IDENTITY, full, strength.coerceIn(0f, 1f))
        val manual = build(brightness, contrast, saturation, warmth, tint, fade, exposure, whites, blacks, hue)
        return manual.after(base)
    }

    companion object {
        /** Order: white balance, hue, saturation, exposure, black/white point, contrast, brightness, fade. */
        fun build(b: Float, c: Float, s: Float, w: Float, t: Float, f: Float, e: Float = 0f, wh: Float = 0f, bl: Float = 0f, hue: Float = 0f): Affine {
            var a = Affine.diag(1f + 0.25f * w, 1f - 0.15f * t, 1f - 0.25f * w)
            if (hue != 0f) a = Affine.hue(hue).after(a)
            a = Affine.saturation(s).after(a)
            if (e != 0f) {
                val g = Math.pow(2.0, e.toDouble()).toFloat()
                a = Affine.diag(g, g, g).after(a)
            }
            if (wh != 0f || bl != 0f) {
                val wp = 1f - wh
                val k = 1f / maxOf(0.2f, wp - bl)
                a = Affine.diag(k, k, k, -bl * k).after(a)
            }
            a = Affine.contrast(c).after(a)
            a = Affine.diag(1f, 1f, 1f, b).after(a)
            if (f != 0f) a = Affine(floatArrayOf(1f - f, 0f, 0f, 0f, 1f - f, 0f, 0f, 0f, 1f - f), floatArrayOf(f, f, f)).after(a)
            return a
        }
    }
}
