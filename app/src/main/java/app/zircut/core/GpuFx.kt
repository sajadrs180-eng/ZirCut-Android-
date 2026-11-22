package app.zircut.core

import android.content.Context
import android.opengl.GLES20
import androidx.media3.common.VideoFrameProcessingException
import androidx.media3.common.util.GlProgram
import androidx.media3.common.util.GlUtil
import androidx.media3.common.util.Size
import androidx.media3.common.util.UnstableApi
import androidx.media3.effect.BaseGlShaderProgram
import androidx.media3.effect.GlEffect
import androidx.media3.effect.GlShaderProgram

/** Transition played at every cut between two clips: the end of a clip leaves, the start of the next one arrives. */
enum class TrKind(val id: String, val nameFa: String, val code: Float) {
    NONE("none", "بدون", 0f),
    FADE("fade", "محو", 1f),
    ZOOM("zoom", "زوم", 2f),
    SLIDE("slide", "اسلاید", 3f),
    SPIN("spin", "چرخش", 4f),
    BLUR("blur", "بلور", 5f),
    GLITCH("glitch", "گلیچ", 6f);

    companion object {
        fun byId(id: String): TrKind = values().firstOrNull { it.id == id } ?: NONE
    }
}

/** How far a transition has gone (0 = picture normal, 1 = fully hidden) and which way it moves (+1 leaving, -1 arriving). */
class TrState(val s: Float, val dir: Float, val kind: TrKind = TrKind.NONE)

/** the transition of one cut: its kind and length */
class CutTr(val kind: TrKind, val ms: Long) {
    companion object { val NONE = CutTr(TrKind.NONE, 0L) }
}

/** Which transition plays at each cut: a clip's own setting (Range.tr / trMs) or else the project's (Fx.trKind / trMs). */
object Cuts {
    /** the cut BEFORE clip [i] (none for the first clip) */
    fun enter(ranges: List<Range>, i: Int, fx: Fx): CutTr {
        if (i <= 0 || i >= ranges.size) return CutTr.NONE
        val r = ranges[i]
        val kinds = TrKind.values()
        val kind = if (r.tr in kinds.indices) kinds[r.tr] else TrKind.byId(fx.trKind)
        return CutTr(kind, if (r.trMs > 0L) r.trMs else fx.trMs)
    }

    /** the cut AFTER clip [i] (none for the last clip) = the entrance cut of the next clip */
    fun exit(ranges: List<Range>, i: Int, fx: Fx): CutTr = if (i < 0 || i >= ranges.size - 1) CutTr.NONE else enter(ranges, i + 1, fx)
}

/** ONE function for preview and export, so both play the transition at the same moments. */
object TrMath {
    /** [enter] = the cut before this clip, [exit] = the cut after it (each with its own kind and length) */
    fun at(msInClip: Long, lenMs: Long, first: Boolean, last: Boolean, enter: CutTr, exit: CutTr): TrState? {
        if (!last && exit.kind != TrKind.NONE) {
            val d = minOf(exit.ms, lenMs / 2L)
            if (d >= 40L && msInClip > lenMs - d) {
                val x = ((msInClip - (lenMs - d)).toFloat() / d).coerceIn(0f, 1f)
                return TrState(x * x * (3f - 2f * x), 1f, exit.kind)
            }
        }
        if (!first && enter.kind != TrKind.NONE) {
            val d = minOf(enter.ms, lenMs / 2L)
            if (d >= 40L && msInClip < d) {
                val x = (1f - msInClip.toFloat() / d).coerceIn(0f, 1f)
                return TrState(x * x * (3f - 2f * x), -1f, enter.kind)
            }
        }
        return null
    }
}

/** Where one clip sits in the export: its start on the edited timeline (for frame-time detection) and its length. */
class ClipTiming(
    val startUs: Long, val lenMs: Long, val first: Boolean, val last: Boolean,
    val enter: CutTr = CutTr.NONE, val exit: CutTr = CutTr.NONE
)

/** Custom GL effect: blur, sharpen, RGB split, glitch, noise, glow, highlights/shadows and the cut transition, in one pass. */
@UnstableApi
class GpuFxEffect(private val fx: Fx, private val clip: ClipTiming) : GlEffect {
    override fun toGlShaderProgram(context: Context, useHdr: Boolean): GlShaderProgram = GpuFxProgram(fx, clip)
}

@UnstableApi
private class GpuFxProgram(private val fx: Fx, private val clip: ClipTiming) : BaseGlShaderProgram(false, 1) {
    private val program: GlProgram = makeProgram()
    private var w = 1
    private var h = 1
    private var mode = -1

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

    override fun configure(inputWidth: Int, inputHeight: Int): Size {
        w = inputWidth
        h = inputHeight
        return Size(inputWidth, inputHeight)
    }

    // frame times are either relative to the clip or to the whole export (same detection the overlays use)
    private fun clipMs(us: Long): Long {
        if (mode < 0) mode = if (clip.startUs > 0 && us < clip.startUs / 2) 1 else 0
        val abs = if (mode == 1) us + clip.startUs else us
        return (abs - clip.startUs) / 1000L
    }

