package app.zircut.project

import android.content.Context
import android.graphics.Bitmap
import android.media.MediaMetadataRetriever
import app.zircut.core.CapLayout
import app.zircut.core.ClipLook
import app.zircut.core.KeyLook
import app.zircut.core.ColorGrade
import app.zircut.core.ExportSettings
import app.zircut.core.Fx
import app.zircut.core.Options
import app.zircut.core.Phrase
import app.zircut.core.Range
import app.zircut.core.VideoInfo
import app.zircut.core.Word
import app.zircut.timeline.AudioClip
import app.zircut.timeline.Keyframe
import app.zircut.timeline.Layer
import app.zircut.timeline.LayerKeys
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.util.UUID

/** What the library needs to show a project card. Small, so listing is fast. */
data class ProjectMeta(
    val id: String,
    val name: String,
    val createdAt: Long,
    val updatedAt: Long,
    val durationMs: Long,
    val width: Int,
    val height: Int
)

/** Extra media added to a project (second video, photo). Stored next to media.mp4. id = source index used by [app.zircut.timeline.Src]. */
data class ExtraMedia(
    val id: Int,
    val file: String,
    val image: Boolean,
    val durationMs: Long,
    val width: Int,
    val height: Int
)

/** Everything that makes up the user's edit. The source video itself lives next to it as media.mp4. */
data class ProjectData(
    val meta: ProjectMeta,
    val phrases: List<Phrase>,
    val original: List<Phrase>,
    val options: Options,
    val styleId: String,
    val fontId: String,
    val layout: CapLayout,
    val grade: ColorGrade,
    /** manual clip list on the edited timeline; null = automatic cut (silence removal) */
    val segments: List<Range>? = null,
    val extras: List<ExtraMedia> = emptyList(),
    /** text and picture layers over the video (part 4) */
    val layers: List<Layer> = emptyList(),
    /** music / voice-over clips (part 5) */
    val audio: List<AudioClip> = emptyList(),
    /** volume of the video's own sound, 0..1, and whether it is switched off */
    val mainVolume: Float = 1f,
    val mainMuted: Boolean = false,
    /** whole-video visual effects (part 6) */
    val fx: Fx = Fx(),
    /** export sheet choices (part 7) */
    val export: ExportSettings = ExportSettings(),
    /** music is turned down automatically while a voice-over clip plays */
    val duckMusic: Boolean = false
)

/**
 * Persistent, non-destructive project storage.
 *
 * filesDir/projects/<id>/
 *   media.mp4     the source video (never modified)
 *   thumb.jpg     poster frame for the library
 *   meta.json     name, dates, size (small, read for the library list)
 *   project.json  the edit: subtitles, options, style, layout, color grade
 */
class ProjectStore(private val ctx: Context) {
    private val root = File(ctx.filesDir, "projects").apply { mkdirs() }

    fun dir(id: String) = File(root, id)
    fun mediaFile(id: String) = File(dir(id), "media.mp4")
    fun thumbFile(id: String) = File(dir(id), "thumb.jpg")

    // ---------------- create / save / load ----------------

    /** Moves (or copies) the prepared video into a new project folder and makes its poster frame. */
    fun create(src: File, info: VideoInfo, name: String): ProjectMeta {
        val id = UUID.randomUUID().toString().replace("-", "").take(12)
        dir(id).mkdirs()
        val media = mediaFile(id)
        val inCache = src.absolutePath.startsWith(ctx.cacheDir.absolutePath)
        val moved = inCache && src.renameTo(media)
        if (!moved) {
            src.copyTo(media, overwrite = true)
            if (inCache) src.delete()
        }
        makeThumb(media, thumbFile(id), info.durationMs)
        val now = System.currentTimeMillis()
        val meta = ProjectMeta(id, name, now, now, info.durationMs, info.width, info.height)
        writeMeta(meta)
        return meta
    }

