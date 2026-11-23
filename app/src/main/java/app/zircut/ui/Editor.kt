@file:OptIn(ExperimentalMaterial3Api::class, androidx.compose.foundation.layout.ExperimentalLayoutApi::class)

package app.zircut.ui

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.SurfaceTexture
import android.graphics.Typeface
import android.media.MediaMetadataRetriever
import android.media.MediaPlayer
import android.net.Uri
import android.text.TextPaint
import android.text.TextUtils
import android.view.Surface
import android.view.TextureView
import android.view.View
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.util.UnstableApi
import java.io.File
import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import app.zircut.core.VoiceRecorder
import app.zircut.core.Waveform
import app.zircut.timeline.AudioClip
import app.zircut.timeline.AudioOps
import app.zircut.MainViewModel
import app.zircut.UiState
import app.zircut.core.CapLayout
import app.zircut.core.CapAnim
import app.zircut.core.ChromaKeyMath
import app.zircut.core.ClipLook
import app.zircut.core.KeyPreviewFx
import app.zircut.core.CaptionRenderer
import app.zircut.core.CaptionTimeline
import app.zircut.core.ColorGrade
import app.zircut.core.Affine
import app.zircut.core.CubeLut
import app.zircut.core.DeviceCaps
import app.zircut.core.ExportPlan
import app.zircut.core.ExportSettings
import app.zircut.core.Fx
import app.zircut.core.VideoKeys
import app.zircut.core.VideoPose
import app.zircut.core.FxRender
import app.zircut.core.fineCover
import app.zircut.core.TrKind
import app.zircut.core.TrMath
import app.zircut.core.Cuts
import app.zircut.core.TrState
import app.zircut.core.FontStore
import app.zircut.core.Frame
import app.zircut.core.LayerBitmapCache
import app.zircut.core.LayerRenderer
import app.zircut.core.Looks
import app.zircut.core.Options
import app.zircut.core.Phrase
import app.zircut.core.Planner
import app.zircut.core.Range
import app.zircut.core.Styles
import app.zircut.core.SubStyle
import app.zircut.core.VideoInfo
import app.zircut.core.Word
import app.zircut.timeline.EdlMap
import app.zircut.timeline.Src
import app.zircut.timeline.EdlOps
import app.zircut.timeline.EdlResult
import app.zircut.timeline.MyTemplate
import app.zircut.timeline.VideoTemplates
import app.zircut.timeline.Layer
import app.zircut.timeline.LayerAnim
import app.zircut.timeline.LayerKeys
import app.zircut.timeline.LayerOps
import app.zircut.timeline.NamedStyle
import app.zircut.timeline.TextStyles
import app.zircut.timeline.OutPhrase
import app.zircut.timeline.captionRanges
import app.zircut.timeline.outPhrasesOf
import app.zircut.timeline.SpeechMap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import android.graphics.Paint as AndroidPaint

private enum class EdTab(val label: String, val title: String, val icon: ZI) {
    CLIP("کلیپ", "ویرایش کلیپ", ZI.Scissors),
    KEY("کروما", "کروما کی (پرده سبز)", ZI.Spark),
    LAYER("لایه", "لایه‌های متن و عکس", ZI.Layers),
    AUDIO("صدا", "صدا و موسیقی", ZI.Music),
    CAPTIONS("زیرنویس", "زیرنویس‌ها", ZI.Captions),
    STYLE("استایل", "استایل زیرنویس", ZI.Palette),
    FONT("فونت", "فونت زیرنویس", ZI.Type),
    POSITION("جایگاه", "جایگاه و اندازه", ZI.Position),
    FILTER("فیلتر", "فیلتر و رنگ", ZI.Filters),
    EDIT("ادیت", "ویرایش خودکار", ZI.Tune)
}

private class PlayerHolder {
    /** index of the clip being played */
    var cur = 0
    /** after a seek the player reports old positions for a moment; ignore them until this time */
    var ignoreUntil = 0L
    /** photo clips: wall-clock start of the current photo and the timeline position it started at */
    var imgWall = 0L
    var imgBase = 0L
}

private fun pct(v: Float): String = fa(Math.round(v * 100f)) + "٪"

private fun signed(n: Int): String = when {
    n > 0 -> "+" + fa(n)
    n < 0 -> "−" + fa(-n)
    else -> fa(0)
}

/**
 * Editor: preview on top, a scrolling two-track timeline (video + subtitles) under it,
 * and a tool dock at the bottom. All tools open as a panel above the dock.
 */
