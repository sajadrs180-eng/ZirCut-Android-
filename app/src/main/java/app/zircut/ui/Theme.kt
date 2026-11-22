package app.zircut.ui

import android.graphics.Typeface
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Fill
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.zircut.R

// =====================================================================================
//  ZirCut design system
//  Neutral graphite surfaces + ONE accent (electric sky blue). Estedad for all UI text.
// =====================================================================================

object Z {
    // ---- neutrals (cool graphite) ----
    val Bg = Color(0xFF111113)          // app background
    val Surface = Color(0xFF1B1C20)     // panels
    val Surface2 = Color(0xFF25262B)    // controls, fields
    val Surface3 = Color(0xFF303137)    // raised / selected neutral
    val Border = Color(0xFF2E2F35)      // hairlines
    val BorderStrong = Color(0xFF45464E)

    // ---- text ----
    val Text = Color(0xFFF3F4F6)
    val TextSec = Color(0xFFA3A9B4)
    val TextTer = Color(0xFF6C7380)

    // ---- the single accent ----
    val Accent = Color(0xFF2F9BFF)
    val AccentSoft = Color(0x262F9BFF)  // 15%
    val AccentLine = Color(0x662F9BFF)  // 40%
    val OnAccent = Color(0xFF04141D)

    // ---- status (used sparingly) ----
    val Danger = Color(0xFFF2675F)
    val DangerSoft = Color(0x24F2675F)
    val Warn = Color(0xFFF5B544)
    val WarnSoft = Color(0x24F5B544)
    val Ok = Color(0xFF4ADE80)

    // ---- spacing scale (4pt grid) ----
    val S1 = 4.dp
    val S2 = 8.dp
    val S3 = 12.dp
    val S4 = 16.dp
    val S5 = 20.dp
    val S6 = 24.dp
    val S8 = 32.dp

    // ---- radius scale ----
    val RXs = 6.dp     // timeline blocks, tiny chips
    val RSm = 10.dp    // buttons, fields, chips
    val RMd = 12.dp    // cards, tiles
    val RLg = 20.dp    // sheets

    // ---- icon sizes ----
    val IconSm = 18.dp
    val IconMd = 22.dp
    val IconLg = 26.dp

    // ---- control heights ----
    val ControlH = 44.dp
    val ControlHSm = 36.dp
    val Hair = 1.dp
}

// ---------------------------------------------------------------------------------
//  Typography: Estedad variable font, one Typeface per weight (same loader the
//  caption font store already uses, so it is known to work on the device).
// ---------------------------------------------------------------------------------

object ZFont {
    class Set(
        val regular: FontFamily,
        val medium: FontFamily,
        val semibold: FontFamily,
        val bold: FontFamily,
        val extrabold: FontFamily
    )

    /** Estedad at a given weight (100..900), or null if the font cannot be loaded. */
    fun typeface(ctx: android.content.Context, w: Int): Typeface? = try {
        Typeface.Builder(ctx.assets, "fonts/Estedad.ttf")
            .setFontVariationSettings("'wght' $w")
            .build()
    } catch (_: Throwable) {
        null
    }

    private fun build(ctx: android.content.Context, w: Int): FontFamily {
        val tf = typeface(ctx, w)
        return if (tf != null) FontFamily(tf) else FontFamily.Default
    }

    fun load(ctx: android.content.Context) = Set(
        regular = build(ctx, 400),
        medium = build(ctx, 500),
        semibold = build(ctx, 600),
        bold = build(ctx, 700),
        extrabold = build(ctx, 800)
    )

    fun fallback() = Set(FontFamily.Default, FontFamily.Default, FontFamily.Default, FontFamily.Default, FontFamily.Default)
}

