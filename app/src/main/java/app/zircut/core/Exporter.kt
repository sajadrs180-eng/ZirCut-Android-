package app.zircut.core

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Typeface
import android.net.Uri
import android.os.Handler
import android.os.Looper
import androidx.media3.common.Effect
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.common.util.UnstableApi
import androidx.media3.common.util.Size
import androidx.media3.effect.BitmapOverlay
import androidx.media3.effect.Crop
import androidx.media3.effect.FrameDropEffect
import androidx.media3.effect.MatrixTransformation
import androidx.media3.effect.OverlaySettings
import androidx.media3.effect.OverlayEffect
import androidx.media3.effect.VideoCompositorSettings
import androidx.media3.effect.Presentation
import androidx.media3.effect.RgbMatrix
import androidx.media3.effect.ScaleAndRotateTransformation
import androidx.media3.effect.SingleColorLut
import androidx.media3.effect.TextureOverlay
import androidx.media3.transformer.Composition
import androidx.media3.transformer.DefaultEncoderFactory
import androidx.media3.transformer.EditedMediaItem
import androidx.media3.transformer.EditedMediaItemSequence
import androidx.media3.transformer.Effects
import androidx.media3.transformer.ExportException
import androidx.media3.transformer.ExportResult
import androidx.media3.transformer.ProgressHolder
import androidx.media3.transformer.Transformer
import androidx.media3.transformer.VideoEncoderSettings
import app.zircut.timeline.Layer
import app.zircut.timeline.LayerAnim
import app.zircut.timeline.LayerKeys
import app.zircut.timeline.VideoOverlay
import app.zircut.timeline.VideoOverlayKeys
import com.google.common.collect.ImmutableList
import java.io.File
import java.io.FileOutputStream
import kotlin.math.min

@UnstableApi
class Exporter(private val ctx: Context) {
    private var transformer: Transformer? = null
    private val handler = Handler(Looper.getMainLooper())
    private var polling = false

    /** Where clip i comes from when the timeline mixes several media. Image clips have no clipping, only a duration. */
    class ClipSource(val uri: Uri, val image: Boolean, val w: Int = 0, val h: Int = 0)

    class Params(
        val uri: Uri,
        val info: VideoInfo,
        val ranges: List<Range>,
        val phrases: List<Phrase>,
        val style: SubStyle,
        val typeface: Typeface,
        val options: Options,
        val out: File,
        val layout: CapLayout = CapLayout(),
        val grade: ColorGrade = ColorGrade(),
        /** parallel to [ranges]; null = every clip comes from [uri] (single video project) */
        val sources: List<ClipSource>? = null,
        /** clip list used for subtitles (clips without subtitles get impossible source times) */
        val captionRanges: List<Range>? = null,
        /** text and picture layers (times on the edited timeline) */
        val layers: List<Layer> = emptyList(),
        /** project folder where picture layers live */
        val projectDir: File? = null,
        /** typeface of a text layer by font id ("" = project font) */
        val layerFont: (String) -> Typeface = { typeface },
        /** finished stereo WAV with the whole sound track (video sound + music + voice-over); null = keep each clip's own sound */
        val mixAudio: File? = null,
        /** whole-video effects: vignette, grain, cinema bars, light leak */
        val fx: Fx = Fx(),
        /** resolution / frame rate / codec / bitrate chosen in the export sheet */
        val settings: ExportSettings = ExportSettings(),
        /** independent video layers, placed on the edited output timeline */
        val videoOverlays: List<VideoOverlay> = emptyList()
    )

    /** Must be called on the main thread. */
    fun start(p: Params, onProgress: (Float) -> Unit, onDone: () -> Unit, onError: (String) -> Unit) =
        run(p, 0, onProgress, onDone, onError)