@UnstableApi
@Composable
fun EditorScreen(
    ui: UiState,
    vm: MainViewModel,
    onSettings: () -> Unit,
    onPickFont: () -> Unit,
    onAddMedia: (Long) -> Unit,
    onAddLayerImage: (Long, Long) -> Unit,
    onAddAudio: (Long, Long) -> Unit,
    onPickLut: () -> Unit,
    onPickKeyBg: (Long) -> Unit,
    onImportSrt: () -> Unit,
    modifier: Modifier = Modifier
) {
    val info: VideoInfo = ui.info ?: return
    val uri = ui.video ?: return
    val holder = remember { PlayerHolder() }
    var playing by remember { mutableStateOf(false) }
    var posMs by remember { mutableStateOf(0L) }
    // Open the editor on the timeline first. Tool panels are opened only when the
    // user chooses a tool, keeping the preview and timeline visible on small screens.
    var tab by remember { mutableStateOf(EdTab.CLIP) }
    var panelOpen by remember { mutableStateOf(false) }
    var showExport by remember { mutableStateOf(false) }
    var showRename by remember { mutableStateOf(false) }
    var tlScale by remember { mutableStateOf(48f) }

    val appCtx = LocalContext.current
    // frame rate of the main video: frame-accurate time readout and frame stepping
    val srcFps by produceState(30f, uri) {
        value = withContext(Dispatchers.IO) { runCatching { DeviceCaps.sourceFps(appCtx, uri) }.getOrDefault(30f) }
    }
    val player = remember { PreviewPlayer(appCtx) }
    // music / voice-over clips play next to the video; recording state of the voice-over
    val audioPrev = remember { AudioPreview(appCtx) }
    var recorder by remember { mutableStateOf<VoiceRecorder?>(null) }
    var recStart by remember { mutableStateOf(0L) }
    var recLevel by remember { mutableStateOf(0f) }
    var recElapsed by remember { mutableStateOf(0L) }
    val projDir = vm.mediaDir(ui.projectId)
    player.uriOf = { idx ->
        if (idx == 0) uri
        else ui.extras.firstOrNull { it.id == idx }?.let { x -> Uri.fromFile(File(projDir, x.file)) }
    }
    val extrasNow by rememberUpdatedState(ui.extras)
    val audioNow by rememberUpdatedState(ui.audio)
    val duckNow by rememberUpdatedState(ui.duckMusic)

    val words = remember(ui.phrases) { ui.phrases.flatMap { it.words } }
    val ranges = remember(ui.activity, ui.options, info, ui.segments) {
        ui.segments?.takeIf { it.isNotEmpty() } ?: Planner.ranges(ui.activity, info.durationMs, ui.options)
    }
    // posMs is a position on the EDITED timeline (clips laid end to end), not in a source video.
    val map = remember(ranges) { EdlMap(ranges) }
    val mapNow by rememberUpdatedState(map)
    val outPhrases = remember(ui.phrases, map) { outPhrasesOf(ui.phrases, map) }
    // where the video's own speech is (edited timeline): music ducks under it when the auto-duck switch is on and the video sound is audible
    val speechMap = remember(outPhrases, ui.mainMuted, ui.mainVolume) {
        if (ui.mainMuted || ui.mainVolume <= 0f) null
        else SpeechMap.of(outPhrases.map { it.startMs to it.endMs })
    }
    val speechNow by rememberUpdatedState(speechMap)
    val kept = map.total
    val located = map.locate(posMs)
    val curSeg = located.first
    val curSrc = Src.idx(located.second)
    val curLocal = Src.local(located.second)
    val curExtra = if (curSrc == 0) null else ui.extras.firstOrNull { it.id == curSrc }
    val curIsImage = curExtra?.image == true
    // 0 = main video clip, 1 = extra video clip, 2 = photo clip
    val clipKinds = remember(map, ui.extras) {
        map.segs.map { r ->
            val k = Src.idx(r.startMs)
            if (k == 0) 0 else if (ui.extras.firstOrNull { it.id == k }?.image == true) 2 else 1
        }
    }

    /** show the clip i at source position src (video: seek/play in the player, photo: count time ourselves) */
    fun enterAt(m: EdlMap, i: Int, src: Long, play: Boolean) {
        if (m.segs.isEmpty()) return
        val k = Src.idx(m.segs[i].startMs)
        val x = if (k == 0) null else extrasNow.firstOrNull { it.id == k }
        if (k != 0 && x == null) return
        if (x?.image == true) {
            player.pause()
            holder.imgWall = System.currentTimeMillis()
            holder.imgBase = m.outStart(i) + (Src.local(src) - Src.local(m.segs[i].startMs))
        } else {
            player.setSpeed(m.segs[i].speed)
            player.enter(k, Src.local(src), play)
        }
    }

    fun seekOut(t: Long, m: EdlMap = map) {
        val tt = t.coerceIn(0L, m.total)
        val loc = m.locate(tt)
        holder.cur = loc.first
        holder.ignoreUntil = System.currentTimeMillis() + 250L
        posMs = tt
        enterAt(m, loc.first, loc.second, playing)
    }

    fun pauseIfPlaying() {
        if (playing) {
            player.pause()
            playing = false
        }
    }

    fun toggle() {
        if (playing) {
            pauseIfPlaying()
            return
        }
        if (map.segs.isEmpty()) return
        if (posMs >= map.total - 120L) seekOut(0L)
        val loc = map.locate(posMs)
        holder.cur = loc.first
        holder.ignoreUntil = System.currentTimeMillis() + 250L
        enterAt(map, loc.first, loc.second, true)
        holder.imgWall = System.currentTimeMillis()
        holder.imgBase = posMs
        playing = true
    }

    fun scrub(ms: Long) {
        pauseIfPlaying()
        seekOut(ms)
    }

    /** moves the playhead by whole frames (negative = back); stays inside the edited timeline */
    fun stepFrames(n: Int) {
        val f = if (srcFps in 1f..240f) srcFps else 30f
        val cur = Math.round(posMs * f / 1000f)
        var target = Math.round((cur + n) * 1000f / f).toLong()
        if (target == posMs) target += if (n > 0) 1L else -1L
        scrub(target.coerceIn(0L, map.total))
    }

    /** jump to a time of the main video (word strip, subtitle list); ignored if that part was cut away */
    fun seekSrc(srcMs: Long) {
        val o = map.srcToOut(srcMs)
        if (o != null) scrub(o)
    }

    fun prevCaption() {
        seekOut(outPhrases.lastOrNull { it.startMs < posMs - 500 }?.startMs ?: 0L)
    }

    fun nextCaption() {
        val n = outPhrases.firstOrNull { it.startMs > posMs + 80 }
        if (n != null) seekOut(n.startMs)
    }

    fun applyEdl(r: EdlResult?) {
        if (r == null) return
        pauseIfPlaying()
        vm.setSegments(r.segs)
        seekOut(r.playhead, EdlMap(r.segs))
    }

    // ---- voice-over: the video plays while the microphone records; pausing/stopping/reaching the end finishes the take ----
    fun startRecording() {
        if (recorder != null || map.segs.isEmpty()) return
        pauseIfPlaying()
        if (posMs >= map.total - 120L) seekOut(0L)
        val r = VoiceRecorder(File(projDir, "rec_" + System.currentTimeMillis() + ".wav"))
        if (!r.start()) {
            vm.showMessage("میکروفون باز نشد؛ شاید برنامه‌ی دیگری از آن استفاده می‌کند.")
            return
        }
        recStart = posMs
        recorder = r
        toggle()
    }

    fun stopRecording() {
        val r = recorder ?: return
        recorder = null
        val ms = r.stop()
        pauseIfPlaying()
        vm.addRecording(r.file, recStart, ms)
    }

    val micLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) startRecording() else vm.showMessage("برای ضبط صدا باید اجازه‌ی میکروفون را بدهی.")
    }

    fun askRecord() {
        if (ContextCompat.checkSelfPermission(appCtx, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) startRecording()
        else micLauncher.launch(Manifest.permission.RECORD_AUDIO)
    }

    LaunchedEffect(playing, recorder) {
        if (recorder != null && !playing) stopRecording()
    }

    LaunchedEffect(recorder) {
        val r = recorder ?: return@LaunchedEffect
        while (true) {
            recLevel = r.level
            recElapsed = r.elapsedMs
            delay(80)
        }
    }

    LaunchedEffect(ui.mainVolume, ui.mainMuted) {
        player.setVolume(if (ui.mainMuted) 0f else ui.mainVolume)
    }

    LaunchedEffect(Unit) {
        while (true) {
            val m = mapNow
            if (playing && m.segs.isNotEmpty()) {
                val now = System.currentTimeMillis()
                var i = holder.cur.coerceIn(0, m.segs.size - 1)
                val seg = m.segs[i]
                val k = Src.idx(seg.startMs)
                val img = k != 0 && extrasNow.firstOrNull { it.id == k }?.image == true
                var localPos = -1L
                if (img) {
                    localPos = Src.local(seg.startMs) + (now - holder.imgWall) + (holder.imgBase - m.outStart(i))
                } else if (player.isReady && player.loadedIdx == k) {
                    localPos = player.positionMs()
                }
                if (localPos >= 0L && now >= holder.ignoreUntil) {
                    val segEnd = Src.local(seg.endMs)
                    if (localPos >= segEnd - (if (img) 0L else 40L)) {
                        if (i + 1 < m.segs.size) {
                            i += 1
                            holder.cur = i
                            val nx = m.segs[i]
                            player.setSpeed(nx.speed)
                            val contiguous = !img && Src.idx(nx.startMs) == k &&
                                kotlin.math.abs(Src.local(nx.startMs) - segEnd) <= 60L
                            if (!contiguous) {
                                holder.ignoreUntil = now + 250L
                                enterAt(m, i, nx.startMs, true)
                            }
                            posMs = m.outStart(i)
                        } else {
                            player.pause()
                            playing = false
                            posMs = m.total
                        }
                    } else {
                        val o = m.outStart(i)
                        posMs = (o + Math.round((localPos - Src.local(seg.startMs)) / seg.speed.toDouble())).coerceIn(o, o + seg.outLen)
                    }
                }
            }
            audioPrev.update(audioNow, posMs, playing, projDir, duckNow, speechNow)
            delay(33)
        }
    }

    // after undo/redo or any change of the clip list: keep the playhead valid and the player in sync
    LaunchedEffect(map) { seekOut(minOf(posMs, map.total), map) }

    DisposableEffect(Unit) {
        onDispose {
            player.release()
            audioPrev.release()
            recorder?.cancel()
        }
    }

    val style = Styles.byId(ui.styleId)
    val font: Typeface = ui.fonts.firstOrNull { it.id == ui.fontId }?.typeface ?: Typeface.DEFAULT_BOLD
    val capTimeline = remember(ui.phrases, ranges) { CaptionTimeline(ui.phrases, captionRanges(ranges)) }
    val st = if (ui.options.captions) capTimeline.stateAt(posMs) else null
    val activeIdx = st?.phraseIdx ?: -1

    // frame = output shape; va = shape of the video that is showing right now
    val mainVa = info.width.toFloat() / info.height
    val rawVa = if (curExtra != null && curExtra.height > 0) curExtra.width.toFloat() / curExtra.height else mainVa
    // picture transform of video clips (rotation by 90s, zoom, pan); photos are not transformed
    val tfOn = !curIsImage
    val tfRot = if (tfOn) ui.fx.rot else 0
    val tfSwap = tfRot % 180 != 0
    // zoom / pan come from the keyframes at the playhead when there are any (same function as the exporter)
    val vPose = VideoKeys.pose(ui.fx, posMs)
    val tfZoom = if (tfOn) vPose.zoom else 1f
    val tfPanX = if (tfOn) vPose.panX else 0f
    val tfPanY = if (tfOn) vPose.panY else 0f
    val tfFlipH = tfOn && ui.fx.flipH
    val tfFlipV = tfOn && ui.fx.flipV
    val va = if (tfSwap) 1f / rawVa else rawVa
    val aspect = Frame.aspect(ui.options).let { if (it > 0f) it else mainVa }
    val renderW = 540
    val renderH = (540f / aspect).toInt()
    val capBmp = remember(st?.phraseIdx, st?.wordIdx, style, font, ui.layout, ui.phrases, aspect) {
        if (st == null || st.phraseIdx !in ui.phrases.indices) null
        else try {
            CaptionRenderer(renderW, renderH, style, font, ui.layout).render(ui.phrases[st.phraseIdx], st.wordIdx)
        } catch (e: Throwable) {
            null
        }
    }
    val zoom = if (ui.options.zoom && curSeg % 2 == 1) 1.18f else 1f
    // transition at the cuts: the same function the exporter uses
    val trNow: TrState? = if (ranges.isEmpty()) null else {
        val ci = curSeg.coerceIn(0, ranges.size - 1)
        TrMath.at(posMs - map.outStart(ci), ranges[ci].outLen, ci == 0, ci == ranges.size - 1, Cuts.enter(ranges, ci, ui.fx), Cuts.exit(ranges, ci, ui.fx))
    }
    val lastBlur = remember { floatArrayOf(-1f) }
    val keySig = remember { arrayOfNulls<Any>(1) }

    // text/picture layers that are visible at the playhead, drawn like the exporter draws them
    val layerCache = remember { LayerBitmapCache() }
    val layerRows = remember(ui.layers) { LayerOps.rows(ui.layers) }
    val audioRows = remember(ui.audio) { AudioOps.rows(ui.audio) }
    val fxLayers = remember(ui.fx, renderW, renderH) { FxRender.build(ui.fx, renderW, renderH) }
    // colour LUT: read the file once, then a matrix that imitates it for the preview (the export uses the exact LUT)
    val lutData by produceState<CubeLut?>(null, ui.fx.lutFile, projDir) {
        value = if (ui.fx.lutFile.isEmpty()) null else withContext(Dispatchers.IO) { CubeLut.load(File(projDir, ui.fx.lutFile)) }
    }
    val lutAffine: Affine? = remember(lutData, ui.fx.lutAmt) { lutData?.takeIf { ui.fx.lutAmt > 0.01f }?.fit(ui.fx.lutAmt) }
    val drawnLayers = ui.layers.filter { !it.hidden && it.visibleAt(posMs) }.mapNotNull { l ->
        val bs = LayerRenderer.bakeScale(LayerKeys.maxScale(l))
        val fid = if (l.fontId.isEmpty()) ui.fontId else l.fontId
        val tf = ui.fonts.firstOrNull { it.id == fid }?.typeface ?: font
        val bmp = layerCache.get(l, renderW, renderH, tf, fid, projDir, bs)
        if (bmp == null) null else DrawnLayer(l, bmp, bs)
    }

    // a newly selected layer (added, or picked in the timeline) is brought into view
    LaunchedEffect(ui.selectedLayer) {
        val l = ui.layers.firstOrNull { it.id == ui.selectedLayer }
        if (l != null && !l.visibleAt(posMs)) scrub(l.startMs)
    }

    val screenH = LocalConfiguration.current.screenHeightDp
    val panelH = (screenH * 0.29f).dp.coerceIn(190.dp, 270.dp)

    Column(modifier.fillMaxWidth()) {
        // ================= top bar =================
        Row(
            Modifier.fillMaxWidth().height(56.dp).padding(horizontal = Z.S3),
            verticalAlignment = Alignment.CenterVertically
        ) {
            ZIconButton(ZI.Arrow, { vm.reset() })
            Spacer(Modifier.width(Z.S1))
            Column(Modifier.weight(1f).press(true) { showRename = true }) {
                Txt(if (ui.projectName.isBlank()) "ویرایش" else app.zircut.i18n.Lang.raw(ui.projectName), style = ZType.heading, maxLines = 1)
                val saveLabel = when (ui.saveStatus) {
                    "saving" -> "در حال ذخیره…"
                    "saved" -> "ذخیره شد"
                    "error" -> "ذخیره نشد"
                    else -> ""
                }
                Txt(
                    listOf(saveLabel, Frame.label(ui.options), faTime(kept))
                        .filter { it.isNotEmpty() }.joinToString("  ·  "),
                    style = ZType.caption, color = if (ui.saveStatus == "error") Z.Danger else Z.TextSec, maxLines = 1
                )
            }
            ZIconButton(ZI.Undo, { pauseIfPlaying(); vm.undo() }, enabled = ui.undoCount > 0, size = 20.dp, box = 38.dp)
            ZIconButton(ZI.Redo, { pauseIfPlaying(); vm.redo() }, enabled = ui.redoCount > 0, size = 20.dp, box = 38.dp)
            Spacer(Modifier.width(Z.S1))
            ZButton("خروجی", { pauseIfPlaying(); showExport = true }, icon = ZI.Export, compact = true)
        }

        if (ui.message.isNotEmpty()) {
            LaunchedEffect(ui.message) {
                delay(3500)
                vm.clearMessage()
            }
            Row(
                Modifier.fillMaxWidth().padding(horizontal = Z.S3).clip(RoundedCornerShape(Z.RSm)).background(Z.Surface2)
                    .border(Z.Hair, Z.Border, RoundedCornerShape(Z.RSm)).padding(Z.S3),
                verticalAlignment = Alignment.CenterVertically
            ) {
                ZIcon(ZI.Info, tint = Z.Accent, size = Z.IconSm)
                Spacer(Modifier.width(Z.S2))
                Txt(ui.message, style = ZType.caption, color = Z.Text, modifier = Modifier.weight(1f))
            }
        }

        // ================= preview =================
        BoxWithConstraints(
            Modifier.fillMaxWidth().weight(1f).padding(horizontal = Z.S3, vertical = Z.S1),
            contentAlignment = Alignment.Center
        ) {
            var fw: Dp = maxWidth
            var fh: Dp = fw / aspect
            if (fh > maxHeight) {
                fh = maxHeight
                fw = fh * aspect
            }
            val cw: Dp
            val ch: Dp
            if (va >= aspect) {
                ch = fh
                cw = fh * va
            } else {
                cw = fw
                ch = fw / va
            }
            Box(
                Modifier.size(fw, fh).clip(RoundedCornerShape(Z.RXs)).background(Color.Black)
                    .border(Z.Hair, Z.Border, RoundedCornerShape(Z.RXs))
                    .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) { toggle() },
                contentAlignment = Alignment.Center
            ) {
                // chroma key: what shows through the removed colour (checkerboard / colour / picture) lies behind the video surface
                val keyCur = ranges.getOrNull(curSeg)?.look?.key?.takeIf { it.on && it.ai == 0 && !curIsImage && KeyPreviewFx.supported }
                if (keyCur != null) {
                    val keyBg = rememberKeyBackground(keyCur, projDir, 1080)
                    KeyBackdrop(keyCur, keyBg, Modifier.fillMaxSize())
                }
                Box(
                    Modifier.requiredSize(fw, fh).graphicsLayer {
                        val fd = if (tfOn) vPose.rot else 0f
                        if (kotlin.math.abs(fd) > 0.05f) {
                            val z = fineCover(fd, fw.toPx(), fh.toPx())
                            rotationZ = fd; scaleX = z; scaleY = z
                        }
                    },
                    contentAlignment = Alignment.Center
                ) {
                AndroidView(
                    factory = { c ->
                        TextureView(c).apply {
                            surfaceTextureListener = object : TextureView.SurfaceTextureListener {
                                override fun onSurfaceTextureAvailable(t: SurfaceTexture, w: Int, h: Int) {
                                    player.setSurface(Surface(t))
                                }

                                override fun onSurfaceTextureSizeChanged(t: SurfaceTexture, w: Int, h: Int) {}

                                override fun onSurfaceTextureDestroyed(t: SurfaceTexture): Boolean {
                                    player.setSurface(null)
                                    return true
                                }

                                override fun onSurfaceTextureUpdated(t: SurfaceTexture) {}
                            }
                        }
                    },
                    modifier = Modifier.requiredSize(if (tfSwap) ch else cw, if (tfSwap) cw else ch).graphicsLayer {
                        var sc = zoom * tfZoom
                        var al = 1f
                        var tx = tfPanX * (tfZoom - 1f) / 2f * fw.toPx()
                        val ty = tfPanY * (tfZoom - 1f) / 2f * fh.toPx()
                        var rot = tfRot.toFloat()
                        val t = trNow
                        if (t != null) {
                            when (t.kind) {
                                TrKind.FADE -> al = 1f - t.s
                                TrKind.ZOOM -> { sc *= 1f / (1f - 0.35f * t.s); al = 1f - t.s * t.s }
                                TrKind.SLIDE -> tx += -t.dir * t.s * (if (tfSwap) this.size.height else this.size.width) * (if (va >= aspect) aspect / va else 1f)
                                TrKind.SPIN -> { sc *= 1f / (1f - 0.3f * t.s); rot += t.dir * t.s * 1.2f * 57.29578f; al = 1f - t.s * t.s }
                                TrKind.BLUR -> al = 1f - 0.6f * t.s * t.s
                                TrKind.GLITCH -> al = 1f - t.s * t.s
                                TrKind.NONE -> {}
                            }
                        }
                        scaleX = if (tfFlipH) -sc else sc; scaleY = if (tfFlipV) -sc else sc; alpha = (al * (if (tfOn) vPose.op else 1f)).coerceIn(0f, 1f); translationX = tx; translationY = ty; rotationZ = rot
                    },
                    update = { v ->
                        val clipLook = ranges.getOrNull(curSeg)?.look ?: ClipLook()
                        val amt = ui.fx.blur + clipLook.blur + (if (trNow != null && trNow.kind == TrKind.BLUR) trNow.s * 1.2f else 0f)
                        val ck = clipLook.key
                        if (ck.on && ck.ai == 0 && !curIsImage && KeyPreviewFx.supported) {
                            // chroma key: grade -> key on the video surface, the backdrop is drawn behind it (Android 13+)
                            val total = ChromaKeyMath.total(ui.grade, lutAffine, clipLook)
                            val keyRgb = ChromaKeyMath.gradedKey(ck, ui.grade, lutAffine, clipLook)
                            val sig = listOf(ck.tol, ck.soft, ck.spill, keyRgb.toList(), total?.l?.toList(), total?.o?.toList(), Math.round(amt * 100f), v.width)
                            if (keySig[0] != sig) {
                                keySig[0] = sig
                                v.tag = null
                                lastBlur[0] = -1f
                                KeyPreviewFx.apply(v, keyRgb, ck, total, amt)
                            }
                        } else {
                            if (keySig[0] != null) {
                                keySig[0] = null
                                KeyPreviewFx.clear(v)
                                v.tag = null
                                lastBlur[0] = -1f
                            }
                            applyGrade(v, ui.grade, lutAffine, clipLook)
                            if (kotlin.math.abs(amt - lastBlur[0]) > 0.01f) {
                                lastBlur[0] = amt
                                applyBlur(v, amt)
                            }
                        }
                    }
                )
                }
                if (curIsImage && curExtra != null) {
                    PreviewImage(File(projDir, curExtra.file).absolutePath, Modifier.fillMaxSize())
                }
                Canvas(Modifier.fillMaxSize()) {
                    val lk = size.width / renderW
                    // effects lie directly on the picture, under layers and subtitles (same pictures the exporter uses)
                    for (fl in fxLayers) {
                        drawImage(
                            fl.at(posMs).asImageBitmap(),
                            dstSize = androidx.compose.ui.unit.IntSize(size.width.toInt(), size.height.toInt()),
                            alpha = fl.alpha
                        )
                    }
                    for (dl in drawnLayers) {
                        val l = dl.l
                        val img = dl.bmp.asImageBitmap()
                        val an = LayerAnim.at(l, posMs)
                        val pz = LayerKeys.pose(l, posMs)
                        val sc = pz.scale / dl.bs * an.scale
                        val lw = img.width * lk * sc
                        val lh = img.height * lk * sc
                        val lcx = (pz.x + an.dx) * size.width
                        val lcy = (pz.y + an.dy) * size.height
                        rotate(pz.rotation, Offset(lcx, lcy)) {
                            drawImage(
                                img,
                                dstOffset = IntOffset((lcx - lw / 2f).toInt(), (lcy - lh / 2f).toInt()),
                                dstSize = IntSize(lw.toInt().coerceAtLeast(1), lh.toInt().coerceAtLeast(1)),
                                alpha = (pz.opacity * an.alpha).coerceIn(0f, 1f)
                            )
                            if (l.id == ui.selectedLayer && panelOpen && tab == EdTab.LAYER) {
                                drawRect(Z.Accent, Offset(lcx - lw / 2f, lcy - lh / 2f), Size(lw, lh), style = Stroke(1.5f * density))
                            }
                        }
                    }
                    val b = capBmp
                    if (b != null && st != null) {
                        val img = b.asImageBitmap()
                        val k = size.width / renderW
                        val w = img.width * k
                        val h = img.height * k
                        val cx = ui.layout.x * size.width
                        val cp = CapAnim.pose(ui.layout.anim, style, st.pop, st.tMs, st.wordMs)
                        val cy = (ui.layout.y + cp.dy) * size.height
                        val sc = cp.scale
                        val al = cp.alpha
                        scale(sc, Offset(cx, cy)) {
                            drawImage(
                                img,
                                dstOffset = IntOffset((cx - w / 2f).toInt(), (cy - h / 2f).toInt()),
                                dstSize = IntSize(w.toInt(), h.toInt()),
                                alpha = al
                            )
                        }
                    }
                }
                val selLayer = ui.layers.firstOrNull { it.id == ui.selectedLayer }
                if (selLayer != null && !selLayer.locked && !selLayer.hidden && panelOpen && tab == EdTab.LAYER && selLayer.visibleAt(posMs)) {
                    // one finger moves the selected layer, two fingers resize and turn it
                    val selNow by rememberUpdatedState(selLayer)
                    val posNow by rememberUpdatedState(posMs)
                    Box(
                        Modifier.fillMaxSize().pointerInput(selLayer.id) {
                            detectTransformGestures { _, pan, zoomChange, rotChange ->
                                val cur = selNow
                                val gw = size.width.toFloat().coerceAtLeast(1f)
                                val gh = size.height.toFloat().coerceAtLeast(1f)
                                vm.editLayerAt(cur.id, "xf", posNow) {
                                    it.copy(
                                        x = (it.x + pan.x / gw).coerceIn(0f, 1f),
                                        y = (it.y + pan.y / gh).coerceIn(0f, 1f),
                                        scale = (it.scale * zoomChange).coerceIn(0.1f, 6f),
                                        rotation = LayerOps.normDeg(it.rotation + rotChange)
                                    )
                                }
                            }
                        }
                    )
                }
                if (!playing) {
                    Box(
                        Modifier.size(54.dp).clip(CircleShape).background(Color(0x99000000))
                            .border(1.dp, Color(0x33FFFFFF), CircleShape),
                        contentAlignment = Alignment.Center
                    ) { ZIcon(ZI.Play, tint = Color.White, size = 26.dp) }
                }
            }
        }

        // ================= word strip =================
        WordStrip(words, if (curSrc == 0) curLocal else -1L) { t -> seekSrc(t) }

        // ================= transport =================
        CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
            Row(
                Modifier.fillMaxWidth().height(52.dp).padding(horizontal = Z.S3),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically) {
                    Txt(faTimeFrames(posMs, srcFps), style = ZType.mono, size = 13.sp)
                    Txt("  /  ", style = ZType.mono, color = Z.TextTer, size = 13.sp)
                    Txt(faTime(kept), style = ZType.mono, color = Z.TextSec, size = 13.sp)
                }
                Row(horizontalArrangement = Arrangement.spacedBy(Z.S2), verticalAlignment = Alignment.CenterVertically) {
                    ZIconButton(ZI.SkipPrev, { prevCaption() }, enabled = ui.phrases.isNotEmpty(), size = 20.dp)
                    ZIconButton(
                        if (playing) ZI.Pause else ZI.Play, { toggle() },
                        bg = Z.Text, tint = Z.Bg, box = 44.dp, size = 22.dp, round = true
                    )
                    ZIconButton(ZI.SkipNext, { nextCaption() }, enabled = ui.phrases.isNotEmpty(), size = 20.dp)
                }
                Row(Modifier.weight(1f), horizontalArrangement = Arrangement.End, verticalAlignment = Alignment.CenterVertically) {
                    ZIconButton(ZI.Minus, { tlScale = (tlScale / 1.4f).coerceAtLeast(14f) }, size = 18.dp, box = 36.dp, tint = Z.TextSec)
                    ZIconButton(ZI.Plus, { tlScale = (tlScale * 1.4f).coerceAtMost(220f) }, size = 18.dp, box = 36.dp, tint = Z.TextSec)
                }
            }
        }

        // ================= timeline =================
        Timeline(
            total = map.total,
            posMs = posMs,
            map = map,
            outPhrases = outPhrases,
            activeIdx = activeIdx,
            curSeg = curSeg,
            kinds = clipKinds,
            layers = ui.layers,
            layerRows = layerRows,
            selLayer = if (panelOpen && tab == EdTab.LAYER) ui.selectedLayer else -1,
            audio = ui.audio,
            audioRows = audioRows,
            selAudio = if (panelOpen && tab == EdTab.AUDIO) ui.selectedAudio else -1,
            waves = ui.waves,
            scale = tlScale,
            onScale = { tlScale = it },
            videoKeys = ui.fx.vkeys.map { it.tMs },
            onScrub = { scrub(it) },
            onReorderClip = { from, to -> applyEdl(EdlOps.moveIndex(map.segs, from, to)) },
            onTrimClip = { index, edge, out -> applyEdl(EdlOps.trimBoundary(map.segs, index, edge, out)) },
            onMoveLayer = { id, delta -> vm.editLayer(id, "timeline") { layer ->
                // Keep the complete overlay inside the edited video duration.
                val latestStart = (map.total - layer.len).coerceAtLeast(0L)
                val start = (layer.startMs + delta).coerceIn(0L, latestStart)
                val shift = start - layer.startMs
                layer.copy(startMs = start, endMs = start + layer.len)
            } },
            onTrimLayer = { id, edge, target -> vm.editLayer(id, "timeline-trim") { layer ->
                if (edge == 0) layer.copy(startMs = target.coerceIn(0L, layer.endMs - Layer.MIN_MS))
                else layer.copy(endMs = target.coerceIn(layer.startMs + Layer.MIN_MS, map.total))
            } },
            onMoveAudio = { id, delta -> vm.editAudio(id, "timeline") { clip ->
                // Do not leave audio hanging past the end of the edited video.
                val latestStart = (map.total - clip.lenMs).coerceAtLeast(0L)
                clip.copy(startMs = (clip.startMs + delta).coerceIn(0L, latestStart))
            } },
            onTrimAudio = { id, edge, target -> vm.editAudio(id, "timeline-trim") { clip ->
                if (edge == 0) {
                    val earliestStart = (clip.startMs - clip.inMs).coerceAtLeast(0L)
                    val newStart = target.coerceIn(earliestStart, clip.endMs - AudioClip.MIN_MS)
                    val cut = newStart - clip.startMs
                    clip.copy(startMs = newStart, inMs = clip.inMs + cut, lenMs = clip.lenMs - cut)
                } else clip.copy(lenMs = (target - clip.startMs).coerceIn(AudioClip.MIN_MS, clip.lenMs))
            } },
            onTrimPhrase = { i, edge, target -> vm.trimPhraseAtOut(i, edge, target) },
            onPhrase = { i ->
                val op = outPhrases.firstOrNull { it.idx == i }
                if (op != null) {
                    scrub(op.startMs)
                    tab = EdTab.CAPTIONS
                    panelOpen = true
                }
            },
            onLayer = { id ->
                vm.selectLayer(id)
                tab = EdTab.LAYER
                panelOpen = true
            },
            onAudio = { id ->
                vm.selectAudio(id)
                tab = EdTab.AUDIO
                panelOpen = true
            }
        )

        // ================= tool panel =================
        AnimatedVisibility(
            visible = panelOpen,
            enter = expandVertically(tween(220)) + fadeIn(tween(220)),
            exit = shrinkVertically(tween(180)) + fadeOut(tween(120))
        ) {
            Column(Modifier.fillMaxWidth().height(panelH).background(Z.Surface)) {
                ZHairline()
                Row(
                    Modifier.fillMaxWidth().height(44.dp).padding(horizontal = Z.S4),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Txt(tab.title, style = ZType.heading, modifier = Modifier.weight(1f), maxLines = 1)
                    if (tab == EdTab.CAPTIONS && ui.phrases.isNotEmpty()) {
                        Txt(fa(ui.phrases.size) + " بخش", style = ZType.caption, color = Z.TextSec)
                        Spacer(Modifier.width(Z.S2))
                    }
                    ZIconButton(ZI.Chevron, { panelOpen = false }, tint = Z.TextSec, box = 36.dp, size = 20.dp)
                }
                Box(Modifier.weight(1f).fillMaxWidth().padding(start = Z.S4, end = Z.S4, bottom = Z.S3)) {
                    when (tab) {
                        EdTab.CLIP -> ClipPanel(
                            map = map,
                            posMs = posMs,
                            manual = ui.segments != null,
                            onStep = { stepFrames(it) },
                            onApply = { applyEdl(it) },
                            onAuto = { pauseIfPlaying(); vm.setSegments(null) },
                            onAddMedia = { pauseIfPlaying(); onAddMedia(posMs) },
                            onFreeze = { len -> pauseIfPlaying(); vm.freezeFrame(posMs, len) },
                            fx = ui.fx,
                            onFx = { vm.setFx(it) },
                            canSpeed = clipKinds.getOrNull(curSeg) != 2,
                            kinds = clipKinds,
                            ripple = ui.ripple,
                            onRipple = { vm.setRipple(it) },
                            onGoto = { t -> scrub(t) },
                            onSeekClip = { i -> seekOut(map.outStart(i)) }
                        )
                        EdTab.KEY -> ChromaKeyPanel(
                            clipIndex = curSeg.coerceIn(0, maxOf(0, ranges.size - 1)),
                            clipCount = ranges.size,
                            isPhoto = curIsImage,
                            look = ranges.getOrNull(curSeg)?.look ?: ClipLook(),
                            grade = ui.grade,
                            lut = lutAffine,
                            projDir = projDir,
                            posMs = posMs,
                            onGrab = { t -> vm.keyFrame(t) },
                            onLook = { lk ->
                                val i = curSeg.coerceIn(0, maxOf(0, map.segs.size - 1))
                                if (map.segs.isNotEmpty()) applyEdl(EdlResult(map.segs.toMutableList().also { it[i] = it[i].copy(look = lk) }, posMs))
                            },
                            onPickBackground = { pauseIfPlaying(); onPickKeyBg(posMs) }
                        )
                        EdTab.LAYER -> LayerPanel(
                            layers = ui.layers,
                            selectedId = ui.selectedLayer,
                            posMs = posMs,
                            total = kept,
                            fonts = ui.fonts,
                            projectFontId = ui.fontId,
                            onSelect = { id -> vm.selectLayer(id) },
                            customStyles = vm.customTextStyles,
                            onSaveStyle = { id -> vm.saveTextStyle(id) },
                            onDeleteStyle = { name -> vm.deleteTextStyle(name) },
                            onAddText = { pauseIfPlaying(); vm.addTextLayer(posMs, kept) },
                            onAddImage = { pauseIfPlaying(); onAddLayerImage(posMs, kept) },
                            onEdit = { id, key, f -> vm.editLayerAt(id, key, posMs, f) },
                            onDelete = { id -> vm.deleteLayer(id) },
                            onDuplicate = { id -> vm.duplicateLayer(id) },
                            onSplit = { id -> vm.splitLayer(id, posMs) },
                            onMove = { id, d -> vm.moveLayer(id, d) },
                            onGoto = { t -> scrub(t) }
                        )
                        EdTab.AUDIO -> AudioPanel(
                            clips = ui.audio,
                            selectedId = ui.selectedAudio,
                            posMs = posMs,
                            mainVolume = ui.mainVolume,
                            mainMuted = ui.mainMuted,
                            duck = ui.duckMusic,
                            onDuck = { vm.setDuckMusic(it) },
                            recording = recorder != null,
                            recLevel = recLevel,
                            recElapsed = recElapsed,
                            onSelect = { id -> vm.selectAudio(id) },
                            onAddFile = { pauseIfPlaying(); onAddAudio(posMs, kept) },
                            onRecord = { askRecord() },
                            onStopRecord = { stopRecording() },
                            onMainVolume = { v -> vm.setMainVolume(v) },
                            onMainMuted = { m -> vm.setMainMuted(m) },
                            onEdit = { id, key, f -> vm.editAudio(id, key, f) },
                            onDelete = { id -> vm.deleteAudio(id) },
                            onDuplicate = { id -> vm.duplicateAudio(id) },
                            onSplit = { id -> vm.splitAudio(id, posMs) },
                            onClean = { id, st -> vm.cleanAudio(id, st) },
                            onEq = { id, bands -> vm.eqAudio(id, bands) },
                            onExtract = { pauseIfPlaying(); vm.extractVideoAudio() },
                            onSilence = { id, lv -> vm.removeAudioSilence(id, lv) },
                            onGoto = { t -> scrub(t) }
                        )
                        EdTab.CAPTIONS -> CaptionsPanel(
                            phrases = ui.phrases,
                            activeIdx = activeIdx,
                            onSeek = { t -> seekSrc(t) },
                            onEdit = { i, t -> vm.editPhrase(i, t) },
                            onMerge = { vm.mergeWithNext(it) },
                            onDelete = { vm.deletePhrase(it) },
                            onSplitShort = { vm.splitShort(it) },
                            onNudge = { i, a, b -> vm.nudgePhrase(i, a, b) },
                            onNudgeWord = { i, k, a, b -> vm.nudgeWord(i, k, a, b) },
                            onGenerate = onImportSrt
                        )
                        EdTab.STYLE -> StylePanel(ui) { vm.setStyle(it) }
                        EdTab.FONT -> FontPanel(ui.fonts, ui.fontId, { vm.setFont(it) }, onPickFont)
                        EdTab.POSITION -> PositionPanel(ui.layout) { vm.setLayout(it) }
                        EdTab.FILTER -> FilterPanel(ui.video, ui.grade, ui.fx, { vm.setFx(it) }, ui.myTemplates, { vm.applyTemplate(it) }, { vm.applyMyTemplate(it) }, { vm.saveMyTemplate() }, { vm.deleteMyTemplate(it) }, onPickLut) { vm.setGrade(it) }
                        EdTab.EDIT -> EditPanel(
                            ui.options, info, kept, onSettings,
                            onSrtOut = { vm.exportSrt() }, onSrtIn = onImportSrt,
                            onScenes = { vm.detectScenes() }, onClean = { vm.cleanUnused() }
                        ) { vm.setOptions(it) }
                    }
                }
            }
        }

        // ================= tool dock =================
        Column(Modifier.fillMaxWidth().background(Z.Surface)) {
            ZHairline()
            // the dock scrolls sideways: 6.5 items fit, so the half item at the edge shows there are more tools
            val itemW = 64.dp
            Row(Modifier.fillMaxWidth().height(68.dp).horizontalScroll(rememberScrollState()).padding(horizontal = Z.S2)) {
                EdTab.values().forEach { t ->
                    ToolItem(t.icon, t.label, selected = panelOpen && tab == t, action = false, modifier = Modifier.width(itemW)) {
                        if (panelOpen && tab == t) {
                            panelOpen = false
                        } else {
                            tab = t
                            panelOpen = true
                        }
                    }
                }
            }
        }
    }

    if (showRename) {
        RenameDialog(
            initial = ui.projectName,
            onConfirm = { vm.renameProject(ui.projectId, it); showRename = false },
            onDismiss = { showRename = false }
        )
    }

    if (showExport) {
        ExportSheet(
            opt = ui.options,
            phraseCount = ui.phrases.size,
            original = info.durationMs,
            kept = kept,
            video = ui.video,
            srcW = info.width,
            srcH = info.height,
            settings = ui.exportSettings,
            onSettings = { vm.setExportSettings(it) },
            onOptions = { vm.setOptions(it) },
            onDismiss = { showExport = false },
            onExport = {
                showExport = false
                vm.export()
            }
        )
    }
}