class ZTypeScale(f: ZFont.Set) {
    // Persian needs generous line height: ~1.6 for body, ~1.35 for display.
    val display = TextStyle(fontFamily = f.extrabold, fontSize = 30.sp, lineHeight = 42.sp)
    val title = TextStyle(fontFamily = f.bold, fontSize = 20.sp, lineHeight = 30.sp)
    val heading = TextStyle(fontFamily = f.semibold, fontSize = 16.sp, lineHeight = 26.sp)
    val body = TextStyle(fontFamily = f.regular, fontSize = 15.sp, lineHeight = 24.sp)
    val bodyStrong = TextStyle(fontFamily = f.medium, fontSize = 15.sp, lineHeight = 24.sp)
    val label = TextStyle(fontFamily = f.medium, fontSize = 13.sp, lineHeight = 20.sp)
    val labelStrong = TextStyle(fontFamily = f.semibold, fontSize = 13.sp, lineHeight = 20.sp)
    val caption = TextStyle(fontFamily = f.regular, fontSize = 12.sp, lineHeight = 19.sp)
    val micro = TextStyle(fontFamily = f.medium, fontSize = 11.sp, lineHeight = 16.sp)
    val mono = TextStyle(fontFamily = f.medium, fontSize = 12.sp, lineHeight = 16.sp, fontFeatureSettings = "tnum", textDirection = TextDirection.Ltr)
    val button = TextStyle(fontFamily = f.semibold, fontSize = 14.sp, lineHeight = 22.sp)
}

private val LocalZType = staticCompositionLocalOf<ZTypeScale?> { null }
private val FallbackType = ZTypeScale(ZFont.fallback())

val ZType: ZTypeScale
    @Composable get() = LocalZType.current ?: FallbackType

private val Scheme = darkColorScheme(
    primary = Z.Accent,
    onPrimary = Z.OnAccent,
    secondary = Z.Accent,
    onSecondary = Z.OnAccent,
    background = Z.Bg,
    onBackground = Z.Text,
    surface = Z.Surface,
    onSurface = Z.Text,
    surfaceVariant = Z.Surface2,
    onSurfaceVariant = Z.TextSec,
    outline = Z.BorderStrong,
    outlineVariant = Z.Border,
    error = Z.Danger
)

@Composable
fun ZirCutTheme(content: @Composable () -> Unit) {
    val ctx = LocalContext.current
    val scale = remember { ZTypeScale(ZFont.load(ctx)) }
    CompositionLocalProvider(LocalZType provides scale) {
        MaterialTheme(colorScheme = Scheme, content = content)
    }
}

/** Kept for compatibility with the view-model (the theme id is still stored). */
data class BgTheme(val id: String, val nameFa: String, val res: Int)

object Themes {
    val all = listOf(
        BgTheme("violet_blue", "بنفش‌آبی", R.drawable.bg_violet_blue),
        BgTheme("night_blue", "شب آبی", R.drawable.bg_night_blue),
        BgTheme("graphite", "گرافیتی", R.drawable.bg_graphite),
        BgTheme("forest", "جنگل", R.drawable.bg_forest),
        BgTheme("ocean", "اقیانوس", R.drawable.bg_ocean),
        BgTheme("crimson", "قرمز", R.drawable.bg_crimson)
    )

    fun byId(id: String) = all.firstOrNull { it.id == id } ?: all.first()
}

// ---------------------------------------------------------------------------------
//  Persian helpers
// ---------------------------------------------------------------------------------

fun fa(s: String): String {
    if (!app.zircut.i18n.Lang.persianDigits) return s
    val sb = StringBuilder(s.length)
    for (ch in s) sb.append(if (ch in '0'..'9') ('۰' + (ch - '0')) else ch)
    return sb.toString()
}

fun fa(n: Int): String = fa(n.toString())

/** m:ss:ff (ff = frame number inside the second) with Persian digits. */
fun faTimeFrames(ms: Long, fps: Float): String {
    val f = if (fps in 1f..240f) fps else 30f
    val frames = Math.round(ms.coerceAtLeast(0L) * f / 1000f).toLong()
    val per = Math.round(f).coerceAtLeast(1)
    val sec = (frames / per).toInt()
    return fa("%d:%02d:%02d".format(sec / 60, sec % 60, (frames % per).toInt()))
}

