package app.zircut.ui

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.media.MediaMetadataRetriever
import android.net.Uri
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import app.zircut.Recent
import app.zircut.UiState
import app.zircut.project.ProjectMeta
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@Composable
fun HomeScreen(
    ui: UiState,
    modifier: Modifier,
    thumbPath: (String) -> String,
    onNew: () -> Unit,
    onSettings: () -> Unit,
    onOpenProject: (ProjectMeta) -> Unit,
    onProjectMenu: (ProjectMeta) -> Unit,
    onOpen: (Recent) -> Unit,
    onDelete: (Recent) -> Unit
) {
    var query by remember { mutableStateOf("") }
    val projects = ui.projects
    val shown = if (query.isBlank()) projects else projects.filter { it.name.contains(query.trim(), ignoreCase = true) }
    val latest = projects.firstOrNull()

    LazyColumn(
        modifier = modifier,
        contentPadding = PaddingValues(horizontal = Z.S4, vertical = Z.S3),
        verticalArrangement = Arrangement.spacedBy(Z.S3)
    ) {
        item {
            Row(
                Modifier.fillMaxWidth().padding(vertical = Z.S2),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    Modifier.size(46.dp).clip(RoundedCornerShape(14.dp)).background(Z.Surface2)
                        .border(Z.Hair, Z.Border, RoundedCornerShape(14.dp)),
                    contentAlignment = Alignment.Center
                ) { ZMark(34.dp) }
                Spacer(Modifier.size(Z.S3))
                Column(Modifier.weight(1f)) {
                    Txt("ZirCut", style = ZType.title)
                    Txt("استودیوی تدوین ویدیو", style = ZType.caption, color = Z.TextSec)
                }
                ZIconButton(
                    ZI.Tune, onSettings, bg = Z.Surface2, box = 42.dp, size = Z.IconMd,
                    modifier = Modifier.border(Z.Hair, Z.Border, RoundedCornerShape(Z.RSm))
                )
            }
        }

        item {
            Column(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(Z.RLg)).background(Z.Surface)
                    .border(Z.Hair, Z.Border, RoundedCornerShape(Z.RLg)).padding(Z.S4),
                verticalArrangement = Arrangement.spacedBy(Z.S3)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Txt("از ایده تا ویدیو", style = ZType.title)
                        Txt("پروژه‌ات را بساز و دقیق ویرایش کن", style = ZType.caption, color = Z.TextSec)
                    }
                    Box(
                        Modifier.size(42.dp).clip(RoundedCornerShape(13.dp)).background(Z.AccentSoft),
                        contentAlignment = Alignment.Center
                    ) { ZIcon(ZI.Film, tint = Z.Accent, size = Z.IconLg) }
                }
                Row(
                    Modifier.fillMaxWidth().height(54.dp).clip(RoundedCornerShape(Z.RSm))
                        .background(Z.Accent).press(true, onNew).padding(horizontal = Z.S4),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    ZIcon(ZI.Plus, tint = Z.OnAccent, size = 22.dp)
                    Spacer(Modifier.width(Z.S2))
                    Txt("ساخت پروژه جدید", style = ZType.button, color = Z.OnAccent, modifier = Modifier.weight(1f))
                    ZIcon(ZI.Arrow, tint = Z.OnAccent, size = 18.dp)
                }
            }
        }

        if (latest != null && query.isBlank()) {
            item {
                Row(
                    Modifier.fillMaxWidth().padding(top = Z.S1),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    ZSectionLabel("ادامه ویرایش", Modifier.weight(1f))
                    Txt("آخرین پروژه", style = ZType.caption, color = Z.TextTer)
                }
            }
            item {
                ZCard(
                    Modifier.fillMaxWidth(),
                    padding = PaddingValues(Z.S2),
                    onClick = { onOpenProject(latest) }
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(Z.S3)
                    ) {
                        ProjectThumb(
                            thumbPath(latest.id),
                            Modifier.size(width = 66.dp, height = 76.dp)
                                .clip(RoundedCornerShape(Z.RSm))
                        )
                        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                            Txt(app.zircut.i18n.Lang.raw(latest.name), style = ZType.bodyStrong, maxLines = 1)
                            Txt(meta(latest), style = ZType.caption, color = Z.TextSec, maxLines = 1)
                            Txt("ویرایش " + ago(latest.updatedAt), style = ZType.micro, color = Z.TextTer, maxLines = 1)
                        }
                        ZIconButton(
                            ZI.Chevron, { onOpenProject(latest) },
                            bg = Z.AccentSoft, tint = Z.Accent, box = 38.dp, size = 19.dp
                        )
                    }
                }
            }
        }

        item {
            Row(
                Modifier.fillMaxWidth().padding(top = Z.S2),
                verticalAlignment = Alignment.CenterVertically
            ) {
                ZSectionLabel("کتابخانه پروژه‌ها", Modifier.weight(1f))
                if (projects.isNotEmpty()) {
                    Txt(fa(projects.size) + " پروژه", style = ZType.caption, color = Z.TextTer)
                }
            }
        }
        if (projects.size >= 2) {
            item { ZSearchField(query, { query = it }, "جستجو در پروژه‌ها") }
        }
        if (projects.isEmpty()) {
            item {
                ZCard(
                    Modifier.fillMaxWidth(),
                    padding = PaddingValues(vertical = Z.S6, horizontal = Z.S4)
                ) {
                    Column(
                        Modifier.fillMaxWidth(),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(Z.S2)
                    ) {
                        Box(
                            Modifier.size(48.dp).clip(RoundedCornerShape(14.dp)).background(Z.Surface3),
                            contentAlignment = Alignment.Center
                        ) { ZIcon(ZI.Film, tint = Z.TextSec, size = Z.IconLg) }
                        Txt("هنوز پروژه‌ای نداری", style = ZType.labelStrong)
                        Txt(
                            "با ساخت پروژه جدید، اولین ویدیوت را وارد کن.",
                            style = ZType.caption, color = Z.TextSec
                        )
                    }
                }
            }
        } else if (shown.isEmpty()) {
            item { Txt("پروژه‌ای با این نام پیدا نشد.", style = ZType.caption, color = Z.TextSec) }
        } else {
            items(shown.chunked(2), key = { it.first().id }) { row ->
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(Z.S3)) {
                    row.forEach { p ->
                        ProjectCard(p, thumbPath(p.id), Modifier.weight(1f), { onOpenProject(p) }, { onProjectMenu(p) })
                    }
                    if (row.size == 1) Spacer(Modifier.weight(1f))
                }
            }
        }

        if (ui.recent.isNotEmpty()) {
            item { ZSectionLabel("خروجی‌های اخیر", Modifier.padding(top = Z.S2)) }
            items(ui.recent, key = { it.uri }) { r ->
                ZCard(Modifier.fillMaxWidth(), padding = PaddingValues(Z.S2), onClick = { onOpen(r) }) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Z.S3)) {
                        Thumb(r.uri, Modifier.size(width = 46.dp, height = 58.dp).clip(RoundedCornerShape(Z.RSm)))
                        Column(Modifier.weight(1f)) {
                            Txt(app.zircut.i18n.Lang.raw(r.title), style = ZType.bodyStrong, maxLines = 1)
                            Txt(ago(r.time), style = ZType.caption, color = Z.TextSec)
                        }
                        ZIconButton(ZI.Trash, { onDelete(r) }, tint = Z.Danger, box = 38.dp, size = Z.IconSm)
                    }
                }
            }
        }
        item { Spacer(Modifier.height(Z.S6)) }
    }
}

