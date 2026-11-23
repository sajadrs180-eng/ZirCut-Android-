package app.zircut.core

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.opengl.GLES20
import android.opengl.GLUtils
import android.os.Build
import android.view.TextureView
import android.view.View
import androidx.media3.common.VideoFrameProcessingException
import androidx.media3.common.util.GlProgram
import androidx.media3.common.util.GlUtil
import androidx.media3.common.util.Size
import androidx.media3.common.util.UnstableApi
import androidx.media3.effect.BaseGlShaderProgram
import androidx.media3.effect.GlEffect
import androidx.media3.effect.GlShaderProgram

/**
 * Chroma key (green screen) of ONE clip. Classic algorithm, no AI: the distance between a pixel's chroma (Cb/Cr) and the key colour decides
 * how transparent the pixel is, then the key colour is removed from the edges (spill suppression).
 *
 * [color] is the key colour as it is in the video file (what the eyedropper picked); it is passed through the same grade as the picture
 * before it is compared, so a colour grade does not break the key.
 * [bgMode]: 0 = transparent (checkerboard in the preview; an MP4 cannot hold transparency, so the export fills it with black),
 * 1 = solid colour [bgColor], 2 = picture [bgFile] (file in the project folder).
 */
data class KeyLook(
    val on: Boolean = false,
    val color: Int = 0xFF00B140.toInt(),
    val tol: Float = 0.30f,
    val soft: Float = 0.25f,
    val spill: Float = 0.60f,
    val bgMode: Int = 0,
    val bgColor: Int = 0xFF000000.toInt(),
    val bgFile: String = "",
    /** 0 = colour key (green screen), 1 = AI subject cut-out (fix236) */
    val ai: Int = 0,
    val aiInvert: Boolean = false,
    val aiEdge: Float = 0.5f,
    /** fix239: parts kept by the AI (bit mask: 1 hair, 2 body skin, 4 face skin, 8 clothes, 16 accessories). 0 = the whole person (old behaviour) */
    val aiParts: Int = 0
)

/** The ONE definition of the key maths. The Kotlin version below and the two shaders (export GLSL, preview AGSL) use the same formulas. */
object ChromaKeyMath {
    /** GLSL / AGSL source of the key (identical formulas to [key]). Uses only vec2/vec3/vec4 so both languages accept it. */
    const val SNIPPET = """
vec2 zkCbCr(vec3 c) {
  return vec2(-0.1687 * c.r - 0.3313 * c.g + 0.5 * c.b, 0.5 * c.r - 0.4187 * c.g - 0.0813 * c.b);
}
vec4 zkKey(vec3 c, vec3 k, vec3 prm) {
  vec2 pc = zkCbCr(c);
  vec2 kc = zkCbCr(k);
  float dy = (dot(c, vec3(0.299, 0.587, 0.114)) - dot(k, vec3(0.299, 0.587, 0.114))) * 0.15;
  float d = sqrt(dot(pc - kc, pc - kc) + dy * dy);
  float t0 = prm.x * 0.45;
  float t1 = t0 + 0.002 + prm.y * 0.3;
  float a = smoothstep(t0, t1, d);
  float kl = length(kc);
  vec3 o = c;
  if (kl > 0.02) {
    vec2 kd = kc / kl;
    float amt = prm.z * max(0.0, dot(pc, kd));
    float dcb = -amt * kd.x;
    float dcr = -amt * kd.y;
    o = c + vec3(1.402 * dcr, -0.344136 * dcb - 0.714136 * dcr, 1.772 * dcb);
  }
  return vec4(clamp(o, 0.0, 1.0), a);
}
"""

    private fun smooth(e0: Float, e1: Float, x: Float): Float {
        val t = ((x - e0) / (e1 - e0)).coerceIn(0f, 1f)
        return t * t * (3f - 2f * t)
    }

