@file:OptIn(ExperimentalMaterial3Api::class, androidx.compose.foundation.layout.ExperimentalLayoutApi::class)

package app.zircut.ui

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.view.WindowManager
import android.widget.MediaController
import android.widget.VideoView
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.media3.common.util.UnstableApi
import app.zircut.MainViewModel
import app.zircut.Stage
import app.zircut.core.Styles
import app.zircut.project.ProjectMeta

@UnstableApi
@Composable
fun ZirCutApp(vm: MainViewModel = viewModel()) {
    val ui by vm.ui.collectAsState()
    val keepOn = ui.stage is Stage.Working
    val hostView = androidx.compose.ui.platform.LocalView.current
    DisposableEffect(keepOn) {
        hostView.keepScreenOn = keepOn
        onDispose { hostView.keepScreenOn = false }
    }
    var showSettings by remember { mutableStateOf(false) }
    var showNew by remember { mutableStateOf(false) }
    var menuFor by remember { mutableStateOf<ProjectMeta?>(null) }
    var renameFor by remember { mutableStateOf<ProjectMeta?>(null) }
    var deleteFor by remember { mutableStateOf<ProjectMeta?>(null) }
    val busy = ui.stage is Stage.Working
    BackHandler(enabled = ui.stage is Stage.Done || ui.stage is Stage.Failed) {
        if (ui.info != null && ui.video != null && ui.stage !is Stage.Done) vm.backToReview() else vm.reset()
    }

    val ctx = LocalContext.current
    DisposableEffect(busy) {
        val w = (ctx as? Activity)?.window
        if (busy) w?.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        onDispose { w?.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON) }
    }

    val videoPicker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) vm.pickVideo(uri)
    }
    var insertAt by remember { mutableStateOf(0L) }
    val mediaPicker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) vm.addMedia(uri, insertAt)
    }
    var layerAt by remember { mutableStateOf(0L) }
    var layerTotal by remember { mutableStateOf(0L) }
    val layerPicker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) vm.addImageLayer(uri, layerAt, layerTotal)
    }
    var audioAt by remember { mutableStateOf(0L) }
    var audioTotal by remember { mutableStateOf(0L) }
    val audioPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) vm.importAudio(uri, audioAt, audioTotal)
    }
    var keyBgAt by remember { mutableStateOf(0L) }
    val keyBgPicker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) vm.importKeyBackground(uri, keyBgAt)
    }
    val lutPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) vm.importLut(uri)
    }
    val srtPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) vm.importSrt(uri)
    }
    val fontPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) vm.addFont(uri)
    }

    CompositionLocalProvider(LocalLayoutDirection provides app.zircut.i18n.Lang.dir) {
        Box(Modifier.fillMaxSize().background(Z.Bg)) {
            Column(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding()) {
                if (ui.stage !is Stage.Idle && ui.stage !is Stage.Review) {
                    AppHeader(onSettings = { showSettings = true })
                }

                when (val s = ui.stage) {
                    is Stage.Idle -> HomeScreen(
                        ui = ui,
                        modifier = Modifier.weight(1f),
                        thumbPath = { vm.thumbPath(it) },
                        onNew = { showNew = true },
                        onSettings = { showSettings = true },
                        onOpenProject = { vm.openProject(it.id) },
                        onProjectMenu = { menuFor = it },
                        onOpen = { vm.openRecent(it) },
                        onDelete = { vm.removeRecent(it) }
                    )

                    is Stage.Working -> StageBody {
                        ZCard(Modifier.fillMaxWidth(), padding = PaddingValues(Z.S5)) {
                            Txt(s.label, style = ZType.heading)
                            Spacer(Modifier.height(Z.S4))
                            val pr = s.progress
                            ZProgress(pr)
                            Spacer(Modifier.height(Z.S3))
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Txt(
                                    if (pr != null) pctText(pr) else "در حال پردازش…",
                                    style = if (pr != null) ZType.title else ZType.label,
                                    color = if (pr != null) Z.Text else Z.TextSec,
                                    modifier = Modifier.weight(1f)
                                )
                            }
                            Spacer(Modifier.height(Z.S2))
                            Txt("صفحه رو روشن نگه دار. ممکنه چند دقیقه طول بکشه.", style = ZType.caption, color = Z.TextSec)
                        }
                        ZButton("لغو", { vm.cancel() }, Modifier.fillMaxWidth(), kind = ZBtn.Secondary)
                    }

                    is Stage.Review -> EditorScreen(
                        ui = ui,
                        vm = vm,
                        onSettings = { showSettings = true },
                        onPickFont = { fontPicker.launch(arrayOf("*/*")) },
                        onAddMedia = { pos ->
                            insertAt = pos
                            mediaPicker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageAndVideo))
                        },
                        onAddLayerImage = { pos, total ->
                            layerAt = pos
                            layerTotal = total
                            layerPicker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                        },
                        onAddAudio = { pos, total ->
                            audioAt = pos
                            audioTotal = total
                            audioPicker.launch(arrayOf("audio/*"))
                        },
                        onImportSrt = { srtPicker.launch(arrayOf("*/*")) },
                        onPickLut = { lutPicker.launch(arrayOf("*/*")) },
                        onPickKeyBg = { pos ->
                            keyBgAt = pos
                            keyBgPicker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                        },
                        modifier = Modifier.weight(1f)
                    )

                    is Stage.Done -> StageBody {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(Modifier.size(36.dp).clip(CircleShape).background(Z.Ok), contentAlignment = Alignment.Center) {
                                ZIcon(ZI.Check, tint = Z.OnAccent, size = Z.IconMd)
                            }
                            Spacer(Modifier.width(Z.S3))
                            Column {
                                Txt("ویدیو آماده شد", style = ZType.title)
                                Txt("توی گالری (Movies/ZirCut) ذخیره شد.", style = ZType.caption, color = Z.TextSec)
                            }
                        }
                        if (ui.debug.isNotEmpty()) Txt(ui.debug, style = ZType.mono, color = Z.TextTer)
                        Box(
                            Modifier.fillMaxWidth().clip(RoundedCornerShape(Z.RMd)).background(Color.Black)
                                .border(Z.Hair, Z.Border, RoundedCornerShape(Z.RMd))
                        ) {
                            AndroidView(
                                factory = { c ->
                                    VideoView(c).apply {
                                        val mc = MediaController(c)
                                        mc.setAnchorView(this)
                                        setMediaController(mc)
                                        setVideoURI(s.uri)
                                        setOnPreparedListener { it.isLooping = true; start() }
                                    }
                                },
                                modifier = Modifier.fillMaxWidth().height(420.dp)
                            )
                        }
                        ZButton("اشتراک‌گذاری", { share(ctx, s.uri) }, Modifier.fillMaxWidth(), icon = ZI.Share)
                        if (ui.phrases.isNotEmpty()) {
                            ZButton("ویرایش دوباره", { vm.backToReview() }, Modifier.fillMaxWidth(), kind = ZBtn.Secondary, icon = ZI.Scissors)
                        }
                        ZButton("برگشت به خانه", { vm.reset() }, Modifier.fillMaxWidth(), kind = ZBtn.Ghost, icon = ZI.Home)
                    }

                    is Stage.Failed -> StageBody {
                        ZCard(Modifier.fillMaxWidth(), padding = PaddingValues(Z.S5)) {
                            IconTile(ZI.Info, Z.DangerSoft, Z.Danger)
                            Spacer(Modifier.height(Z.S3))
                            Txt("یه مشکلی پیش اومد", style = ZType.title)
                            val hint = when {
                                s.message.contains("Codec", ignoreCase = true) || s.message.contains("encoder", ignoreCase = true) ->
                                    "گوشی تنظیمات ساخت ویدیو رو قبول نکرد. دوباره امتحان کن؛ برنامه خودش کیفیت رو پایین‌تر می‌آره."
                                s.message.contains("مدل") ->
                                    "مدل هوش مصنوعی روی این گوشی بارگذاری نشد. برنامه را ببند و دوباره امتحان کن."
                                else -> null
                            }
                            if (hint != null) {
                                Spacer(Modifier.height(Z.S2))
                                Txt(hint, style = ZType.body, color = Z.TextSec)
                            }
                            Spacer(Modifier.height(Z.S3))
                            SelectionContainer {
                                Txt(
                                    s.message, style = ZType.mono, color = Z.Danger,
                                    modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(Z.RSm)).background(Z.Surface2).padding(Z.S3)
                                )
                            }
                        }
                        if (ui.info != null && ui.video != null) {
                            ZButton("بازگشت به ادیتور", { vm.backToReview() }, Modifier.fillMaxWidth())
                            ZButton("برگشت به خانه", { vm.reset() }, Modifier.fillMaxWidth(), kind = ZBtn.Ghost)
                        } else {
                            ZButton("برگشت", { vm.reset() }, Modifier.fillMaxWidth())
                        }
                    }
                }
            }

            if (showNew) {
                NewProjectSheet(
                    options = ui.options,
                    onOptions = { vm.setOptions(it) },
                    onPick = {
                        showNew = false
                        videoPicker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.VideoOnly))
                    },
                    onDismiss = { showNew = false }
                )
            }
            menuFor?.let { m ->
                ProjectMenuSheet(
                    meta = m,
                    onRename = { renameFor = m; menuFor = null },
                    onDuplicate = { vm.duplicateProject(m.id); menuFor = null },
                    onDelete = { deleteFor = m; menuFor = null },
                    onDismiss = { menuFor = null }
                )
            }
            renameFor?.let { m ->
                RenameDialog(m.name, { vm.renameProject(m.id, it); renameFor = null }, { renameFor = null })
            }
            deleteFor?.let { m ->
                DeleteProjectDialog(m.name, { vm.deleteProject(m.id); deleteFor = null }, { deleteFor = null })
            }

            if (showSettings) {
                ModalBottomSheet(
                    onDismissRequest = { showSettings = false },
                    sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
                    containerColor = Z.Surface,
                    shape = RoundedCornerShape(topStart = Z.RLg, topEnd = Z.RLg),
                    dragHandle = { ZSheetHandle() }
                ) {
                    CompositionLocalProvider(LocalLayoutDirection provides app.zircut.i18n.Lang.dir) {
                        Column(
                            Modifier.fillMaxWidth().verticalScroll(rememberScrollState())
                                .padding(horizontal = Z.S5).padding(bottom = Z.S8),
                            verticalArrangement = Arrangement.spacedBy(Z.S3)
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Txt("تنظیمات", style = ZType.title, modifier = Modifier.weight(1f))
                                ZIconButton(ZI.Close, { showSettings = false }, tint = Z.TextSec)
                            }

                            ZSectionLabel("زبان برنامه")
                            LanguageChoices()

                        }
                    }
                }
            }
        }
    }
}

