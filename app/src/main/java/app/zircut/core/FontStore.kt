package app.zircut.core

import android.content.Context
import android.graphics.Paint
import android.graphics.Typeface
import android.net.Uri
import java.io.File

class FontStore(private val ctx: Context) {
    private val dir = File(ctx.filesDir, "fonts").apply { mkdirs() }

    data class Font(
        val id: String,
        val name: String,
        val typeface: Typeface,
        val persianOk: Boolean,
        val bundled: Boolean
    )

    private val VAR_WEIGHTS = mapOf("Cairo" to 900, "Changa" to 800, "Estedad" to 800)

    private fun supportsPersian(tf: Typeface): Boolean =
        Paint().apply { typeface = tf }.hasGlyph("ب")

    fun list(): List<Font> {
        val res = ArrayList<Font>()
        val assetNames = try {
            ctx.assets.list("fonts")?.filter { it.endsWith(".ttf", true) || it.endsWith(".otf", true) } ?: emptyList()
        } catch (_: Exception) {
            emptyList()
        }
        for (n in assetNames) {
            try {
                val base = n.substringBeforeLast('.')
                val vw = VAR_WEIGHTS[base]
                val tf = (if (vw != null) Typeface.Builder(ctx.assets, "fonts/$n").setFontVariationSettings("'wght' $vw").build() else null)
                    ?: Typeface.createFromAsset(ctx.assets, "fonts/$n")
                res.add(Font("asset:$n", n.substringBeforeLast('.'), tf, supportsPersian(tf), true))
            } catch (_: Exception) {
            }
        }
        dir.listFiles()?.sortedBy { it.name }?.forEach { f ->
            try {
                val tf = Typeface.createFromFile(f)
                res.add(Font("file:${f.name}", f.nameWithoutExtension, tf, supportsPersian(tf), false))
            } catch (_: Exception) {
            }
        }
        res.add(Font("system", "فونت سیستم", Typeface.DEFAULT_BOLD, true, true))
        return res
    }

    /** Copies a user-picked .ttf/.otf into the app's private storage. Returns the new font id. */
    fun import(uri: Uri, displayName: String?): String? {
        val raw = (displayName ?: "font_${System.currentTimeMillis()}.ttf")
        val name = raw.replace(Regex("[^A-Za-z0-9._-]"), "_").let {
            if (it.endsWith(".ttf", true) || it.endsWith(".otf", true)) it else "$it.ttf"
        }
        val dest = File(dir, name)
        ctx.contentResolver.openInputStream(uri)?.use { ins ->
            dest.outputStream().use { o -> ins.copyTo(o) }
        } ?: return null
        return try {
            Typeface.createFromFile(dest)
            "file:$name"
        } catch (_: Exception) {
            dest.delete()
            null
        }
    }
}