    /** Pixel (r, g, b in 0..1) against the key colour: returns r, g, b after spill suppression and the alpha (0 = removed, 1 = kept). */
    fun key(r: Float, g: Float, b: Float, kr: Float, kg: Float, kb: Float, tol: Float, soft: Float, spill: Float): FloatArray {
        val pcb = -0.1687f * r - 0.3313f * g + 0.5f * b
        val pcr = 0.5f * r - 0.4187f * g - 0.0813f * b
        val kcb = -0.1687f * kr - 0.3313f * kg + 0.5f * kb
        val kcr = 0.5f * kr - 0.4187f * kg - 0.0813f * kb
        val dy = ((0.299f * r + 0.587f * g + 0.114f * b) - (0.299f * kr + 0.587f * kg + 0.114f * kb)) * 0.15f
        val d = Math.sqrt(((pcb - kcb) * (pcb - kcb) + (pcr - kcr) * (pcr - kcr) + dy * dy).toDouble()).toFloat()
        val t0 = tol * 0.45f
        val t1 = t0 + 0.002f + soft * 0.3f
        val a = smooth(t0, t1, d)
        var outR = r
        var og = g
        var ob = b
        val kl = Math.sqrt((kcb * kcb + kcr * kcr).toDouble()).toFloat()
        if (kl > 0.02f) {
            val kdx = kcb / kl
            val kdy = kcr / kl
            val amt = spill * maxOf(0f, pcb * kdx + pcr * kdy)
            val dcb = -amt * kdx
            val dcr = -amt * kdy
            outR = r + 1.402f * dcr
            og = g - 0.344136f * dcb - 0.714136f * dcr
            ob = b + 1.772f * dcb
        }
        return floatArrayOf(outR.coerceIn(0f, 1f), og.coerceIn(0f, 1f), ob.coerceIn(0f, 1f), a)
    }

    /** The colour matrix the picture goes through before the key (project grade, LUT look, clip look) - null when none. Same order as the preview / export. */
    fun total(grade: ColorGrade, lut: Affine?, clip: ClipLook): Affine? {
        if (grade.isIdentity && lut == null && !clip.hasColor) return null
        val base = if (lut == null) grade.affine() else lut.after(grade.affine())
        return if (clip.hasColor) clip.affine().after(base) else base
    }

    /** Key colour as the shader sees it: the picked colour sent through the same colour matrix as the picture. */
    fun gradedKey(k: KeyLook, grade: ColorGrade, lut: Affine?, clip: ClipLook): FloatArray {
        val raw = floatArrayOf(Color.red(k.color) / 255f, Color.green(k.color) / 255f, Color.blue(k.color) / 255f)
        val t = total(grade, lut, clip) ?: return raw
        return t.apply(raw)
    }

    /** Checkerboard colour for transparent areas (preview only). */
    fun checker(x: Int, y: Int, cell: Int): Int = if (((x / cell) + (y / cell)) % 2 == 0) 0xFF3C3C3C.toInt() else 0xFF5A5A5A.toInt()
}

/** Still-frame preview of the key on the CPU (used by the panel, works on every Android version). */
object KeyStill {
    fun render(src: Bitmap, total: Affine?, keyRgb: FloatArray, k: KeyLook, bg: Bitmap?, mask: FloatArray? = null): Bitmap {
        val w = src.width
        val h = src.height
        val px = IntArray(w * h)
        src.getPixels(px, 0, w, 0, 0, w, h)
        val out = IntArray(w * h)
        val cell = maxOf(6, w / 24)
        val bgPx: IntArray? = if (k.bgMode == 2 && bg != null) {
            // cover-fit the picture to the frame (same as the export)
            val fa = w.toFloat() / h
            val ba = bg.width.toFloat() / bg.height
            val sw: Int
            val sh: Int
            if (ba > fa) { sh = bg.height; sw = Math.round(bg.height * fa) } else { sw = bg.width; sh = Math.round(bg.width / fa) }
            val x0 = (bg.width - sw) / 2
            val y0 = (bg.height - sh) / 2
            val crop = Bitmap.createBitmap(bg, x0.coerceAtLeast(0), y0.coerceAtLeast(0), sw.coerceAtMost(bg.width), sh.coerceAtMost(bg.height))
            val scaled = Bitmap.createScaledBitmap(crop, w, h, true)
            val a = IntArray(w * h)
            scaled.getPixels(a, 0, w, 0, 0, w, h)
            a
        } else null
        for (y in 0 until h) {
            for (x in 0 until w) {
                val i = y * w + x
                val p = px[i]
                var rgb = floatArrayOf(Color.red(p) / 255f, Color.green(p) / 255f, Color.blue(p) / 255f)
                if (total != null) rgb = total.apply(rgb)
                val r = if (mask != null && i < mask.size) floatArrayOf(rgb[0], rgb[1], rgb[2], mask[i])
                else ChromaKeyMath.key(rgb[0], rgb[1], rgb[2], keyRgb[0], keyRgb[1], keyRgb[2], k.tol, k.soft, k.spill)
                val back = when {
                    bgPx != null -> bgPx[i]
                    k.bgMode == 1 -> k.bgColor
                    else -> ChromaKeyMath.checker(x, y, cell)
                }
                val a = r[3]
                val rr = Color.red(back) / 255f * (1f - a) + r[0] * a
                val gg = Color.green(back) / 255f * (1f - a) + r[1] * a
                val bb = Color.blue(back) / 255f * (1f - a) + r[2] * a
                out[i] = Color.argb(255, (rr * 255f + 0.5f).toInt().coerceIn(0, 255), (gg * 255f + 0.5f).toInt().coerceIn(0, 255), (bb * 255f + 0.5f).toInt().coerceIn(0, 255))
            }
        }
        return Bitmap.createBitmap(out, w, h, Bitmap.Config.ARGB_8888)
    }
}

