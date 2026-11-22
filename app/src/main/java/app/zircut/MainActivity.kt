package app.zircut

import android.graphics.Color
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.media3.common.util.UnstableApi
import app.zircut.i18n.Lang
import app.zircut.ui.LanguageScreen
import app.zircut.ui.ZirCutApp
import app.zircut.ui.ZirCutTheme

@UnstableApi
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Always light status/navigation icons: the whole UI is dark, whatever the phone's theme is.
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(Color.TRANSPARENT)
        )
        Lang.init(applicationContext)
        setContent {
            ZirCutTheme {
                // First run: pick the app language before anything else.
                if (Lang.chosen) ZirCutApp() else LanguageScreen()
            }
        }
    }
}