/** m:ss with Persian digits. */
fun faTime(ms: Long): String {
    val s = (ms / 1000).toInt().coerceAtLeast(0)
    return fa("%d:%02d".format(s / 60, s % 60))
}

// ---------------------------------------------------------------------------------
//  Text
// ---------------------------------------------------------------------------------

@Composable
fun Txt(
    text: String,
    modifier: Modifier = Modifier,
    style: TextStyle = ZType.body,
    color: Color = Z.Text,
    maxLines: Int = Int.MAX_VALUE,
    align: TextAlign? = null,
    size: TextUnit = TextUnit.Unspecified
) {
    Text(
        text = app.zircut.i18n.Lang.tr(text),
        modifier = modifier,
        style = if (size != TextUnit.Unspecified) style.copy(fontSize = size) else style,
        color = color,
        maxLines = maxLines,
        textAlign = align,
        overflow = if (maxLines == Int.MAX_VALUE) TextOverflow.Clip else TextOverflow.Ellipsis
    )
}

// ---------------------------------------------------------------------------------
//  Press feedback: no ripple, a soft 0.97 scale + dim. Used by every control.
// ---------------------------------------------------------------------------------

fun Modifier.press(enabled: Boolean = true, onClick: () -> Unit): Modifier = composed {
    val src = remember { MutableInteractionSource() }
    val pressed by src.collectIsPressedAsState()
    val s by animateFloatAsState(if (pressed && enabled) 0.97f else 1f, tween(110, easing = FastOutSlowInEasing), label = "press")
    val a by animateFloatAsState(if (!enabled) 0.4f else if (pressed) 0.88f else 1f, tween(110), label = "pressA")
    this
        .graphicsLayer { scaleX = s; scaleY = s; alpha = a }
        .clickable(interactionSource = src, indication = null, enabled = enabled, onClick = onClick)
}

// ---------------------------------------------------------------------------------
//  Icons: one 24-unit grid, 1.8 stroke, round caps. Drawn natively: no emoji, no assets.
// ---------------------------------------------------------------------------------

enum class ZI {
    Plus, Minus, Close, Check, Chevron, Arrow, Play, Pause, SkipPrev, SkipNext, Tune, Export, Share,
    Captions, Palette, Type, Position, Filters, Scissors, Trash, Merge, Wave, Expand, Phone,
    Globe, Info, Download, Home, Film, Spark, Undo, File, Search, More, Edit, Copy, Redo, Layers, Music, Mic
}

@Composable
fun ZIcon(icon: ZI, modifier: Modifier = Modifier, tint: Color = Z.Text, size: Dp = Z.IconMd, rotation: Float = 0f) {
    Canvas(modifier.size(size).rotate(rotation)) {
        val u = this.size.width / 24f
        drawZIcon(icon, tint, u)
    }
}

