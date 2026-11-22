package app.zircut.core

import android.content.Context
import android.graphics.Bitmap
import com.google.mediapipe.framework.image.BitmapImageBuilder
import com.google.mediapipe.framework.image.ByteBufferExtractor
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
class AiSegmenter(context: Context) : AutoCloseable {
    private val seg: ImageSegmenter

    init {
        val base = BaseOptions.builder().setModelAssetPath(MODEL).build()
        val opt = ImageSegmenter.ImageSegmenterOptions.builder()
            .setBaseOptions(base)
            .setRunningMode(RunningMode.IMAGE)
            .setOutputCategoryMask(false)
            .setOutputConfidenceMasks(true)
            .build()
        seg = ImageSegmenter.createFromOptions(context.applicationContext, opt)
    }

    /** Subject probability for a bitmap (ARGB_8888). The mask always has the bitmap's own size. Null when the model gave nothing. */
    fun foreground(bmp: Bitmap): AiMask? {
        val res = seg.segment(BitmapImageBuilder(bmp).build())
        val opt = res.confidenceMasks()
        if (!opt.isPresent) return null
        val list = opt.get()
        if (list.isEmpty()) return null
        // two masks = [background, subject]; one mask = subject
        val m = if (list.size >= 2) list[list.size - 1] else list[0]
        val fb = ByteBufferExtractor.extract(m).order(ByteOrder.nativeOrder()).asFloatBuffer()
        val mw = m.width
        val mh = m.height
        if (mw <= 0 || mh <= 0 || fb.remaining() < mw * mh) return null
        val raw = FloatArray(mw * mh)
        fb.get(raw, 0, mw * mh)
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

        /** one-shot helper for the panel's still preview */
        fun once(context: Context, bmp: Bitmap): AiMask? {
            val s = AiSegmenter(context)
            return try { s.foreground(bmp) } finally { s.close() }
        }
    }
}

/** The ONE shaping of the raw probability (used by the still preview and the export): invert + soft edge. */
object AiMaskMath {
    fun shape(p: Float, invert: Boolean, edge: Float): Float {
        val f = if (invert) 1f - p else p
        val wd = 0.05f + 0.45f * edge.coerceIn(0f, 1f)
        val t = ((f - (0.5f - wd)) / (2f * wd)).coerceIn(0f, 1f)
        return t * t * (3f - 2f * t)
    }

    fun shapeAll(m: AiMask, invert: Boolean, edge: Float): FloatArray = FloatArray(m.v.size) { shape(m.v[it], invert, edge) }
}