    fun save(d: ProjectData) {
        dir(d.meta.id).mkdirs()
        writeMeta(d.meta)
        val o = JSONObject()
        o.put("v", 1)
        o.put("phrases", phrasesJson(d.phrases))
        o.put("original", phrasesJson(d.original))
        o.put("styleId", d.styleId)
        o.put("fontId", d.fontId)
        o.put(
            "options", JSONObject()
                .put("captions", d.options.captions).put("removeSilence", d.options.removeSilence)
                .put("removeFillers", d.options.removeFillers).put("zoom", d.options.zoom)
                .put("vertical", d.options.vertical).put("ratio", d.options.ratio).put("maxGapMs", d.options.maxGapMs)
                .put("language", d.options.language)
        )
        o.put(
            "layout", JSONObject()
                .put("x", d.layout.x.toDouble()).put("y", d.layout.y.toDouble()).put("mw", d.layout.mw.toDouble())
                .put("ls", d.layout.ls.toDouble()).put("lw", d.layout.lw).put("size", d.layout.size.toDouble()).put("an", d.layout.anim)
        )
        o.put(
            "grade", JSONObject()
                .put("lookId", d.grade.lookId).put("strength", d.grade.strength.toDouble())
                .put("brightness", d.grade.brightness.toDouble()).put("contrast", d.grade.contrast.toDouble())
                .put("saturation", d.grade.saturation.toDouble()).put("warmth", d.grade.warmth.toDouble())
                .put("tint", d.grade.tint.toDouble()).put("fade", d.grade.fade.toDouble())
                .put("exposure", d.grade.exposure.toDouble()).put("whites", d.grade.whites.toDouble())
                .put("blacks", d.grade.blacks.toDouble()).put("hue", d.grade.hue.toDouble())
        )
        val ex = JSONArray()
        d.extras.forEach { x ->
            ex.put(
                JSONObject().put("id", x.id).put("file", x.file).put("image", x.image)
                    .put("dur", x.durationMs).put("w", x.width).put("h", x.height)
            )
        }
        o.put("extras", ex)
        o.put("layers", layersJson(d.layers))
        o.put("audio", audioJson(d.audio))
        o.put("mainVol", d.mainVolume.toDouble())
        o.put("mainMute", d.mainMuted)
        o.put("duck", d.duckMusic)
        o.put(
            "export", JSONObject().put("edge", d.export.shortEdge).put("fps", d.export.fps)
                .put("hevc", d.export.hevc).put("mbps", d.export.videoMbps)
        )
        o.put(
            "fx", JSONObject().put("vignette", d.fx.vignette.toDouble()).put("grain", d.fx.grain.toDouble())
                .put("bars", d.fx.bars.toDouble()).put("leak", d.fx.leak.toDouble())
                .put("blur", d.fx.blur.toDouble()).put("sharpen", d.fx.sharpen.toDouble()).put("split", d.fx.split.toDouble())
                .put("glitch", d.fx.glitch.toDouble()).put("noise", d.fx.noise.toDouble()).put("glow", d.fx.glow.toDouble())
                .put("hi", d.fx.highlights.toDouble()).put("sh", d.fx.shadows.toDouble())
                .put("tr", d.fx.trKind).put("trMs", d.fx.trMs)
                .put("rot", d.fx.rot).put("vz", d.fx.vzoom.toDouble()).put("vpx", d.fx.vpanX.toDouble()).put("vpy", d.fx.vpanY.toDouble())
                .put("fh", d.fx.flipH).put("fv", d.fx.flipV).put("fr", d.fx.fine.toDouble())
                .put("lut", d.fx.lutFile).put("lutA", d.fx.lutAmt.toDouble())
                .put("vk", JSONArray().also { arr -> d.fx.vkeys.forEach { k -> arr.put(JSONArray().put(k.tMs).put(k.zoom.toDouble()).put(k.panX.toDouble()).put(k.panY.toDouble()).put(k.rot.toDouble()).put(k.op.toDouble()).put(k.ease)) } })
        )
        val sg = d.segments
        if (sg != null) {
            val a = JSONArray()
            sg.forEach { r ->
                val ja = JSONArray().put(r.startMs).put(r.endMs).put(r.speed.toDouble()).put(r.tr).put(r.trMs)
                val lk = r.look
                if (!lk.isNone) {
                    ja.put(lk.brightness.toDouble()).put(lk.contrast.toDouble()).put(lk.saturation.toDouble())
                        .put(lk.warmth.toDouble()).put(lk.blur.toDouble()).put(lk.sharpen.toDouble())
                    // chroma key (indices 11..18): on, colour, tolerance, softness, spill, background mode, background colour, background file
                    val kk = lk.key
                    ja.put(if (kk.on) 1 else 0).put(kk.color.toLong() and 0xFFFFFFFFL).put(kk.tol.toDouble()).put(kk.soft.toDouble())
                        .put(kk.spill.toDouble()).put(kk.bgMode).put(kk.bgColor.toLong() and 0xFFFFFFFFL).put(kk.bgFile)
                        .put(kk.ai).put(if (kk.aiInvert) 1 else 0).put(kk.aiEdge.toDouble())
                }
                a.put(ja)
            }
            o.put("segments", a)
        }
        val main = File(dir(d.meta.id), "project.json")
        // keep the last good copy: if project.json is ever damaged, load() falls back to it
        try {
            if (main.exists() && main.length() > 2L) main.copyTo(File(dir(d.meta.id), "project.json.bak"), overwrite = true)
        } catch (_: Exception) {
        }
        atomicWrite(main, o.toString())
    }

