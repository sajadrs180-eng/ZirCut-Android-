package app.zircut.ui

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import app.zircut.core.Affine
import app.zircut.core.ChromaKeyMath
import app.zircut.core.ClipLook
import app.zircut.core.ColorGrade
import androidx.compose.ui.platform.LocalContext
import app.zircut.core.AiMask
import app.zircut.core.AiMaskMath
import app.zircut.core.AiSegmenter
import app.zircut.core.KeyLook
import app.zircut.core.KeyPreviewFx
import app.zircut.core.KeyStill
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

// Black-and-white look of the chroma key tools: no accent colour, only white on black (the picture itself brings the colour).
private val KBg = Color(0xFF000000)
private val KCard = Color(0xFF0E0E0E)
private val KLine = Color(0xFF2B2B2B)
private val KDim = Color(0xFF8C8C8C)
private val KFg = Color(0xFFFFFFFF)

@Composable
private fun KLabel(text: String, strong: Boolean = false) {
    Txt(text, style = if (strong) ZType.labelStrong else ZType.label, color = if (strong) KFg else KDim)
}

@Composable
private fun KChip(text: String, selected: Boolean, onClick: () -> Unit) {
    val shape = RoundedCornerShape(10.dp)
    Box(
        Modifier.height(34.dp).clip(shape).background(if (selected) KFg else KCard).border(1.dp, if (selected) KFg else KLine, shape)
            .press(true, onClick).padding(horizontal = 14.dp),
        contentAlignment = Alignment.Center
    ) { Txt(text, style = ZType.label, color = if (selected) KBg else KFg, maxLines = 1) }
}

@Composable
private fun KButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier, filled: Boolean = false, enabled: Boolean = true) {
    val shape = RoundedCornerShape(10.dp)
    Box(
        modifier.height(40.dp).clip(shape).background(if (filled) KFg else KCard).border(1.dp, if (filled) KFg else KLine, shape)
            .press(enabled, onClick),
        contentAlignment = Alignment.Center
    ) { Txt(text, style = ZType.labelStrong, color = if (filled) KBg else if (enabled) KFg else KDim, maxLines = 1) }
}

@Composable
private fun KSlider(label: String, value: Float, onChange: (Float) -> Unit) {
    Column(Modifier.fillMaxWidth()) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Txt(label, style = ZType.label, color = KDim, modifier = Modifier.weight(1f), maxLines = 1)
            Txt(fa(Math.round(value * 100f)) + "٪", style = ZType.mono, color = KFg)
        }
        CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
            Slider(
                value = value.coerceIn(0f, 1f),
                onValueChange = onChange,
                colors = SliderDefaults.colors(
                    thumbColor = KFg, activeTrackColor = KFg, inactiveTrackColor = Color(0xFF3A3A3A),
                    activeTickColor = KFg, inactiveTickColor = Color(0xFF3A3A3A)
                )
            )
        }
    }
}

@Composable
private fun KSwatch(color: Color, selected: Boolean, onClick: () -> Unit) {
    Box(
        Modifier.size(34.dp).clip(CircleShape).background(color).border(if (selected) 2.dp else 1.dp, if (selected) KFg else KLine, CircleShape)
            .press(true, onClick)
    )
}

private fun decodeSmall(f: File, maxSide: Int): Bitmap? = try {
    val bo = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    BitmapFactory.decodeFile(f.absolutePath, bo)
    var sample = 1
    while (bo.outWidth / sample > maxSide * 2 || bo.outHeight / sample > maxSide * 2) sample *= 2
    BitmapFactory.decodeFile(f.absolutePath, BitmapFactory.Options().apply { inSampleSize = sample })
} catch (_: Throwable) {
    null
}

/** the backdrop drawn behind the video surface while a clip is keyed (preview only; the export blends the background in the shader) */
@Composable
fun KeyBackdrop(k: KeyLook, bg: ImageBitmap?, modifier: Modifier = Modifier) {
    Canvas(modifier) {
        when {
            k.bgMode == 2 && bg != null -> drawCover(bg)
            k.bgMode == 1 -> drawRect(Color(k.bgColor))
            else -> {
                // checkerboard = transparent
                val cell = 12.dp.toPx()
                val cols = (size.width / cell).toInt() + 1
                val rows = (size.height / cell).toInt() + 1
                drawRect(Color(0xFF3C3C3C))
                for (y in 0 until rows) for (x in 0 until cols) {
                    if ((x + y) % 2 == 1) drawRect(Color(0xFF5A5A5A), Offset(x * cell, y * cell), Size(cell, cell))
                }
            }
        }
    }
}