// =====================================================================================
//  Dock
// =====================================================================================

@Composable
private fun ToolItem(icon: ZI, label: String, selected: Boolean, action: Boolean, modifier: Modifier, onClick: () -> Unit) {
    val tint by animateColorAsState(if (selected || action) Z.Accent else Z.TextSec, tween(160), label = "toolTint")
    val iconTint = if (action) Z.OnAccent else tint
    val bg by animateColorAsState(
        if (action) Z.Accent else if (selected) Z.AccentSoft else Color.Transparent, tween(160), label = "toolBg"
    )
    Column(
        modifier.fillMaxHeight().press(true, onClick).padding(horizontal = 2.dp, vertical = 5.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Box(
            Modifier.size(width = 46.dp, height = 34.dp).clip(RoundedCornerShape(11.dp))
                .background(bg)
                .then(if (selected) Modifier.border(1.dp, Z.AccentLine, RoundedCornerShape(11.dp)) else Modifier),
            contentAlignment = Alignment.Center
        ) { ZIcon(icon, tint = iconTint, size = 20.dp) }
        Spacer(Modifier.height(3.dp))
        Txt(label, style = ZType.micro, color = tint, maxLines = 1)
    }
}

// =====================================================================================
//  Word strip: every word is a pill, tap to jump, the current one follows the playhead
// =====================================================================================

@Composable
private fun WordStrip(words: List<Word>, posMs: Long, onSeek: (Long) -> Unit) {
    if (words.isEmpty()) return
    var lo = 0
    var hi = words.size - 1
    var cur = 0
    while (lo <= hi) {
        val m = (lo + hi) / 2
        if (words[m].startMs <= posMs) { cur = m; lo = m + 1 } else hi = m - 1
    }
    val state = rememberLazyListState()
    LaunchedEffect(cur) { state.animateScrollToItem((cur - 1).coerceAtLeast(0)) }
    LazyRow(
        state = state,
        modifier = Modifier.fillMaxWidth().height(40.dp),
        contentPadding = PaddingValues(horizontal = Z.S3),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        itemsIndexed(words) { i, w ->
            val sel = i == cur
            val bg by animateColorAsState(if (sel) Z.Accent else Z.Surface2, tween(120), label = "wordBg")
            Box(
                Modifier.clip(RoundedCornerShape(8.dp)).background(bg)
                    .press(true) { onSeek(w.startMs) }
                    .padding(horizontal = 12.dp, vertical = 4.dp)
            ) {
                Txt(app.zircut.i18n.Lang.raw(w.text), style = if (sel) ZType.labelStrong else ZType.label, color = if (sel) Z.OnAccent else Z.TextSec, maxLines = 1)
            }
        }
    }
}

// =====================================================================================
//  Timeline: playhead fixed in the middle, content scrolls under it.
//  Drag = scrub, pinch = zoom, long-press + drag a video clip = reorder, tap a block = select it.
// =====================================================================================

@Composable
private fun Timeline(
    total: Long,
    posMs: Long,
    map: EdlMap,
    outPhrases: List<OutPhrase>,
    activeIdx: Int,
    curSeg: Int,
    kinds: List<Int>,
    layers: List<Layer>,
    layerRows: IntArray,
    selLayer: Int,
    audio: List<AudioClip>,
    audioRows: IntArray,
    selAudio: Int,
    waves: Map<String, ByteArray>,
    scale: Float,
    onScale: (Float) -> Unit,
    onScrub: (Long) -> Unit,
    onReorderClip: (Int, Int) -> Unit,
    onTrimClip: (Int, Int, Long) -> Unit,
    onMoveLayer: (Int, Long) -> Unit,
    onTrimLayer: (Int, Int, Long) -> Unit,
    onMoveAudio: (Int, Long) -> Unit,
    onTrimAudio: (Int, Int, Long) -> Unit,
    onTrimPhrase: (Int, Int, Long) -> Unit,
    onPhrase: (Int) -> Unit,
    onLayer: (Int) -> Unit,
    onAudio: (Int) -> Unit,
    /** times (edited timeline) of the video picture's keyframes: drawn as diamonds on the ruler, a tap on one jumps to it */
    videoKeys: List<Long> = emptyList()
) {
    val ctx = LocalContext.current
    val vkeysNow by rememberUpdatedState(videoKeys)
    val paint = remember {
        TextPaint(AndroidPaint.ANTI_ALIAS_FLAG).apply { typeface = ZFont.typeface(ctx, 500) ?: Typeface.DEFAULT }
    }
    val scrubCb by rememberUpdatedState(onScrub)
    val scaleCb by rememberUpdatedState(onScale)
    val phraseCb by rememberUpdatedState(onPhrase)
    val scaleNow by rememberUpdatedState(scale)
    val phrasesNow by rememberUpdatedState(outPhrases)
    val layerCb by rememberUpdatedState(onLayer)
    val layersNow by rememberUpdatedState(layers)
    val rowsNow by rememberUpdatedState(layerRows)
    val rowsN = LayerOps.rowCount(layerRows)
    val audioCb by rememberUpdatedState(onAudio)
    val audioNowL by rememberUpdatedState(audio)
    val audioRowsNow by rememberUpdatedState(audioRows)
    val audioRowsN = AudioOps.rowCount(audioRows)
    val reorderCb by rememberUpdatedState(onReorderClip)
    val trimCb by rememberUpdatedState(onTrimClip)
    val moveLayerCb by rememberUpdatedState(onMoveLayer)
    val trimLayerCb by rememberUpdatedState(onTrimLayer)
    val moveAudioCb by rememberUpdatedState(onMoveAudio)
    val trimAudioCb by rememberUpdatedState(onTrimAudio)
    val trimPhraseCb by rememberUpdatedState(onTrimPhrase)
    val tlHeight = run {
        val base = 110 + (if (rowsN > 0) 4 + 26 * rowsN else 0)
        if (audioRowsN > 0) maxOf(base, 102 + 26 * rowsN + 26 * audioRowsN + 8) else base
    }
    val live = remember { floatArrayOf(0f) }
    live[0] = posMs.toFloat()

    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
        Canvas(
            Modifier.fillMaxWidth().height(tlHeight.dp)
                .pointerInput(total) {
                    detectTransformGestures { _, pan, zoom, _ ->
                        if (zoom != 1f) scaleCb((scaleNow * zoom).coerceIn(14f, 220f))
                        if (pan.x != 0f) {
                            val pxMs = scaleNow * density / 1000f
                            val t = (live[0] - pan.x / pxMs).coerceIn(0f, total.toFloat())
                            live[0] = t
                            scrubCb(t.toLong())
                        }
                    }
                }
                .pointerInput(map.segs, total, scale, posMs, layers, layerRows, audio, audioRows) {
                    var fromIndex = -1
                    var toIndex = -1
                    var trimIndex = -1
                    var trimEdge = -1
                    var trimTarget = 0L
                    var moveLayerId = -1
                    var moveAudioId = -1
                    var trimLayerId = -1
                    var trimAudioId = -1
                    var trimPhrase = -1
                    var dragOrigin = 0L
                    var moveDelta = 0L
                    var movingStart = 0L
                    var movingEnd = 0L
                    fun outAt(x: Float): Long {
                        val pxMs = scale * density / 1000f
                        return (posMs + (x - size.width / 2f) / pxMs).toLong().coerceIn(0L, map.total)
                    }
                    fun indexAt(x: Float): Int = if (map.segs.isEmpty()) -1 else map.locate(outAt(x).coerceAtMost((map.total - 1L).coerceAtLeast(0L))).first
                    detectDragGesturesAfterLongPress(
                        onDragStart = { o ->
                            fromIndex = -1; toIndex = -1; trimIndex = -1; trimEdge = -1; trimPhrase = -1
                            moveLayerId = -1; moveAudioId = -1; trimLayerId = -1; trimAudioId = -1; trimEdge = -1; moveDelta = 0L
                            val layerTopPx = 102f * density
                            val audioTopPx = (102f + 26f * LayerOps.rowCount(layerRows)) * density
                            val at = outAt(o.x)
                            if (o.y >= audioTopPx && audioRows.isNotEmpty()) {
                                val row = ((o.y - audioTopPx) / (26f * density)).toInt()
                                for (i in audio.indices.reversed()) if (audioRows.getOrNull(i) == row && at >= audio[i].startMs && at <= audio[i].endMs) {
                                    val c = audio[i]
                                    val edgeRadius = (16f * density / (scale * density / 1000f)).toLong().coerceIn(70L, 450L)
                                    if (kotlin.math.abs(at - c.startMs) <= edgeRadius) { trimAudioId = c.id; trimEdge = 0 }
                                    else if (kotlin.math.abs(at - c.endMs) <= edgeRadius) { trimAudioId = c.id; trimEdge = 1 }
                                    else moveAudioId = c.id
                                    break
                                }
                                if (trimAudioId >= 0) trimTarget = if (trimEdge == 0) audio.first { it.id == trimAudioId }.startMs else audio.first { it.id == trimAudioId }.endMs
                                if (moveAudioId >= 0) {
                                    dragOrigin = at
                                    val moving = audio.first { it.id == moveAudioId }
                                    movingStart = moving.startMs
                                    movingEnd = moving.endMs
                                }
                            } else if (o.y >= layerTopPx && layers.isNotEmpty()) {
                                val row = ((o.y - layerTopPx) / (26f * density)).toInt()
                                for (i in layers.indices.reversed()) if (layerRows.getOrNull(i) == row && at >= layers[i].startMs && at <= layers[i].endMs) {
                                    val l = layers[i]
                                    val edgeRadius = (16f * density / (scale * density / 1000f)).toLong().coerceIn(70L, 450L)
                                    if (kotlin.math.abs(at - l.startMs) <= edgeRadius) { trimLayerId = l.id; trimEdge = 0 }
                                    else if (kotlin.math.abs(at - l.endMs) <= edgeRadius) { trimLayerId = l.id; trimEdge = 1 }
                                    else moveLayerId = l.id
                                    break
                                }
                                if (trimLayerId >= 0) trimTarget = if (trimEdge == 0) layers.first { it.id == trimLayerId }.startMs else layers.first { it.id == trimLayerId }.endMs
                                if (moveLayerId >= 0) {
                                    dragOrigin = at
                                    val moving = layers.first { it.id == moveLayerId }
                                    movingStart = moving.startMs
                                    movingEnd = moving.endMs
                                }
                            } else if (o.y in (62f * density)..(98f * density) && phrasesNow.isNotEmpty()) {
                                // long-press near the start or end of a subtitle, then drag it
                                val edgeRadius = (16f * density / (scale * density / 1000f)).toLong().coerceIn(70L, 450L)
                                var best = edgeRadius + 1L
                                for (op in phrasesNow) {
                                    val ds = kotlin.math.abs(at - op.startMs)
                                    val de = kotlin.math.abs(at - op.endMs)
                                    if (ds <= edgeRadius && ds < best) { best = ds; trimPhrase = op.idx; trimEdge = 0; trimTarget = op.startMs }
                                    if (de <= edgeRadius && de < best) { best = de; trimPhrase = op.idx; trimEdge = 1; trimTarget = op.endMs }
                                }
                            } else if (o.y in (26f * density)..(58f * density) && map.segs.isNotEmpty()) {
                                val hitRadius = 18f * density
                                val x = o.x
                                map.segs.indices.forEach { i ->
                                    val left = size.width / 2f + (map.outStart(i) - posMs) * scale * density / 1000f
                                    val right = size.width / 2f + (map.outStart(i) + map.segs[i].outLen - posMs) * scale * density / 1000f
                                    if (kotlin.math.abs(x - left) <= hitRadius) { trimIndex = i; trimEdge = 0 }
                                    if (kotlin.math.abs(x - right) <= hitRadius) { trimIndex = i; trimEdge = 1 }
                                }
                                if (trimIndex >= 0) {
                                    val i = trimIndex
                                    val start = map.outStart(i)
                                    val len = map.segs[i].outLen
                                    trimTarget = if (trimEdge == 0) start else start + len
                                } else {
                                    fromIndex = indexAt(o.x)
                                    toIndex = fromIndex
                                }
                            }
                        },
                        onDragEnd = {
                            if (trimPhrase >= 0) trimPhraseCb(trimPhrase, trimEdge, trimTarget)
                            else if (trimIndex >= 0) trimCb(trimIndex, trimEdge, trimTarget)
                            else if (trimLayerId >= 0) trimLayerCb(trimLayerId, trimEdge, trimTarget)
                            else if (trimAudioId >= 0) trimAudioCb(trimAudioId, trimEdge, trimTarget)
                            else if (moveLayerId >= 0 && moveDelta != 0L) moveLayerCb(moveLayerId, moveDelta)
                            else if (moveAudioId >= 0 && moveDelta != 0L) moveAudioCb(moveAudioId, moveDelta)
                            else if (fromIndex >= 0 && toIndex >= 0 && fromIndex != toIndex) reorderCb(fromIndex, toIndex)
                            fromIndex = -1; toIndex = -1; trimIndex = -1; trimPhrase = -1; trimLayerId = -1; trimAudioId = -1; trimEdge = -1; moveLayerId = -1; moveAudioId = -1
                        },
                        onDragCancel = { fromIndex = -1; toIndex = -1; trimIndex = -1; trimPhrase = -1; trimLayerId = -1; trimAudioId = -1; trimEdge = -1; moveLayerId = -1; moveAudioId = -1 }
                    ) { change, _ ->
                        if (trimPhrase >= 0) {
                            trimTarget = outAt(change.position.x)
                            change.consume()
                        } else if (trimLayerId >= 0 || trimAudioId >= 0) {
                            val id = if (trimLayerId >= 0) trimLayerId else trimAudioId
                            val itemStart = if (trimLayerId >= 0) layers.first { it.id == id }.startMs else audio.first { it.id == id }.startMs
                            val itemEnd = if (trimLayerId >= 0) layers.first { it.id == id }.endMs else audio.first { it.id == id }.endMs
                            trimTarget = outAt(change.position.x).coerceIn(if (trimEdge == 0) 0L else itemStart + 200L, if (trimEdge == 0) itemEnd - 200L else map.total)
                            change.consume()
                        } else if (moveLayerId >= 0 || moveAudioId >= 0) {
                            val requestedDelta = outAt(change.position.x) - dragOrigin
                            val minDelta = -movingStart
                            val maxDelta = (map.total - movingEnd).coerceAtLeast(minDelta)
                            val rawDelta = requestedDelta.coerceIn(minDelta, maxDelta)
                            val rawStart = movingStart + rawDelta
                            val rawEnd = movingEnd + rawDelta
                            val anchors = ArrayList<Long>()
                            // Project boundaries and playhead are useful alignment targets.
                            anchors.add(0L)
                            anchors.add(map.total)
                            anchors.add(posMs)
                            map.segs.indices.forEach { i ->
                                anchors.add(map.outStart(i))
                                anchors.add(map.outStart(i) + map.segs[i].outLen)
                            }
                            layers.filter { it.id != moveLayerId }.forEach {
                                anchors.add(it.startMs); anchors.add(it.endMs)
                            }
                            audio.filter { it.id != moveAudioId }.forEach {
                                anchors.add(it.startMs); anchors.add(it.endMs)
                            }
                            // Snap either edge to the nearest timeline landmark (about 120 ms).
                            val snapWindow = (120f / scale.coerceAtLeast(1f) * 1000f).toLong().coerceIn(35L, 180L)
                            var bestDelta = rawDelta
                            var bestDistance = snapWindow + 1L
                            anchors.forEach { anchor ->
                                val ds = anchor - rawStart
                                val de = anchor - rawEnd
                                if (kotlin.math.abs(ds) < bestDistance) {
                                    bestDistance = kotlin.math.abs(ds); bestDelta = rawDelta + ds
                                }
                                if (kotlin.math.abs(de) < bestDistance) {
                                    bestDistance = kotlin.math.abs(de); bestDelta = rawDelta + de
                                }
                            }
                            // Snapping must never push the clip outside the project bounds.
                            val snapped = if (bestDistance <= snapWindow) bestDelta else rawDelta
                            moveDelta = snapped.coerceIn(minDelta, maxDelta)
                            change.consume()
                        } else if (trimIndex >= 0) {
                            val i = trimIndex
                            val start = map.outStart(i)
                            val len = map.segs[i].outLen
                            trimTarget = outAt(change.position.x).coerceIn(start + 100L, start + len - 100L)
                            change.consume()
                        } else if (fromIndex >= 0) {
                            toIndex = indexAt(change.position.x)
                            change.consume()
                        }
                    }
                }
                .pointerInput(total) {
                    detectTapGestures { o ->
                        val pxMs = scaleNow * density / 1000f
                        val ms = (live[0] + (o.x - size.width / 2f) / pxMs).coerceIn(0f, total.toFloat()).toLong()
                        var hit = -1
                        var layerHit = -1
                        var audioHit = -1
                        if (o.y >= 100f * density) {
                            val audioTop = (102f + 26f * LayerOps.rowCount(rowsNow)) * density
                            if (o.y >= audioTop) {
                                // rows of music / voice-over under the layers
                                val row = ((o.y - audioTop) / (26f * density)).toInt()
                                val cs = audioNowL
                                val rs = audioRowsNow
                                for (i in cs.indices.reversed()) {
                                    if (rs.getOrNull(i) == row && ms >= cs[i].startMs && ms <= cs[i].endMs) { audioHit = cs[i].id; break }
                                }
                            } else {
                                // rows of layers under the subtitle track; the last layer of a row is the one on top
                                val row = ((o.y - 102f * density) / (26f * density)).toInt()
                                val ls = layersNow
                                val rs = rowsNow
                                for (i in ls.indices.reversed()) {
                                    if (rs.getOrNull(i) == row && ms >= ls[i].startMs && ms <= ls[i].endMs) { layerHit = ls[i].id; break }
                                }
                            }
                        } else if (o.y >= 60f * density) {
                            for (op in phrasesNow) {
                                if (ms >= op.startMs && ms <= op.endMs) { hit = op.idx; break }
                            }
                        }
                        // tap on a video keyframe diamond (ruler) jumps to that key
                        val keyTap = if (o.y < 28f * density) vkeysNow.minByOrNull { kotlin.math.abs(it - ms) }?.takeIf { kotlin.math.abs(it - ms) * pxMs <= 16f * density } else null
                        if (keyTap != null) { scrubCb(keyTap); return@detectTapGestures }
                        if (layerHit >= 0) layerCb(layerHit) else if (audioHit >= 0) audioCb(audioHit) else if (hit >= 0) phraseCb(hit) else scrubCb(ms)
                    }
                }
        ) {
            val d = density
            val w = size.width
            val h = size.height
            val pxMs = scale * d / 1000f
            val cx = w / 2f
            val pos = posMs
            fun xOf(ms: Long): Float = cx + (ms - pos) * pxMs

            val lane1Top = 26f * d
            val laneH = 32f * d
            val lane2Top = 64f * d
            val r = CornerRadius(5f * d, 5f * d)

            drawRect(Z.Surface, Offset(0f, lane1Top), Size(w, laneH))
            drawRect(Z.Surface, Offset(0f, lane2Top), Size(w, laneH))
            val layerTop0 = 102f * d
            val layerRowH = 24f * d
            val layerStep = 26f * d
            for (rr in 0 until rowsN) drawRect(Z.Surface, Offset(0f, layerTop0 + rr * layerStep), Size(w, layerRowH))
            val audioTop0 = layerTop0 + rowsN * layerStep
            for (rr in 0 until audioRowsN) drawRect(Z.Surface, Offset(0f, audioTop0 + rr * layerStep), Size(w, layerRowH))

            // ---- ruler ----
            val stepsSec = intArrayOf(1, 2, 5, 10, 15, 30, 60, 120, 300, 600)
            var stepSec = 600
            for (s in stepsSec) {
                if (s * 1000f * pxMs >= 70f * d) { stepSec = s; break }
            }
            val stepMs = stepSec * 1000L
            val visStart = (pos - cx / pxMs).toLong()
            val visEnd = (pos + cx / pxMs).toLong()
            var t = (maxOf(0L, visStart - stepMs) / stepMs) * stepMs
            paint.textSize = 10.5f * d
            paint.textAlign = AndroidPaint.Align.LEFT
            paint.color = Z.TextTer.toArgb()
            while (t <= minOf(total, visEnd + stepMs)) {
                val x = xOf(t)
                drawLine(Z.BorderStrong, Offset(x, 17f * d), Offset(x, 24f * d), strokeWidth = 1f * d)
                val label = faTime(t)
                drawIntoCanvas { it.nativeCanvas.drawText(label, x + 8f * d, 13f * d, paint) }
                if (t + stepMs / 2 <= total) {
                    val mx = xOf(t + stepMs / 2)
                    drawLine(Z.Border, Offset(mx, 20f * d), Offset(mx, 24f * d), strokeWidth = 1f * d)
                }
                t += stepMs
            }

            // ---- video picture keyframes: small diamonds on the ruler ----
            for (kt in videoKeys) {
                val kx = xOf(kt)
                if (kx < -6f * d || kx > w + 6f * d) continue
                val ky = 22f * d
                val rr2 = 4.5f * d
                val dia = androidx.compose.ui.graphics.Path().apply {
                    moveTo(kx, ky - rr2); lineTo(kx + rr2, ky); lineTo(kx, ky + rr2); lineTo(kx - rr2, ky); close()
                }
                drawPath(dia, Z.Warn)
            }

            // ---- video track: clips laid end to end, the one under the playhead is highlighted ----
            for (i in map.segs.indices) {
                val sg = map.segs[i]
                val s0 = map.outStart(i)
                val x1 = xOf(s0) + 1f * d
                val x2 = xOf(s0 + sg.outLen) - 1f * d
                if (x2 < 0f || x1 > w) continue
                val bw = maxOf(3f * d, x2 - x1)
                val cur = i == curSeg
                val photo = kinds.getOrNull(i) == 2
                val fill = if (photo) (if (cur) Color(0xFF343C4A) else Color(0xFF272D38)) else (if (cur) Color(0xFF1B4A63) else Color(0xFF15384A))
                val edge = if (cur) Z.Accent else if (photo) Z.BorderStrong else Z.AccentLine
                drawRoundRect(fill, Offset(x1, lane1Top), Size(bw, laneH), r)
                drawRoundRect(edge, Offset(x1, lane1Top), Size(bw, laneH), r, style = Stroke((if (cur) 1.6f else 1f) * d))
                if (cur && bw > 28f * d) {
                    val gripColor = Z.Text
                    drawRoundRect(gripColor, Offset(x1 + 2f * d, lane1Top + 9f * d), Size(3f * d, 14f * d), CornerRadius(2f * d, 2f * d))
                    drawRoundRect(gripColor, Offset(x1 + bw - 5f * d, lane1Top + 9f * d), Size(3f * d, 14f * d), CornerRadius(2f * d, 2f * d))
                }
                val tx = minOf(x1 + bw, w) - 8f * d
                if (tx - maxOf(x1, 0f) > 56f * d) {
                    paint.textSize = 11f * d
                    paint.textAlign = AndroidPaint.Align.RIGHT
                    paint.color = Z.Accent.toArgb()
                    val label = app.zircut.i18n.Lang.tr((if (photo) "عکس " else "ویدیو ") + fa(i + 1) + (if (sg.speed != 1f) "  " + speedLabel(sg.speed) else ""))
                    drawIntoCanvas { it.nativeCanvas.drawText(label, tx, lane1Top + laneH / 2f + 4f * d, paint) }
                }
            }

            // ---- subtitle track ----
            if (outPhrases.isEmpty()) {
                paint.textSize = 11.5f * d
                paint.textAlign = AndroidPaint.Align.CENTER
                paint.color = Z.TextTer.toArgb()
                drawIntoCanvas { it.nativeCanvas.drawText(app.zircut.i18n.Lang.tr("هنوز زیرنویسی ساخته نشده"), cx, lane2Top + laneH / 2f + 4f * d, paint) }
            } else {
                for (op in outPhrases) {
                    val x1 = xOf(op.startMs) + 1f * d
                    val x2 = xOf(op.endMs) - 1f * d
                    if (x2 < 0f || x1 > w) continue
                    val bw = maxOf(4f * d, x2 - x1)
                    val active = op.idx == activeIdx
                    drawRoundRect(if (active) Z.Accent else Z.Surface3, Offset(x1, lane2Top), Size(bw, laneH), r)
                    if (!active) drawRoundRect(Z.BorderStrong, Offset(x1, lane2Top), Size(bw, laneH), r, style = Stroke(1f * d))
                    if (bw > 30f * d) {
                        paint.textSize = 12f * d
                        paint.textAlign = AndroidPaint.Align.RIGHT
                        paint.color = if (active) Z.OnAccent.toArgb() else Z.Text.toArgb()
                        val shown = TextUtils.ellipsize(op.text, paint, bw - 14f * d, TextUtils.TruncateAt.END).toString()
                        drawIntoCanvas { it.nativeCanvas.drawText(shown, x1 + bw - 7f * d, lane2Top + laneH / 2f + 4.2f * d, paint) }
                    }
                    if (active && bw > 28f * d) {
                        drawRoundRect(Z.OnAccent, Offset(x1 + 2f * d, lane2Top + 9f * d), Size(3f * d, 14f * d), CornerRadius(2f * d, 2f * d))
                        drawRoundRect(Z.OnAccent, Offset(x1 + bw - 5f * d, lane2Top + 9f * d), Size(3f * d, 14f * d), CornerRadius(2f * d, 2f * d))
                    }
                    if (op.low) drawCircle(Z.Warn, 3f * d, Offset(x1 + 6f * d, lane2Top + 6f * d))
                }
            }

            // ---- text / picture layers, one row per overlapping group ----
            for (i in layers.indices) {
                val l = layers[i]
                val x1 = xOf(l.startMs) + 1f * d
                val x2 = xOf(l.endMs) - 1f * d
                if (x2 < 0f || x1 > w) continue
                val bw = maxOf(3f * d, x2 - x1)
                val top = layerTop0 + (layerRows.getOrNull(i) ?: 0) * layerStep
                val sel = l.id == selLayer
                val lr = CornerRadius(5f * d, 5f * d)
                val dim = if (l.hidden) 0.4f else 1f
                drawRoundRect(if (l.isText) Color(0xFF2E2A45) else Color(0xFF283F36), Offset(x1, top), Size(bw, layerRowH), lr, alpha = dim)
                drawRoundRect(if (sel) Z.Accent else if (l.locked) Z.Warn else Z.BorderStrong, Offset(x1, top), Size(bw, layerRowH), lr, style = Stroke((if (sel || l.locked) 1.6f else 1f) * d), alpha = dim)
                for (k in l.keys) {
                    val kx = xOf(l.startMs + k.tMs)
                    if (kx >= x1 && kx <= x1 + bw) drawCircle(Z.Accent, 3f * d, Offset(kx, top + layerRowH - 5f * d))
                }
                if (bw > 36f * d) {
                    paint.textSize = 11f * d
                    paint.textAlign = AndroidPaint.Align.RIGHT
                    paint.color = (if (sel) Z.Accent else Z.TextSec).toArgb()
                    val name = (if (l.locked) "قفل · " else "") + (if (l.hidden) "مخفی · " else "") + (if (l.isText) l.text.replace('\n', ' ') else "عکس")
                    val shown = TextUtils.ellipsize(name, paint, bw - 14f * d, TextUtils.TruncateAt.END).toString()
                    drawIntoCanvas { it.nativeCanvas.drawText(shown, x1 + bw - 7f * d, top + layerRowH / 2f + 4f * d, paint) }
                }
            }

            // ---- music / voice-over: real waveform of the file, fades drawn as dark corners ----
            for (i in audio.indices) {
                val c = audio[i]
                val x1 = xOf(c.startMs) + 1f * d
                val x2 = xOf(c.endMs) - 1f * d
                if (x2 < 0f || x1 > w) continue
                val bw = maxOf(3f * d, x2 - x1)
                val top = audioTop0 + (audioRows.getOrNull(i) ?: 0) * layerStep
                val sel = c.id == selAudio
                val ar = CornerRadius(5f * d, 5f * d)
                val bgc = if (c.muted) Color(0xFF22252B) else if (c.isVoice) Color(0xFF3A2A2E) else Color(0xFF26304A)
                val wvc = if (c.muted) Color(0xFF555B66) else if (c.isVoice) Color(0xFFFF8F8F) else Color(0xFF7FB2FF)
                drawRoundRect(bgc, Offset(x1, top), Size(bw, layerRowH), ar)
                val pk = waves[c.file]
                if (pk != null) {
                    val colStep = 2f * d
                    val mid = top + layerRowH / 2f
                    var x = maxOf(x1, 0f)
                    val xEnd = minOf(x1 + bw, w)
                    while (x < xEnd) {
                        val t0 = c.inMs + (x - x1) / pxMs
                        val t1 = c.inMs + (x + colStep - x1) / pxMs
                        val i0 = (t0 * Waveform.PER_SEC / 1000f).toInt().coerceAtLeast(0)
                        val i1 = maxOf(i0, (t1 * Waveform.PER_SEC / 1000f).toInt())
                        if (i0 < pk.size) {
                            var m = 0
                            for (k in i0..minOf(i1, pk.size - 1)) {
                                val v = pk[k].toInt() and 0xFF
                                if (v > m) m = v
                            }
                            val hh = maxOf(0.6f * d, m / 255f * (layerRowH - 6f * d) / 2f)
                            drawLine(wvc, Offset(x, mid - hh), Offset(x, mid + hh), strokeWidth = 1.4f * d)
                        }
                        x += colStep
                    }
                }
                val shade = Color(0x99000000)
                if (c.fadeInMs > 0L) {
                    val fw = c.fadeInMs * pxMs
                    val tri = Path().apply { moveTo(x1, top); lineTo(x1 + fw, top); lineTo(x1, top + layerRowH); close() }
                    drawPath(tri, shade)
                }
                if (c.fadeOutMs > 0L) {
                    val fw = c.fadeOutMs * pxMs
                    val tri = Path().apply { moveTo(x1 + bw, top); lineTo(x1 + bw - fw, top); lineTo(x1 + bw, top + layerRowH); close() }
                    drawPath(tri, shade)
                }
                drawRoundRect(if (sel) Z.Accent else Z.BorderStrong, Offset(x1, top), Size(bw, layerRowH), ar, style = Stroke((if (sel) 1.6f else 1f) * d))
                if (sel && bw > 18f * d) {
                    drawRoundRect(Z.Text, Offset(x1 + 2f * d, top + 7f * d), Size(3f * d, layerRowH - 14f * d), CornerRadius(2f * d, 2f * d))
                    drawRoundRect(Z.Text, Offset(x1 + bw - 5f * d, top + 7f * d), Size(3f * d, layerRowH - 14f * d), CornerRadius(2f * d, 2f * d))
                }
                if (bw > 60f * d) {
                    paint.textSize = 10.5f * d
                    paint.textAlign = AndroidPaint.Align.RIGHT
                    paint.color = (if (sel) Z.Accent else Z.TextSec).toArgb()
                    val shown = TextUtils.ellipsize(c.name, paint, bw - 14f * d, TextUtils.TruncateAt.END).toString()
                    drawIntoCanvas { it.nativeCanvas.drawText(shown, x1 + bw - 7f * d, top + 11f * d, paint) }
                    paint.textAlign = AndroidPaint.Align.LEFT
                }
            }

            // ---- outside the timeline: dim ----
            val x0 = xOf(0L)
            val xe = xOf(total)
            val dim = Color(0xCC0A0B0D)
            if (x0 > 0f) drawRect(dim, Offset(0f, 0f), Size(minOf(x0, w), h))
            if (xe < w) {
                val sx = maxOf(xe, 0f)
                drawRect(dim, Offset(sx, 0f), Size(w - sx, h))
            }

            // ---- playhead ----
            drawLine(Z.Text, Offset(cx, 0f), Offset(cx, h), strokeWidth = 1.5f * d)
            val head = Path().apply {
                moveTo(cx - 5.5f * d, 0f)
                lineTo(cx + 5.5f * d, 0f)
                lineTo(cx + 5.5f * d, 6f * d)
                lineTo(cx, 11f * d)
                lineTo(cx - 5.5f * d, 6f * d)
                close()
            }
            drawPath(head, Z.Text)
        }
    }
}