private fun DrawScope.drawZIcon(icon: ZI, c: Color, u: Float) {
    val sw = 1.8f * u
    val st = Stroke(width = sw, cap = StrokeCap.Round, join = StrokeJoin.Round)
    fun o(x: Float, y: Float) = Offset(x * u, y * u)
    fun line(x1: Float, y1: Float, x2: Float, y2: Float) = drawLine(c, o(x1, y1), o(x2, y2), strokeWidth = sw, cap = StrokeCap.Round)
    fun poly(vararg p: Float, close: Boolean = false, fill: Boolean = false) {
        val path = Path()
        path.moveTo(p[0] * u, p[1] * u)
        var i = 2
        while (i < p.size) { path.lineTo(p[i] * u, p[i + 1] * u); i += 2 }
        if (close) path.close()
        if (fill) drawPath(path, c) else drawPath(path, c, style = st)
    }
    fun ring(cx: Float, cy: Float, r: Float) = drawCircle(c, r * u, o(cx, cy), style = st)
    fun dot(cx: Float, cy: Float, r: Float) = drawCircle(c, r * u, o(cx, cy))
    fun rrect(x: Float, y: Float, w: Float, h: Float, r: Float, fill: Boolean = false) {
        if (fill) drawRoundRect(c, o(x, y), Size(w * u, h * u), CornerRadius(r * u, r * u))
        else drawRoundRect(c, o(x, y), Size(w * u, h * u), CornerRadius(r * u, r * u), style = st)
    }

    when (icon) {
        ZI.Plus -> { line(12f, 5f, 12f, 19f); line(5f, 12f, 19f, 12f) }
        ZI.Minus -> line(5f, 12f, 19f, 12f)
        ZI.Close -> { line(6f, 6f, 18f, 18f); line(18f, 6f, 6f, 18f) }
        ZI.Check -> poly(5f, 12.5f, 10f, 17f, 19f, 7.5f)
        ZI.Chevron -> poly(6f, 9f, 12f, 15f, 18f, 9f)
        ZI.Arrow -> { line(4.5f, 12f, 19.5f, 12f); poly(13.5f, 6f, 19.5f, 12f, 13.5f, 18f) }
        ZI.Play -> poly(8f, 5.5f, 18.5f, 12f, 8f, 18.5f, close = true, fill = true)
        ZI.Pause -> { rrect(6.5f, 5f, 3.8f, 14f, 1.2f, true); rrect(13.7f, 5f, 3.8f, 14f, 1.2f, true) }
        ZI.SkipPrev -> { line(6f, 6f, 6f, 18f); poly(18f, 6f, 9.5f, 12f, 18f, 18f, close = true, fill = true) }
        ZI.SkipNext -> { line(18f, 6f, 18f, 18f); poly(6f, 6f, 14.5f, 12f, 6f, 18f, close = true, fill = true) }
        ZI.Tune -> {
            line(4f, 7f, 20f, 7f); line(4f, 12f, 20f, 12f); line(4f, 17f, 20f, 17f)
            drawCircle(Z.Bg, 2.8f * u, o(15f, 7f)); ring(15f, 7f, 2.3f)
            drawCircle(Z.Bg, 2.8f * u, o(8f, 12f)); ring(8f, 12f, 2.3f)
            drawCircle(Z.Bg, 2.8f * u, o(13f, 17f)); ring(13f, 17f, 2.3f)
        }
        ZI.Export -> { line(12f, 15f, 12f, 4f); poly(7.5f, 8.5f, 12f, 4f, 16.5f, 8.5f); poly(5f, 13f, 5f, 19f, 19f, 19f, 19f, 13f) }
        ZI.Download -> { line(12f, 4f, 12f, 15f); poly(7.5f, 10.5f, 12f, 15f, 16.5f, 10.5f); poly(5f, 13f, 5f, 19f, 19f, 19f, 19f, 13f) }
        ZI.Share -> {
            ring(6.5f, 12f, 2.4f); ring(17.5f, 6.5f, 2.4f); ring(17.5f, 17.5f, 2.4f)
            line(8.7f, 10.9f, 15.3f, 7.6f); line(8.7f, 13.1f, 15.3f, 16.4f)
        }
        ZI.Captions -> {
            rrect(3f, 5f, 18f, 14f, 3.2f)
            line(7f, 12f, 11f, 12f); line(13.5f, 12f, 17f, 12f); line(7f, 15.5f, 9.5f, 15.5f); line(12f, 15.5f, 17f, 15.5f)
        }
        ZI.Palette -> {
            ring(12f, 12f, 8.6f)
            dot(8.4f, 10.4f, 1.3f); dot(12f, 7.8f, 1.3f); dot(15.6f, 10.4f, 1.3f); dot(12.6f, 15f, 1.3f)
        }
        ZI.Type -> { line(5f, 6f, 19f, 6f); line(12f, 6f, 12f, 18f); line(9f, 18f, 15f, 18f) }
        ZI.Position -> { rrect(4f, 3.5f, 16f, 17f, 3f); rrect(7.5f, 14.2f, 9f, 2.6f, 1.3f, true) }
        ZI.Filters -> { ring(9f, 10f, 5.6f); ring(15f, 10f, 5.6f); ring(12f, 15.5f, 5.6f) }
        ZI.Scissors -> {
            ring(6.5f, 17.5f, 2.8f); ring(17.5f, 17.5f, 2.8f)
            line(8.4f, 15.4f, 18.5f, 4.5f); line(15.6f, 15.4f, 5.5f, 4.5f)
        }
        ZI.Trash -> {
            line(4.5f, 7f, 19.5f, 7f); line(9.5f, 4.5f, 14.5f, 4.5f); line(9.5f, 4.5f, 9.5f, 7f); line(14.5f, 4.5f, 14.5f, 7f)
            poly(6.5f, 7f, 7.4f, 19.5f, 16.6f, 19.5f, 17.5f, 7f)
        }
        ZI.Merge -> { poly(6f, 4f, 12f, 10f, 18f, 4f); line(12f, 10f, 12f, 20f); poly(8.5f, 16.5f, 12f, 20f, 15.5f, 16.5f) }
        ZI.Wave -> {
            line(4f, 10f, 4f, 14f); line(8f, 6f, 8f, 18f); line(12f, 3.5f, 12f, 20.5f)
            line(16f, 7.5f, 16f, 16.5f); line(20f, 10f, 20f, 14f)
        }
        ZI.Expand -> {
            poly(4f, 9f, 4f, 4f, 9f, 4f); poly(15f, 4f, 20f, 4f, 20f, 9f)
            poly(20f, 15f, 20f, 20f, 15f, 20f); poly(9f, 20f, 4f, 20f, 4f, 15f)
        }
        ZI.Phone -> { rrect(7f, 2.8f, 10f, 18.4f, 2.6f); line(10.6f, 17.6f, 13.4f, 17.6f) }
        ZI.Globe -> {
            ring(12f, 12f, 8.6f); line(3.4f, 12f, 20.6f, 12f)
            drawOval(c, o(7.9f, 3.4f), Size(8.2f * u, 17.2f * u), style = st)
        }
        ZI.Info -> { ring(12f, 12f, 8.6f); line(12f, 11f, 12f, 16.4f); dot(12f, 7.8f, 1.1f) }
        ZI.Home -> { poly(4f, 11f, 12f, 4f, 20f, 11f); poly(6.5f, 9.5f, 6.5f, 19.5f, 17.5f, 19.5f, 17.5f, 9.5f) }
        ZI.Film -> { rrect(3.5f, 5f, 17f, 14f, 2.6f); poly(10.4f, 9.2f, 14.6f, 12f, 10.4f, 14.8f, close = true, fill = true) }
        ZI.Spark -> poly(12f, 3f, 14f, 10f, 21f, 12f, 14f, 14f, 12f, 21f, 10f, 14f, 3f, 12f, 10f, 10f, close = true)
        ZI.Undo -> { poly(9f, 6f, 4.5f, 10.5f, 9f, 15f); poly(5f, 10.5f, 14f, 10.5f, 19.5f, 13.5f, 19.5f, 18f) }
        ZI.Redo -> { poly(15f, 6f, 19.5f, 10.5f, 15f, 15f); poly(19f, 10.5f, 10f, 10.5f, 4.5f, 13.5f, 4.5f, 18f) }
        ZI.Search -> { ring(10.5f, 10.5f, 6.2f); line(15.2f, 15.2f, 20f, 20f) }
        ZI.More -> { dot(12f, 5f, 1.7f); dot(12f, 12f, 1.7f); dot(12f, 19f, 1.7f) }
        ZI.Edit -> { poly(4f, 20f, 4.8f, 15.5f, 16f, 4.3f, 19.7f, 8f, 8.5f, 19.2f, close = true); line(13.5f, 6.8f, 17.2f, 10.5f) }
        ZI.Copy -> {
            rrect(9f, 9f, 11f, 11f, 2.5f)
            poly(9f, 15f, 6.5f, 15f, 4.5f, 13f, 4.5f, 6.5f, 6.5f, 4.5f, 13f, 4.5f, 15f, 6.5f, 15f, 9f)
        }
        ZI.Layers -> {
            poly(12f, 3.8f, 20.5f, 8.4f, 12f, 13f, 3.5f, 8.4f, close = true)
            poly(3.5f, 12.4f, 12f, 17f, 20.5f, 12.4f)
            poly(3.5f, 16.2f, 12f, 20.8f, 20.5f, 16.2f)
        }
        ZI.File -> { poly(6f, 3.5f, 6f, 20.5f, 18f, 20.5f, 18f, 9f, 12.5f, 3.5f, close = true); poly(12.5f, 3.5f, 12.5f, 9f, 18f, 9f) }
        ZI.Music -> {
            ring(7f, 17.5f, 2.6f); ring(16f, 15.5f, 2.6f)
            line(9.6f, 17.5f, 9.6f, 6f); line(18.6f, 15.5f, 18.6f, 4.5f); line(9.6f, 6f, 18.6f, 4.5f)
        }
        ZI.Mic -> {
            rrect(9f, 3f, 6f, 11f, 3f)
            poly(6f, 11.5f, 7.5f, 15.2f, 12f, 17f, 16.5f, 15.2f, 18f, 11.5f)
            line(12f, 17f, 12f, 21f); line(8.5f, 21f, 15.5f, 21f)
        }
    }
}