    /** level 0 = best quality; if the phone's encoder refuses, level 1 (30 fps, lower bitrate) and level 2 (720p) are tried automatically. */
    private fun run(p: Params, level: Int, onProgress: (Float) -> Unit, onDone: () -> Unit, onError: (String) -> Unit) {
        val opt = p.options
        val srcW = p.info.width
        val srcH = p.info.height

        val frameAspect = Frame.aspect(opt)
        // Automatic = full HD or better as before; otherwise the size the user chose (level 2 = emergency fallback)
        val (targetW, targetH) = ExportPlan.size(p.settings, frameAspect, srcW, srcH, level)
        // frame rate: 0 = keep the video's own; the fallback levels drop to 30 at most
        val wantFps = if (level >= 1) min(30, if (p.settings.fps > 0) p.settings.fps else 30) else p.settings.fps

        // layers are drawn once per run at the real output size, then only moved/faded per frame
        val baked = p.layers.filter { !it.hidden }.mapNotNull { l ->
            val bs = LayerRenderer.bakeScale(LayerKeys.maxScale(l))
            val b = LayerRenderer.base(l, targetW, targetH, p.layerFont(l.fontId), p.projectDir, bs) ?: return@mapNotNull null
            // a layer whose turn is keyframed (fix229) is rotated per frame by the overlay; every other layer is baked as before
            BakedLayer(l, if (LayerKeys.rotationAnimated(l)) b else LayerRenderer.bakeRotation(b, l.rotation), bs)
        }
        val fxLayers = FxRender.build(p.fx, targetW, targetH)
        // colour LUT (.cube): the exact table goes to Media3; if the file is missing or broken the export simply goes on without it
        val lutObj: CubeLut? = if (p.fx.hasLut && p.projectDir != null) {
            try { CubeLut.load(File(p.projectDir, p.fx.lutFile)) } catch (_: Throwable) { null }
        } else null
        val lutCube: Array<Array<IntArray>>? = try { lutObj?.cube(p.fx.lutAmt) } catch (_: Throwable) { null }
        // the same matrix the preview uses for the LUT: the chroma key compares colours after the grade
        val lutFit: Affine? = try { lutObj?.takeIf { p.fx.lutAmt > 0.01f }?.fit(p.fx.lutAmt) } catch (_: Throwable) { null }
        // chroma key backgrounds (pictures from the project folder), decoded once per file
        val keyBgCache = HashMap<String, Bitmap?>()
        val timeline = CaptionTimeline(p.phrases, p.captionRanges ?: p.ranges)
        val renderer = CaptionRenderer(targetW, targetH, p.style, p.typeface, p.layout)
        val totalDurationMs = p.ranges.sumOf { it.len }
        val overlayItems = p.videoOverlays.filter {
            it.valid() && !it.hidden && it.startMs < totalDurationMs && p.projectDir != null && File(p.projectDir, it.file).isFile
        }.take(VideoOverlay.MAX_LAYERS)
        val hasPip = overlayItems.isNotEmpty()
        var acc = 0L
        val multi = p.sources != null
        val items = p.ranges.mapIndexed { i, r ->
            val cs = p.sources?.getOrNull(i)
            val isImage = cs?.image == true
            val mi = if (isImage) {
                MediaItem.fromUri(cs!!.uri)
            } else {
                MediaItem.Builder()
                    .setUri(cs?.uri ?: p.uri)
                    .setClippingConfiguration(
                        MediaItem.ClippingConfiguration.Builder()
                            .setStartPositionMs(r.startMs)
                            .setEndPositionMs(r.endMs)
                            .build()
                    ).build()
            }
            val v = ArrayList<Effect>()
            if (wantFps > 0 && !isImage) v.add(FrameDropEffect.createDefaultFrameDropEffect(wantFps.toFloat()))
            if (!p.grade.isIdentity) v.add(GradeEffect(p.grade.affine().toRgbMatrix()))
            if (lutCube != null) {
                try { v.add(SingleColorLut.createFromCube(lutCube)) } catch (_: Throwable) { }
            }
            // effects of this clip only: colour matrix after the project's grade and LUT (same order as the preview)
            if (r.look.hasColor) v.add(GradeEffect(r.look.affine().toRgbMatrix()))
            // picture transform (rotate 90s, zoom, pan) of video clips: same maths as the preview
            if (!isImage && p.fx.hasTransform) {
                val cw = if ((cs?.w ?: 0) > 0) cs!!.w else srcW
                val ch = if ((cs?.h ?: 0) > 0) cs!!.h else srcH
                v.addAll(transformEffects(p.fx, cw, ch, targetW, targetH, acc * 1000L))
            }
            if (opt.zoom && i % 2 == 1) {
                v.add(ScaleAndRotateTransformation.Builder().setScale(1.18f, 1.18f).build())
            }
            // when media of different shapes are mixed, every clip is scaled/cropped to the same output size
            if (frameAspect > 0f || multi) {
                v.add(Presentation.createForWidthAndHeight(targetW, targetH, Presentation.LAYOUT_SCALE_TO_FIT_WITH_CROP))
            } else {
                v.add(Presentation.createForHeight(targetH))
            }
            // free-angle rotation of the finished frame (the preview rotates the video picture inside the frame the same way)
            if (!isImage && p.fx.hasFine) v.add(if (p.fx.vkeys.isNotEmpty()) KeyedFineRotation(p.fx, targetW, targetH, acc * 1000L) else FineRotation(p.fx.fine, targetW, targetH))
            // keyframed opacity of the video picture (fix231): the picture is faded towards black (RGB scaled), as if laid over black
            if (!isImage && p.fx.hasFade) v.add(KeyedFade(p.fx, acc * 1000L))
            // chroma key (green screen): graded picture is keyed and blended with the background right here, before the blur / transition shader and overlays
            if (!isImage && r.look.key.on) {
                val kl = r.look.key
                val keyRgb = ChromaKeyMath.gradedKey(kl, p.grade, lutFit, r.look)
                val bgBmp: Bitmap? = if (kl.bgMode == 2 && kl.bgFile.isNotEmpty() && p.projectDir != null) {
                    keyBgCache.getOrPut(kl.bgFile) { decodeKeyBackground(File(p.projectDir, kl.bgFile)) }
                } else null
                v.add(ChromaKeyEffect(keyRgb, kl, bgBmp))
            }
            // blur / sharpen / RGB split / glitch / noise / glow / highlights-shadows and the cut transition (one custom shader)
            val enterTr = Cuts.enter(p.ranges, i, p.fx)
            val exitTr = Cuts.exit(p.ranges, i, p.fx)
            // blur / sharpen of this clip add to the project's values
            val clipFx = if (r.look.blur > 0f || r.look.sharpen > 0f)
                p.fx.copy(blur = (p.fx.blur + r.look.blur).coerceAtMost(1f), sharpen = (p.fx.sharpen + r.look.sharpen).coerceAtMost(1f))
            else p.fx
            if (clipFx.hasShader || enterTr.kind != TrKind.NONE || exitTr.kind != TrKind.NONE) {
                v.add(GpuFxEffect(clipFx, ClipTiming(acc * 1000L, r.len, i == 0, i == p.ranges.size - 1, enterTr, exitTr)))
            }
            if (!hasPip) {
                val overlays = ArrayList<TextureOverlay>()
                for (fl in fxLayers) overlays.add(FxOverlay(fl, targetW, targetH))
                for (b in baked) {
                    if (b.layer.startMs < acc + r.len && b.layer.endMs > acc) overlays.add(LayerOverlay(b, acc * 1000L))
                }
                if (opt.captions && p.phrases.isNotEmpty()) {
                    overlays.add(CaptionOverlay(timeline, p.phrases, renderer, p.style, p.layout, acc * 1000L))
                }
                if (overlays.isNotEmpty()) v.add(OverlayEffect(ImmutableList.copyOf(overlays)))
            }
            acc += r.len
            val eb = EditedMediaItem.Builder(mi).setEffects(Effects(emptyList(), v))
            if (isImage) eb.setDurationUs(r.len * 1000L).setFrameRate(if (wantFps > 0) wantFps else 30)
            // the mixed track already contains the video's own sound, so the clips themselves go silent
            else if (p.mixAudio != null) eb.setRemoveAudio(true)
            eb.build()
        }

        val videoSeq = EditedMediaItemSequence(items)
        val transparentPng = if (hasPip) ensureTransparentPng() else null
        fun transparentItem(durationMs: Long, frameWidth: Int, frameHeight: Int, frameRate: Int): EditedMediaItem {
            val image = MediaItem.Builder().setUri(Uri.fromFile(transparentPng!!))
                .setMimeType(MimeTypes.IMAGE_PNG).setImageDurationMs(durationMs).build()
            val fit = Presentation.createForWidthAndHeight(frameWidth, frameHeight, Presentation.LAYOUT_SCALE_TO_FIT)
            return EditedMediaItem.Builder(image).setDurationUs(durationMs * 1000L)
                .setFrameRate(frameRate.coerceAtLeast(1)).setRemoveAudio(true)
                .setEffects(Effects(emptyList(), listOf(fit))).build()
        }
        val overlaySequences = overlayItems.map { layer ->
            val source = File(p.projectDir!!, layer.file)
            val startMs = layer.startMs.coerceIn(0L, totalDurationMs)
            val durationMs = minOf(layer.len, layer.sourceDurationMs, (totalDurationMs - startMs).coerceAtLeast(0L))
            val endMs = startMs + durationMs
            val sourceW = layer.sourceWidth.coerceAtLeast(2)
            val sourceH = layer.sourceHeight.coerceAtLeast(2)
            val pipPresentation = Presentation.createForWidthAndHeight(sourceW, sourceH, Presentation.LAYOUT_SCALE_TO_FIT)
            val media = MediaItem.Builder().setUri(Uri.fromFile(source)).setClippingConfiguration(
                MediaItem.ClippingConfiguration.Builder().setStartPositionMs(0L).setEndPositionMs(durationMs).build()
            ).build()
            val pipItem = EditedMediaItem.Builder(media).setRemoveAudio(true)
                .setEffects(Effects(emptyList(), listOf(pipPresentation, VideoOverlayMaskEffect(layer.maskType)))).build()
            val seqItems = ArrayList<EditedMediaItem>()
            if (startMs > 0L) seqItems.add(transparentItem(startMs, sourceW, sourceH, wantFps.takeIf { it > 0 } ?: 30))
            seqItems.add(pipItem)
            if (endMs < totalDurationMs) seqItems.add(transparentItem(totalDurationMs - endMs, sourceW, sourceH, wantFps.takeIf { it > 0 } ?: 30))
            EditedMediaItemSequence(seqItems)
        }
        val compositionVideoOverlays = ArrayList<TextureOverlay>()
        if (hasPip) {
            for (fl in fxLayers) compositionVideoOverlays.add(FxOverlay(fl, targetW, targetH))
            for (b in baked) compositionVideoOverlays.add(LayerOverlay(b, 0L))
            if (opt.captions && p.phrases.isNotEmpty()) {
                compositionVideoOverlays.add(CaptionOverlay(timeline, p.phrases, renderer, p.style, p.layout, 0L))
            }
        }
        val topOverlaySequence = if (hasPip && compositionVideoOverlays.isNotEmpty()) {
            val overlayEffect = OverlayEffect(ImmutableList.copyOf(compositionVideoOverlays))
            val overlayMedia = MediaItem.Builder().setUri(Uri.fromFile(transparentPng!!))
                .setMimeType(MimeTypes.IMAGE_PNG).setImageDurationMs(totalDurationMs).build()
            val overlayItem = EditedMediaItem.Builder(overlayMedia).setDurationUs(totalDurationMs * 1000L)
                .setFrameRate(wantFps.takeIf { it > 0 } ?: 30).setRemoveAudio(true)
                .setEffects(Effects(emptyList(), listOf(
                    Presentation.createForWidthAndHeight(targetW, targetH, Presentation.LAYOUT_SCALE_TO_FIT),
                    overlayEffect
                ))).build()
            EditedMediaItemSequence(listOf(overlayItem))
        } else null
        val mixItem = p.mixAudio?.let { EditedMediaItem.Builder(MediaItem.fromUri(Uri.fromFile(it))).build() }
        // Media3 draws earlier registered video sequences on top: optional text/FX sequence, ordered PIP layers, then the main timeline.
        val allSequences = ArrayList<EditedMediaItemSequence>()
        if (topOverlaySequence != null) allSequences.add(topOverlaySequence)
        allSequences.addAll(overlaySequences)
        allSequences.add(videoSeq)
        if (mixItem != null) allSequences.add(EditedMediaItemSequence(listOf(mixItem)))
        val cb = if (allSequences.size == 1) Composition.Builder(videoSeq)
            else Composition.Builder(allSequences.first(), *allSequences.drop(1).toTypedArray())
        if (hasPip) cb.setVideoCompositorSettings(
            PipCompositorSettings(targetW, targetH, overlayItems, totalDurationMs, if (topOverlaySequence != null) 1 else 0)
        )
        if (mixItem == null && p.sources?.any { it.image } == true) {
            // photos have no audio: ask Transformer to fill the gaps with silence (method is experimental, so call it softly)
            try {
                Composition.Builder::class.java
                    .getMethod("experimentalSetForceAudioTrack", Boolean::class.javaPrimitiveType)
                    .invoke(cb, true)
            } catch (_: Throwable) { }
        }
        val composition = cb.build()

        // HEVC only when this phone's encoder accepts the size and rate; any retry falls back to H.264
        val useHevc = p.settings.hevc && level == 0 && DeviceCaps.supports(DeviceCaps.hevc, targetW, targetH, wantFps)
        val wanted = ExportPlan.videoBitrate(p.settings, targetW, targetH, useHevc)
        val bitrate = when (level) {
            0 -> wanted
            1 -> min(wanted, 8_000_000)
            else -> min(wanted, 5_000_000)
        }
        val encoderFactory = DefaultEncoderFactory.Builder(ctx)
            .setRequestedVideoEncoderSettings(VideoEncoderSettings.Builder().setBitrate(bitrate).build())
            .build()

        val t = Transformer.Builder(ctx)
            .setVideoMimeType(if (useHevc) MimeTypes.VIDEO_H265 else MimeTypes.VIDEO_H264)
            .setAudioMimeType(MimeTypes.AUDIO_AAC)
            .setEncoderFactory(encoderFactory)
            .addListener(object : Transformer.Listener {
                override fun onCompleted(composition: Composition, exportResult: ExportResult) {
                    polling = false
                    onDone()
                }

                override fun onError(composition: Composition, exportResult: ExportResult, exportException: ExportException) {
                    polling = false
                    if (level < 2) {
                        handler.post { run(p, level + 1, onProgress, onDone, onError) }
                    } else {
                        onError((exportException.message ?: "خطا در ساخت ویدیو") + " [" + exportException.errorCodeName + "]")
                    }
                }
            })
            .build()
        transformer = t
        t.start(composition, p.out.absolutePath)

        polling = true
        val holder = ProgressHolder()
        handler.post(object : Runnable {
            override fun run() {
                if (!polling) return
                val tr = transformer ?: return
                if (tr.getProgress(holder) == Transformer.PROGRESS_STATE_AVAILABLE) {
                    onProgress(holder.progress / 100f)
                }
                handler.postDelayed(this, 400)
            }
        })
    }