@Composable
private fun ProjectCard(p: ProjectMeta, thumb: String, modifier: Modifier, onOpen: () -> Unit, onMenu: () -> Unit) {
    val shape = RoundedCornerShape(Z.RMd)
    Column(
        modifier.clip(shape).background(Z.Surface)
            .border(Z.Hair, Z.Border, shape).press(true, onOpen),
        verticalArrangement = Arrangement.spacedBy(Z.S2)
    ) {
        Box(Modifier.fillMaxWidth().aspectRatio(0.88f)) {
            ProjectThumb(thumb, Modifier.matchParentSize())
            Box(
                Modifier.align(Alignment.BottomStart).padding(Z.S2)
                    .clip(RoundedCornerShape(6.dp)).background(Color(0xCC111113))
                    .padding(horizontal = 8.dp, vertical = 4.dp)
            ) {
                Txt(faTime(p.durationMs), style = ZType.mono, color = Color.White)
            }
            ZIconButton(
                ZI.More, onMenu, tint = Color.White, bg = Color(0x99000000),
                modifier = Modifier.align(Alignment.TopEnd).padding(Z.S2),
                box = 32.dp, size = 18.dp, round = true
            )
        }
        Column(
            Modifier.fillMaxWidth().padding(start = Z.S3, end = Z.S3, bottom = Z.S3),
            verticalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            Txt(app.zircut.i18n.Lang.raw(p.name), style = ZType.labelStrong, maxLines = 1)
            Txt(ratioLabel(p.width, p.height), style = ZType.caption, color = Z.TextSec, maxLines = 1)
            Txt(ago(p.updatedAt), style = ZType.micro, color = Z.TextTer, maxLines = 1)
        }
    }
}