private fun DrawScope.drawCover(img: ImageBitmap) {
    val fa = size.width / size.height
    val ba = img.width.toFloat() / img.height
    val sw: Int
    val sh: Int
    if (ba > fa) { sh = img.height; sw = Math.round(img.height * fa) } else { sw = img.width; sh = Math.round(img.width / fa) }
    drawImage(
        img,
        srcOffset = androidx.compose.ui.unit.IntOffset((img.width - sw) / 2, (img.height - sh) / 2),
        srcSize = IntSize(sw, sh),
        dstSize = IntSize(size.width.toInt(), size.height.toInt())
    )
}

/** loads the background picture of a key (null when none) for the preview backdrop */
@Composable
fun rememberKeyBackground(k: KeyLook, projDir: File, maxSide: Int): ImageBitmap? {
    val bmp by produceState<ImageBitmap?>(null, k.bgMode, k.bgFile, projDir) {
        value = if (k.bgMode == 2 && k.bgFile.isNotEmpty()) {
            withContext(Dispatchers.IO) { decodeSmall(File(projDir, k.bgFile), maxSide)?.asImageBitmap() }
        } else null
    }
    return bmp
}

/**
 * Chroma key tab (works on the clip under the playhead): eyedropper on a frame of the clip, tolerance, edge softness, spill suppression,
 * background (transparent / colour / picture) and a still preview of the result. The live result is on the main preview (Android 13+);
 * the export keys on every Android version.
 */