    private fun ensureTransparentPng(): File {
        val file = File(ctx.cacheDir, "zircut-transparent-pip.png")
        if (!file.isFile || file.length() == 0L) {
            val bitmap = Bitmap.createBitmap(2, 2, Bitmap.Config.ARGB_8888)
            try { FileOutputStream(file).use { out -> bitmap.compress(Bitmap.CompressFormat.PNG, 100, out) } }
            finally { bitmap.recycle() }
        }
        return file
    }

    fun cancel() {
        polling = false
        transformer?.cancel()
    }
}

/**
 * PIP sequences are registered in project order before the main sequence because Media3 composites earlier inputs on top.
 * Position/size are normalized to the output frame and timing is evaluated on the edited timeline.
 */
@UnstableApi
private class PipCompositorSettings(
    private val outW: Int,
    private val outH: Int,
    private val overlays: List<VideoOverlay>,
    private val totalDurationMs: Long,
    private val pipInputOffset: Int
) : VideoCompositorSettings {
    override fun getOutputSize(inputSizes: List<Size>): Size = Size(outW, outH)

    override fun getOverlaySettings(inputId: Int, presentationTimeUs: Long): OverlaySettings {
        // PIP sequences are registered before the main sequence and after any transparent text/FX sequence.
        val layer = overlays.getOrNull(inputId - pipInputOffset)
            ?: return OverlaySettings.Builder().build() // transparent top sequence or main timeline
        val ms = presentationTimeUs / 1000L
        val visible = ms >= layer.startMs && ms < minOf(layer.endMs, totalDurationMs)
        val pose = VideoOverlayKeys.pose(layer, ms)
        val scale = (layer.width * pose.scale).coerceIn(0.04f, 2f)
        val settings = OverlaySettings.Builder()
            .setScale(scale, scale)
            .setOverlayFrameAnchor(0f, 0f)
            .setBackgroundFrameAnchor((pose.x.coerceIn(0f, 1f) - 0.5f) * 2f, (0.5f - pose.y.coerceIn(0f, 1f)) * 2f)
            .setAlphaScale(if (visible) pose.opacity.coerceIn(0f, 1f) else 0f)
        if (pose.rotation != 0f) settings.setRotationDegrees(-pose.rotation)
        return settings.build()
    }
}