// =====================================================================================
//  Shared pieces of the shell
// =====================================================================================

@Composable
private fun AppHeader(onSettings: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().height(60.dp).padding(horizontal = Z.S5),
        verticalAlignment = Alignment.CenterVertically
    ) {
        ZMark(32.dp)
        Spacer(Modifier.width(Z.S3))
        Txt("زیرکات", style = ZType.heading, modifier = Modifier.weight(1f))
        ZIconButton(
            ZI.Tune, onSettings, bg = Z.Surface2, box = 40.dp, size = Z.IconMd,
            modifier = Modifier.border(Z.Hair, Z.Border, RoundedCornerShape(Z.RSm))
        )
    }
}

@Composable
private fun ColumnScope.StageBody(content: @Composable ColumnScope.() -> Unit) {
    Column(
        Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState())
            .padding(horizontal = Z.S5, vertical = Z.S2),
        verticalArrangement = Arrangement.spacedBy(Z.S3)
    ) {
        content()
        Spacer(Modifier.height(Z.S4))
    }
}

@Composable
private fun IconTile(icon: ZI, bg: Color, tint: Color) {
    Box(Modifier.size(44.dp).clip(RoundedCornerShape(Z.RSm)).background(bg), contentAlignment = Alignment.Center) {
        ZIcon(icon, tint = tint, size = Z.IconMd)
    }
}

@Composable
private fun NoticeBox(text: String, fg: Color, bg: Color) {
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(Z.RSm)).background(bg).padding(Z.S3),
        verticalAlignment = Alignment.Top
    ) {
        ZIcon(ZI.Info, tint = fg, size = Z.IconSm, modifier = Modifier.padding(top = 2.dp))
        Spacer(Modifier.width(Z.S2))
        Txt(text, style = ZType.caption, color = fg)
    }
}

private fun pctText(p: Float): String = fa(Math.round(p * 100f)) + "٪"

private fun share(ctx: android.content.Context, uri: Uri) {
    val i = Intent(Intent.ACTION_SEND).apply {
        type = "video/mp4"
        putExtra(Intent.EXTRA_STREAM, uri)
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
    ctx.startActivity(Intent.createChooser(i, "اشتراک‌گذاری"))
}