/**
 * Live preview of the key on the video surface (Android 13+, AGSL shader through RenderEffect). The picture is graded first (colour matrix),
 * then keyed; the area that becomes transparent shows the backdrop drawn behind the surface. Older Android versions: the export still keys,
 * and the panel shows a still preview.
 */
object KeyPreviewFx {
    val supported: Boolean get() = Build.VERSION.SDK_INT >= 33

    private const val AGSL = """
uniform shader content;
uniform float3 keyC;
uniform float3 prm;
${ChromaKeyMath.SNIPPET}
half4 main(float2 p) {
  half4 s = content.eval(p);
  vec4 r = zkKey(vec3(s.rgb), keyC, prm);
  return half4(half3(r.rgb * r.a), half(r.a));
}
"""

    /** returns false when the effect could not be built (the preview then simply shows the unkeyed picture) */
    fun apply(v: View, keyRgb: FloatArray, k: KeyLook, total: Affine?, blurAmt: Float): Boolean {
        if (Build.VERSION.SDK_INT < 33) return false
        return try {
            val sh = android.graphics.RuntimeShader(AGSL)
            sh.setFloatUniform("keyC", keyRgb[0], keyRgb[1], keyRgb[2])
            sh.setFloatUniform("prm", k.tol, k.soft, k.spill)
            var eff = android.graphics.RenderEffect.createRuntimeShaderEffect(sh, "content")
            if (total != null) {
                val cf = ColorMatrixColorFilter(ColorMatrix(total.toColorMatrix()))
                eff = android.graphics.RenderEffect.createChainEffect(eff, android.graphics.RenderEffect.createColorFilterEffect(cf))
            }
            if (blurAmt > 0.01f) {
                val r = maxOf(0.5f, blurAmt * 0.025f * v.width * 0.5f)
                eff = android.graphics.RenderEffect.createChainEffect(
                    android.graphics.RenderEffect.createBlurEffect(r, r, android.graphics.Shader.TileMode.CLAMP), eff
                )
            }
            v.setLayerType(View.LAYER_TYPE_NONE, null)
            v.setRenderEffect(eff)
            (v as? TextureView)?.isOpaque = false
            true
        } catch (_: Throwable) {
            false
        }
    }

    fun clear(v: View) {
        if (Build.VERSION.SDK_INT < 31) return
        try {
            v.setRenderEffect(null)
            (v as? TextureView)?.isOpaque = true
        } catch (_: Throwable) {
        }
    }
}

/** Export: the same key as a GL effect. Foreground + background are blended here, so the output frame is opaque. */
@UnstableApi
class ChromaKeyEffect(private val keyRgb: FloatArray, private val k: KeyLook, private val bg: Bitmap?) : GlEffect {
    override fun toGlShaderProgram(context: Context, useHdr: Boolean): GlShaderProgram = ChromaKeyProgram(context, keyRgb, k, bg)
}