@UnstableApi
private class CaptionOverlay(
    private val timeline: CaptionTimeline,
    private val phrases: List<Phrase>,
    private val renderer: CaptionRenderer,
    private val style: SubStyle,
    private val layout: CapLayout,
    private val startUs: Long
) : BitmapOverlay() {
    private val blank: Bitmap = Bitmap.createBitmap(2, 2, Bitmap.Config.ARGB_8888)
    private var lastKey = Long.MIN_VALUE
    private var lastBmp: Bitmap = blank

    private var mode = -1

    private fun outMs(presentationTimeUs: Long): Long {
        if (mode < 0) {
            mode = if (startUs > 0 && presentationTimeUs < startUs / 2) 1 else 0
            Diag.mode = if (mode == 1) "نسبی" else "مطلق"
        }
        return (if (mode == 1) presentationTimeUs + startUs else presentationTimeUs) / 1000
    }

    override fun getBitmap(presentationTimeUs: Long): Bitmap {
        Diag.calls++
        Diag.lastUs = presentationTimeUs
        try {
            val st = timeline.stateAt(outMs(presentationTimeUs))
            val key = if (st == null) -1L else st.phraseIdx * 1000L + st.wordIdx
            if (st != null) Diag.shown++
            if (key != lastKey) {
                lastBmp = if (st == null) blank else renderer.render(phrases[st.phraseIdx], st.wordIdx)
                lastKey = key
            }
        } catch (t: Throwable) {
            Diag.err = t.toString().take(140)
            return blank
        }
        return lastBmp
    }

    override fun getOverlaySettings(presentationTimeUs: Long): OverlaySettings {
        val st = timeline.stateAt(outMs(presentationTimeUs))
        val pz = if (st != null) CapAnim.pose(layout.anim, style, st.pop, st.tMs, st.wordMs) else CapPose(1f, 1f, 0f)
        return OverlaySettings.Builder()
            .setBackgroundFrameAnchor((layout.x - 0.5f) * 2f, 1f - 2f * (layout.y + pz.dy))
            .setOverlayFrameAnchor(0f, 0f)
            .setScale(pz.scale, pz.scale)
            .setAlphaScale(pz.alpha)
            .build()
    }
}