@Composable
fun ChromaKeyPanel(
    clipIndex: Int,
    clipCount: Int,
    isPhoto: Boolean,
    look: ClipLook,
    grade: ColorGrade,
    lut: Affine?,
    projDir: File,
    posMs: Long,
    onGrab: suspend (Long) -> Bitmap?,
    onLook: (ClipLook) -> Unit,
    onPickBackground: () -> Unit
) {
    val k = look.key
    // the finger gestures live longer than one composition: they must always see the newest look
    val lookNow by rememberUpdatedState(look)
    val onLookNow by rememberUpdatedState(onLook)
    val scope = rememberCoroutineScope()
    var frame by remember(clipIndex) { mutableStateOf<Bitmap?>(null) }
    var grabbing by remember { mutableStateOf(false) }
    var marker by remember(clipIndex) { mutableStateOf<Offset?>(null) }

    fun grab() {
        if (grabbing || isPhoto) return
        grabbing = true
        scope.launch {
            frame = onGrab(posMs)
            grabbing = false
        }
    }
    LaunchedEffect(clipIndex, isPhoto) { if (!isPhoto && frame == null) grab() }

    fun setKey(n: KeyLook) = onLook(look.copy(key = n))

    // the colour under the finger = average of a 5x5 patch of the source frame (as it is in the file, before any grade)
    fun pickAt(f: Bitmap, o: Offset, w: Float, h: Float) {
        val fx = (o.x / w).coerceIn(0f, 1f)
        val fy = (o.y / h).coerceIn(0f, 1f)
        marker = Offset(fx, fy)
        val cx = (fx * (f.width - 1)).toInt()
        val cy = (fy * (f.height - 1)).toInt()
        var r = 0
        var g = 0
        var b = 0
        var n = 0
        for (yy in (cy - 2)..(cy + 2)) for (xx in (cx - 2)..(cx + 2)) {
            if (xx in 0 until f.width && yy in 0 until f.height) {
                val p = f.getPixel(xx, yy)
                r += android.graphics.Color.red(p)
                g += android.graphics.Color.green(p)
                b += android.graphics.Color.blue(p)
                n++
            }
        }
        if (n > 0) onLookNow(lookNow.copy(key = lookNow.key.copy(on = true, color = android.graphics.Color.rgb(r / n, g / n, b / n))))
    }

    val bgSmall by produceState<Bitmap?>(null, k.bgMode, k.bgFile, projDir) {
        value = if (k.bgMode == 2 && k.bgFile.isNotEmpty()) withContext(Dispatchers.IO) { decodeSmall(File(projDir, k.bgFile), 480) } else null
    }
    val total = remember(grade, lut, look) { ChromaKeyMath.total(grade, lut, look) }
    val keyRgb = remember(k.color, grade, lut, look) { ChromaKeyMath.gradedKey(k, grade, lut, look) }
    val ctx = LocalContext.current
    val small = remember(frame) {
        frame?.let { f ->
            val sm = 320f / maxOf(f.width, f.height)
            if (sm < 1f) Bitmap.createScaledBitmap(f, maxOf(1, (f.width * sm).toInt()), maxOf(1, (f.height * sm).toInt()), true) else f
        }
    }
    // AI subject mask of the grabbed frame (raw probability; computed once per frame, shaped by the sliders below)
    var aiFailed by remember(clipIndex) { mutableStateOf<String?>(null) }
    val aiRaw by produceState<AiMask?>(null, small, k.ai == 1 && k.on) {
        val sm = small
        value = if (sm == null || k.ai != 1 || !k.on) null else withContext(Dispatchers.Default) {
            try {
                aiFailed = null
                AiSegmenter.once(ctx, sm)
            } catch (e: Throwable) {
                aiFailed = e.message ?: "خطا"
                null
            }
        }
    }
    val still by produceState<ImageBitmap?>(null, small, k, total, bgSmall, aiRaw) {
        val f = small
        value = if (f == null || !k.on) null else withContext(Dispatchers.Default) {
            if (k.ai == 1) {
                val m = aiRaw
                if (m == null || m.w != f.width || m.h != f.height) null
                else KeyStill.render(f, total, keyRgb, k, bgSmall, AiMaskMath.shapeAll(m, k.aiInvert, k.aiEdge)).asImageBitmap()
            } else KeyStill.render(f, total, keyRgb, k, bgSmall).asImageBitmap()
        }
    }

    Column(
        Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).background(KBg).padding(vertical = 4.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        if (isPhoto) {
            Txt("کروما کی فقط روی کلیپ‌های ویدیویی کار می‌کند. پلی‌هد را روی یک ویدیو ببر.", style = ZType.label, color = KDim)
            return@Column
        }
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                KLabel("کروما کی — کلیپ " + fa(clipIndex + 1) + " از " + fa(clipCount), strong = true)
                KLabel(if (k.on) "روشن" else "خاموش")
            }
            KChip(if (k.on) "خاموش‌کردن" else "روشن‌کردن", k.on) { setKey(k.copy(on = !k.on)) }
        }

        KLabel("روش حذف پس‌زمینه", strong = true)
        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            item(key = "ai0") { KChip("رنگ پرده (کروما)", k.ai == 0) { setKey(k.copy(on = true, ai = 0)) } }
            item(key = "ai1") { KChip("هوش مصنوعی (حذف پس‌زمینه سوژه)", k.ai == 1) { setKey(k.copy(on = true, ai = 1)) } }
        }
        if (k.ai == 1) {
            KLabel("هوش مصنوعی سوژه‌ی اصلی (آدم) را جدا می‌کند و بقیه را با پس‌زمینه‌ی انتخابی عوض می‌کند. کاملاً روی گوشی و بدون اینترنت کار می‌کند.")
            KChip("برعکس‌کردن انتخاب (پس‌زمینه بماند، سوژه برود)", k.aiInvert) { setKey(k.copy(on = true, aiInvert = !k.aiInvert)) }
            KSlider("نرمی لبه‌ی سوژه", k.aiEdge) { setKey(k.copy(on = true, aiEdge = it)) }
            val fe = aiFailed
            if (fe != null) KLabel("مدل هوش مصنوعی اجرا نشد: " + fe)
        }
        if (k.ai == 0) {
        // ---- 1. eyedropper ----
        KLabel("۱. رنگ پرده را انتخاب کن (رنگ‌چکان)", strong = true)
        val f = frame
        if (f == null) {
            Box(Modifier.fillMaxWidth().height(120.dp).clip(RoundedCornerShape(10.dp)).background(KCard).border(1.dp, KLine, RoundedCornerShape(10.dp)), contentAlignment = Alignment.Center) {
                Txt(if (grabbing) "در حال خواندن فریم…" else "فریمی خوانده نشد", style = ZType.label, color = KDim)
            }
        } else {
            val ar = f.width.toFloat() / f.height
            Box(
                Modifier.fillMaxWidth().aspectRatio(ar).clip(RoundedCornerShape(10.dp)).border(1.dp, KLine, RoundedCornerShape(10.dp))
                    .pointerInput(f) { detectTapGestures { pickAt(f, it, size.width.toFloat(), size.height.toFloat()) } }
                    .pointerInput(f) {
                        detectDragGestures { change, _ ->
                            change.consume()
                            pickAt(f, change.position, size.width.toFloat(), size.height.toFloat())
                        }
                    }
            ) {
                Image(f.asImageBitmap(), null, Modifier.fillMaxWidth(), contentScale = ContentScale.FillBounds)
                val m = marker
                if (m != null) {
                    Canvas(Modifier.fillMaxSize()) {
                        val c = Offset(m.x * size.width, m.y * size.height)
                        drawCircle(Color.Black, 14.dp.toPx(), c, style = Stroke(3.dp.toPx()))
                        drawCircle(Color.White, 14.dp.toPx(), c, style = Stroke(1.5.dp.toPx()))
                        drawCircle(Color.White, 2.dp.toPx(), c)
                    }
                }
            }
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(34.dp).clip(CircleShape).background(Color(k.color)).border(1.dp, KFg, CircleShape))
            Txt("رنگ انتخاب‌شده: " + "#%06X".format(k.color and 0xFFFFFF), style = ZType.mono, color = KFg, modifier = Modifier.weight(1f))
            KButton("فریم جدید از پلی‌هد", { grab() }, Modifier.height(40.dp).weight(1.3f))
        }
        KLabel("روی یک جای صاف و بدون سایه‌ی پرده بزن؛ می‌شود انگشت را کشید.")
        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            item(key = "g") { KChip("سبز استاندارد", k.color == 0xFF00B140.toInt()) { setKey(k.copy(on = true, color = 0xFF00B140.toInt())) } }
            item(key = "b") { KChip("آبی استاندارد", k.color == 0xFF0047BB.toInt()) { setKey(k.copy(on = true, color = 0xFF0047BB.toInt())) } }
        }

        // ---- 2. sliders ----
        KLabel("۲. تنظیم حذف رنگ", strong = true)
        KSlider("تلرانس رنگ (چقدر از رنگ‌های نزدیک حذف شود)", k.tol) { setKey(k.copy(on = true, tol = it)) }
        KSlider("نرمی لبه", k.soft) { setKey(k.copy(on = true, soft = it)) }
        KSlider("حذف انعکاس رنگ پرده روی سوژه (Spill)", k.spill) { setKey(k.copy(on = true, spill = it)) }
        }

        // ---- 3. result ----
        KLabel("۳. نتیجه روی همین فریم", strong = true)
        val st = still
        if (st == null) {
            KLabel(if (k.on) "…" else "برای دیدن نتیجه، کروما را روشن کن یا رنگی انتخاب کن.")
        } else {
            Image(
                st, null,
                Modifier.fillMaxWidth().aspectRatio(st.width.toFloat() / st.height).clip(RoundedCornerShape(10.dp)).border(1.dp, KLine, RoundedCornerShape(10.dp)),
                contentScale = ContentScale.FillBounds
            )
        }
        KLabel(
            if (k.ai == 1) "پیش‌نمایش زنده روی ویدیو در حالت هوش مصنوعی هنوز نیست؛ نتیجه را همین‌جا و در خروجی می‌بینی. خروجی با هوش مصنوعی کندتر است."
            else if (KeyPreviewFx.supported) "پیش‌نمایش زنده روی خود ویدیوی بالا هم دیده می‌شود."
            else "پیش‌نمایش زنده‌ی روی ویدیو به اندروید ۱۳ یا بالاتر نیاز دارد؛ در این گوشی نتیجه را همین‌جا و در خروجی می‌بینی."
        )

        // ---- 4. background ----
        KLabel("۴. پس‌زمینه پشت سوژه", strong = true)
        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            item(key = "m0") { KChip("شفاف", k.bgMode == 0) { setKey(k.copy(on = true, bgMode = 0)) } }
            item(key = "m1") { KChip("رنگ ساده", k.bgMode == 1) { setKey(k.copy(on = true, bgMode = 1)) } }
            item(key = "m2") {
                KChip("عکس", k.bgMode == 2) {
                    if (k.bgFile.isEmpty()) onPickBackground() else setKey(k.copy(on = true, bgMode = 2))
                }
            }
        }
        if (k.bgMode == 1) {
            val cols = listOf(0xFF000000, 0xFFFFFFFF, 0xFF808080, 0xFF00B140, 0xFF0047BB, 0xFFD32F2F).map { it.toInt() }
            LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                items(cols, key = { it }) { c -> KSwatch(Color(c), k.bgColor == c) { setKey(k.copy(on = true, bgColor = c)) } }
            }
        }
        if (k.bgMode == 2 || k.bgFile.isNotEmpty()) {
            KButton(if (k.bgFile.isEmpty()) "انتخاب عکس پس‌زمینه" else "تعویض عکس پس‌زمینه", onPickBackground, Modifier.fillMaxWidth())
        }
        KLabel("ویدیوی MP4 شفافیت ندارد؛ پس در خروجی بخش «شفاف» با مشکی پر می‌شود. برای دیدن سوژه روی ویدیو/عکس دیگر، عکس پس‌زمینه را انتخاب کن.")

        if (k != KeyLook()) {
            KButton("حذف کروما از این کلیپ", { setKey(KeyLook()) }, Modifier.fillMaxWidth())
        }
        KLabel("کروما فقط روی همین کلیپ اعمال می‌شود. برای کلیپ‌های دیگر، پلی‌هد را ببر روی آن‌ها.")
    }
}