/** The ZirCut mark: accent tile with a "Z" cut. */
@Composable
fun ZMark(size: Dp = 36.dp) {
    // the app logo (black Z with scissors on white), same picture as the launcher icon
    Box(Modifier.size(size).clip(RoundedCornerShape(size * 0.28f)).background(Color.White), contentAlignment = Alignment.Center) {
        androidx.compose.foundation.Image(
            androidx.compose.ui.res.painterResource(R.drawable.ic_launcher_foreground), null, Modifier.size(size * 1.4f)
        )
    }
}

// ---------------------------------------------------------------------------------
//  Buttons
// ---------------------------------------------------------------------------------

enum class ZBtn { Primary, Secondary, Ghost, Danger }

@Composable
fun ZButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    kind: ZBtn = ZBtn.Primary,
    icon: ZI? = null,
    enabled: Boolean = true,
    compact: Boolean = false
) {
    val bg: Color
    val fg: Color
    val line: Color
    when (kind) {
        ZBtn.Primary -> { bg = Z.Accent; fg = Z.OnAccent; line = Color.Transparent }
        ZBtn.Secondary -> { bg = Z.Surface2; fg = Z.Text; line = Z.BorderStrong }
        ZBtn.Ghost -> { bg = Color.Transparent; fg = Z.TextSec; line = Color.Transparent }
        ZBtn.Danger -> { bg = Z.DangerSoft; fg = Z.Danger; line = Color.Transparent }
    }
    val shape = RoundedCornerShape(Z.RSm)
    Row(
        modifier
            .height(if (compact) Z.ControlHSm else Z.ControlH)
            .clip(shape)
            .background(bg)
            .border(Z.Hair, line, shape)
            .press(enabled, onClick)
            .padding(horizontal = if (compact) Z.S3 else Z.S4),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center
    ) {
        if (icon != null) {
            ZIcon(icon, tint = fg, size = Z.IconSm)
            Spacer(Modifier.width(Z.S2))
        }
        Txt(text, style = ZType.button, color = fg, maxLines = 1)
    }
}