/** One whole-frame effect picture. Media3 sizes an overlay by its pixels, so it is scaled up to the output size. */
@UnstableApi
private class FxOverlay(private val l: FxLayer, private val outW: Int, private val outH: Int) : BitmapOverlay() {
    override fun getBitmap(presentationTimeUs: Long): Bitmap = l.at(presentationTimeUs / 1000L)

    override fun getOverlaySettings(presentationTimeUs: Long): OverlaySettings {
        val b = l.bitmaps[0]
        return OverlaySettings.Builder()
            .setScale(outW.toFloat() / b.width, outH.toFloat() / b.height)
            .setAlphaScale(l.alpha.coerceIn(0f, 1f))
            .build()
    }
}

/** A layer drawn at output size, rotation already applied; it is shown at layer.scale / bs. */
private class BakedLayer(val layer: Layer, val bmp: Bitmap, val bs: Float)

/** One text/picture layer as a Media3 overlay: visible only between its start and end on the edited timeline. */
@UnstableApi
private class LayerOverlay(private val b: BakedLayer, private val startUs: Long) : BitmapOverlay() {
    private val blank: Bitmap = Bitmap.createBitmap(2, 2, Bitmap.Config.ARGB_8888)
    private var mode = -1

    // same time handling as the captions: frame times are either relative to the clip or to the whole export
    private fun outMs(presentationTimeUs: Long): Long {
        if (mode < 0) mode = if (startUs > 0 && presentationTimeUs < startUs / 2) 1 else 0
        return (if (mode == 1) presentationTimeUs + startUs else presentationTimeUs) / 1000
    }