// =====================================================================================
//  Panels
// =====================================================================================

@Composable
private fun CaptionsPanel(
    phrases: List<Phrase>,
    activeIdx: Int,
    onSeek: (Long) -> Unit,
    onEdit: (Int, String) -> Unit,
    onMerge: (Int) -> Unit,
    onDelete: (Int) -> Unit,
    onSplitShort: (Int) -> Unit,
    onNudge: (Int, Long, Long) -> Unit,
    onNudgeWord: (Int, Int, Long, Long) -> Unit,
    onGenerate: () -> Unit
) {
    val listState = rememberLazyListState()
    LaunchedEffect(activeIdx) {
        // item 0 is the "short parts" header
        if (activeIdx in phrases.indices) listState.animateScrollToItem(activeIdx + 1)
    }
    if (phrases.isEmpty()) {
        Column(
            Modifier.fillMaxSize().verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Box(Modifier.size(40.dp).clip(RoundedCornerShape(Z.RSm)).background(Z.AccentSoft), contentAlignment = Alignment.Center) {
                ZIcon(ZI.Wave, tint = Z.Accent, size = Z.IconMd)
            }
            Spacer(Modifier.height(Z.S2))
            Txt("هنوز زیرنویسی ساخته نشده", style = ZType.labelStrong)
            Txt(
                "زیرنویس را از یک فایل SRT وارد کن. اگه وارد نکنی، ویدیو بدون زیرنویس خروجی می‌گیره.",
                style = ZType.caption, color = Z.TextSec, align = androidx.compose.ui.text.style.TextAlign.Center
            )
            Spacer(Modifier.height(Z.S3))
            ZButton("ورود SRT", onGenerate, icon = ZI.File, compact = true)
        }
        return
    }
    LazyColumn(state = listState, verticalArrangement = Arrangement.spacedBy(Z.S2), modifier = Modifier.fillMaxSize()) {
        item {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Txt("تقسیم به بخش‌های کوتاه (حداکثر تعداد کلمه)", style = ZType.label, color = Z.TextSec)
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(Z.S2)) {
                    listOf(2, 3, 4, 5, 6).forEach { n -> ZChip(fa(n), false, { onSplitShort(n) }, Modifier.weight(1f)) }
                }
            }
        }
        itemsIndexed(phrases) { i, p ->
            var t by remember(p.startMs, p.words.size) { mutableStateOf(p.text) }
            var editIdx by remember(p.startMs, p.words.size) { mutableStateOf(-1) }
            var wordText by remember { mutableStateOf("") }
            var focused by remember { mutableStateOf(false) }
            var selWord by remember(p.words.size) { mutableStateOf(0) }
            val active = i == activeIdx
            val shape = RoundedCornerShape(Z.RSm)
            val line by animateColorAsState(
                if (focused) Z.Accent else if (active) Z.AccentLine else Z.Border, tween(140), label = "capLine"
            )
            Column(
                Modifier.fillMaxWidth().clip(shape)
                    .background(if (active) Color(0xFF12202A) else Z.Surface2)
                    .border(Z.Hair, line, shape)
                    .padding(horizontal = Z.S2, vertical = 6.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        Modifier.clip(RoundedCornerShape(7.dp)).background(if (active) Z.AccentSoft else Z.Surface3)
                            .press(true) { onSeek(p.startMs) }.padding(horizontal = 8.dp, vertical = 4.dp)
                    ) { Txt(faTime(p.startMs), style = ZType.mono, color = if (active) Z.Accent else Z.TextSec) }
                    Spacer(Modifier.width(Z.S2))
                    BasicTextField(
                        value = t,
                        onValueChange = { t = it; onEdit(i, it) },
                        textStyle = ZType.body.copy(color = Z.Text),
                        cursorBrush = SolidColor(Z.Accent),
                        modifier = Modifier.weight(1f).onFocusChanged {
                            focused = it.isFocused
                            if (it.isFocused) onSeek(p.startMs)
                        }
                    )
                    if (i < phrases.size - 1) {
                        ZIconButton(ZI.Merge, { onMerge(i) }, tint = Z.TextSec, box = 34.dp, size = 18.dp)
                    }
                    ZIconButton(ZI.Trash, { onDelete(i) }, tint = Z.Danger, box = 34.dp, size = 18.dp)
                }
                if (active) {
                    // exact timing of the active subtitle: 0.1 s steps
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                        Txt("زمان‌بندی", style = ZType.caption, color = Z.TextSec)
                        ZChip("شروع −", false, { onNudge(i, -100L, 0L) }, Modifier.weight(1f))
                        ZChip("شروع +", false, { onNudge(i, 100L, 0L) }, Modifier.weight(1f))
                        ZChip("پایان −", false, { onNudge(i, 0L, -100L) }, Modifier.weight(1f))
                        ZChip("پایان +", false, { onNudge(i, 0L, 100L) }, Modifier.weight(1f))
                    }
                    // timing of ONE word: the highlight reaches a word at its start
                    if (p.words.size > 1) {
                        val sw = selWord.coerceIn(0, p.words.size - 1)
                        Txt("زمان هر کلمه (هایلایت در شروع کلمه می‌رسد)", style = ZType.caption, color = Z.TextSec)
                        androidx.compose.foundation.layout.FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            p.words.forEachIndexed { k, wd ->
                                ZChip(wd.text.replace("*", ""), sw == k, { selWord = k; onSeek(wd.startMs) })
                            }
                        }
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                            ZChip("شروع −", false, { onNudgeWord(i, sw, -50L, 0L) }, Modifier.weight(1f))
                            ZChip("شروع +", false, { onNudgeWord(i, sw, 50L, 0L) }, Modifier.weight(1f))
                            ZChip("پایان −", false, { onNudgeWord(i, sw, 0L, -50L) }, Modifier.weight(1f))
                            ZChip("پایان +", false, { onNudgeWord(i, sw, 0L, 50L) }, Modifier.weight(1f))
                        }
                    }
                }
                val bad = p.words.withIndex().filter { it.value.conf < 0.5f }
                if (bad.isNotEmpty()) {
                    androidx.compose.foundation.layout.FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Txt("احتمال خطا:", style = ZType.caption, color = Z.Warn, modifier = Modifier.padding(top = 3.dp))
                        bad.forEach { iv ->
                            Box(
                                Modifier.clip(RoundedCornerShape(7.dp)).background(Z.WarnSoft)
                                    .press(true) { editIdx = iv.index; wordText = iv.value.text }
                                    .padding(horizontal = 10.dp, vertical = 3.dp)
                            ) { Txt(app.zircut.i18n.Lang.raw(iv.value.text), style = ZType.label, color = Z.Warn) }
                        }
                    }
                }
            }
            if (editIdx >= 0 && editIdx < p.words.size) {
                AlertDialog(
                    onDismissRequest = { editIdx = -1 },
                    containerColor = Z.Surface2,
                    shape = RoundedCornerShape(Z.RMd),
                    title = { Txt("اصلاح کلمه", style = ZType.heading) },
                    text = { ZField(wordText, { wordText = it }, singleLine = true) },
                    confirmButton = {
                        ZButton("تأیید", {
                            val nt = p.words.mapIndexed { k, w -> if (k == editIdx) wordText.trim() else w.text }
                                .filter { it.isNotEmpty() }.joinToString(" ")
                            t = nt
                            onEdit(i, nt)
                            editIdx = -1
                        }, compact = true)
                    },
                    dismissButton = { ZButton("لغو", { editIdx = -1 }, kind = ZBtn.Ghost, compact = true) }
                )
            }
        }
    }
}

@Composable
private fun StylePanel(ui: UiState, onPick: (String) -> Unit) {
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(Z.S3)) {
        LazyRow(horizontalArrangement = Arrangement.spacedBy(Z.S3)) {
            items(Styles.all, key = { it.id }) { s ->
                StyleCard(s, ui.styleId == s.id, ui.fonts.firstOrNull { it.name == s.font }?.typeface) { onPick(s.id) }
            }
        }
        Row(
            Modifier.fillMaxWidth().clip(RoundedCornerShape(Z.RSm)).background(Z.Surface2).padding(Z.S3),
            verticalAlignment = Alignment.Top
        ) {
            ZIcon(ZI.Info, tint = Z.TextSec, size = Z.IconSm, modifier = Modifier.padding(top = 2.dp))
            Spacer(Modifier.width(Z.S2))
            Txt(
                "اگه اول یک کلمه در متن زیرنویس * بذاری، اون کلمه با رنگ تاکیدی نشون داده می‌شه.",
                style = ZType.caption, color = Z.TextSec
            )
        }
    }
}

/** A real typography preset: the card is rendered by the same CaptionRenderer that exports the video. */
@Composable
private fun StyleCard(s: SubStyle, selected: Boolean, tf: Typeface?, onClick: () -> Unit) {
    val bmp = remember(s.id, tf) {
        try {
            val ph = Phrase(listOf(Word("سلام", 0L, 400L), Word("دنیا", 400L, 800L)))
            CaptionRenderer(240, 150, s, tf ?: Typeface.DEFAULT_BOLD, CapLayout(x = 0.5f, y = 0.5f, mw = 0.92f, ls = 1.5f, lw = 4, size = 2.3f))
                .render(ph, 1)
        } catch (e: Throwable) {
            null
        }
    }
    val shape = RoundedCornerShape(Z.RSm)
    val line by animateColorAsState(if (selected) Z.Accent else Z.Border, tween(160), label = "styleLine")
    Column(Modifier.width(112.dp).press(true, onClick), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Box(
            Modifier.fillMaxWidth().height(74.dp).clip(shape)
                .background(Brush.verticalGradient(listOf(Color(0xFF474E5C), Color(0xFF242932))))
                .border(if (selected) 1.5.dp else Z.Hair, line, shape),
            contentAlignment = Alignment.Center
        ) {
            val b = bmp
            if (b != null) {
                Image(
                    bitmap = b.asImageBitmap(),
                    contentDescription = null,
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp),
                    contentScale = ContentScale.Fit
                )
            }
            if (selected) {
                Box(
                    Modifier.align(Alignment.TopEnd).padding(5.dp).size(16.dp).clip(CircleShape).background(Z.Accent),
                    contentAlignment = Alignment.Center
                ) { ZIcon(ZI.Check, tint = Z.OnAccent, size = 12.dp) }
            }
        }
        Column {
            Txt(s.nameFa, style = ZType.labelStrong, color = if (selected) Z.Text else Z.TextSec, maxLines = 1)
            Txt(s.font.substringBefore('-'), style = ZType.micro, color = Z.TextTer, maxLines = 1)
        }
    }
}