@Composable
fun ZIconButton(
    icon: ZI,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    tint: Color = Z.Text,
    bg: Color = Color.Transparent,
    box: Dp = 40.dp,
    size: Dp = Z.IconMd,
    rotation: Float = 0f,
    enabled: Boolean = true,
    round: Boolean = false
) {
    val shape = if (round) CircleShape else RoundedCornerShape(Z.RSm)
    Box(
        modifier.size(box).clip(shape).background(bg).press(enabled, onClick),
        contentAlignment = Alignment.Center
    ) { ZIcon(icon, tint = tint, size = size, rotation = rotation) }
}

// ---------------------------------------------------------------------------------
//  Selection controls
// ---------------------------------------------------------------------------------

@Composable
fun ZChip(text: String, selected: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier, trailing: String? = null) {
    val bg by animateColorAsState(if (selected) Z.AccentSoft else Z.Surface2, tween(160), label = "chipBg")
    val line by animateColorAsState(if (selected) Z.AccentLine else Z.Border, tween(160), label = "chipLine")
    val shape = RoundedCornerShape(Z.RSm)
    Row(
        modifier.height(Z.ControlHSm).clip(shape).background(bg).border(Z.Hair, line, shape)
            .press(true, onClick).padding(horizontal = Z.S3),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Z.S1)
    ) {
        Txt(text, style = ZType.label, color = if (selected) Z.Accent else Z.TextSec, maxLines = 1)
        if (trailing != null) Txt(trailing, style = ZType.micro, color = Z.TextTer, maxLines = 1)
    }
}