    override fun getBitmap(presentationTimeUs: Long): Bitmap =
        if (b.layer.visibleAt(outMs(presentationTimeUs))) b.bmp else blank

    override fun getOverlaySettings(presentationTimeUs: Long): OverlaySettings {
        val l = b.layer
        val ms = outMs(presentationTimeUs)
        val on = l.visibleAt(ms)
        val a = LayerAnim.at(l, ms)
        val p = LayerKeys.pose(l, ms)
        val s = p.scale / b.bs * a.scale
        val ob = OverlaySettings.Builder()
            .setBackgroundFrameAnchor((p.x + a.dx - 0.5f) * 2f, 1f - 2f * (p.y + a.dy))
            .setOverlayFrameAnchor(0f, 0f)
            .setScale(s, s)
            .setAlphaScale(if (on) (p.opacity * a.alpha).coerceIn(0f, 1f) else 0f)
        // keyframed turn: the bitmap was NOT pre-rotated, Media3 turns it about its centre (its angle is counter-clockwise, ours is clockwise)
        if (LayerKeys.rotationAnimated(l)) ob.setRotationDegrees(-p.rotation)
        return ob.build()
    }
}


/** Small counters so the app can explain by itself why captions did or did not appear. */
object Diag {
    @Volatile var calls = 0
    @Volatile var shown = 0
    @Volatile var lastUs = 0L
    @Volatile var err = ""
    @Volatile var mode = "-"
    fun reset() { calls = 0; shown = 0; lastUs = 0L; err = ""; mode = "-" }
    fun text(): String =
        "اورلی صدا زده شد: $calls بار · فریم‌های دارای زیرنویس: $shown · زمان‌بندی: $mode · آخرین زمان: ${lastUs / 1000}ms · خطای رندر: ${if (err.isEmpty()) "ندارد" else err}"
}


