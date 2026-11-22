package app.zircut.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** Single/multi line text field in the design-system look (used for subtitle fixes, settings). */
@Composable
fun ZField(
    value: String,
    onChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    label: String? = null,
    singleLine: Boolean = false,
    ltr: Boolean = false
) {
    var focused by remember { mutableStateOf(false) }
    val shape = RoundedCornerShape(Z.RSm)
    Column(modifier) {
        if (label != null) {
            Txt(label, style = ZType.label, color = Z.TextSec)
            Spacer(Modifier.size(Z.S1))
        }
        Box(
            Modifier.fillMaxWidth().clip(shape).background(Z.Surface2)
                .border(Z.Hair, if (focused) Z.Accent else Z.Border, shape)
                .padding(horizontal = Z.S3, vertical = Z.S3)
        ) {
            BasicTextField(
                value = value,
                onValueChange = onChange,
                singleLine = singleLine,
                textStyle = ZType.body.copy(color = Z.Text, textDirection = if (ltr) TextDirection.Ltr else TextDirection.Content),
                cursorBrush = SolidColor(Z.Accent),
                modifier = Modifier.fillMaxWidth().onFocusChanged { focused = it.isFocused }
            )
        }
    }
}

/** Icon + title + optional description + switch. The whole row toggles. */
@Composable
fun ZToggleRow(
    icon: ZI,
    title: String,
    sub: String?,
    checked: Boolean,
    modifier: Modifier = Modifier,
    onChange: (Boolean) -> Unit
) {
    Row(
        modifier.fillMaxWidth().clip(RoundedCornerShape(Z.RSm)).press(true) { onChange(!checked) }.padding(vertical = Z.S2),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            Modifier.size(36.dp).clip(RoundedCornerShape(Z.RSm)).background(if (checked) Z.AccentSoft else Z.Surface3),
            contentAlignment = Alignment.Center
        ) { ZIcon(icon, tint = if (checked) Z.Accent else Z.TextSec, size = Z.IconSm) }
        Spacer(Modifier.width(Z.S3))
        Column(Modifier.weight(1f)) {
            Txt(title, style = ZType.bodyStrong, maxLines = 1)
            if (sub != null) Txt(sub, style = ZType.caption, color = Z.TextSec, maxLines = 2)
        }
        Spacer(Modifier.width(Z.S3))
        ZSwitch(checked, null)
    }
}

/** Small read-only value box: label on top, value below. */
@Composable
fun ZStat(label: String, value: String, modifier: Modifier = Modifier, highlight: Boolean = false) {
    val shape = RoundedCornerShape(Z.RSm)
    Column(
        modifier.clip(shape).background(if (highlight) Z.AccentSoft else Z.Surface2)
            .border(Z.Hair, if (highlight) Z.AccentLine else Z.Border, shape)
            .padding(horizontal = Z.S3, vertical = Z.S2)
    ) {
        Txt(label, style = ZType.caption, color = Z.TextSec, maxLines = 1)
        Txt(value, style = ZType.mono.copy(fontSize = 15.sp), color = if (highlight) Z.Accent else Z.Text)
    }
}

/** Search box with a magnifier, placeholder and a clear button. */
@Composable
fun ZSearchField(value: String, onChange: (String) -> Unit, placeholder: String, modifier: Modifier = Modifier) {
    var focused by remember { mutableStateOf(false) }
    val shape = RoundedCornerShape(Z.RSm)
    Row(
        modifier.fillMaxWidth().height(Z.ControlH).clip(shape).background(Z.Surface2)
            .border(Z.Hair, if (focused) Z.Accent else Z.Border, shape).padding(horizontal = Z.S3),
        verticalAlignment = Alignment.CenterVertically
    ) {
        ZIcon(ZI.Search, tint = Z.TextTer, size = Z.IconSm)
        Spacer(Modifier.width(Z.S2))
        Box(Modifier.weight(1f)) {
            if (value.isEmpty()) Txt(placeholder, style = ZType.body, color = Z.TextTer, maxLines = 1)
            BasicTextField(
                value = value,
                onValueChange = onChange,
                singleLine = true,
                textStyle = ZType.body.copy(color = Z.Text),
                cursorBrush = SolidColor(Z.Accent),
                modifier = Modifier.fillMaxWidth().onFocusChanged { focused = it.isFocused }
            )
        }
        if (value.isNotEmpty()) ZIconButton(ZI.Close, { onChange("") }, tint = Z.TextSec, box = 32.dp, size = 16.dp)
    }
}