@Composable
fun ZSegmented(options: List<Pair<String, String>>, selected: String, onSelect: (String) -> Unit, modifier: Modifier = Modifier) {
    val shape = RoundedCornerShape(Z.RSm)
    Row(
        modifier.fillMaxWidth().height(Z.ControlH).clip(shape).background(Z.Surface2).border(Z.Hair, Z.Border, shape).padding(3.dp),
        horizontalArrangement = Arrangement.spacedBy(3.dp)
    ) {
        options.forEach { (id, name) ->
            val sel = id == selected
            val bg by animateColorAsState(if (sel) Z.Surface3 else Color.Transparent, tween(160), label = "segBg")
            Box(
                Modifier.weight(1f).fillMaxHeight().clip(RoundedCornerShape(7.dp)).background(bg)
                    .press(true) { onSelect(id) },
                contentAlignment = Alignment.Center
            ) { Txt(name, style = if (sel) ZType.labelStrong else ZType.label, color = if (sel) Z.Text else Z.TextSec, maxLines = 1) }
        }
    }
}

@Composable
fun ZSwitch(checked: Boolean, onChange: ((Boolean) -> Unit)?, modifier: Modifier = Modifier) {
    val track by animateColorAsState(if (checked) Z.Accent else Z.Surface3, tween(180), label = "swTrack")
    val x by animateFloatAsState(if (checked) 1f else 0f, tween(180, easing = FastOutSlowInEasing), label = "swX")
    val base = modifier.size(width = 40.dp, height = 24.dp).clip(CircleShape).background(track)
    val m = if (onChange != null) base.press(true) { onChange(!checked) } else base
    Canvas(m) {
        val r = 9.dp.toPx()
        val pad = 3.dp.toPx()
        val cx = pad + r + x * (size.width - 2 * pad - 2 * r)
        drawCircle(if (checked) Z.OnAccent else Z.TextSec, r, Offset(cx, size.height / 2f))
    }
}

@Composable
fun ZRadioDot(selected: Boolean) {
    Canvas(Modifier.size(20.dp)) {
        val line = if (selected) Z.Accent else Z.BorderStrong
        drawCircle(line, 9.dp.toPx(), center, style = Stroke(width = 1.6.dp.toPx()))
        if (selected) drawCircle(Z.Accent, 4.5.dp.toPx(), center)
    }
}

// ---------------------------------------------------------------------------------
//  Slider: thin track, accent fill, 18dp knob, value read-out. Always LTR
//  (low -> high, left -> right) like every audio/video tool.
// ---------------------------------------------------------------------------------