@Composable
private fun FontPanel(fonts: List<FontStore.Font>, selectedId: String, onPick: (String) -> Unit, onAdd: () -> Unit) {
    Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(Z.S2)) {
        LazyColumn(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            items(fonts, key = { it.id }) { f ->
                val sel = f.id == selectedId
                val shape = RoundedCornerShape(Z.RSm)
                val line by animateColorAsState(if (sel) Z.AccentLine else Z.Border, tween(140), label = "fontLine")
                Row(
                    Modifier.fillMaxWidth().height(48.dp).clip(shape)
                        .background(if (sel) Z.AccentSoft else Z.Surface2)
                        .border(Z.Hair, line, shape)
                        .press(true) { onPick(f.id) }.padding(horizontal = Z.S3),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    ZRadioDot(sel)
                    Spacer(Modifier.width(Z.S3))
                    Column(Modifier.weight(1f)) {
                        Txt(app.zircut.i18n.Lang.raw(f.name), style = ZType.label, color = if (sel) Z.Text else Z.TextSec, maxLines = 1)
                        if (!f.persianOk) Txt("بدون حروف فارسی", style = ZType.micro, color = Z.Warn, maxLines = 1)
                    }
                    Text(
                        if (f.persianOk) "سلام دنیا" else "Aa Bb",
                        style = TextStyle(fontFamily = FontFamily(f.typeface), fontSize = 19.sp),
                        color = Z.Text,
                        maxLines = 1
                    )
                }
            }
        }
        ZButton("افزودن فونت (.ttf / .otf)", onAdd, Modifier.fillMaxWidth(), kind = ZBtn.Secondary, icon = ZI.Plus, compact = true)
    }
}

@Composable
private fun TwoCols(a: @Composable () -> Unit, b: @Composable () -> Unit) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(Z.S5)) {
        Box(Modifier.weight(1f)) { a() }
        Box(Modifier.weight(1f)) { b() }
    }
}

@Composable
private fun PositionPanel(layout: CapLayout, onLayout: (CapLayout) -> Unit) {
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(Z.S2)) {
        val presets = listOf(
            "بالا چپ" to (0.18f to 0.18f), "بالا وسط" to (0.5f to 0.18f), "بالا راست" to (0.82f to 0.18f),
            "وسط چپ" to (0.18f to 0.5f), "وسط" to (0.5f to 0.5f), "وسط راست" to (0.82f to 0.5f),
            "پایین چپ" to (0.18f to 0.82f), "پایین وسط" to (0.5f to 0.82f), "پایین راست" to (0.82f to 0.82f)
        )
        androidx.compose.foundation.layout.FlowRow(
            horizontalArrangement = Arrangement.spacedBy(Z.S2),
            verticalArrangement = Arrangement.spacedBy(Z.S2)
        ) {
            presets.forEach { (n, p) ->
                ZChip(
                    n,
                    kotlin.math.abs(layout.x - p.first) < 0.02f && kotlin.math.abs(layout.y - p.second) < 0.02f,
                    onClick = { onLayout(layout.copy(x = p.first, y = p.second)) }
                )
            }
        }
        TwoCols(
            { ZSlider("اندازه", layout.size, 0.6f, 1.5f, { onLayout(layout.copy(size = it)) }, readout = ::pct) },
            { ZSlider("عرض متن", layout.mw, 0.4f, 0.96f, { onLayout(layout.copy(mw = it)) }, readout = ::pct) }
        )
        TwoCols(
            { ZSlider("جای عمودی", layout.y, 0.08f, 0.94f, { onLayout(layout.copy(y = it)) }, readout = ::pct) },
            { ZSlider("جای افقی", layout.x, 0.1f, 0.9f, { onLayout(layout.copy(x = it)) }, readout = ::pct) }
        )
        TwoCols(
            { ZSlider("فاصله خطوط", layout.ls, 1.1f, 2f, { onLayout(layout.copy(ls = it)) }, readout = { fa(Math.round(it * 10f)).let { s -> s.dropLast(1) + "٫" + s.last() } }) },
            { ZSlider("کلمه در هر خط", layout.lw.toFloat(), 1f, 6f, { onLayout(layout.copy(lw = it.toInt())) }, steps = 4) }
        )
        Txt("انیمیشن زیرنویس", style = ZType.label, color = Z.TextSec)
        androidx.compose.foundation.layout.FlowRow(
            horizontalArrangement = Arrangement.spacedBy(Z.S2),
            verticalArrangement = Arrangement.spacedBy(Z.S2)
        ) {
            CapAnim.names.forEach { (m, label) ->
                ZChip(label, layout.anim == m, onClick = { onLayout(layout.copy(anim = m)) })
            }
        }
    }
}


/** A layer that is visible at the playhead, with its bitmap ready to draw. */
private val AnimInNames: List<Pair<Int, String>> =
    listOf(0 to "بدون", 1 to "محو", 2 to "از پایین", 3 to "از بالا", 4 to "از چپ", 5 to "از راست", 6 to "زوم")

private val AnimOutNames: List<Pair<Int, String>> =
    listOf(0 to "بدون", 1 to "محو", 2 to "به پایین", 3 to "به بالا", 4 to "به چپ", 5 to "به راست", 6 to "زوم")

private class DrawnLayer(val l: Layer, val bmp: Bitmap, val bs: Float)

private val TextColors: List<Int> = listOf(
    0xFFFFFFFF, 0xFF000000, 0xFFFFD83A, 0xFFFF4D4D, 0xFFFF9F43, 0xFF3DDC84, 0xFF3DD6F5, 0xFF4D8DFF, 0xFFA66BFF, 0xFFFF6BB5
).map { it.toInt() }

private val BoxColors: List<Int> = listOf(
    0xCC000000, 0xFFFFFFFF, 0xFFFFD83A, 0xFFFF4D4D, 0xFF4D8DFF, 0xFF3DDC84
).map { it.toInt() }

private fun secs(v: Float): String = fa(String.format(java.util.Locale.US, "%.1f", v)).replace('.', '٫') + " ث"

private fun layerLabel(l: Layer, layers: List<Layer>): String {
    val n = layers.indexOfFirst { it.id == l.id } + 1
    return if (l.isText) l.text.trim().replace('\n', ' ').take(9).ifEmpty { "متن " + fa(n) } else "عکس " + fa(n)
}

@Composable
private fun ColorRow(colors: List<Int>, selected: Int, allowNone: Boolean, onPick: (Int) -> Unit) {
    LazyRow(horizontalArrangement = Arrangement.spacedBy(Z.S2), verticalAlignment = Alignment.CenterVertically) {
        if (allowNone) {
            item {
                Box(
                    Modifier.size(32.dp).clip(CircleShape).background(Z.Surface2)
                        .border(if (selected == 0) 2.dp else 1.dp, if (selected == 0) Z.Accent else Z.BorderStrong, CircleShape)
                        .press(true) { onPick(0) },
                    contentAlignment = Alignment.Center
                ) { ZIcon(ZI.Close, tint = Z.TextSec, size = 14.dp) }
            }
        }
        items(colors) { c ->
            Box(
                Modifier.size(32.dp).clip(CircleShape).background(Color(c))
                    .border(if (selected == c) 2.dp else 1.dp, if (selected == c) Z.Accent else Z.BorderStrong, CircleShape)
                    .press(true) { onPick(c) }
            )
        }
    }
}

/**
 * Text and picture layers: add, select, move/resize/turn (sliders here, or by finger on the preview),
 * opacity, time on the timeline, order, split, copy, delete. Everything is rendered in the preview and in the export.
 */
@Composable
private fun LayerPanel(
    layers: List<Layer>,
    selectedId: Int,
    posMs: Long,
    total: Long,
    fonts: List<FontStore.Font>,
    projectFontId: String,
    onSelect: (Int) -> Unit,
    customStyles: List<NamedStyle>,
    onSaveStyle: (Int) -> Unit,
    onDeleteStyle: (String) -> Unit,
    onAddText: () -> Unit,
    onAddImage: () -> Unit,
    onEdit: (Int, String, (Layer) -> Layer) -> Unit,
    onDelete: (Int) -> Unit,
    onDuplicate: (Int) -> Unit,
    onSplit: (Int) -> Unit,
    onMove: (Int, Int) -> Unit,
    onGoto: (Long) -> Unit
) {
    val sel = layers.firstOrNull { it.id == selectedId }
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(Z.S3)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(Z.S2)) {
            ZButton("متن جدید", onAddText, Modifier.weight(1f), icon = ZI.Type, compact = true)
            ZButton("عکس جدید", onAddImage, Modifier.weight(1f), kind = ZBtn.Secondary, icon = ZI.Plus, compact = true)
        }
        if (layers.isNotEmpty()) {
            LazyRow(horizontalArrangement = Arrangement.spacedBy(Z.S2)) {
                itemsIndexed(layers) { _, l -> ZChip(layerLabel(l, layers), l.id == selectedId, { onSelect(l.id) }) }
            }
        }
        if (sel == null) {
            Txt(
                if (layers.isEmpty()) "متن یا عکس اضافه کن؛ روی پیش‌نمایش با انگشت جابه‌جا و با دو انگشت بزرگ و بچرخانش."
                else "یک لایه را از بالا یا از روی تایم‌لاین (زیر زیرنویس‌ها) انتخاب کن.",
                style = ZType.caption, color = Z.TextSec
            )
            return@Column
        }
        // with keyframes the sliders show the animated look at the playhead
        val l: Layer = LayerKeys.withPose(sel, posMs)
        val onNow = l.visibleAt(posMs)
        Txt(
            (if (l.isText) "متن" else "عکس") + "  ·  " + faTime(l.startMs) + " تا " + faTime(l.endMs) +
                (if (onNow) "" else "  ·  در این لحظه دیده نمی‌شود"),
            style = ZType.labelStrong, color = if (onNow) Z.TextSec else Z.Warn
        )
        ZToggleRow(ZI.Layers, "قفل لایه", "جلوگیری از جابه‌جایی و ویرایش ناخواسته", l.locked) { v -> onEdit(l.id, "lock") { it.copy(locked = v) } }
        ZToggleRow(ZI.Filters, "مخفی‌کردن لایه", "روی تایم‌لاین می‌ماند، ولی در پیش‌نمایش و خروجی دیده نمی‌شود", l.hidden) { v -> onEdit(l.id, "hide") { it.copy(hidden = v) } }
        if (l.locked) {
            Txt("این لایه قفل است. برای ویرایش، اول قفل را بردار.", style = ZType.caption, color = Z.Warn)
            ClipTool(ZI.Position, "برو به لایه", !onNow, Z.Text, Modifier.fillMaxWidth()) { onGoto(l.startMs) }
            return@Column
        }
        if (l.isText) {
            Txt("استایل‌های آماده", style = ZType.label, color = Z.TextSec)
            LazyRow(horizontalArrangement = Arrangement.spacedBy(Z.S2)) {
                items(TextStyles.presets) { p ->
                    ZChip(p.name, TextStyles.matches(l, p.style), { onEdit(l.id, "style") { TextStyles.apply(it, p.style) } })
                }
            }
            if (customStyles.isNotEmpty()) {
                Txt("استایل‌های من", style = ZType.label, color = Z.TextSec)
                LazyRow(horizontalArrangement = Arrangement.spacedBy(Z.S2)) {
                    items(customStyles) { c ->
                        ZChip(c.name, TextStyles.matches(l, c.style), { onEdit(l.id, "style") { TextStyles.apply(it, c.style) } })
                    }
                }
            }
            val matchedCustom = customStyles.firstOrNull { TextStyles.matches(l, it.style) }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(Z.S2)) {
                ZButton("ذخیره‌ی استایل این متن", { onSaveStyle(l.id) }, Modifier.weight(1f), kind = ZBtn.Secondary, compact = true)
                if (matchedCustom != null) {
                    ZButton("حذف «" + matchedCustom.name + "»", { onDeleteStyle(matchedCustom.name) }, Modifier.weight(1f), kind = ZBtn.Secondary, compact = true)
                }
            }
            ZField(l.text, { t -> onEdit(l.id, "text") { it.copy(text = t) } }, Modifier.fillMaxWidth(), label = "متن")
            Txt("رنگ متن", style = ZType.label, color = Z.TextSec)
            ColorRow(TextColors, l.color, false) { c -> onEdit(l.id, "color") { it.copy(color = c) } }
            Txt("پس‌زمینه", style = ZType.label, color = Z.TextSec)
            ColorRow(BoxColors, l.bgColor, true) { c -> onEdit(l.id, "bg") { it.copy(bgColor = c) } }
            ZToggleRow(ZI.Edit, "حاشیه‌ی تیره دور حروف", null, l.stroke) { v -> onEdit(l.id, "stroke") { it.copy(stroke = v) } }
            if (l.stroke) {
                Txt("رنگ حاشیه", style = ZType.label, color = Z.TextSec)
                ColorRow(TextColors, l.outlineColor, false) { c -> onEdit(l.id, "outline") { it.copy(outlineColor = c) } }
            }
            Txt("سایه", style = ZType.label, color = Z.TextSec)
            androidx.compose.foundation.layout.FlowRow(horizontalArrangement = Arrangement.spacedBy(Z.S2), verticalArrangement = Arrangement.spacedBy(Z.S2)) {
                listOf(0 to "خودکار", 1 to "بدون", 2 to "ملایم", 3 to "قوی", 4 to "درخشش").forEach { (m, label) ->
                    ZChip(label, l.shadowMode == m, { onEdit(l.id, "shadow") { it.copy(shadowMode = m) } })
                }
            }
            ZToggleRow(ZI.Type, "پررنگ (Bold)", null, l.bold) { v -> onEdit(l.id, "bold") { it.copy(bold = v) } }
            ZToggleRow(ZI.Type, "کج (Italic)", null, l.italic) { v -> onEdit(l.id, "italic") { it.copy(italic = v) } }
            Txt("تراز متن", style = ZType.label, color = Z.TextSec)
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(Z.S2)) {
                listOf(1 to "چپ", 0 to "وسط", 2 to "راست").forEach { (v, label) ->
                    ZChip(label, l.align == v, { onEdit(l.id, "align") { it.copy(align = v) } })
                }
            }
            TwoCols(
                { ZSlider("فاصله خطوط", l.lineSpacing, 0.8f, 2.5f, { v -> onEdit(l.id, "lineSp") { it.copy(lineSpacing = v) } }, readout = ::pct) },
                { ZSlider("فاصله حروف", l.letterSpacing, 0f, 0.4f, { v -> onEdit(l.id, "letterSp") { it.copy(letterSpacing = v) } }, readout = ::pct) }
            )
            Txt("فاصله حروف فقط برای حروف لاتین اثر دارد؛ در فارسی حروف را از هم جدا می‌کرد، پس نادیده گرفته می‌شود.", style = ZType.caption, color = Z.TextSec)
            if (fonts.isNotEmpty()) {
                Txt("فونت", style = ZType.label, color = Z.TextSec)
                val eff = if (l.fontId.isEmpty()) projectFontId else l.fontId
                LazyRow(horizontalArrangement = Arrangement.spacedBy(Z.S2)) {
                    items(fonts) { f -> ZChip(f.name, eff == f.id, { onEdit(l.id, "font") { it.copy(fontId = f.id) } }) }
                }
            }
        } else {
            Txt("حذف پس‌زمینه‌ی رنگی (کروما کی)", style = ZType.label, color = Z.TextSec)
            androidx.compose.foundation.layout.FlowRow(horizontalArrangement = Arrangement.spacedBy(Z.S2), verticalArrangement = Arrangement.spacedBy(Z.S2)) {
                listOf(0 to "خاموش", 1 to "سبز", 2 to "آبی", 3 to "سفید", 4 to "سیاه").forEach { (m, label) ->
                    ZChip(label, l.keyMode == m, { onEdit(l.id, "key-mode") { it.copy(keyMode = m) } })
                }
            }
            if (l.keyMode != 0) {
                ZSlider("حساسیت", l.keyTol, 0f, 1f, { v -> onEdit(l.id, "key-tol") { it.copy(keyTol = v) } }, readout = ::pct)
            }
            Txt("روی عکس‌هایی که پس‌زمینه‌ی یک‌رنگ دارند (پرده‌ی سبز، پس‌زمینه‌ی سفید) کار می‌کنه. حساسیت رو تا جایی بالا ببر که پس‌زمینه برود و لبه‌ی سوژه سالم بماند. این ابزار فقط روی لایه‌ی عکس است، نه روی خود ویدیو.", style = ZType.caption, color = Z.TextSec)
        }
        TwoCols(
            { ZSlider("اندازه", l.scale, 0.2f, 4f, { v -> onEdit(l.id, "scale") { it.copy(scale = v) } }, readout = ::pct) },
            { ZSlider("چرخش", l.rotation, -180f, 180f, { v -> onEdit(l.id, "rot") { it.copy(rotation = v) } }, readout = { v -> signed(Math.round(v)) + "°" }) }
        )
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(Z.S2)) {
            listOf(0.5f to "۵۰٪", 1f to "۱۰۰٪", 1.5f to "۱۵۰٪", 2f to "۲۰۰٪").forEach { (v, label) ->
                ZChip(label, kotlin.math.abs(l.scale - v) < 0.01f, { onEdit(l.id, "scale-preset") { it.copy(scale = v) } })
            }
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(Z.S2)) {
            ZButton("چرخش ۹۰°", { onEdit(l.id, "rotate90") { it.copy(rotation = LayerOps.normDeg(it.rotation + 90f)) } }, Modifier.weight(1f), kind = ZBtn.Secondary, compact = true)
            ZButton("بازنشانی لایه", { onEdit(l.id, "reset") { it.copy(x = 0.5f, y = 0.5f, scale = 1f, rotation = 0f, opacity = 1f, keys = emptyList()) } }, Modifier.weight(1f), kind = ZBtn.Secondary, compact = true)
        }
        TwoCols(
            { ZSlider("شفافیت", l.opacity, 0.05f, 1f, { v -> onEdit(l.id, "opacity") { it.copy(opacity = v) } }, readout = ::pct) },
            { ZSlider("جای افقی", l.x, 0f, 1f, { v -> onEdit(l.id, "x") { it.copy(x = v) } }, readout = ::pct) }
        )
        TwoCols(
            { ZSlider("جای عمودی", l.y, 0f, 1f, { v -> onEdit(l.id, "y") { it.copy(y = v) } }, readout = ::pct) },
            {
                ZSlider(
                    "زمان شروع", l.startMs / 1000f, 0f, maxOf(0.1f, (total - l.len) / 1000f),
                    { v -> onEdit(l.id, "start") { val st = (v * 1000f).toLong(); it.copy(startMs = st, endMs = st + it.len) } },
                    readout = ::secs
                )
            }
        )
        ZSlider(
            "مدت نمایش", l.len / 1000f, 0.2f, maxOf(0.5f, (total - l.startMs) / 1000f),
            { v -> onEdit(l.id, "len") { it.copy(endMs = it.startMs + (v * 1000f).toLong()) } },
            readout = ::secs
        )
        // ---- keyframes: position, size and opacity over time ----
        Txt("کلیدفریم (موقعیت، اندازه، چرخش، شفافیت)", style = ZType.label, color = Z.TextSec)
        val rel = posMs - sel.startMs
        val prevKey = sel.keys.lastOrNull { it.tMs < rel - LayerKeys.NEAR_MS }
        val nextKey = sel.keys.firstOrNull { it.tMs > rel + LayerKeys.NEAR_MS }
        val keyHere = LayerKeys.nearest(sel, posMs)
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(Z.S2)) {
            ClipTool(ZI.SkipPrev, "کلید قبلی", prevKey != null, Z.Text, Modifier.weight(1f)) { if (prevKey != null) onGoto(sel.startMs + prevKey.tMs) }
            ClipTool(
                ZI.Plus, if (keyHere != null) "به‌روزرسانی" else "افزودن", onNow && (keyHere != null || sel.keys.size < LayerKeys.MAX_KEYS), Z.Accent, Modifier.weight(1f)
            ) { onEdit(l.id, "kf-add") { c -> LayerKeys.upsert(c, posMs, LayerKeys.pose(c, posMs)) } }
            ClipTool(ZI.Trash, "حذف کلید", keyHere != null, Z.Danger, Modifier.weight(1f)) { onEdit(l.id, "kf-del") { c -> LayerKeys.remove(c, posMs) } }
            ClipTool(ZI.SkipNext, "کلید بعدی", nextKey != null, Z.Text, Modifier.weight(1f)) { if (nextKey != null) onGoto(sel.startMs + nextKey.tMs) }
        }
        if (keyHere != null) {
            // how the layer travels from the key here to the next one, and the key's own time
            Txt("حرکت از این کلید تا کلید بعدی", style = ZType.label, color = Z.TextSec)
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(Z.S2)) {
                listOf(LayerKeys.EASE_SMOOTH to "نرم", LayerKeys.EASE_LINEAR to "خطی", LayerKeys.EASE_HOLD to "ثابت (پله‌ای)").forEach { (v, label) ->
                    ZChip(label, keyHere.ease == v, { onEdit(l.id, "kf-ease") { c -> LayerKeys.setEase(c, posMs, v) } })
                }
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(Z.S2)) {
                ZButton("کلید ۱۰۰ms زودتر", { if (LayerKeys.nudge(sel, posMs, -100L) !== sel) { onEdit(l.id, "kf-nudge") { c -> LayerKeys.nudge(c, posMs, -100L) }; onGoto((sel.startMs + keyHere.tMs - 100L).coerceAtLeast(sel.startMs)) } }, Modifier.weight(1f), kind = ZBtn.Secondary, compact = true)
                ZButton("کلید ۱۰۰ms دیرتر", { if (LayerKeys.nudge(sel, posMs, 100L) !== sel) { onEdit(l.id, "kf-nudge") { c -> LayerKeys.nudge(c, posMs, 100L) }; onGoto((sel.startMs + keyHere.tMs + 100L).coerceAtMost(sel.endMs)) } }, Modifier.weight(1f), kind = ZBtn.Secondary, compact = true)
            }
        }
        if (sel.keys.isNotEmpty()) {
            ZButton("پاک‌کردن همه‌ی کلیدفریم‌ها (" + fa(sel.keys.size) + ")", { onEdit(l.id, "kf-clear") { c -> c.copy(keys = emptyList()) } }, Modifier.fillMaxWidth(), kind = ZBtn.Secondary, compact = true)
        }
        Txt(
            "پلی‌هد را روی یک لحظه بگذار و «افزودن» بزن؛ به لحظه‌ی دیگر برو و لایه را جابه‌جا یا کوچک و بزرگ کن (یا شفافیت را عوض کن) تا خودکار کلیدفریم ساخته شود. نوع حرکت بین کلیدها (نرم، خطی یا پله‌ای) را از همان کلید انتخاب می‌کنی. چرخش هم کلیدفریم می‌شود؛ روی کلیدهای دور از هم، چرخش از روی عددهای اسلایدر درون‌یابی می‌شود.",
            style = ZType.caption, color = Z.TextSec
        )
        Txt("انیمیشن ورود", style = ZType.label, color = Z.TextSec)
        LazyRow(horizontalArrangement = Arrangement.spacedBy(Z.S2)) {
            items(AnimInNames) { (k, name) -> ZChip(name, l.animIn == k, { onEdit(l.id, "animIn") { it.copy(animIn = k) } }) }
        }
        Txt("انیمیشن خروج", style = ZType.label, color = Z.TextSec)
        LazyRow(horizontalArrangement = Arrangement.spacedBy(Z.S2)) {
            items(AnimOutNames) { (k, name) -> ZChip(name, l.animOut == k, { onEdit(l.id, "animOut") { it.copy(animOut = k) } }) }
        }
        if (l.animIn != 0 || l.animOut != 0) {
            ZSlider(
                "مدت انیمیشن", l.animMs / 1000f, 0.1f, 1.5f,
                { v -> onEdit(l.id, "animMs") { it.copy(animMs = (v * 1000f).toLong().coerceIn(100L, 1500L)) } },
                readout = ::secs
            )
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(Z.S2)) {
            ClipTool(ZI.SkipPrev, "شروع اینجا", posMs < l.endMs - Layer.MIN_MS, Z.Text, Modifier.weight(1f)) {
                onEdit(l.id, "cutStart") { it.copy(startMs = posMs.coerceAtLeast(0L)) }
            }
            ClipTool(ZI.SkipNext, "پایان اینجا", posMs > l.startMs + Layer.MIN_MS, Z.Text, Modifier.weight(1f)) {
                onEdit(l.id, "cutEnd") { it.copy(endMs = posMs) }
            }
            ClipTool(ZI.Scissors, "تقسیم", posMs - l.startMs >= Layer.MIN_MS && l.endMs - posMs >= Layer.MIN_MS, Z.Text, Modifier.weight(1f)) {
                onSplit(l.id)
            }
            ClipTool(ZI.Copy, "کپی", true, Z.Text, Modifier.weight(1f)) { onDuplicate(l.id) }
        }
        val idx = layers.indexOfFirst { it.id == l.id }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(Z.S2)) {
            ClipTool(ZI.Arrow, "بالاتر", idx < layers.size - 1, Z.Text, Modifier.weight(1f), rotation = 270f) { onMove(l.id, 1) }
            ClipTool(ZI.Arrow, "پایین‌تر", idx > 0, Z.Text, Modifier.weight(1f), rotation = 90f) { onMove(l.id, -1) }
            ClipTool(ZI.Position, "برو به لایه", !onNow, Z.Text, Modifier.weight(1f)) { onGoto(l.startMs) }
            ClipTool(ZI.Trash, "حذف", true, Z.Danger, Modifier.weight(1f)) { onDelete(l.id) }
        }
        Txt(
            "برای جابه‌جایی سریع، روی لایه‌ی تایم‌لاین چند لحظه نگه دار و افقی بکش. «بالاتر» لایه را روی لایه‌های دیگر می‌آورد؛ زیرنویس همیشه روی لایه‌هاست.",
            style = ZType.caption, color = Z.TextSec
        )
    }
}