    override fun drawFrame(inputTexId: Int, presentationTimeUs: Long) {
        try {
            val ms = clipMs(presentationTimeUs)
            val tr = TrMath.at(ms, clip.lenMs, clip.first, clip.last, clip.enter, clip.exit)
            program.use()
            program.setSamplerTexIdUniform("uTex", inputTexId, 0)
            program.setFloatsUniform("uSize", floatArrayOf(w.toFloat(), h.toFloat()))
            program.setFloatsUniform("uA", floatArrayOf(fx.blur, fx.sharpen, fx.split, fx.glitch))
            program.setFloatsUniform("uB", floatArrayOf(fx.noise, fx.glow, fx.highlights, fx.shadows))
            program.setFloatsUniform(
                "uC", floatArrayOf(tr?.kind?.code ?: 0f, tr?.s ?: 0f, tr?.dir ?: 0f, (presentationTimeUs % 100000000L) / 1000000f)
            )
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

        // uA = blur, sharpen, split, glitch   uB = noise, glow, highlights, shadows   uC = transition type, strength, direction, time (s)
        private const val FRAG = """
precision highp float;
uniform sampler2D uTex;
uniform vec2 uSize;
uniform vec4 uA;
uniform vec4 uB;
uniform vec4 uC;
varying vec2 vTexSamplingCoord;

float rnd(vec2 p) {
  return fract(sin(dot(p, vec2(12.9898, 78.233))) * 43758.5453);
}

vec3 fetch(vec2 q, float sp) {
  float r = texture2D(uTex, clamp(q + vec2(sp, 0.0), 0.0, 1.0)).r;
  float g = texture2D(uTex, clamp(q, 0.0, 1.0)).g;
  float b = texture2D(uTex, clamp(q - vec2(sp, 0.0), 0.0, 1.0)).b;
  return vec3(r, g, b);
}

void main() {
  float ar = uSize.x / uSize.y;
  float type = uC.x;
  float s = uC.y;
  float dir = uC.z;
  float t = uC.w;
  float blurAmt = uA.x;
  float glitch = uA.w;
  float tf = 1.0;
  vec2 c = vTexSamplingCoord - 0.5;
  if (type > 0.5 && s > 0.0) {
    if (type < 1.5) {
      tf = 1.0 - s;
    } else if (type < 2.5) {
      c *= 1.0 - 0.35 * s;
      tf = 1.0 - s * s;
    } else if (type < 3.5) {
      c.x += dir * s;
    } else if (type < 4.5) {
      float a = dir * s * 1.2;
      float ca = cos(a);
      float sa = sin(a);
      vec2 d = vec2(c.x * ar, c.y);
      d = vec2(ca * d.x - sa * d.y, sa * d.x + ca * d.y);
      c = vec2(d.x / ar, d.y) * (1.0 - 0.3 * s);
      tf = 1.0 - s * s;
    } else if (type < 5.5) {
      blurAmt += s * 1.2;
      tf = 1.0 - 0.6 * s * s;
    } else {
      glitch += s;
      tf = 1.0 - s * s;
    }
  }
  vec2 q = c + 0.5;
  float inside = step(0.0, q.x) * step(q.x, 1.0) * step(0.0, q.y) * step(q.y, 1.0);
  float sp = uA.z * 0.02;
  if (glitch > 0.0) {
    float row = floor(q.y * 40.0);
    float tt = floor(t * 12.0);
    float n = rnd(vec2(row, tt));
    float bar = step(0.85, n);
    q.x += (rnd(vec2(row + 3.0, tt)) - 0.5) * 0.12 * glitch * bar;
    sp += 0.015 * glitch * (0.3 + bar);
  }
  vec3 col;
  if (blurAmt > 0.0) {
    float rad = 0.025 * blurAmt;
    vec3 sum = fetch(q, sp);
    for (int i = 0; i < 16; i++) {
      float a = float(i) * 0.3927;
      float k = 0.4 + 0.6 * mod(float(i), 2.0);
      vec2 o = vec2(cos(a) / ar, sin(a)) * rad * k;
      sum += fetch(q + o, sp);
    }
    col = sum / 17.0;
  } else {
    col = fetch(q, sp);
  }
  if (uA.y > 0.0) {
    vec2 px = 1.0 / uSize;
    vec3 n1 = texture2D(uTex, clamp(q + vec2(px.x, 0.0), 0.0, 1.0)).rgb;
    vec3 n2 = texture2D(uTex, clamp(q - vec2(px.x, 0.0), 0.0, 1.0)).rgb;
    vec3 n3 = texture2D(uTex, clamp(q + vec2(0.0, px.y), 0.0, 1.0)).rgb;
    vec3 n4 = texture2D(uTex, clamp(q - vec2(0.0, px.y), 0.0, 1.0)).rgb;
    col += uA.y * 0.8 * (4.0 * col - n1 - n2 - n3 - n4);
  }
  if (uB.y > 0.0) {
    vec3 gl = vec3(0.0);
    for (int i = 0; i < 12; i++) {
      float a = float(i) * 0.5236;
      float k = 0.5 + 0.5 * mod(float(i), 2.0);
      vec2 o = vec2(cos(a) / ar, sin(a)) * 0.03 * k;
      gl += max(texture2D(uTex, clamp(q + o, 0.0, 1.0)).rgb - vec3(0.55), vec3(0.0));
    }
    col += gl / 12.0 * uB.y * 2.2;
  }
  float lum = dot(col, vec3(0.2126, 0.7152, 0.0722));
  col += vec3(uB.z * 0.25) * smoothstep(0.5, 1.0, lum);
  col += vec3(uB.w * 0.25) * (1.0 - smoothstep(0.0, 0.5, lum));
  if (uB.x > 0.0) {
    col += (rnd(q * uSize + vec2(t * 37.0, t * 91.0)) - 0.5) * uB.x * 0.35;
  }
  col *= tf * inside;
  gl_FragColor = vec4(clamp(col, 0.0, 1.0), 1.0);
}
"""
    }
}