@UnstableApi
private class ChromaKeyProgram(private val ctx: Context, private val keyRgb: FloatArray, private val k: KeyLook, bg: Bitmap?) : BaseGlShaderProgram(false, 1) {
    private val program: GlProgram = makeProgram()
    private var bgTex = 0
    private var w = 1
    private var h = 1
    private val bgW: Float
    private val bgH: Float
    private val mode: Float
    private val aiOn = k.ai == 1
    private var aiSeg: AiSegmenter? = null
    private var copyProg: GlProgram? = null
    private var maskTex = 0
    private var smallTex = 0
    private var smallFbo = 0
    private var sw = 0
    private var sh = 0
    private var readBuf: java.nio.ByteBuffer? = null
    private var maskBuf: java.nio.ByteBuffer? = null
    private var prevMask: FloatArray? = null

    private fun makeProgram(): GlProgram {
        try {
            val gp = GlProgram(VERT, FRAG)
            gp.setBufferAttribute(
                "aFramePosition", GlUtil.getNormalizedCoordinateBounds(), GlUtil.HOMOGENEOUS_COORDINATE_VECTOR_SIZE
            )
            return gp
        } catch (e: Exception) {
            throw VideoFrameProcessingException(e)
        }
    }

    init {
        val usable = if (k.bgMode == 2 && bg != null && !bg.isRecycled && bg.width > 0 && bg.height > 0) bg else null
        mode = when {
            usable != null -> 2f
            k.bgMode == 1 -> 1f
            else -> 0f
        }
        val tex = usable ?: Bitmap.createBitmap(1, 1, Bitmap.Config.ARGB_8888)
        bgW = tex.width.toFloat()
        bgH = tex.height.toFloat()
        try {
            val ids = IntArray(1)
            GLES20.glGenTextures(1, ids, 0)
            bgTex = ids[0]
            GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, bgTex)
            GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MIN_FILTER, GLES20.GL_LINEAR)
            GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MAG_FILTER, GLES20.GL_LINEAR)
            GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_S, GLES20.GL_CLAMP_TO_EDGE)
            GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_T, GLES20.GL_CLAMP_TO_EDGE)
            GLUtils.texImage2D(GLES20.GL_TEXTURE_2D, 0, tex, 0)
            // mask texture (1x1 placeholder when the AI mode is off, so the sampler is always valid)
            GLES20.glGenTextures(1, ids, 0)
            maskTex = ids[0]
            GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, maskTex)
            GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MIN_FILTER, GLES20.GL_LINEAR)
            GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MAG_FILTER, GLES20.GL_LINEAR)
            GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_S, GLES20.GL_CLAMP_TO_EDGE)
            GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_T, GLES20.GL_CLAMP_TO_EDGE)
            GLES20.glPixelStorei(GLES20.GL_UNPACK_ALIGNMENT, 1)
            GLES20.glTexImage2D(GLES20.GL_TEXTURE_2D, 0, GLES20.GL_LUMINANCE, 1, 1, 0, GLES20.GL_LUMINANCE, GLES20.GL_UNSIGNED_BYTE, java.nio.ByteBuffer.wrap(byteArrayOf(-1)))
        } catch (e: Exception) {
            throw VideoFrameProcessingException(e)
        }
        if (aiOn) {
            try {
                aiSeg = AiSegmenter(ctx, k.aiParts != 0)
                val gp = GlProgram(VERT, FRAG_COPY)
                gp.setBufferAttribute("aFramePosition", GlUtil.getNormalizedCoordinateBounds(), GlUtil.HOMOGENEOUS_COORDINATE_VECTOR_SIZE)
                copyProg = gp
            } catch (e: Throwable) {
                throw VideoFrameProcessingException(IllegalStateException("مدل هوش مصنوعی داخل برنامه باز نشد: " + e.message, e))
            }
        }
    }

    /** AI mode: a small copy of the frame -> MediaPipe -> mask texture. Runs once per frame on the GL thread. */
    private fun updateMask(inputTexId: Int) {
        val seg = aiSeg ?: return
        val cp = copyProg ?: return
        if (sw <= 0 || sh <= 0) return
        val prevFbo = IntArray(1)
        val vp = IntArray(4)
        GLES20.glGetIntegerv(GLES20.GL_FRAMEBUFFER_BINDING, prevFbo, 0)
        GLES20.glGetIntegerv(GLES20.GL_VIEWPORT, vp, 0)
        val rb = readBuf ?: return
        GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER, smallFbo)
        GLES20.glViewport(0, 0, sw, sh)
        cp.use()
        cp.setSamplerTexIdUniform("uTex", inputTexId, 0)
        cp.bindAttributesAndUniforms()
        GLES20.glDrawArrays(GLES20.GL_TRIANGLE_STRIP, 0, 4)
        rb.clear()
        GLES20.glReadPixels(0, 0, sw, sh, GLES20.GL_RGBA, GLES20.GL_UNSIGNED_BYTE, rb)
        GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER, prevFbo[0])
        GLES20.glViewport(vp[0], vp[1], vp[2], vp[3])
        // GL rows run bottom -> top: flip so the model sees an upright picture
        val px = IntArray(sw * sh)
        for (y in 0 until sh) {
            val dst = (sh - 1 - y) * sw
            for (x in 0 until sw) {
                val o = (y * sw + x) * 4
                px[dst + x] = (0xFF shl 24) or ((rb.get(o).toInt() and 0xFF) shl 16) or ((rb.get(o + 1).toInt() and 0xFF) shl 8) or (rb.get(o + 2).toInt() and 0xFF)
            }
        }
        val bmp = Bitmap.createBitmap(px, sw, sh, Bitmap.Config.ARGB_8888)
        val m = try { seg.foreground(bmp, k.aiParts) } finally { bmp.recycle() } ?: return
        val shaped = AiMaskMath.shapeAll(m, k.aiInvert, k.aiEdge)
        // light temporal smoothing: less flicker between frames
        val pv = prevMask
        if (pv != null && pv.size == shaped.size) for (i in shaped.indices) shaped[i] = 0.65f * shaped[i] + 0.35f * pv[i]
        prevMask = shaped
        val mb = maskBuf ?: return
        mb.clear()
        for (i in 0 until sw * sh) mb.put((shaped[i].coerceIn(0f, 1f) * 255f + 0.5f).toInt().toByte())
        mb.rewind()
        GLES20.glActiveTexture(GLES20.GL_TEXTURE0)
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, maskTex)
        GLES20.glPixelStorei(GLES20.GL_UNPACK_ALIGNMENT, 1)
        GLES20.glTexImage2D(GLES20.GL_TEXTURE_2D, 0, GLES20.GL_LUMINANCE, sw, sh, 0, GLES20.GL_LUMINANCE, GLES20.GL_UNSIGNED_BYTE, mb)
    }

    override fun configure(inputWidth: Int, inputHeight: Int): Size {
        w = inputWidth
        h = inputHeight
        if (aiOn) {
            try {
                val s = 256f / maxOf(inputWidth, inputHeight)
                sw = maxOf(16, Math.round(inputWidth * s))
                sh = maxOf(16, Math.round(inputHeight * s))
                if (smallFbo != 0) GLES20.glDeleteFramebuffers(1, intArrayOf(smallFbo), 0)
                if (smallTex != 0) GLES20.glDeleteTextures(1, intArrayOf(smallTex), 0)
                val ids = IntArray(1)
                GLES20.glGenTextures(1, ids, 0)
                smallTex = ids[0]
                GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, smallTex)
                GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MIN_FILTER, GLES20.GL_LINEAR)
                GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MAG_FILTER, GLES20.GL_LINEAR)
                GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_S, GLES20.GL_CLAMP_TO_EDGE)
                GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_T, GLES20.GL_CLAMP_TO_EDGE)
                GLES20.glTexImage2D(GLES20.GL_TEXTURE_2D, 0, GLES20.GL_RGBA, sw, sh, 0, GLES20.GL_RGBA, GLES20.GL_UNSIGNED_BYTE, null)
                GLES20.glGenFramebuffers(1, ids, 0)
                smallFbo = ids[0]
                val prev = IntArray(1)
                GLES20.glGetIntegerv(GLES20.GL_FRAMEBUFFER_BINDING, prev, 0)
                GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER, smallFbo)
                GLES20.glFramebufferTexture2D(GLES20.GL_FRAMEBUFFER, GLES20.GL_COLOR_ATTACHMENT0, GLES20.GL_TEXTURE_2D, smallTex, 0)
                val ok = GLES20.glCheckFramebufferStatus(GLES20.GL_FRAMEBUFFER) == GLES20.GL_FRAMEBUFFER_COMPLETE
                GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER, prev[0])
                if (!ok) throw IllegalStateException("framebuffer")
                readBuf = java.nio.ByteBuffer.allocateDirect(sw * sh * 4)
                maskBuf = java.nio.ByteBuffer.allocateDirect(sw * sh)
                prevMask = null
            } catch (e: Exception) {
                throw VideoFrameProcessingException(e)
            }
        }
        return Size(inputWidth, inputHeight)
    }

    override fun drawFrame(inputTexId: Int, presentationTimeUs: Long) {
        try {
            if (aiOn) updateMask(inputTexId)
            program.use()
            program.setSamplerTexIdUniform("uTex", inputTexId, 0)
            program.setSamplerTexIdUniform("uBg", bgTex, 1)
            program.setSamplerTexIdUniform("uMask", maskTex, 2)
            program.setFloatsUniform("uAi", floatArrayOf(if (aiOn) 1f else 0f))
            program.setFloatsUniform("uKey", floatArrayOf(keyRgb[0], keyRgb[1], keyRgb[2]))
            program.setFloatsUniform("uPrm", floatArrayOf(k.tol, k.soft, k.spill))
            program.setFloatsUniform(
                "uBgC", floatArrayOf(Color.red(k.bgColor) / 255f, Color.green(k.bgColor) / 255f, Color.blue(k.bgColor) / 255f, mode)
            )
            program.setFloatsUniform("uSize", floatArrayOf(w.toFloat(), h.toFloat()))
            program.setFloatsUniform("uBgSize", floatArrayOf(bgW, bgH))
            program.bindAttributesAndUniforms()
            GLES20.glDrawArrays(GLES20.GL_TRIANGLE_STRIP, 0, 4)
        } catch (e: Exception) {
            throw VideoFrameProcessingException(e, presentationTimeUs)
        }
    }

    override fun release() {
        super.release()
        try {
            program.delete()
            copyProg?.delete()
            aiSeg?.close()
            if (bgTex != 0) GLES20.glDeleteTextures(1, intArrayOf(bgTex), 0)
            if (maskTex != 0) GLES20.glDeleteTextures(1, intArrayOf(maskTex), 0)
            if (smallTex != 0) GLES20.glDeleteTextures(1, intArrayOf(smallTex), 0)
            if (smallFbo != 0) GLES20.glDeleteFramebuffers(1, intArrayOf(smallFbo), 0)
        } catch (e: Exception) {
            throw VideoFrameProcessingException(e)
        }
    }

    companion object {
        private const val VERT = """
attribute vec4 aFramePosition;
varying vec2 vTexSamplingCoord;
void main() {
  gl_Position = aFramePosition;
  vTexSamplingCoord = aFramePosition.xy * 0.5 + 0.5;
}
"""

        private const val FRAG_COPY = """
precision mediump float;
uniform sampler2D uTex;
varying vec2 vTexSamplingCoord;
void main() {
  gl_FragColor = vec4(texture2D(uTex, vTexSamplingCoord).rgb, 1.0);
}
"""

        private const val FRAG = """
precision highp float;
uniform sampler2D uTex;
uniform sampler2D uBg;
uniform sampler2D uMask;
uniform float uAi;
uniform vec3 uKey;
uniform vec3 uPrm;
uniform vec4 uBgC;
uniform vec2 uSize;
uniform vec2 uBgSize;
varying vec2 vTexSamplingCoord;
${ChromaKeyMath.SNIPPET}
void main() {
  vec3 c = texture2D(uTex, vTexSamplingCoord).rgb;
  vec4 kk = zkKey(c, uKey, uPrm);
  if (uAi > 0.5) {
    kk = vec4(c, texture2D(uMask, vec2(vTexSamplingCoord.x, 1.0 - vTexSamplingCoord.y)).r);
  }
  vec3 bgc = uBgC.rgb;
  if (uBgC.a > 1.5) {
    float fa = uSize.x / uSize.y;
    float ba = uBgSize.x / uBgSize.y;
    vec2 q = vTexSamplingCoord - 0.5;
    if (ba > fa) {
      q.x = q.x * (fa / ba);
    } else {
      q.y = q.y * (ba / fa);
    }
    q = q + 0.5;
    bgc = texture2D(uBg, vec2(q.x, 1.0 - q.y)).rgb;
  }
  gl_FragColor = vec4(mix(bgc, kk.rgb, kk.a), 1.0);
}
"""
    }
}