/** Color grade as a single RGB matrix (same numbers as the editor preview). */
@UnstableApi
private class GradeEffect(private val matrix: FloatArray) : RgbMatrix {
    override fun getMatrix(presentationTimeUs: Long, useHdr: Boolean): FloatArray = matrix
}


/**
 * Fade of the video picture towards black from the keyframes' opacity (fix231). RgbMatrix = 4x4, column-major, so a diagonal of (o, o, o, 1).
 * Same time detection as [KeyedZoomPan]. Not compiled offline: if the build fails here, only this class and its one use need a look.
 */
@UnstableApi
private class KeyedFade(private val fx: Fx, private val startUs: Long) : RgbMatrix {
    private var mode = -1
    override fun getMatrix(presentationTimeUs: Long, useHdr: Boolean): FloatArray {
        if (mode < 0) mode = if (startUs > 0 && presentationTimeUs < startUs / 2) 1 else 0
        val ms = (if (mode == 1) presentationTimeUs + startUs else presentationTimeUs) / 1000
        val o = VideoKeys.pose(fx, ms).op.coerceIn(0f, 1f)
        return floatArrayOf(o, 0f, 0f, 0f, 0f, o, 0f, 0f, 0f, 0f, o, 0f, 0f, 0f, 0f, 1f)
    }
}

/** Rotation by 90s, then zoom + pan as a crop window of the rotated picture; the Presentation after it fills the frame. */
private fun transformEffects(fx: Fx, clipW: Int, clipH: Int, outW: Int, outH: Int, startUs: Long): List<Effect> {
    val list = ArrayList<Effect>()
    // mirror first, then rotate (the preview scales with a negative factor, then rotates)
    if (fx.flipH || fx.flipV) {
        list.add(ScaleAndRotateTransformation.Builder().setScale(if (fx.flipH) -1f else 1f, if (fx.flipV) -1f else 1f).build())
    }
    val rot = ((fx.rot % 360) + 360) % 360
    if (rot != 0) list.add(ScaleAndRotateTransformation.Builder().setRotationDegrees(((360 - rot) % 360).toFloat()).build())
    val swap = rot % 180 != 0
    val vw = if (swap) clipH else clipW
    val vh = if (swap) clipW else clipH
    if (fx.vkeys.isNotEmpty() && vw > 0 && vh > 0 && outW > 0 && outH > 0) {
        // animated zoom / pan: one matrix per frame (same window maths as the static crop below)
        list.add(KeyedZoomPan(fx, vw, vh, outW, outH, startUs))
    } else if (fx.vzoom > 1.001f && vw > 0 && vh > 0 && outW > 0 && outH > 0) {
        val va = vw.toFloat() / vh
        val fa = outW.toFloat() / outH
        // part of the rotated picture the frame shows when it is only filled (cropped, centred)
        val visW = if (va >= fa) fa / va else 1f
        val visH = if (va >= fa) 1f else va / fa
        val z = fx.vzoom.coerceIn(1f, 4f)
        val wf = visW / z
        val hf = visH / z
        // the picture moves right / down by tx, ty frame sizes, so the window moves the other way
        val tx = fx.vpanX.coerceIn(-1f, 1f) * (z - 1f) / 2f
        val ty = fx.vpanY.coerceIn(-1f, 1f) * (z - 1f) / 2f
        val cx = 0.5f - tx * wf
        val cy = 0.5f - ty * hf
        val l = (cx - wf / 2f).coerceIn(0f, 1f)
        val r = (cx + wf / 2f).coerceIn(0f, 1f)
        val t = (cy - hf / 2f).coerceIn(0f, 1f)
        val b = (cy + hf / 2f).coerceIn(0f, 1f)
        list.add(Crop(2f * l - 1f, 2f * r - 1f, 1f - 2f * b, 1f - 2f * t))
    }
    return list
}