/**
 * Sound: volume/mute of the video's own sound, music or sound files, voice-over recording, and for each clip
 * volume, fade in/out, position, which part of the file plays, split, copy, delete.
 */
@Composable
private fun AudioPanel(
    clips: List<AudioClip>,
    selectedId: Int,
    posMs: Long,
    mainVolume: Float,
    mainMuted: Boolean,
    duck: Boolean,
    onDuck: (Boolean) -> Unit,
    recording: Boolean,
    recLevel: Float,
    recElapsed: Long,
    onSelect: (Int) -> Unit,
    onAddFile: () -> Unit,
    onRecord: () -> Unit,
    onStopRecord: () -> Unit,
    onMainVolume: (Float) -> Unit,
    onMainMuted: (Boolean) -> Unit,
    onEdit: (Int, String, (AudioClip) -> AudioClip) -> Unit,
    onDelete: (Int) -> Unit,
    onDuplicate: (Int) -> Unit,
    onSplit: (Int) -> Unit,
    onClean: (Int, Float) -> Unit,
    onEq: (Int, app.zircut.core.EqBands) -> Unit,
    onExtract: () -> Unit,
    onSilence: (Int, Int) -> Unit,
    onGoto: (Long) -> Unit
) {
    val sel = clips.firstOrNull { it.id == selectedId }
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(Z.S3)) {
        ZToggleRow(ZI.Wave, "صدای اصلی ویدیو", if (mainMuted) "خاموش" else "روشن", !mainMuted) { on -> onMainMuted(!on) }
        ZSlider("بلندی صدای اصلی", mainVolume, 0f, 1f, onMainVolume, readout = ::pct)
        ZToggleRow(ZI.Wave, "کم‌کردن خودکار موسیقی هنگام صحبت", "زیر ویس (Voice Over) و زیر صحبت خود ویدیو (طبق زیرنویس‌ها)", duck) { on -> onDuck(on) }
        if (recording) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Z.S3)) {
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Txt("در حال ضبط…  " + faTime(recElapsed), style = ZType.labelStrong, color = Z.Danger)
                    Box(Modifier.fillMaxWidth().height(6.dp).clip(RoundedCornerShape(3.dp)).background(Z.Surface3)) {
                        Box(Modifier.fillMaxWidth(recLevel.coerceIn(0.02f, 1f)).fillMaxHeight().background(Z.Danger))
                    }
                }
                ZButton("توقف", onStopRecord, kind = ZBtn.Danger, icon = ZI.Pause, compact = true)
            }
            Txt(
                "ویدیو هنگام ضبط پخش می‌شود. برای اینکه صدای ویدیو داخل ضبط نیفتد از هدفون استفاده کن. با توقف پخش یا رسیدن به انتهای تایم‌لاین هم ضبط تمام می‌شود.",
                style = ZType.caption, color = Z.TextSec
            )
        } else {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(Z.S2)) {
                ZButton("موسیقی یا فایل صوتی", onAddFile, Modifier.weight(1f), icon = ZI.Music, compact = true)
                ZButton("ضبط صدا", onRecord, Modifier.weight(1f), kind = ZBtn.Secondary, icon = ZI.Mic, compact = true)
            }
            ZButton("استخراج صدای ویدیو", onExtract, Modifier.fillMaxWidth(), kind = ZBtn.Secondary, icon = ZI.Wave, compact = true)
            Txt(
                "صدای ویدیو (با برش‌ها و سرعت‌های فعلی) به یک کلیپ صوتی تبدیل می‌شه و صدای اصلی خاموش می‌شه. بعدش می‌تونی حذف نویز، اکولایزر، بلندی و محو شدن رو روی اون اعمال کنی.",
                style = ZType.caption, color = Z.TextSec
            )
        }
        if (clips.isNotEmpty()) {
            LazyRow(horizontalArrangement = Arrangement.spacedBy(Z.S2)) {
                items(clips) { c ->
                    ZChip(c.name.take(14), c.id == selectedId, { onSelect(c.id) }, trailing = if (c.isVoice) "ضبط" else "موسیقی")
                }
            }
        }
        if (sel == null) {
            Txt(
                if (clips.isEmpty()) "موسیقی یا صدا را از فایل اضافه کن، یا با «ضبط صدا» گویندگی بساز. هر صدا روی تایم‌لاین (زیر لایه‌ها) با شکل‌موج واقعی دیده می‌شود."
                else "یک صدا را از بالا یا از روی تایم‌لاین انتخاب کن.",
                style = ZType.caption, color = Z.TextSec
            )
            return@Column
        }
        val c: AudioClip = sel
        val onNow = c.activeAt(posMs)
        Txt(
            (if (c.isVoice) "ضبط صدا" else "موسیقی / صدا") + "  ·  " + faTime(c.startMs) + " تا " + faTime(c.endMs),
            style = ZType.labelStrong, color = Z.TextSec
        )
        ZToggleRow(ZI.Wave, "پخش این صدا", if (c.muted) "بی‌صدا" else null, !c.muted) { on -> onEdit(c.id, "mute") { it.copy(muted = !on) } }
        ZSlider("بلندی صدا", c.volume, 0f, 1f, { v -> onEdit(c.id, "vol") { it.copy(volume = v) } }, readout = ::pct)
        TwoCols(
            { ZSlider("محو شدن در شروع", c.fadeInMs / 1000f, 0f, maxOf(0.5f, c.lenMs / 1000f), { v -> onEdit(c.id, "fi") { it.copy(fadeInMs = (v * 1000f).toLong()) } }, readout = ::secs) },
            { ZSlider("محو شدن در پایان", c.fadeOutMs / 1000f, 0f, maxOf(0.5f, c.lenMs / 1000f), { v -> onEdit(c.id, "fo") { it.copy(fadeOutMs = (v * 1000f).toLong()) } }, readout = ::secs) }
        )
        Txt("حالت‌های محوشدن", style = ZType.label, color = Z.TextSec)
        androidx.compose.foundation.layout.FlowRow(
            horizontalArrangement = Arrangement.spacedBy(Z.S2),
            verticalArrangement = Arrangement.spacedBy(Z.S2)
        ) {
            val fade = minOf(500L, c.lenMs / 2)
            listOf(
                Triple("بدون محو", 0L, 0L),
                Triple("محو شروع", fade, 0L),
                Triple("محو پایان", 0L, fade),
                Triple("شروع و پایان", fade, fade)
            ).forEach { (label, fi, fo) ->
                ZChip(
                    label,
                    c.fadeInMs == fi && c.fadeOutMs == fo,
                    onClick = { onEdit(c.id, "fade-preset") { it.copy(fadeInMs = fi, fadeOutMs = fo) } }
                )
            }
        }
        ZSlider(
            "زمان شروع روی تایم‌لاین", c.startMs / 1000f, 0f, maxOf(0.5f, (c.startMs + 60_000L) / 1000f),
            { v -> onEdit(c.id, "start") { it.copy(startMs = (v * 1000f).toLong()) } }, readout = ::secs
        )
        TwoCols(
            { ZSlider("شروع از ثانیه‌ی…", c.inMs / 1000f, 0f, maxOf(0.5f, (c.fileMs - AudioClip.MIN_MS) / 1000f), { v -> onEdit(c.id, "in") { it.copy(inMs = (v * 1000f).toLong()) } }, readout = ::secs) },
            { ZSlider("مدت پخش", c.lenMs / 1000f, 0.2f, maxOf(0.5f, (c.fileMs - c.inMs) / 1000f), { v -> onEdit(c.id, "len") { it.copy(lenMs = (v * 1000f).toLong()) } }, readout = ::secs) }
        )
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(Z.S2)) {
            ClipTool(ZI.SkipPrev, "شروع اینجا", true, Z.Text, Modifier.weight(1f)) {
                onEdit(c.id, "here") { it.copy(startMs = posMs.coerceAtLeast(0L)) }
            }
            ClipTool(ZI.SkipNext, "پایان اینجا", posMs > c.startMs + AudioClip.MIN_MS, Z.Text, Modifier.weight(1f)) {
                onEdit(c.id, "end-here") { it.copy(lenMs = (posMs - it.startMs).coerceAtLeast(AudioClip.MIN_MS)) }
            }
            ClipTool(ZI.Scissors, "تقسیم", posMs - c.startMs >= AudioClip.MIN_MS && c.endMs - posMs >= AudioClip.MIN_MS, Z.Text, Modifier.weight(1f)) {
                onSplit(c.id)
            }
            ClipTool(ZI.Copy, "کپی", true, Z.Text, Modifier.weight(1f)) { onDuplicate(c.id) }
            ClipTool(ZI.Trash, "حذف", true, Z.Danger, Modifier.weight(1f)) { onDelete(c.id) }
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(Z.S2)) {
            ClipTool(ZI.Position, "برو به صدا", !onNow, Z.Text, Modifier.weight(1f)) { onGoto(c.startMs) }
            Spacer(Modifier.weight(3f))
        }
        Txt("حذف نویز (مخصوص گفتار)", style = ZType.labelStrong, color = Z.TextSec)
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(Z.S2)) {
            ZButton("ملایم", { onClean(c.id, 0.35f) }, Modifier.weight(1f), kind = ZBtn.Secondary, compact = true)
            ZButton("متوسط", { onClean(c.id, 0.65f) }, Modifier.weight(1f), kind = ZBtn.Secondary, compact = true)
            ZButton("قوی", { onClean(c.id, 1f) }, Modifier.weight(1f), kind = ZBtn.Secondary, compact = true)
        }
        Txt(
            "نویز پس‌زمینه از روی بخش‌های ساکت خود فایل اندازه‌گیری و کم می‌شود و صدای رامبل پایین حذف می‌شود. نتیجه یک فایل تازه است و با بازگشت (Undo) به صدای قبلی برمی‌گردی. برای موسیقی استفاده نکن.",
            style = ZType.caption, color = Z.TextSec
        )
        Txt("حذف سکوت", style = ZType.labelStrong, color = Z.TextSec)
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(Z.S2)) {
            ZButton("فقط سکوت کامل", { onSilence(c.id, 0) }, Modifier.weight(1f), kind = ZBtn.Secondary, compact = true)
            ZButton("متوسط", { onSilence(c.id, 1) }, Modifier.weight(1f), kind = ZBtn.Secondary, compact = true)
            ZButton("شدید", { onSilence(c.id, 2) }, Modifier.weight(1f), kind = ZBtn.Secondary, compact = true)
        }
        Txt("مکث‌های بیشتر از نیم‌ثانیه حذف می‌شوند و بخش‌ها به هم می‌چسبند. کلیپ به چند کلیپ پشت‌سرهم تقسیم می‌شود و کلیپ‌های دیگر جابه‌جا نمی‌شوند. برای صدایی که با تصویر هماهنگ است (مثل صدای استخراج‌شده) استفاده نکن، چون هماهنگی با تصویر به هم می‌خورد.", style = ZType.caption, color = Z.TextSec)
        Txt("اکولایزر ۵ باند", style = ZType.labelStrong, color = Z.TextSec)
        var eq by remember(c.id) { mutableStateOf(app.zircut.core.EqBands()) }
        val dbText = { v: Float -> signed(Math.round(v)) + " dB" }
        ZSlider("بم (۱۲۰ هرتز)", eq.bass, -12f, 12f, { eq = eq.copy(bass = it) }, readout = dbText)
        ZSlider("بم‌میانه (۳۵۰ هرتز)", eq.lowMid, -12f, 12f, { eq = eq.copy(lowMid = it) }, readout = dbText)
        ZSlider("میانه (۱ کیلوهرتز)", eq.mid, -12f, 12f, { eq = eq.copy(mid = it) }, readout = dbText)
        ZSlider("زیر‌میانه (۳٫۵ کیلوهرتز)", eq.highMid, -12f, 12f, { eq = eq.copy(highMid = it) }, readout = dbText)
        ZSlider("زیر (۶ کیلوهرتز)", eq.treble, -12f, 12f, { eq = eq.copy(treble = it) }, readout = dbText)
        androidx.compose.foundation.layout.FlowRow(horizontalArrangement = Arrangement.spacedBy(Z.S2), verticalArrangement = Arrangement.spacedBy(Z.S2)) {
            ZChip("گفتار شفاف", false, { eq = app.zircut.core.EqBands(bass = -4f, lowMid = -2f, mid = 1f, highMid = 3f, treble = 2f) })
            ZChip("بم قوی", false, { eq = app.zircut.core.EqBands(bass = 7f, lowMid = 2f) })
            ZChip("روشن", false, { eq = app.zircut.core.EqBands(bass = -2f, highMid = 2f, treble = 6f) })
            ZChip("حذف صدای تو‌دماغی", false, { eq = app.zircut.core.EqBands(lowMid = -2f, mid = -4f, highMid = 1f) })
            ZChip("صفر", false, { eq = app.zircut.core.EqBands() })
        }
        ZButton("اعمال اکولایزر", { onEq(c.id, eq); eq = app.zircut.core.EqBands() }, Modifier.fillMaxWidth(), kind = ZBtn.Secondary, compact = true)
        Txt(
            "اکولایزر روی یک فایل تازه اعمال می‌شود (با Undo برمی‌گردد). برای اثر بیشتر می‌شود چند بار اعمال کرد؛ اگر صدا خش افتاد، مقدارها را کمتر کن.",
            style = ZType.caption, color = Z.TextSec
        )
        Txt(
            "برای جابه‌جایی سریع، روی کلیپ صوتی در تایم‌لاین چند لحظه نگه دار و افقی بکش. بلندی هر صدا تا ۱۰۰٪ است و صداها هنگام خروجی با همین مقدارها میکس می‌شوند.",
            style = ZType.caption, color = Z.TextSec
        )
    }
}