    fun meta(id: String): ProjectMeta? = try {
        val o = JSONObject(File(dir(id), "meta.json").readText())
        ProjectMeta(
            id, o.getString("name"), o.getLong("created"), o.getLong("updated"),
            o.getLong("duration"), o.getInt("w"), o.getInt("h")
        )
    } catch (e: Exception) {
        null
    }

    /** project.json, or its last good backup when the main file is missing or damaged. */
    fun load(id: String): ProjectData? =
        loadFile(id, File(dir(id), "project.json")) ?: loadFile(id, File(dir(id), "project.json.bak"))

    private fun loadFile(id: String, file: File): ProjectData? = try {
        val meta = meta(id) ?: return null
        val o = JSONObject(file.readText())
        val op = o.getJSONObject("options")
        val lo = o.getJSONObject("layout")
        val gr = o.getJSONObject("grade")
        ProjectData(
            meta = meta,
            phrases = phrasesFrom(o.optJSONArray("phrases")),
            original = phrasesFrom(o.optJSONArray("original")),
            options = Options(
                captions = op.optBoolean("captions", true),
                removeSilence = op.optBoolean("removeSilence", true),
                removeFillers = op.optBoolean("removeFillers", true),
                zoom = op.optBoolean("zoom", true),
                vertical = op.optBoolean("vertical", true),
                ratio = op.optString("ratio", ""),
                maxGapMs = op.optLong("maxGapMs", 500L),
                language = op.optString("language", "fa")
            ),
            styleId = o.optString("styleId", "zarbati"),
            fontId = o.optString("fontId", ""),
            layout = CapLayout(
                x = lo.optDouble("x", 0.5).toFloat(), y = lo.optDouble("y", 0.66).toFloat(),
                mw = lo.optDouble("mw", 0.9).toFloat(), ls = lo.optDouble("ls", 1.5).toFloat(),
                lw = lo.optInt("lw", 4), size = lo.optDouble("size", 1.0).toFloat(),
                anim = lo.optInt("an", 0).coerceIn(0, 6)
            ),
            grade = ColorGrade(
                lookId = gr.optString("lookId", "none"), strength = gr.optDouble("strength", 1.0).toFloat(),
                brightness = gr.optDouble("brightness", 0.0).toFloat(), contrast = gr.optDouble("contrast", 1.0).toFloat(),
                saturation = gr.optDouble("saturation", 1.0).toFloat(), warmth = gr.optDouble("warmth", 0.0).toFloat(),
                tint = gr.optDouble("tint", 0.0).toFloat(), fade = gr.optDouble("fade", 0.0).toFloat(),
                exposure = gr.optDouble("exposure", 0.0).toFloat(), whites = gr.optDouble("whites", 0.0).toFloat(),
                blacks = gr.optDouble("blacks", 0.0).toFloat(), hue = gr.optDouble("hue", 0.0).toFloat()
            ),
            segments = segmentsFrom(o.optJSONArray("segments"), Long.MAX_VALUE),
            extras = extrasFrom(o.optJSONArray("extras")),
            layers = layersFrom(o.optJSONArray("layers")),
            audio = audioFrom(o.optJSONArray("audio")),
            mainVolume = o.optDouble("mainVol", 1.0).toFloat().coerceIn(0f, 1f),
            mainMuted = o.optBoolean("mainMute", false),
            duckMusic = o.optBoolean("duck", false),
            fx = o.optJSONObject("fx")?.let {
                Fx(
                    vignette = it.optDouble("vignette", 0.0).toFloat().coerceIn(0f, 1f), grain = it.optDouble("grain", 0.0).toFloat().coerceIn(0f, 1f),
                    bars = it.optDouble("bars", 0.0).toFloat().coerceIn(0f, 1f), leak = it.optDouble("leak", 0.0).toFloat().coerceIn(0f, 1f),
                    blur = it.optDouble("blur", 0.0).toFloat().coerceIn(0f, 1f), sharpen = it.optDouble("sharpen", 0.0).toFloat().coerceIn(0f, 1f),
                    split = it.optDouble("split", 0.0).toFloat().coerceIn(0f, 1f), glitch = it.optDouble("glitch", 0.0).toFloat().coerceIn(0f, 1f),
                    noise = it.optDouble("noise", 0.0).toFloat().coerceIn(0f, 1f), glow = it.optDouble("glow", 0.0).toFloat().coerceIn(0f, 1f),
                    highlights = it.optDouble("hi", 0.0).toFloat().coerceIn(-1f, 1f), shadows = it.optDouble("sh", 0.0).toFloat().coerceIn(-1f, 1f),
                    trKind = it.optString("tr", "none"), trMs = it.optLong("trMs", 400L).coerceIn(100L, 1500L),
                    rot = ((it.optInt("rot", 0) % 360) + 360) % 360 / 90 * 90, vzoom = it.optDouble("vz", 1.0).toFloat().coerceIn(1f, 4f),
                    vpanX = it.optDouble("vpx", 0.0).toFloat().coerceIn(-1f, 1f), vpanY = it.optDouble("vpy", 0.0).toFloat().coerceIn(-1f, 1f),
                    flipH = it.optBoolean("fh", false), flipV = it.optBoolean("fv", false),
                    fine = it.optDouble("fr", 0.0).toFloat().coerceIn(-45f, 45f),
                    lutFile = it.optString("lut", "").takeIf { n -> Regex("lut_\\d+\\.cube").matches(n) } ?: "",
                    lutAmt = it.optDouble("lutA", 1.0).toFloat().coerceIn(0f, 1f),
                    vkeys = (it.optJSONArray("vk") ?: JSONArray()).let { arr ->
                        (0 until arr.length()).mapNotNull { i ->
                            val a = arr.optJSONArray(i) ?: return@mapNotNull null
                            if (a.length() < 4) null
                            else app.zircut.core.VideoKey(a.getLong(0).coerceAtLeast(0L), a.getDouble(1).toFloat().coerceIn(1f, 4f), a.getDouble(2).toFloat().coerceIn(-1f, 1f), a.getDouble(3).toFloat().coerceIn(-1f, 1f),
                                // keys saved before fix230 keep the turn the project had (no jump)
                                if (a.length() >= 5) a.getDouble(4).toFloat().coerceIn(-45f, 45f) else it.optDouble("fr", 0.0).toFloat().coerceIn(-45f, 45f),
                                if (a.length() >= 6) a.getDouble(5).toFloat().coerceIn(0f, 1f) else 1f,
                                if (a.length() >= 7) a.getInt(6).coerceIn(0, 2) else 0)
                        }.sortedBy { k -> k.tMs }.take(app.zircut.core.VideoKeys.MAX_KEYS)
                    }
                )
            } ?: Fx(),
            export = o.optJSONObject("export")?.let {
                ExportSettings(
                    shortEdge = it.optInt("edge", 0), fps = it.optInt("fps", 0),
                    hevc = it.optBoolean("hevc", false), videoMbps = it.optInt("mbps", 0)
                )
            } ?: ExportSettings()
        )
    } catch (e: Exception) {
        null
    }

