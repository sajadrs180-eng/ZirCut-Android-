package app.zircut.core

import android.content.Context
import android.graphics.Bitmap
import com.google.mediapipe.framework.image.BitmapImageBuilder
import com.google.mediapipe.framework.image.ByteBufferExtractor
import com.google.mediapipe.framework.image.MPImage
import com.google.mediapipe.tasks.core.BaseOptions
import com.google.mediapipe.tasks.vision.core.RunningMode
import com.google.mediapipe.tasks.vision.imagesegmenter.ImageSegmenter
import java.nio.ByteOrder

/** Foreground probability (0..1) per pixel, row 0 = top of the picture. */
class AiMask(val w: Int, val h: Int, val v: FloatArray)

/**
 * On-phone person segmentation (fix236). Google MediaPipe "selfie segmenter" model, bundled INSIDE the APK
 * (assets/ai/selfie_segmenter.tflite, put there at build time by the `downloadAiModels` Gradle task). No internet,
 * no server and no download at run time. One model serves background removal and the smart subject mask.
 */
class AiSegmenter(context: Context, private val multi: Boolean = false) : AutoCloseable {
    private val seg: ImageSegmenter

    init {
        val base = BaseOptions.builder().setModelAssetPath(if (multi) MODEL_MULTI else MODEL).build()
        val opt = ImageSegmenter.ImageSegmenterOptions.builder()
            .setBaseOptions(base)
            .setRunningMode(RunningMode.IMAGE)
            .setOutputCategoryMask(false)
            .setOutputConfidenceMasks(true)
            .build()
        seg = ImageSegmenter.createFromOptions(context.applicationContext, opt)
    }

    private fun floats(m: MPImage): FloatArray? {
        val fb = ByteBufferExtractor.extract(m).order(ByteOrder.nativeOrder()).asFloatBuffer()
        val n = m.width * m.height
        if (m.width <= 0 || m.height <= 0 || fb.remaining() < n) return null
        val raw = FloatArray(n)
        fb.get(raw, 0, n)
        return raw
    }

    /**
     * Subject probability for a bitmap (ARGB_8888). The mask always has the bitmap's own size. Null when the model gave nothing.
     * [parts] only matters for the multi-class model (fix239): bit mask of the parts to keep, see [AiMaskMath.mixParts]; 0 = every part.
     */
    fun foreground(bmp: Bitmap, parts: Int = 0): AiMask? {
        val res = seg.segment(BitmapImageBuilder(bmp).build())
        val opt = res.confidenceMasks()
        if (!opt.isPresent) return null
        val list = opt.get()
        if (list.isEmpty()) return null
        val mw: Int
        val mh: Int
        val raw: FloatArray
        if (multi) {
            // six masks: [background, hair, body skin, face skin, clothes, others]
            if (list.size < 6) return null
            val all = ArrayList<FloatArray>()
            for (i in 0 until 6) all.add(floats(list[i]) ?: return null)
            mw = list[0].width
            mh = list[0].height
            raw = AiMaskMath.mixParts(all, parts)
        } else {
            // two masks = [background, subject]; one mask = subject
            val m = if (list.size >= 2) list[list.size - 1] else list[0]
            mw = m.width
            mh = m.height
            raw = floats(m) ?: return null
        }
        if (mw == bmp.width && mh == bmp.height) return AiMask(mw, mh, raw)
        // nearest-neighbour resample to the bitmap size
        val out = FloatArray(bmp.width * bmp.height)
        for (y in 0 until bmp.height) {
            val sy = (y * mh / bmp.height).coerceIn(0, mh - 1)
            for (x in 0 until bmp.width) out[y * bmp.width + x] = raw[sy * mw + (x * mw / bmp.width).coerceIn(0, mw - 1)]
        }
        return AiMask(bmp.width, bmp.height, out)
    }

    override fun close() {
        try { seg.close() } catch (_: Throwable) {}
    }

    companion object {
        const val MODEL = "ai/selfie_segmenter.tflite"
        /** fix239: hair / body / face / clothes model (MediaPipe selfie multi-class, Apache-2.0), bundled in the APK like [MODEL] */
        const val MODEL_MULTI = "ai/selfie_multiclass_256x256.tflite"

        /** true when this build contains the multi-class model (the panel hides the part chips otherwise) */
        fun multiAvailable(context: Context): Boolean =
            try { context.applicationContext.assets.open(MODEL_MULTI).close(); true } catch (_: Throwable) { false }

        /** one-shot helper for the panel's still preview */
        fun once(context: Context, bmp: Bitmap, parts: Int = 0): AiMask? {
            val s = AiSegmenter(context, parts != 0)
            return try { s.foreground(bmp, parts) } finally { s.close() }
        }
    }
}

/** The ONE shaping of the raw probability (used by the still preview and the export): invert + soft edge. */
object AiMaskMath {
    /**
     * Multi-class model: [probs] = [background, hair, body skin, face skin, clothes, others]. [parts] is a bit mask of the classes to keep
     * (1 hair, 2 body skin, 4 face skin, 8 clothes, 16 others). 0 = keep every part (= 1 - background). Result 0..1 per pixel.
     */
    fun mixParts(probs: List<FloatArray>, parts: Int): FloatArray {
        val n = probs[0].size
        val out = FloatArray(n)
        if (parts == 0) {
            for (i in 0 until n) out[i] = (1f - probs[0][i]).coerceIn(0f, 1f)
            return out
        }
        for (c in 1..5) {
            if ((parts and (1 shl (c - 1))) == 0) continue
            val pc = probs[c]
            for (i in 0 until n) out[i] += pc[i]
        }
        for (i in 0 until n) out[i] = out[i].coerceIn(0f, 1f)
        return out
    }

    fun shape(p: Float, invert: Boolean, edge: Float): Float {
        val f = if (invert) 1f - p else p
        val wd = 0.05f + 0.45f * edge.coerceIn(0f, 1f)
        val t = ((f - (0.5f - wd)) / (2f * wd)).coerceIn(0f, 1f)
        return t * t * (3f - 2f * t)
    }

    fun shapeAll(m: AiMask, invert: Boolean, edge: Float): FloatArray = FloatArray(m.v.size) { shape(m.v[it], invert, edge) }
}