/** A photo clip in the preview (the frame crops it like the exporter does). */
@Composable
private fun PreviewImage(path: String, modifier: Modifier) {
    val bmp by produceState<Bitmap?>(null, path) {
        value = withContext(Dispatchers.IO) {
            try {
                val o = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                BitmapFactory.decodeFile(path, o)
                var sample = 1
                while (o.outWidth / sample > 1600 || o.outHeight / sample > 1600) sample *= 2
                BitmapFactory.decodeFile(path, BitmapFactory.Options().apply { inSampleSize = sample })
            } catch (e: Exception) {
                null
            }
        }
    }
    Box(modifier.background(Color.Black)) {
        val b = bmp
        if (b != null) {
            Image(bitmap = b.asImageBitmap(), contentDescription = null, modifier = Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
        }
    }
}

private fun speedLabel(s: Float): String =
    fa(String.format(java.util.Locale.US, "%.2f", s).trimEnd('0').trimEnd('.')).replace('.', '٫') + "×"

/** Clip tools: they work on the clip under the playhead and are disabled when the edit is not possible there. */
@Composable
private fun ClipPanel(
    map: EdlMap,
    posMs: Long,
    manual: Boolean,
    onStep: (Int) -> Unit,
    onApply: (EdlResult?) -> Unit,
    onAuto: () -> Unit,
    onAddMedia: () -> Unit,
    onFreeze: (Long) -> Unit,
    fx: Fx,
    onFx: (Fx) -> Unit,
    canSpeed: Boolean,
    kinds: List<Int>,
    ripple: Boolean,
    onRipple: (Boolean) -> Unit,
    onGoto: (Long) -> Unit,
    onSeekClip: (Int) -> Unit
) {
    val segs = map.segs
    val ci = map.locate(posMs).first
    var picked by remember(segs.size) { mutableStateOf(setOf<Int>()) }
    var multiSpeed by remember { mutableStateOf(1f) }
    var freezeSec by remember { mutableStateOf(2) }
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(Z.S3)) {
        if (segs.isNotEmpty()) {
            Txt(
                "کلیپ " + fa(ci + 1) + " از " + fa(segs.size) + "  ·  " + faTime(segs[ci.coerceIn(0, segs.size - 1)].outLen),
                style = ZType.labelStrong, color = Z.TextSec
            )
        }
        ZToggleRow(ZI.Wave, "ریپل (جابه‌جایی همراه برش)", "لایه‌ها و صدای بعد از تغییر، همراه کوتاه/بلند شدن کلیپ جابه‌جا می‌شوند؛ لایه‌ی قفل‌شده ثابت می‌ماند", ripple) { on -> onRipple(on) }
        // transition of the cut BEFORE the clip under the playhead (own setting or the project's)
        if (segs.size > 1 && ci in 1 until segs.size) {
            val cur = segs[ci]
            val eff = Cuts.enter(segs, ci, fx)
            Txt("ترنزیشن ورود کلیپ " + fa(ci + 1) + " (محل برش قبلی)", style = ZType.labelStrong, color = Z.TextSec)
            LazyRow(horizontalArrangement = Arrangement.spacedBy(Z.S2)) {
                item(key = "def") {
                    ZChip("پیش‌فرض پروژه", cur.tr < 0, { onApply(EdlResult(segs.toMutableList().also { it[ci] = cur.copy(tr = -1, trMs = 0L) }, posMs)) })
                }
                items(TrKind.values().toList(), key = { it.id }) { k ->
                    ZChip(k.nameFa, cur.tr == k.ordinal, { onApply(EdlResult(segs.toMutableList().also { it[ci] = cur.copy(tr = k.ordinal) }, posMs)) })
                }
            }
            if (eff.kind != TrKind.NONE) {
                ZSlider("مدت ترنزیشن این برش", eff.ms.toFloat(), 150f, 1200f, { v -> onApply(EdlResult(segs.toMutableList().also { it[ci] = cur.copy(trMs = v.toLong()) }, posMs)) }, readout = { fa(Math.round(it)) + " م‌ث" })
            }
        }
        // effects of the clip under the playhead only (on top of the project-wide filter tab)
        if (segs.isNotEmpty()) {
            val cur = segs[ci.coerceIn(0, segs.size - 1)]
            val lk = cur.look
            fun setLook(n: ClipLook) { onApply(EdlResult(segs.toMutableList().also { it[ci.coerceIn(0, segs.size - 1)] = cur.copy(look = n) }, posMs)) }
            Txt("افکت همین کلیپ " + fa(ci + 1), style = ZType.labelStrong, color = Z.TextSec)
            TwoCols(
                { ZSlider("روشنایی", lk.brightness, -0.3f, 0.3f, { setLook(lk.copy(brightness = it)) }, readout = { signed(Math.round(it / 0.3f * 100f)) }) },
                { ZSlider("کنتراست", lk.contrast, 0.6f, 1.6f, { setLook(lk.copy(contrast = it)) }, readout = { signed(Math.round((it - 1f) * 100f)) }) }
            )
            TwoCols(
                { ZSlider("اشباع رنگ", lk.saturation, 0f, 2f, { setLook(lk.copy(saturation = it)) }, readout = { signed(Math.round((it - 1f) * 100f)) }) },
                { ZSlider("گرمی / سردی", lk.warmth, -1f, 1f, { setLook(lk.copy(warmth = it)) }, readout = { signed(Math.round(it * 100f)) }) }
            )
            TwoCols(
                { ZSlider("بلور", lk.blur, 0f, 1f, { setLook(lk.copy(blur = it)) }, readout = ::pct) },
                { ZSlider("شارپ", lk.sharpen, 0f, 1f, { setLook(lk.copy(sharpen = it)) }, readout = ::pct) }
            )
            Txt("حالت‌های آماده برای این کلیپ", style = ZType.label, color = Z.TextSec)
            LazyRow(horizontalArrangement = Arrangement.spacedBy(Z.S2)) {
                items(Looks.all.filter { it.id != "none" }, key = { it.id }) { lo ->
                    val pl = lk.copy(brightness = lo.brightness, contrast = lo.contrast, saturation = lo.saturation, warmth = lo.warmth)
                    ZChip(lo.nameFa, lk == pl, { setLook(pl) })
                }
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(Z.S2)) {
                ZButton("اعمال روی همه‌ی کلیپ‌ها", { onApply(EdlResult(segs.map { it.copy(look = lk) }, posMs)) }, Modifier.weight(1f), kind = ZBtn.Secondary, compact = true)
                if (ci < segs.size - 1) {
                    ZButton("کپی به کلیپ بعدی", { onApply(EdlResult(segs.toMutableList().also { it[ci + 1] = it[ci + 1].copy(look = lk) }, posMs)) }, Modifier.weight(1f), kind = ZBtn.Secondary, compact = true)
                }
            }
            if (!lk.isNone) {
                ZButton("پاک‌کردن افکت این کلیپ", { setLook(ClipLook()) }, Modifier.fillMaxWidth(), kind = ZBtn.Secondary, compact = true)
            }
            Txt("این افکت‌ها فقط روی همین کلیپ‌اند و روی فیلتر و افکت‌های تب «فیلتر» اضافه می‌شن. شارپ در پیش‌نمایش دیده نمی‌شه و فقط در خروجی اعمال می‌شه؛ بلور در پیش‌نمایش فقط روی اندروید ۱۲ به بالا است.", style = ZType.caption, color = Z.TextSec)
        }
        // frame-by-frame playhead: for cutting exactly where you want
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(Z.S2)) {
            ClipTool(ZI.SkipPrev, "۱۰ فریم قبل", posMs > 0L, Z.Text, Modifier.weight(1f)) { onStep(-10) }
            ClipTool(ZI.Arrow, "فریم قبل", posMs > 0L, Z.Text, Modifier.weight(1f), rotation = 180f) { onStep(-1) }
            ClipTool(ZI.Arrow, "فریم بعد", posMs < map.total, Z.Text, Modifier.weight(1f)) { onStep(1) }
            ClipTool(ZI.SkipNext, "۱۰ فریم بعد", posMs < map.total, Z.Text, Modifier.weight(1f)) { onStep(10) }
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(Z.S2)) {
            ClipTool(ZI.Scissors, "تقسیم", EdlOps.split(segs, posMs) != null, Z.Text, Modifier.weight(1f)) { onApply(EdlOps.split(segs, posMs)) }
            ClipTool(ZI.SkipPrev, "حذف قبل", EdlOps.trimStart(segs, posMs) != null, Z.Text, Modifier.weight(1f)) { onApply(EdlOps.trimStart(segs, posMs)) }
            ClipTool(ZI.SkipNext, "حذف بعد", EdlOps.trimEnd(segs, posMs) != null, Z.Text, Modifier.weight(1f)) { onApply(EdlOps.trimEnd(segs, posMs)) }
            ClipTool(ZI.Trash, "حذف کلیپ", EdlOps.delete(segs, posMs) != null, Z.Danger, Modifier.weight(1f)) { onApply(EdlOps.delete(segs, posMs)) }
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(Z.S2)) {
            ClipTool(ZI.Copy, "کپی", EdlOps.duplicate(segs, posMs) != null, Z.Text, Modifier.weight(1f)) { onApply(EdlOps.duplicate(segs, posMs)) }
            ClipTool(ZI.Arrow, "قبل‌تر", EdlOps.moveEarlier(segs, posMs) != null, Z.Text, Modifier.weight(1f), rotation = 180f) { onApply(EdlOps.moveEarlier(segs, posMs)) }
            ClipTool(ZI.Arrow, "بعدتر", EdlOps.moveLater(segs, posMs) != null, Z.Text, Modifier.weight(1f)) { onApply(EdlOps.moveLater(segs, posMs)) }
            ClipTool(ZI.Wave, "برش خودکار", manual, Z.Accent, Modifier.weight(1f)) { onAuto() }
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(Z.S2)) {
            val merge = EdlOps.mergeAdjacent(segs, posMs)
            val reverse = EdlOps.reverseOrder(segs, posMs)
            val duplicateAll = EdlOps.duplicateTimeline(segs, posMs)
            ClipTool(ZI.Merge, "ادغام برش‌ها", merge != null, Z.Text, Modifier.weight(1f)) { onApply(merge) }
            ClipTool(ZI.Arrow, "معکوس ترتیب", reverse != null, Z.Text, Modifier.weight(1f), rotation = 180f) { onApply(reverse) }
            ClipTool(ZI.Copy, "کپی کل تدوین", duplicateAll != null, Z.Text, Modifier.weight(1f)) { onApply(duplicateAll) }
            Spacer(Modifier.weight(1f))
        }
        val curSpeed = segs.getOrNull(ci)?.speed ?: 1f
        Txt("سرعت کلیپ — " + speedLabel(curSpeed), style = ZType.labelStrong, color = Z.TextSec)
        Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(Z.S2)) {
            listOf(0.25f, 0.5f, 0.75f, 1f, 1.5f, 2f, 3f, 4f).forEach { sp ->
                ZChip(speedLabel(sp), kotlin.math.abs(curSpeed - sp) < 0.01f, { if (canSpeed) onApply(EdlOps.setSpeed(segs, posMs, sp)) })
            }
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(Z.S2)) {
            val up = EdlOps.ramp(segs, posMs, listOf(1f, 1.5f, 2.5f, 4f))
            val down = EdlOps.ramp(segs, posMs, listOf(4f, 2.5f, 1.5f, 1f))
            val peak = EdlOps.ramp(segs, posMs, listOf(1f, 3f, 3f, 1f))
            ClipTool(ZI.Arrow, "شتاب‌گیری", canSpeed && up != null, Z.Text, Modifier.weight(1f)) { onApply(up) }
            ClipTool(ZI.Arrow, "کندشدن", canSpeed && down != null, Z.Text, Modifier.weight(1f), rotation = 180f) { onApply(down) }
            ClipTool(ZI.Spark, "اوج سرعت", canSpeed && peak != null, Z.Text, Modifier.weight(1f)) { onApply(peak) }
            Spacer(Modifier.weight(1f))
        }
        Txt(
            if (canSpeed) "سرعت فقط روی کلیپ زیر پلی‌هد اعمال می‌شه. «شتاب‌گیری»، «کندشدن» و «اوج سرعت» کلیپ رو به ۴ تکه با سرعت‌های مختلف تقسیم می‌کنن و هر تکه جدا قابل ویرایشه. خروجی با سرعت کمی بیشتر طول می‌کشه."
            else "روی کلیپ عکس سرعت اعمال نمی‌شه.",
            style = ZType.caption, color = Z.TextSec
        )
        if (segs.size > 1) {
            Txt("انتخاب چند کلیپ", style = ZType.labelStrong, color = Z.TextSec)
            androidx.compose.foundation.layout.FlowRow(
                horizontalArrangement = Arrangement.spacedBy(Z.S2),
                verticalArrangement = Arrangement.spacedBy(Z.S2)
            ) {
                segs.indices.forEach { i ->
                    ZChip(fa(i + 1), i in picked, { picked = if (i in picked) picked - i else picked + i })
                }
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(Z.S2)) {
                ClipTool(ZI.Check, "همه", picked.size < segs.size, Z.Text, Modifier.weight(1f)) { picked = segs.indices.toSet() }
                ClipTool(ZI.Close, "هیچ‌کدام", picked.isNotEmpty(), Z.Text, Modifier.weight(1f)) { picked = emptySet() }
                ClipTool(ZI.Position, "برو به اولی", picked.isNotEmpty(), Z.Text, Modifier.weight(1f)) { onSeekClip(picked.min()) }
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(Z.S2)) {
                ClipTool(ZI.Copy, "کپی", picked.isNotEmpty(), Z.Text, Modifier.weight(1f)) {
                    onApply(EdlOps.duplicateMany(segs, picked)); picked = emptySet()
                }
                ClipTool(ZI.Trash, "حذف", picked.isNotEmpty() && picked.size < segs.size, Z.Danger, Modifier.weight(1f)) {
                    onApply(EdlOps.deleteMany(segs, picked)); picked = emptySet()
                }
            }
            Txt("جابه‌جایی انتخاب‌شده‌ها", style = ZType.label, color = Z.TextSec)
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(Z.S2)) {
                listOf(2 to "به اول", 0 to "یکی جلوتر", 1 to "یکی عقب‌تر", 3 to "به آخر").forEach { (mode, label) ->
                    ZChip(label, false, {
                        EdlOps.moveMany(segs, picked, mode)?.let { r -> onApply(r.result); picked = r.picked }
                    }, Modifier.weight(1f))
                }
            }
            Txt("سرعت انتخاب‌شده‌ها", style = ZType.label, color = Z.TextSec)
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(Z.S2)) {
                listOf(0.5f, 1f, 1.5f, 2f).forEach { sp ->
                    ZChip(fa("%.1f".format(sp)) + "×", multiSpeed == sp, { multiSpeed = sp }, Modifier.weight(1f))
                }
            }
            val photos = kinds.indices.filter { kinds[it] == 2 }.toSet()
            ClipTool(ZI.Arrow, "اعمال سرعت", picked.isNotEmpty(), Z.Text, Modifier.fillMaxWidth()) {
                onApply(EdlOps.setSpeedMany(segs, picked, multiSpeed, photos))
            }
            Txt("شماره‌ی کلیپ‌ها همان ترتیب تایم‌لاین است. کلیپ عکس سرعت نمی‌گیرد و رد می‌شود. همه‌ی تغییرات یک مرحله‌ی Undo هستند.", style = ZType.caption, color = Z.TextSec)
        }
        Txt("چرخش و زوم تصویر (همه‌ی ویدیوها)", style = ZType.labelStrong, color = Z.TextSec)
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(Z.S2)) {
            ClipTool(ZI.Redo, "چرخش ۹۰° راست", true, Z.Text, Modifier.weight(1f)) { onFx(fx.copy(rot = (fx.rot + 90) % 360)) }
            ClipTool(ZI.Undo, "چرخش ۹۰° چپ", true, Z.Text, Modifier.weight(1f)) { onFx(fx.copy(rot = (fx.rot + 270) % 360)) }
            ClipTool(ZI.Close, "بازنشانی", fx.hasTransform || fx.hasFine || fx.vpanX != 0f || fx.vpanY != 0f, Z.Danger, Modifier.weight(1f)) {
                onFx(fx.copy(rot = 0, vzoom = 1f, vpanX = 0f, vpanY = 0f, flipH = false, flipV = false, fine = 0f, vkeys = emptyList()))
            }
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(Z.S2)) {
            ZChip("قرینه افقی", fx.flipH, { onFx(fx.copy(flipH = !fx.flipH)) }, Modifier.weight(1f))
            ZChip("قرینه عمودی", fx.flipV, { onFx(fx.copy(flipV = !fx.flipV)) }, Modifier.weight(1f))
        }
        // with keyframes the sliders show the look at the playhead and a change becomes a keyframe there
        val vp = VideoKeys.pose(fx, posMs)
        val keyed = fx.vkeys.isNotEmpty()
        ZSlider("چرخش آزاد", vp.rot, -45f, 45f, { v0 -> val v = if (kotlin.math.abs(v0) < 0.5f) 0f else v0; onFx(if (keyed) VideoKeys.upsert(fx, posMs, VideoPose(vp.zoom, vp.panX, vp.panY, v, vp.op)) else fx.copy(fine = v)) }, readout = { v -> signed(Math.round(v)) + "°" })
        ZSlider("زوم تصویر", vp.zoom, 1f, 4f, { v -> onFx(if (keyed) VideoKeys.upsert(fx, posMs, VideoPose(v, vp.panX, vp.panY, vp.rot, vp.op)) else fx.copy(vzoom = v)) }, readout = { fa("%.1f".format(it)) + "×" })
        ZSlider("جابه‌جایی افقی", vp.panX, -1f, 1f, { v -> onFx(if (keyed) VideoKeys.upsert(fx, posMs, VideoPose(vp.zoom, v, vp.panY, vp.rot, vp.op)) else fx.copy(vpanX = v)) }, readout = { pct(it) })
        ZSlider("جابه‌جایی عمودی", vp.panY, -1f, 1f, { v -> onFx(if (keyed) VideoKeys.upsert(fx, posMs, VideoPose(vp.zoom, vp.panX, v, vp.rot, vp.op)) else fx.copy(vpanY = v)) }, readout = { pct(it) })
        ZSlider("شفافیت تصویر (محو به سیاه)", vp.op, 0f, 1f, { v -> onFx(VideoKeys.upsert(fx, posMs, VideoPose(vp.zoom, vp.panX, vp.panY, vp.rot, v))) }, readout = { pct(it) })
        Txt("کی‌فریم زوم، جابه‌جایی، چرخش آزاد و شفافیت تصویر" + (if (keyed) "  ·  " + fa(fx.vkeys.size) + " کی‌فریم" else ""), style = ZType.labelStrong, color = Z.TextSec)
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(Z.S2)) {
            ClipTool(ZI.SkipPrev, "کی‌فریم قبل", VideoKeys.previous(fx, posMs) != null, Z.Text, Modifier.weight(1f)) { VideoKeys.previous(fx, posMs)?.let { onGoto(it.tMs) } }
            ClipTool(ZI.Plus, if (VideoKeys.nearest(fx, posMs) != null) "به‌روزرسانی" else "ثبت اینجا", fx.vkeys.size < VideoKeys.MAX_KEYS || VideoKeys.nearest(fx, posMs) != null, Z.Text, Modifier.weight(1f)) {
                onFx(VideoKeys.upsert(fx, posMs, vp))
            }
            ClipTool(ZI.SkipNext, "کی‌فریم بعد", VideoKeys.next(fx, posMs) != null, Z.Text, Modifier.weight(1f)) { VideoKeys.next(fx, posMs)?.let { onGoto(it.tMs) } }
        }
        val vkHere = VideoKeys.nearest(fx, posMs)
        if (vkHere != null) {
            Txt("حرکت از این کی‌فریم تا کی‌فریم بعد", style = ZType.label, color = Z.TextSec)
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(Z.S2)) {
                listOf(VideoKeys.EASE_SMOOTH to "نرم", VideoKeys.EASE_LINEAR to "خطی", VideoKeys.EASE_HOLD to "ثابت (پله‌ای)").forEach { (v, label) ->
                    ZChip(label, vkHere.ease == v, { onFx(VideoKeys.setEase(fx, posMs, v)) })
                }
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(Z.S2)) {
                listOf(-100L to "کی‌فریم ۱۰۰ms زودتر", 100L to "کی‌فریم ۱۰۰ms دیرتر").forEach { (dl, label) ->
                    ZButton(label, {
                        val n = VideoKeys.nudge(fx, posMs, dl, map.total)
                        if (n !== fx) { onFx(n); onGoto((vkHere.tMs + dl).coerceIn(0L, map.total)) }
                    }, Modifier.weight(1f), kind = ZBtn.Secondary, compact = true)
                }
            }
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(Z.S2)) {
            ClipTool(ZI.Trash, "حذف این کی‌فریم", VideoKeys.nearest(fx, posMs) != null, Z.Danger, Modifier.weight(1f)) { onFx(VideoKeys.remove(fx, posMs)) }
            ClipTool(ZI.Close, "پاک‌کردن همه‌ی کی‌فریم‌ها", keyed, Z.Danger, Modifier.weight(1f)) { onFx(fx.copy(vkeys = emptyList())) }
        }
        Txt("پلی‌هد را ببر، زوم/جابه‌جایی را تنظیم کن و «ثبت اینجا» را بزن (چرخش آزاد و شفافیت هم ثبت می‌شوند؛ با لغزنده‌ی شفافیت اولین کی‌فریم خودکار ساخته می‌شود)؛ در جای دیگر دوباره. بین کی‌فریم‌ها حرکت نرم است. با وجود کی‌فریم، لغزنده‌ها خودشان کی‌فریم می‌گذارند. کی‌فریم‌ها به زمان تایم‌لاین بسته‌اند و با برش کلیپ‌ها جابه‌جا نمی‌شوند.", style = ZType.caption, color = Z.TextSec)
        Txt("فریم ثابت (Freeze) — مدت", style = ZType.labelStrong, color = Z.TextSec)
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(Z.S2)) {
            listOf(1, 2, 3, 5).forEach { sec ->
                ZChip(fa(sec) + " ثانیه", freezeSec == sec, { freezeSec = sec }, Modifier.weight(1f))
            }
        }
        ZButton("ثابت‌کردن فریم زیر پلی‌هد", { onFreeze(freezeSec * 1000L) }, Modifier.fillMaxWidth(), icon = ZI.Film, compact = true)
        ZButton("افزودن ویدیو یا عکس در پلی‌هد", onAddMedia, Modifier.fillMaxWidth(), icon = ZI.Plus, compact = true)
        Txt(
            if (manual) "برش دستی فعاله. دستگیره‌های دو سر کلیپ را بکش تا ابتدا یا انتهای همان کلیپ کوتاه شود. با «برش خودکار» دوباره به حذف خودکار سکوت‌ها برمی‌گردی."
            else "با اولین تغییر، برش فعلی (با حذف سکوت‌ها) به کلیپ‌های قابل ویرایش تبدیل می‌شه. پلی‌هد رو روی نقطه‌ی دلخواه بذار و ابزار رو بزن.",
            style = ZType.caption, color = Z.TextSec
        )
    }
}

@Composable
private fun ClipTool(icon: ZI, label: String, enabled: Boolean, tint: Color, modifier: Modifier, rotation: Float = 0f, onClick: () -> Unit) {
    val shape = RoundedCornerShape(Z.RSm)
    Column(
        modifier.press(enabled, onClick),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(5.dp)
    ) {
        Box(
            Modifier.fillMaxWidth().height(48.dp).clip(shape).background(Z.Surface2).border(Z.Hair, Z.Border, shape),
            contentAlignment = Alignment.Center
        ) { ZIcon(icon, tint = tint, size = Z.IconMd, rotation = rotation) }
        Txt(label, style = ZType.micro, color = Z.TextSec, maxLines = 1)
    }
}

@Composable
private fun EditPanel(
    opt: Options, info: VideoInfo, kept: Long, onSettings: () -> Unit,
    onSrtOut: () -> Unit, onSrtIn: () -> Unit, onScenes: () -> Unit, onClean: () -> Unit,
    onOptions: (Options) -> Unit
) {
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(Z.S1)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(Z.S3)) {
            ZStat("مدت اصلی", faTime(info.durationMs), Modifier.weight(1f))
            ZStat("بعد از ادیت", faTime(kept), Modifier.weight(1f), highlight = true)
        }
        OptionToggles(opt, onOptions)
        Spacer(Modifier.height(Z.S2))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(Z.S2)) {
            ZButton("خروجی SRT", onSrtOut, Modifier.weight(1f), kind = ZBtn.Secondary, icon = ZI.Download, compact = true)
            ZButton("ورود SRT", onSrtIn, Modifier.weight(1f), kind = ZBtn.Secondary, icon = ZI.File, compact = true)
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(Z.S2)) {
            ZButton("تشخیص صحنه و برش", onScenes, Modifier.weight(1f), kind = ZBtn.Secondary, icon = ZI.Scissors, compact = true)
            ZButton("پاک‌سازی فایل‌ها", onClean, Modifier.weight(1f), kind = ZBtn.Secondary, icon = ZI.Trash, compact = true)
        }
        Txt(
            "SRT با زمان ویدیوی ادیت‌شده ساخته و خوانده می‌شود. «پاک‌سازی» تاریخچه‌ی Undo را پاک می‌کند.",
            style = ZType.micro, color = Z.TextSec
        )
        Spacer(Modifier.height(Z.S2))
        ZButton("تنظیمات برنامه (زبان…)", onSettings, Modifier.fillMaxWidth(), kind = ZBtn.Secondary, icon = ZI.Tune, compact = true)
    }
}

@Composable
internal fun OptionToggles(opt: Options, onOptions: (Options) -> Unit) {
    ZToggleRow(ZI.Captions, "زیرنویس", "نمایش زیرنویس روی ویدیو", opt.captions) { onOptions(opt.copy(captions = it)) }
    ZToggleRow(ZI.Scissors, "حذف سکوت‌ها", "بخش‌های بی‌صدا از روی تحلیل صدا برش می‌خورن", opt.removeSilence) { onOptions(opt.copy(removeSilence = it)) }
    ZToggleRow(ZI.Expand, "زوم ریتمی", "حرکت ملایم روی برش‌ها", opt.zoom) { onOptions(opt.copy(zoom = it)) }
    Txt("نسبت تصویر خروجی", style = ZType.labelStrong, color = Z.TextSec)
    Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(Z.S2)) {
        Frame.IDS.forEach { id ->
            val label = if (id == "orig") "اصلی" else fa(id)
            ZChip(label, Frame.id(opt) == id, { onOptions(opt.copy(ratio = id, vertical = id != "orig")) })
        }
        ZChip("آزاد", Frame.free(opt) != null, {
            if (Frame.free(opt) == null) onOptions(opt.copy(ratio = Frame.customId(Frame.aspect(opt).let { if (it > 0f) it else 1f }), vertical = true))
        })
    }
    Frame.free(opt)?.let { a ->
        ZSlider("نسبت آزاد (عرض ÷ ارتفاع)", a, Frame.MIN_FREE, Frame.MAX_FREE, { onOptions(opt.copy(ratio = Frame.customId(it), vertical = true)) }, readout = { v -> fa("%.2f".format(java.util.Locale.US, v)) })
    }
}