@Composable
fun ProjectThumb(path: String, modifier: Modifier) {
    val bmp by produceState<Bitmap?>(null, path) {
        value = withContext(Dispatchers.IO) {
            try { BitmapFactory.decodeFile(path) } catch (e: Exception) { null }
        }
    }
    Box(modifier.background(Z.Surface3), contentAlignment = Alignment.Center) {
        val b = bmp
        if (b != null) {
            Image(bitmap = b.asImageBitmap(), contentDescription = null, modifier = Modifier.matchParentSize(), contentScale = ContentScale.Crop)
        } else {
            ZIcon(ZI.Film, tint = Z.TextTer, size = Z.IconMd)
        }
    }
}

@Composable
private fun Thumb(uri: String, modifier: Modifier) {
    val ctx = LocalContext.current
    val bmp by produceState<Bitmap?>(null, uri) {
        value = withContext(Dispatchers.IO) {
            try {
                val r = MediaMetadataRetriever()
                r.setDataSource(ctx, Uri.parse(uri))
                val b = r.getFrameAtTime(500_000L)
                r.release()
                b
            } catch (e: Exception) {
                null
            }
        }
    }
    Box(modifier.background(Z.Surface3), contentAlignment = Alignment.Center) {
        val b = bmp
        if (b != null) {
            Image(bitmap = b.asImageBitmap(), contentDescription = null, modifier = Modifier.matchParentSize(), contentScale = ContentScale.Crop)
        } else {
            ZIcon(ZI.Film, tint = Z.TextTer, size = Z.IconMd)
        }
    }
}

fun ratioLabel(w: Int, h: Int): String {
    if (w <= 0 || h <= 0) return ""
    val r = w.toFloat() / h
    val known = listOf(
        "9:16" to 9f / 16f, "16:9" to 16f / 9f, "1:1" to 1f, "4:5" to 0.8f,
        "3:4" to 0.75f, "4:3" to 4f / 3f, "9:20" to 0.45f, "20:9" to 20f / 9f
    )
    val best = known.minByOrNull { Math.abs(it.second - r) }
    return if (best != null && Math.abs(best.second - r) < 0.02f) fa(best.first) else fa("$w×$h")
}

private fun meta(p: ProjectMeta): String = ratioLabel(p.width, p.height) + "  ·  " + faTime(p.durationMs)

fun ago(t: Long): String {
    val m = (System.currentTimeMillis() - t) / 60000L
    return when {
        m < 1L -> "همین الان"
        m < 60L -> fa("$m") + " دقیقه پیش"
        m < 1440L -> fa("${m / 60L}") + " ساعت پیش"
        else -> fa("${m / 1440L}") + " روز پیش"
    }
}