    // ---------------- library ----------------

    /** All projects, most recently edited first. Folders without a readable meta.json are skipped. */
    fun list(): List<ProjectMeta> =
        (root.listFiles() ?: emptyArray()).filter { it.isDirectory }
            .mapNotNull { meta(it.name) ?: recoverMeta(it.name) }
            .filter { mediaFile(it.id).exists() }
            .sortedByDescending { it.updatedAt }

    /** meta.json is damaged or missing but the video is still there: rebuild the card from the video itself. */
    private fun recoverMeta(id: String): ProjectMeta? {
        val media = mediaFile(id)
        if (!media.exists() || media.length() <= 0L) return null
        return try {
            val r = MediaMetadataRetriever()
            r.setDataSource(media.absolutePath)
            val dur = r.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull() ?: 0L
            var w = r.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH)?.toIntOrNull() ?: 0
            var h = r.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT)?.toIntOrNull() ?: 0
            val rot = r.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_ROTATION)?.toIntOrNull() ?: 0
            r.release()
            if (rot == 90 || rot == 270) { val t = w; w = h; h = t }
            if (dur <= 0L || w <= 0 || h <= 0) return null
            val now = System.currentTimeMillis()
            val m = ProjectMeta(id, "پروژه بازیابی‌شده", now, now, dur, w, h)
            writeMeta(m)
            m
        } catch (e: Exception) {
            null
        }
    }

    fun rename(id: String, name: String) {
        val m = meta(id) ?: return
        writeMeta(m.copy(name = name, updatedAt = System.currentTimeMillis()))
    }

    fun delete(id: String) {
        dir(id).deleteRecursively()
    }

    /** Full copy, so deleting one project never breaks the other. Returns the new id. */
    fun duplicate(id: String): String? {
        val m = meta(id) ?: return null
        val newId = UUID.randomUUID().toString().replace("-", "").take(12)
        val from = dir(id)
        val to = dir(newId)
        to.mkdirs()
        from.listFiles()?.forEach { f -> if (f.isFile) f.copyTo(File(to, f.name), overwrite = true) }
        val now = System.currentTimeMillis()
        writeMeta(m.copy(id = newId, name = m.name + " (کپی)", createdAt = now, updatedAt = now))
        return newId
    }

    // ---------------- helpers ----------------

    private fun writeMeta(m: ProjectMeta) {
        val o = JSONObject()
            .put("name", m.name).put("created", m.createdAt).put("updated", m.updatedAt)
            .put("duration", m.durationMs).put("w", m.width).put("h", m.height)
        atomicWrite(File(dir(m.id), "meta.json"), o.toString())
    }

    private fun atomicWrite(f: File, text: String) {
        val tmp = File(f.parentFile, f.name + ".tmp")
        tmp.writeText(text)
        if (f.exists()) f.delete()
        if (!tmp.renameTo(f)) {
            f.writeText(text)
            tmp.delete()
        }
    }

    private fun phrasesJson(l: List<Phrase>): JSONArray {
        val a = JSONArray()
        l.forEach { p ->
            val wa = JSONArray()
            p.words.forEach { w ->
                wa.put(
                    JSONObject().put("t", w.text).put("s", w.startMs).put("e", w.endMs)
                        .put("c", w.cutBefore).put("q", w.conf.toDouble())
                )
            }
            a.put(wa)
        }
        return a
    }

    private fun layersJson(l: List<Layer>): JSONArray {
        val a = JSONArray()
        l.forEach { x ->
            a.put(
                JSONObject().put("id", x.id).put("kind", x.kind).put("s", x.startMs).put("e", x.endMs)
                    .put("x", x.x.toDouble()).put("y", x.y.toDouble()).put("sc", x.scale.toDouble())
                    .put("rot", x.rotation.toDouble()).put("op", x.opacity.toDouble())
                    .put("text", x.text).put("color", x.color).put("bg", x.bgColor).put("stroke", x.stroke)
                    .put("font", x.fontId).put("file", x.file).put("w", x.imgW).put("h", x.imgH)
                    .put("lock", x.locked).put("hide", x.hidden)
                    .put("keys", JSONArray().also { ka -> x.keys.forEach { k -> ka.put(JSONObject().put("t", k.tMs).put("x", k.x.toDouble()).put("y", k.y.toDouble()).put("sc", k.scale.toDouble()).put("op", k.opacity.toDouble()).put("rot", k.rotation.toDouble()).put("ez", k.ease)) } })
                    .put("ai", x.animIn).put("ao", x.animOut).put("am", x.animMs)
                    .put("b", x.bold).put("i", x.italic).put("al", x.align)
                    .put("ls", x.letterSpacing.toDouble()).put("lh", x.lineSpacing.toDouble()).put("oc", x.outlineColor)
                    .put("km", x.keyMode).put("kt", x.keyTol.toDouble()).put("sd", x.shadowMode)
            )
        }
        return a
    }

    private fun audioJson(l: List<AudioClip>): JSONArray {
        val a = JSONArray()
        l.forEach { x ->
            a.put(
                JSONObject().put("id", x.id).put("kind", x.kind).put("file", x.file).put("name", x.name)
                    .put("s", x.startMs).put("in", x.inMs).put("len", x.lenMs).put("fileMs", x.fileMs)
                    .put("vol", x.volume.toDouble()).put("fi", x.fadeInMs).put("fo", x.fadeOutMs).put("mute", x.muted)
            )
        }
        return a
    }

    private fun audioFrom(a: JSONArray?): List<AudioClip> {
        if (a == null) return emptyList()
        val out = ArrayList<AudioClip>()
        for (i in 0 until a.length()) {
            val o = a.optJSONObject(i) ?: continue
            val file = o.optString("file", "")
            if (file.isEmpty() || o.optLong("len", 0L) < 1L) continue
            out.add(
                AudioClip(
                    id = o.optInt("id", i + 1), kind = o.optInt("kind", AudioClip.KIND_MUSIC), file = file,
                    name = o.optString("name", ""), startMs = o.optLong("s", 0L), inMs = o.optLong("in", 0L),
                    lenMs = o.optLong("len", 0L), fileMs = o.optLong("fileMs", 0L).coerceAtLeast(o.optLong("in", 0L) + o.optLong("len", 0L)),
                    volume = o.optDouble("vol", 1.0).toFloat().coerceIn(0f, 1f),
                    fadeInMs = o.optLong("fi", 0L), fadeOutMs = o.optLong("fo", 0L), muted = o.optBoolean("mute", false)
                )
            )
        }
        // ids must be unique: the editor addresses clips by id
        return out.distinctBy { it.id }
    }

    /** [layerRot]: keys saved before fix229 have no turn of their own; they keep the turn the layer had */
    private fun keysFrom(a: JSONArray?, layerRot: Float = 0f): List<Keyframe> {
        if (a == null) return emptyList()
        val out = ArrayList<Keyframe>()
        for (i in 0 until a.length()) {
            val o = a.optJSONObject(i) ?: continue
            out.add(
                Keyframe(
                    o.optLong("t", 0L), o.optDouble("x", 0.5).toFloat().coerceIn(0f, 1f), o.optDouble("y", 0.5).toFloat().coerceIn(0f, 1f),
                    o.optDouble("sc", 1.0).toFloat().coerceIn(0.1f, 6f), o.optDouble("op", 1.0).toFloat().coerceIn(0.05f, 1f),
                    o.optDouble("rot", layerRot.toDouble()).toFloat().coerceIn(-LayerKeys.ROT_MAX, LayerKeys.ROT_MAX),
                    o.optInt("ez", 0).coerceIn(0, 2)
                )
            )
        }
        return out.sortedBy { it.tMs }.take(LayerKeys.MAX_KEYS)
    }

    private fun layersFrom(a: JSONArray?): List<Layer> {
        if (a == null) return emptyList()
        val out = ArrayList<Layer>()
        for (i in 0 until a.length()) {
            val o = a.optJSONObject(i) ?: continue
            val s = o.optLong("s", 0L)
            val e = o.optLong("e", 0L)
            if (e - s < 1L) continue
            out.add(
                Layer(
                    id = o.optInt("id", i + 1), kind = o.optInt("kind", Layer.KIND_TEXT), startMs = s, endMs = e,
                    x = o.optDouble("x", 0.5).toFloat(), y = o.optDouble("y", 0.5).toFloat(),
                    scale = o.optDouble("sc", 1.0).toFloat(), rotation = o.optDouble("rot", 0.0).toFloat(),
                    opacity = o.optDouble("op", 1.0).toFloat(), text = o.optString("text", ""),
                    color = o.optInt("color", 0xFFFFFFFF.toInt()), bgColor = o.optInt("bg", 0),
                    stroke = o.optBoolean("stroke", true), fontId = o.optString("font", ""),
                    file = o.optString("file", ""), imgW = o.optInt("w", 0), imgH = o.optInt("h", 0),
                    locked = o.optBoolean("lock", false), hidden = o.optBoolean("hide", false),
                    keys = keysFrom(o.optJSONArray("keys"), o.optDouble("rot", 0.0).toFloat()),
                    animIn = o.optInt("ai", 0).coerceIn(0, 6), animOut = o.optInt("ao", 0).coerceIn(0, 6),
                    animMs = o.optLong("am", 400L).coerceIn(100L, 3000L),
                    bold = o.optBoolean("b", false), italic = o.optBoolean("i", false),
                    align = o.optInt("al", 0).coerceIn(0, 2),
                    letterSpacing = o.optDouble("ls", 0.0).toFloat().coerceIn(0f, 0.5f),
                    lineSpacing = o.optDouble("lh", 1.2).toFloat().coerceIn(0.8f, 2.5f),
                    outlineColor = o.optInt("oc", 0xFF000000.toInt()),
                    keyMode = o.optInt("km", 0).coerceIn(0, 4), keyTol = o.optDouble("kt", 0.4).toFloat().coerceIn(0f, 1f),
                    shadowMode = o.optInt("sd", 0).coerceIn(0, 4)
                )
            )
        }
        // ids must be unique: the editor addresses layers by id
        return out.distinctBy { it.id }
    }

    private fun extrasFrom(a: JSONArray?): List<ExtraMedia> {
        if (a == null) return emptyList()
        val out = ArrayList<ExtraMedia>()
        for (i in 0 until a.length()) {
            val o = a.optJSONObject(i) ?: continue
            out.add(
                ExtraMedia(
                    o.optInt("id", 0), o.optString("file", ""), o.optBoolean("image", false),
                    o.optLong("dur", 0L), o.optInt("w", 0), o.optInt("h", 0)
                )
            )
        }
        return out.filter { it.id > 0 && it.file.isNotEmpty() }
    }

    private fun segmentsFrom(a: JSONArray?, durMs: Long): List<Range>? {
        if (a == null) return null
        val out = ArrayList<Range>()
        for (i in 0 until a.length()) {
            val p = a.optJSONArray(i) ?: continue
            val s = p.optLong(0, 0L).coerceIn(0L, durMs)
            val e = p.optLong(1, 0L).coerceIn(0L, durMs)
            val sp = p.optDouble(2, 1.0).toFloat().coerceIn(0.1f, 8f)
            val tr = p.optInt(3, -1).let { if (it in 0..6) it else -1 }
            val trMs = p.optLong(4, 0L).coerceIn(0L, 1500L)
            val look = ClipLook(
                brightness = p.optDouble(5, 0.0).toFloat().coerceIn(-0.3f, 0.3f),
                contrast = p.optDouble(6, 1.0).toFloat().coerceIn(0.6f, 1.6f),
                saturation = p.optDouble(7, 1.0).toFloat().coerceIn(0f, 2f),
                warmth = p.optDouble(8, 0.0).toFloat().coerceIn(-1f, 1f),
                blur = p.optDouble(9, 0.0).toFloat().coerceIn(0f, 1f),
                sharpen = p.optDouble(10, 0.0).toFloat().coerceIn(0f, 1f),
                key = KeyLook(
                    on = p.optInt(11, 0) == 1,
                    color = p.optLong(12, 0xFF00B140L).toInt(),
                    tol = p.optDouble(13, 0.30).toFloat().coerceIn(0f, 1f),
                    soft = p.optDouble(14, 0.25).toFloat().coerceIn(0f, 1f),
                    spill = p.optDouble(15, 0.60).toFloat().coerceIn(0f, 1f),
                    bgMode = p.optInt(16, 0).coerceIn(0, 2),
                    bgColor = p.optLong(17, 0xFF000000L).toInt(),
                    bgFile = p.optString(18, "").takeIf { n -> Regex("keybg_\\d+\\.(jpg|png)").matches(n) } ?: "",
                    ai = p.optInt(19, 0).coerceIn(0, 1),
                    aiInvert = p.optInt(20, 0) == 1,
                    aiEdge = p.optDouble(21, 0.5).toFloat().coerceIn(0f, 1f)
                )
            )
            if (e - s >= 50L) out.add(Range(s, e, sp, tr, trMs, look))
        }
        return if (out.isEmpty()) null else out
    }

    private fun phrasesFrom(a: JSONArray?): List<Phrase> {
        if (a == null) return emptyList()
        val out = ArrayList<Phrase>()
        for (i in 0 until a.length()) {
            val wa = a.optJSONArray(i) ?: continue
            val words = ArrayList<Word>()
            for (j in 0 until wa.length()) {
                val o = wa.getJSONObject(j)
                words.add(
                    Word(
                        o.getString("t"), o.getLong("s"), o.getLong("e"),
                        o.optBoolean("c", false), o.optDouble("q", 1.0).toFloat()
                    )
                )
            }
            if (words.isNotEmpty()) out.add(Phrase(words))
        }
        return out
    }

    private fun makeThumb(media: File, out: File, durMs: Long) {
        try {
            val r = MediaMetadataRetriever()
            r.setDataSource(media.absolutePath)
            val at = minOf(1_000_000L, (durMs * 1000L) / 2L).coerceAtLeast(0L)
            val b = r.getFrameAtTime(at, MediaMetadataRetriever.OPTION_CLOSEST_SYNC) ?: r.getFrameAtTime(0L)
            r.release()
            if (b != null) {
                val k = 400f / maxOf(b.width, b.height)
                val s = if (k < 1f) Bitmap.createScaledBitmap(b, maxOf(1, (b.width * k).toInt()), maxOf(1, (b.height * k).toInt()), true) else b
                out.outputStream().use { s.compress(Bitmap.CompressFormat.JPEG, 85, it) }
            }
        } catch (e: Exception) {
            // the library shows a placeholder icon when there is no poster frame
        }
    }
}

/** "پروژه ۳" style default name with Persian digits. */
fun defaultProjectName(existing: List<ProjectMeta>): String {
    var n = existing.size + 1
    val names = existing.map { it.name }.toSet()
    while (true) {
        val d = n.toString().map { '۰' + (it - '0') }.joinToString("")
        val name = "پروژه $d"
        if (name !in names) return name
        n++
    }
}