@Composable
fun ZSlider(
    label: String,
    value: Float,
    min: Float,
    max: Float,
    onChange: (Float) -> Unit,
    modifier: Modifier = Modifier,
    steps: Int = 0,
    readout: (Float) -> String = { fa("%.0f".format(it)) }
) {
    val cb by rememberUpdatedState(onChange)
    val v = value.coerceIn(min, max)
    Column(modifier.fillMaxWidth()) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Txt(label, style = ZType.label, color = Z.TextSec, modifier = Modifier.weight(1f), maxLines = 1)
            Txt(readout(v), style = ZType.mono, color = Z.Text)
        }
        CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
            Canvas(
                Modifier.fillMaxWidth().height(30.dp)
                    .pointerInput(min, max, steps) {
                        detectTapGestures { o -> cb(snapValue(o.x / size.width, min, max, steps)) }
                    }
                    .pointerInput(min, max, steps) {
                        detectDragGestures { change, _ ->
                            change.consume()
                            cb(snapValue(change.position.x / size.width, min, max, steps))
                        }
                    }
            ) {
                val pad = 9.dp.toPx()
                val w = size.width - 2 * pad
                val cy = size.height / 2f
                val f = if (max > min) (v - min) / (max - min) else 0f
                val kx = pad + f * w
                val th = 3.dp.toPx()
                drawRoundRect(Z.Surface3, Offset(pad, cy - th / 2), Size(w, th), CornerRadius(th, th))
                drawRoundRect(Z.Accent, Offset(pad, cy - th / 2), Size(f * w, th), CornerRadius(th, th))
                if (steps > 0) {
                    for (i in 0..steps + 1) {
                        val tx = pad + w * i / (steps + 1)
                        drawCircle(if (tx <= kx) Z.OnAccent else Z.BorderStrong, 1.2.dp.toPx(), Offset(tx, cy))
                    }
                }
                drawCircle(Color(0x55000000), 11.dp.toPx(), Offset(kx, cy + 1.dp.toPx()))
                drawCircle(Z.Text, 9.dp.toPx(), Offset(kx, cy))
            }
        }
    }
}

private fun snapValue(frac: Float, min: Float, max: Float, steps: Int): Float {
    val f = frac.coerceIn(0f, 1f)
    if (steps <= 0) return min + f * (max - min)
    val n = steps + 1
    return min + (Math.round(f * n).toFloat() / n) * (max - min)
}

// ---------------------------------------------------------------------------------
//  Surfaces
// ---------------------------------------------------------------------------------

@Composable
fun ZCard(
    modifier: Modifier = Modifier,
    selected: Boolean = false,
    padding: PaddingValues = PaddingValues(Z.S4),
    onClick: (() -> Unit)? = null,
    content: @Composable () -> Unit
) {
    val shape = RoundedCornerShape(Z.RMd)
    val line by animateColorAsState(if (selected) Z.AccentLine else Z.Border, tween(160), label = "cardLine")
    val base = modifier.clip(shape).background(if (selected) Color(0xFF12202A) else Z.Surface).border(Z.Hair, line, shape)
    val m = if (onClick != null) base.press(true, onClick) else base
    Column(m.padding(padding)) { content() }
}

@Composable
fun ZSectionLabel(text: String, modifier: Modifier = Modifier) {
    Txt(text, style = ZType.labelStrong, color = Z.TextTer, modifier = modifier.padding(top = Z.S2))
}

@Composable
fun ZHairline(modifier: Modifier = Modifier) {
    Box(modifier.fillMaxWidth().height(Z.Hair).background(Z.Border))
}

@Composable
fun ZSheetHandle() {
    Box(Modifier.fillMaxWidth().padding(vertical = Z.S3), contentAlignment = Alignment.Center) {
        Box(Modifier.size(width = 36.dp, height = 4.dp).clip(CircleShape).background(Z.BorderStrong))
    }
}

@Composable
fun ZProgress(progress: Float?, modifier: Modifier = Modifier) {
    val infinite = rememberInfiniteTransition(label = "ind")
    val t by infinite.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(1300, easing = LinearEasing)),
        label = "indT"
    )
    Canvas(modifier.fillMaxWidth().height(6.dp)) {
        val h = size.height
        drawRoundRect(Z.Surface3, Offset.Zero, size, CornerRadius(h, h))
        if (progress != null) {
            drawRoundRect(Z.Accent, Offset.Zero, Size(size.width * progress.coerceIn(0f, 1f), h), CornerRadius(h, h))
        } else {
            val seg = size.width * 0.32f
            val x = -seg + (size.width + seg) * t
            val l = x.coerceAtLeast(0f)
            val r = (x + seg).coerceAtMost(size.width)
            if (r > l) drawRoundRect(Z.Accent, Offset(l, 0f), Size(r - l, h), CornerRadius(h, h))
        }
    }
}