/** Blur in the preview: Android 12+ only (RenderEffect). The exporter's shader blur is similar but not pixel-identical. */
private fun applyBlur(v: View, amount: Float) {
    if (android.os.Build.VERSION.SDK_INT < 31) return
    try {
        if (amount <= 0.01f) {
            v.setRenderEffect(null)
        } else {
            val r = maxOf(0.5f, amount * 0.025f * v.width * 0.5f)
            v.setRenderEffect(android.graphics.RenderEffect.createBlurEffect(r, r, android.graphics.Shader.TileMode.CLAMP))
        }
    } catch (_: Throwable) {
    }
}

/** Live preview of the color grade: the same matrix the exporter uses is applied to the video surface. */
private fun applyGrade(v: View, g: ColorGrade, lut: Affine? = null, clip: ClipLook = ClipLook()) {
    val key = Triple(g, lut, clip.takeIf { it.hasColor })
    if (v.tag == key) return
    v.tag = key
    if (g.isIdentity && lut == null && !clip.hasColor) {
        v.setLayerType(View.LAYER_TYPE_NONE, null)
        return
    }
    val p = android.graphics.Paint()
    val base = if (lut == null) g.affine() else lut.after(g.affine())
    val total = if (clip.hasColor) clip.affine().after(base) else base
    p.colorFilter = android.graphics.ColorMatrixColorFilter(android.graphics.ColorMatrix(total.toColorMatrix()))
    v.setLayerType(View.LAYER_TYPE_HARDWARE, p)
}

@Composable
private fun FilterPanel(video: android.net.Uri?, grade: ColorGrade, fx: Fx, onFx: (Fx) -> Unit, mine: List<MyTemplate>, onTemplate: (String) -> Unit, onMine: (Int) -> Unit, onSaveMine: () -> Unit, onDeleteMine: (Int) -> Unit, onPickLut: () -> Unit, onGrade: (ColorGrade) -> Unit) {
    val ctx = LocalContext.current
    val frame by produceState<Bitmap?>(null, video) {
        value = withContext(Dispatchers.IO) {
            val src = video ?: return@withContext null
            try {
                val r = MediaMetadataRetriever()
                r.setDataSource(ctx, src)
                val b = r.getFrameAtTime(1_000_000L)
                r.release()
                if (b == null) null else {
                    val k = 160f / maxOf(b.width, b.height)
                    Bitmap.createScaledBitmap(b, maxOf(1, (b.width * k).toInt()), maxOf(1, (b.height * k).toInt()), true)
                }
            } catch (e: Exception) {
                null
            }
        }
    }
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(Z.S2)) {
        Txt("قالب‌های آماده", style = ZType.labelStrong, color = Z.TextSec)
        LazyRow(horizontalArrangement = Arrangement.spacedBy(Z.S2)) {
            items(VideoTemplates.all, key = { it.id }) { t ->
                Column(
                    Modifier.width(150.dp).clip(RoundedCornerShape(Z.RSm)).background(Z.Surface2).border(Z.Hair, Z.Border, RoundedCornerShape(Z.RSm))
                        .press(true) { onTemplate(t.id) }.padding(Z.S2),
                    verticalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    Txt(t.name, style = ZType.labelStrong)
                    Txt(t.note, style = ZType.caption, color = Z.TextSec)
                }
            }
        }
        Txt("قالب‌های من", style = ZType.labelStrong, color = Z.TextSec)
        LazyRow(horizontalArrangement = Arrangement.spacedBy(Z.S2), verticalAlignment = Alignment.CenterVertically) {
            itemsIndexed(mine) { i, t ->
                Row(
                    Modifier.clip(RoundedCornerShape(Z.RSm)).background(Z.Surface2).border(Z.Hair, Z.Border, RoundedCornerShape(Z.RSm)).padding(start = Z.S2),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Txt(app.zircut.i18n.Lang.raw(t.name), style = ZType.label, modifier = Modifier.press(true) { onMine(i) }.padding(vertical = 8.dp))
                    ZIconButton(ZI.Close, { onDeleteMine(i) }, tint = Z.Danger, box = 34.dp, size = 16.dp)
                }
            }
            item { ZButton("ذخیره‌ی تنظیمات فعلی", onSaveMine, kind = ZBtn.Secondary, compact = true) }
        }
        Txt("هر قالب نسبت تصویر، استایل زیرنویس، فیلتر رنگ و یک متن عنوان می‌گذارد. بعد از اعمال همه‌چیز قابل تغییر است و کل قالب با یک Undo برمی‌گردد.", style = ZType.caption, color = Z.TextSec)
        LazyRow(horizontalArrangement = Arrangement.spacedBy(Z.S3)) {
            items(Looks.all, key = { it.id }) { look ->
                val sel = grade.lookId == look.id
                val m = ColorGrade(lookId = look.id).affine().toColorMatrix()
                val shape = RoundedCornerShape(Z.RSm)
                val line by animateColorAsState(if (sel) Z.Accent else Z.Border, tween(140), label = "lookLine")
                Column(
                    Modifier.press(true) { onGrade(grade.copy(lookId = look.id)) },
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(5.dp)
                ) {
                    Box(
                        Modifier.size(width = 58.dp, height = 76.dp).clip(shape).background(Z.Surface3)
                            .border(if (sel) 1.5.dp else Z.Hair, line, shape)
                    ) {
                        val b = frame
                        if (b != null) {
                            Image(
                                bitmap = b.asImageBitmap(),
                                contentDescription = null,
                                modifier = Modifier.matchParentSize(),
                                contentScale = ContentScale.Crop,
                                colorFilter = ColorFilter.colorMatrix(androidx.compose.ui.graphics.ColorMatrix(m))
                            )
                        }
                    }
                    Txt(look.nameFa, style = ZType.micro, color = if (sel) Z.Text else Z.TextSec, maxLines = 1)
                }
            }
        }
        if (grade.lookId != "none") {
            ZSlider("شدت فیلتر", grade.strength, 0f, 1f, { onGrade(grade.copy(strength = it)) }, readout = ::pct)
        }
        TwoCols(
            { ZSlider("روشنایی", grade.brightness, -0.3f, 0.3f, { onGrade(grade.copy(brightness = it)) }, readout = { signed(Math.round(it / 0.3f * 100f)) }) },
            { ZSlider("کنتراست", grade.contrast, 0.6f, 1.6f, { onGrade(grade.copy(contrast = it)) }, readout = { signed(Math.round((it - 1f) * 100f)) }) }
        )
        TwoCols(
            { ZSlider("اشباع رنگ", grade.saturation, 0f, 2f, { onGrade(grade.copy(saturation = it)) }, readout = { signed(Math.round((it - 1f) * 100f)) }) },
            { ZSlider("محو", grade.fade, 0f, 0.3f, { onGrade(grade.copy(fade = it)) }, readout = { pct(it / 0.3f) }) }
        )
        TwoCols(
            { ZSlider("گرمی / سردی", grade.warmth, -1f, 1f, { onGrade(grade.copy(warmth = it)) }, readout = { signed(Math.round(it * 100f)) }) },
            { ZSlider("سبز / سرخ", grade.tint, -1f, 1f, { onGrade(grade.copy(tint = it)) }, readout = { signed(Math.round(it * 100f)) }) }
        )
        TwoCols(
            { ZSlider("نوردهی", grade.exposure, -1f, 1f, { onGrade(grade.copy(exposure = it)) }, readout = { signed(Math.round(it * 100f)) }) },
            { ZSlider("تغییر رنگ‌مایه", grade.hue, -180f, 180f, { onGrade(grade.copy(hue = it)) }, readout = { signed(Math.round(it)) }) }
        )
        TwoCols(
            { ZSlider("سفیدی‌ها", grade.whites, -0.2f, 0.2f, { onGrade(grade.copy(whites = it)) }, readout = { signed(Math.round(it / 0.2f * 100f)) }) },
            { ZSlider("سیاهی‌ها", grade.blacks, -0.15f, 0.15f, { onGrade(grade.copy(blacks = it)) }, readout = { signed(Math.round(it / 0.15f * 100f)) }) }
        )
        Txt("افکت‌های تصویر", style = ZType.labelStrong, color = Z.TextSec)
        TwoCols(
            { ZSlider("وینیت", fx.vignette, 0f, 1f, { onFx(fx.copy(vignette = it)) }, readout = ::pct) },
            { ZSlider("دانه‌ی فیلم", fx.grain, 0f, 1f, { onFx(fx.copy(grain = it)) }, readout = ::pct) }
        )
        TwoCols(
            { ZSlider("نوار سینمایی", fx.bars, 0f, 1f, { onFx(fx.copy(bars = it)) }, readout = ::pct) },
            { ZSlider("نشت نور", fx.leak, 0f, 1f, { onFx(fx.copy(leak = it)) }, readout = ::pct) }
        )
        TwoCols(
            { ZSlider("بلور", fx.blur, 0f, 1f, { onFx(fx.copy(blur = it)) }, readout = ::pct) },
            { ZSlider("تیزی", fx.sharpen, 0f, 1f, { onFx(fx.copy(sharpen = it)) }, readout = ::pct) }
        )
        TwoCols(
            { ZSlider("جدایی RGB", fx.split, 0f, 1f, { onFx(fx.copy(split = it)) }, readout = ::pct) },
            { ZSlider("گلیچ", fx.glitch, 0f, 1f, { onFx(fx.copy(glitch = it)) }, readout = ::pct) }
        )
        TwoCols(
            { ZSlider("نویز", fx.noise, 0f, 1f, { onFx(fx.copy(noise = it)) }, readout = ::pct) },
            { ZSlider("درخشش", fx.glow, 0f, 1f, { onFx(fx.copy(glow = it)) }, readout = ::pct) }
        )
        TwoCols(
            { ZSlider("هایلایت‌ها", fx.highlights, -1f, 1f, { onFx(fx.copy(highlights = it)) }, readout = { signed(Math.round(it * 100f)) }) },
            { ZSlider("سایه‌ها", fx.shadows, -1f, 1f, { onFx(fx.copy(shadows = it)) }, readout = { signed(Math.round(it * 100f)) }) }
        )
        Txt(
            "بلور در پیش‌نمایش فقط روی اندروید ۱۲ به بالا دیده می‌شود. تیزی، جدایی RGB، گلیچ، نویز، درخشش و هایلایت/سایه فقط در ویدیوی خروجی اعمال می‌شوند.",
            style = ZType.micro, color = Z.TextSec
        )
        Txt("ترنزیشن در محل برش‌ها", style = ZType.labelStrong, color = Z.TextSec)
        LazyRow(horizontalArrangement = Arrangement.spacedBy(Z.S2)) {
            items(TrKind.values().toList(), key = { it.id }) { k ->
                ZChip(k.nameFa, fx.trKind == k.id, { onFx(fx.copy(trKind = k.id)) })
            }
        }
        if (fx.trKind != "none") {
            ZSlider("مدت ترنزیشن", fx.trMs.toFloat(), 150f, 1200f, { onFx(fx.copy(trMs = it.toLong())) }, readout = { fa(Math.round(it)) + " م‌ث" })
            Txt(
                "پایان هر کلیپ خارج و ابتدای کلیپ بعدی وارد می‌شود (ترکیب هم‌زمان دو کلیپ نیست). فقط وقتی بیش از یک کلیپ داری دیده می‌شود.",
                style = ZType.micro, color = Z.TextSec
            )
        }
        Txt("LUT رنگ (فایل .cube)", style = ZType.labelStrong, color = Z.TextSec)
        ZButton(if (fx.lutFile.isEmpty()) "انتخاب فایل LUT" else "تعویض فایل LUT", onPickLut, Modifier.fillMaxWidth(), icon = ZI.Plus, compact = true)
        if (fx.lutFile.isNotEmpty()) {
            ZSlider("شدت LUT", fx.lutAmt, 0f, 1f, { onFx(fx.copy(lutAmt = it)) }, readout = ::pct)
            ZButton("حذف LUT", { onFx(fx.copy(lutFile = "")) }, Modifier.fillMaxWidth(), kind = ZBtn.Secondary, icon = ZI.Trash, compact = true)
            Txt("پیش‌نمایش فقط تقریبی است (یک ماتریس رنگ مشابه LUT)؛ ویدیوی خروجی با خود LUT دقیق ساخته می‌شود.", style = ZType.micro, color = Z.TextSec)
        }
        ZButton("برگرداندن همه به حالت اصلی", { onGrade(ColorGrade()); onFx(Fx()) }, Modifier.fillMaxWidth(), kind = ZBtn.Secondary, icon = ZI.Undo, compact = true)
    }
}

// =====================================================================================
//  Export sheet
// =====================================================================================

@Composable
private fun ExportSheet(
    opt: Options,
    phraseCount: Int,
    original: Long,
    kept: Long,
    video: android.net.Uri?,
    srcW: Int,
    srcH: Int,
    settings: ExportSettings,
    onSettings: (ExportSettings) -> Unit,
    onOptions: (Options) -> Unit,
    onDismiss: () -> Unit,
    onExport: () -> Unit
) {
    val ctx = LocalContext.current
    val srcFps by produceState(30f, video) {
        value = if (video == null) 30f else withContext(Dispatchers.IO) { DeviceCaps.sourceFps(ctx, video) }
    }
    // only what this phone's encoder really accepts is offered
    val sizes = remember(opt.vertical, opt.ratio, srcW, srcH) {
        listOf(720, 1080, 1440, 2160).filter { e ->
            val d = ExportPlan.size(ExportSettings(shortEdge = e), Frame.aspect(opt), srcW, srcH)
            DeviceCaps.supports(DeviceCaps.h264, d.first, d.second)
        }
    }
    val dims = ExportPlan.size(settings, Frame.aspect(opt), srcW, srcH)
    val fpsOptions = remember(dims, srcFps) {
        listOf(24, 30, 60).filter { f -> (f < 60 || srcFps >= 50f) && DeviceCaps.supports(DeviceCaps.h264, dims.first, dims.second, f) }
    }
    val hevcOk = remember(dims, settings.fps) {
        DeviceCaps.hasHardware(DeviceCaps.hevc) && DeviceCaps.supports(DeviceCaps.hevc, dims.first, dims.second, settings.fps)
    }
    LaunchedEffect(sizes, fpsOptions, hevcOk, settings) {
        val fixed = settings.copy(
            shortEdge = if (settings.shortEdge == 0 || settings.shortEdge in sizes) settings.shortEdge else 0,
            fps = if (settings.fps == 0 || settings.fps in fpsOptions) settings.fps else 0,
            hevc = settings.hevc && hevcOk
        )
        if (fixed != settings) onSettings(fixed)
    }
    val codecMime = if (settings.hevc && hevcOk) DeviceCaps.hevc else DeviceCaps.h264
    val autoBps = ExportPlan.autoBitrate(dims.first, dims.second, settings.hevc && hevcOk)
    val bps = ExportPlan.videoBitrate(settings, dims.first, dims.second, settings.hevc && hevcOk)
    val maxMbps = (minOf(DeviceCaps.maxBitrate(codecMime), 80_000_000) / 1_000_000).coerceAtLeast(8)
    val estBytes = ExportPlan.estimateBytes(bps, kept)
    val freeBytes = remember { try { android.os.StatFs(ctx.cacheDir.absolutePath).availableBytes } catch (e: Exception) { Long.MAX_VALUE } }
    // the file is written to the app's cache first and then copied to the gallery: twice the space is needed
    val needBytes = (estBytes * 2.2).toLong()
    val spaceOk = freeBytes >= needBytes
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = Z.Surface,
        shape = RoundedCornerShape(topStart = Z.RLg, topEnd = Z.RLg),
        dragHandle = { ZSheetHandle() }
    ) {
        CompositionLocalProvider(LocalLayoutDirection provides app.zircut.i18n.Lang.dir) {
            Column(
                Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = Z.S5).padding(bottom = Z.S6),
                verticalArrangement = Arrangement.spacedBy(Z.S3)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Txt("خروجی ویدیو", style = ZType.title)
                        Txt("گزینه‌ها رو بررسی کن و ویدیو رو بساز.", style = ZType.caption, color = Z.TextSec)
                    }
                    ZIconButton(ZI.Close, onDismiss, tint = Z.TextSec)
                }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(Z.S3)) {
                    ZStat("قالب", Frame.label(opt), Modifier.weight(1f))
                    ZStat("مدت نهایی", faTime(kept), Modifier.weight(1f), highlight = true)
                    ZStat("زیرنویس", if (opt.captions && phraseCount > 0) fa(phraseCount) + " بخش" else "ندارد", Modifier.weight(1f))
                }
                Column { OptionToggles(opt, onOptions) }
                Txt("کیفیت خروجی", style = ZType.labelStrong, color = Z.TextSec)
                Txt("اندازه‌ی تصویر", style = ZType.caption, color = Z.TextSec)
                LazyRow(horizontalArrangement = Arrangement.spacedBy(Z.S2)) {
                    item { ZChip("خودکار", settings.shortEdge == 0, { onSettings(settings.copy(shortEdge = 0)) }) }
                    items(sizes) { e ->
                        val label = if (e == 2160) "۴K (پرمصرف)" else fa(e) + "p"
                        ZChip(label, settings.shortEdge == e, { onSettings(settings.copy(shortEdge = e)) })
                    }
                }
                Txt("نرخ فریم", style = ZType.caption, color = Z.TextSec)
                LazyRow(horizontalArrangement = Arrangement.spacedBy(Z.S2)) {
                    item { ZChip("مثل ویدیو", settings.fps == 0, { onSettings(settings.copy(fps = 0)) }) }
                    items(fpsOptions) { f -> ZChip(fa(f), settings.fps == f, { onSettings(settings.copy(fps = f)) }) }
                }
                Txt("کدک", style = ZType.caption, color = Z.TextSec)
                Row(horizontalArrangement = Arrangement.spacedBy(Z.S2)) {
                    ZChip("H.264 (سازگارتر)", !settings.hevc, { onSettings(settings.copy(hevc = false)) })
                    if (hevcOk) ZChip("HEVC (حجم کمتر)", settings.hevc, { onSettings(settings.copy(hevc = true)) })
                }
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Z.S3)) {
                    Box(Modifier.weight(1f)) {
                        ZSlider(
                            "نرخ بیت ویدیو", (bps / 1_000_000f).coerceIn(2f, maxMbps.toFloat()), 2f, maxMbps.toFloat(),
                            { onSettings(settings.copy(videoMbps = Math.round(it))) },
                            readout = { fa(Math.round(it)) + " Mbps" }
                        )
                    }
                    ZChip("خودکار", settings.videoMbps == 0, { onSettings(settings.copy(videoMbps = 0)) })
                }
                Txt(
                    "خروجی: " + fa(dims.first) + "×" + fa(dims.second) + " · حجم تقریبی " + fa(Math.max(1L, estBytes / 1048576L).toInt()) + " مگابایت" +
                        (if (settings.videoMbps == 0) " · نرخ خودکار " + fa(Math.round(autoBps / 1_000_000f)) + " Mbps" else "") +
                        " · صدا ثابت ۱۲۸ kbps",
                    style = ZType.caption, color = Z.TextSec
                )
                Txt(
                    "انکودر این گوشی: H.264 تا " + fa(DeviceCaps.maxSide(DeviceCaps.h264)) + " پیکسل" +
                        (if (DeviceCaps.hasHardware(DeviceCaps.h264)) " (سخت‌افزاری)" else " (نرم‌افزاری، کند)") +
                        (if (DeviceCaps.maxSide(DeviceCaps.hevc) > 0) " · HEVC " + (if (DeviceCaps.hasHardware(DeviceCaps.hevc)) "سخت‌افزاری" else "فقط نرم‌افزاری") else " · HEVC ندارد"),
                    style = ZType.caption, color = Z.TextTer
                )
                if (settings.shortEdge == 2160) {
                    Txt("۴K حافظه و زمان زیادی می‌خواهد؛ اگر خروجی شکست خورد، برنامه خودش با کیفیت کمتر دوباره تلاش می‌کند.", style = ZType.caption, color = Z.Warn)
                }
                if (!spaceOk) {
                    Txt(
                        "فضای خالی کافی نیست: برای این تنظیمات حدود " + fa((needBytes / 1048576L).toInt()) + " مگابایت لازم است ولی " +
                            fa((freeBytes / 1048576L).toInt()) + " مگابایت خالی است. نرخ بیت یا اندازه را کمتر کن.",
                        style = ZType.caption, color = Z.Danger
                    )
                }
                if (opt.captions && phraseCount == 0) {
                    Row(
                        Modifier.fillMaxWidth().clip(RoundedCornerShape(Z.RSm)).background(Z.WarnSoft).padding(Z.S3),
                        verticalAlignment = Alignment.Top
                    ) {
                        ZIcon(ZI.Info, tint = Z.Warn, size = Z.IconSm, modifier = Modifier.padding(top = 2.dp))
                        Spacer(Modifier.width(Z.S2))
                        Txt(
                            "هنوز زیرنویسی ساخته نشده؛ ویدیو بدون زیرنویس ساخته می‌شه.",
                            style = ZType.caption, color = Z.Warn
                        )
                    }
                }
                if (original > 0 && kept < original) {
                    Txt(
                        "از " + faTime(original) + " به " + faTime(kept) + " کوتاه شد.",
                        style = ZType.caption, color = Z.TextSec
                    )
                }
                ZButton("ساخت ویدیو", onExport, Modifier.fillMaxWidth(), icon = ZI.Export, enabled = spaceOk)
            }
        }
    }
}