/**
 * Turns the whole frame by [deg] degrees clockwise (as seen) and zooms it so the corners stay filled.
 * The matrix works in normalised device coordinates (x and y both -1..1), so it is scaled to pixels first, rotated, then scaled back.
 * Y points up there, so a clockwise turn is a NEGATIVE angle of android.graphics.Matrix.postRotate.
 * NEW Media3 API (MatrixTransformation) used without a compiler: if the build fails, look here first.
 */
@UnstableApi
private class FineRotation(private val deg: Float, private val w: Int, private val h: Int) : MatrixTransformation {
    override fun getMatrix(presentationTimeUs: Long): android.graphics.Matrix {
        val z = fineCover(deg, w.toFloat(), h.toFloat())
        val m = android.graphics.Matrix()
        m.setScale(w.toFloat(), h.toFloat())
        m.postRotate(-deg)
        m.postScale(z / w, z / h)
        return m
    }
}

/**
 * Keyframed free-angle turn of the finished frame (fix230): the same matrix as [FineRotation], with the angle of the video keyframes at the frame's time.
 * Same time detection as [KeyedZoomPan]. NEW time-dependent use of MatrixTransformation, not compiled offline.
 */
@UnstableApi
private class KeyedFineRotation(private val fx: Fx, private val w: Int, private val h: Int, private val startUs: Long) : MatrixTransformation {
    private var mode = -1
    override fun getMatrix(presentationTimeUs: Long): android.graphics.Matrix {
        if (mode < 0) mode = if (startUs > 0 && presentationTimeUs < startUs / 2) 1 else 0
        val ms = (if (mode == 1) presentationTimeUs + startUs else presentationTimeUs) / 1000
        val deg = VideoKeys.pose(fx, ms).rot
        val m = android.graphics.Matrix()
        if (kotlin.math.abs(deg) < 0.05f) return m
        val z = fineCover(deg, w.toFloat(), h.toFloat())
        m.setScale(w.toFloat(), h.toFloat())
        m.postRotate(-deg)
        m.postScale(z / w, z / h)
        return m
    }
}

/**
 * Keyframed zoom + pan of the video picture (fix220). Works on the whole rotated picture: the window centre is moved to the middle and the
 * picture is scaled by the zoom; the Presentation after it then fills the frame, exactly like the static crop does.
 * NDC: x right, y UP, so the window centre (fractions from the top-left) is (2cx - 1, 1 - 2cy).
 * Frame times are either relative to the clip or to the whole export (same detection as the layer overlays).
 * NEW use of MatrixTransformation with a time-dependent matrix, not compiled offline: if the build or the export fails, look here first.
 */
@UnstableApi
private class KeyedZoomPan(
    private val fx: Fx, private val vw: Int, private val vh: Int, private val outW: Int, private val outH: Int, private val startUs: Long
) : MatrixTransformation {
    private var mode = -1

    override fun getMatrix(presentationTimeUs: Long): android.graphics.Matrix {
        if (mode < 0) mode = if (startUs > 0 && presentationTimeUs < startUs / 2) 1 else 0
        val ms = (if (mode == 1) presentationTimeUs + startUs else presentationTimeUs) / 1000
        val p = VideoKeys.pose(fx, ms)
        val m = android.graphics.Matrix()
        if (p.zoom <= 1.001f) return m
        val c = VideoKeys.centre(p.zoom, p.panX, p.panY, vw, vh, outW, outH)
        m.setTranslate(-(2f * c[0] - 1f), -(1f - 2f * c[1]))
        m.postScale(p.zoom, p.zoom)
        return m
    }
}


/** Background picture of a chroma-keyed clip: decoded at most ~1920 px on the long side; null when the file is missing or broken (the export then fills with black). */
private fun decodeKeyBackground(f: File): Bitmap? {
    return try {
        if (!f.isFile) return null
        val bo = android.graphics.BitmapFactory.Options().apply { inJustDecodeBounds = true }
        android.graphics.BitmapFactory.decodeFile(f.absolutePath, bo)
        if (bo.outWidth <= 0 || bo.outHeight <= 0) return null
        var sample = 1
        while (bo.outWidth / sample > 1920 || bo.outHeight / sample > 1920) sample *= 2
        android.graphics.BitmapFactory.decodeFile(
            f.absolutePath,
            android.graphics.BitmapFactory.Options().apply { inSampleSize = sample; inPreferredConfig = Bitmap.Config.ARGB_8888 }
        )
    } catch (_: Throwable) {
        null
    }
}
