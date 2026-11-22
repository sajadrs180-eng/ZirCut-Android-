@file:OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)

package app.zircut.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import app.zircut.i18n.Lang

/**
 * First-run screen: shown before the app, until a language is confirmed. The list only holds languages that
 * have a translation pack. Tapping a language switches the whole UI at once (preview); "Continue" saves it.
 */
@Composable
fun LanguageScreen() {
    val ctx = LocalContext.current
    val cur = Lang.current
    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
        Box(Modifier.fillMaxSize().background(Z.Bg)) {
            Column(
                Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding().padding(horizontal = Z.S5),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Spacer(Modifier.height(Z.S8))
                ZMark(56.dp)
                Spacer(Modifier.height(Z.S4))
                Txt(Lang.raw("Language  ·  زبان"), style = ZType.title)
                Spacer(Modifier.height(Z.S5))
                Column(
                    Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(Z.S2)
                ) {
                    Lang.available.forEach { l ->
                        val sel = l.code == cur.code
                        ZCard(
                            Modifier.fillMaxWidth(), selected = sel,
                            padding = PaddingValues(horizontal = Z.S4, vertical = Z.S3),
                            onClick = { Lang.preview(ctx, l.code) }
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                ZRadioDot(sel)
                                Spacer(Modifier.width(Z.S3))
                                Txt(Lang.raw(l.native), style = ZType.heading, modifier = Modifier.weight(1f))
                            }
                        }
                    }
                }
                Spacer(Modifier.height(Z.S4))
                ZButton(Lang.raw(cur.cont), { Lang.confirm(ctx) }, Modifier.fillMaxWidth())
                Spacer(Modifier.height(Z.S5))
            }
        }
    }
}

/** Language chips for the settings sheet: choosing one switches and saves at once. */
@Composable
fun LanguageChoices(modifier: Modifier = Modifier) {
    val ctx = LocalContext.current
    val cur = Lang.current
    FlowRow(
        modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(Z.S2),
        verticalArrangement = Arrangement.spacedBy(Z.S2)
    ) {
        Lang.available.forEach { l ->
            ZChip(Lang.raw(l.native), l.code == cur.code, { Lang.choose(ctx, l.code) })
        }
    }
}
