@file:OptIn(ExperimentalMaterial3Api::class)

package app.zircut.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import app.zircut.core.Options
import app.zircut.project.ProjectMeta

/** "پروژه جدید": options for the new project, then the video picker. */
@Composable
fun NewProjectSheet(
    options: Options,
    onOptions: (Options) -> Unit,
    onPick: () -> Unit,
    onDismiss: () -> Unit
) {
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
                        Txt("پروژه جدید", style = ZType.title)
                        Txt("تنظیمات اولیه رو انتخاب کن، بعد ویدیو رو وارد کن.", style = ZType.caption, color = Z.TextSec)
                    }
                    ZIconButton(ZI.Close, onDismiss, tint = Z.TextSec)
                }
                ZSectionLabel("ویرایش")
                Column { OptionToggles(options, onOptions) }
                ZButton("انتخاب ویدیو", onPick, Modifier.fillMaxWidth(), icon = ZI.Film)
            }
        }
    }
}

/** Rename / duplicate / delete for one project. */
@Composable
fun ProjectMenuSheet(
    meta: ProjectMeta,
    onRename: () -> Unit,
    onDuplicate: () -> Unit,
    onDelete: () -> Unit,
    onDismiss: () -> Unit
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = Z.Surface,
        shape = RoundedCornerShape(topStart = Z.RLg, topEnd = Z.RLg),
        dragHandle = { ZSheetHandle() }
    ) {
        CompositionLocalProvider(LocalLayoutDirection provides app.zircut.i18n.Lang.dir) {
            Column(Modifier.fillMaxWidth().padding(horizontal = Z.S5).padding(bottom = Z.S6), verticalArrangement = Arrangement.spacedBy(Z.S1)) {
                Txt(app.zircut.i18n.Lang.raw(meta.name), style = ZType.title, maxLines = 1)
                Txt(ratioLabel(meta.width, meta.height) + "  ·  " + faTime(meta.durationMs), style = ZType.caption, color = Z.TextSec)
                Spacer(Modifier.size(Z.S2))
                MenuRow(ZI.Edit, "تغییر نام", Z.Text, onRename)
                MenuRow(ZI.Copy, "ساخت نسخه‌ی کپی", Z.Text, onDuplicate)
                MenuRow(ZI.Trash, "حذف پروژه", Z.Danger, onDelete)
            }
        }
    }
}

@Composable
private fun MenuRow(icon: ZI, text: String, color: Color, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(Z.RSm)).press(true, onClick).padding(vertical = Z.S3, horizontal = Z.S2),
        verticalAlignment = Alignment.CenterVertically
    ) {
        ZIcon(icon, tint = color, size = Z.IconMd)
        Spacer(Modifier.width(Z.S3))
        Txt(text, style = ZType.bodyStrong, color = color)
    }
}

@Composable
fun RenameDialog(initial: String, onConfirm: (String) -> Unit, onDismiss: () -> Unit) {
    var text by remember { mutableStateOf(initial) }
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Z.Surface2,
        shape = RoundedCornerShape(Z.RMd),
        title = { Txt("نام پروژه", style = ZType.heading) },
        text = { ZField(text, { text = it }, singleLine = true) },
        confirmButton = { ZButton("تأیید", { onConfirm(text) }, compact = true, enabled = text.isNotBlank()) },
        dismissButton = { ZButton("لغو", onDismiss, kind = ZBtn.Ghost, compact = true) }
    )
}

@Composable
fun DeleteProjectDialog(name: String, onConfirm: () -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Z.Surface2,
        shape = RoundedCornerShape(Z.RMd),
        title = { Txt("حذف «$name»؟", style = ZType.heading) },
        text = {
            Txt(
                "پروژه و نسخه‌ی ویدیوی داخل برنامه پاک می‌شه. ویدیوی اصلی توی گالری دست نمی‌خوره.",
                style = ZType.body, color = Z.TextSec
            )
        },
        confirmButton = { ZButton("حذف", onConfirm, kind = ZBtn.Danger, compact = true) },
        dismissButton = { ZButton("لغو", onDismiss, kind = ZBtn.Ghost, compact = true) }
    )
}
