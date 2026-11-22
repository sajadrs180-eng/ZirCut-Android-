package app.zircut

import android.app.Application
import android.app.ActivityManager
import android.content.ContentValues
import android.content.Context
import android.graphics.Typeface
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import android.provider.OpenableColumns
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.media3.common.util.UnstableApi
import app.zircut.core.AudioDecoder
import app.zircut.core.SilenceScan
import app.zircut.core.AudioClean
import app.zircut.core.AudioEq
import app.zircut.core.AudioMixer
import app.zircut.core.PcmDecoder
import app.zircut.core.Waveform
import app.zircut.timeline.AudioClip
import app.zircut.timeline.AudioOps
import app.zircut.core.Exporter
import app.zircut.core.FontStore
import app.zircut.core.Options
import app.zircut.core.Phrase
import app.zircut.core.Range
import app.zircut.core.Planner
import app.zircut.core.SpeedRemux
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import app.zircut.core.CapLayout
import app.zircut.core.ColorGrade
import app.zircut.core.ExportSettings
import app.zircut.core.Frame
import app.zircut.core.Fx
import app.zircut.core.Dsp
import app.zircut.core.Diag
import app.zircut.core.Styles
import app.zircut.core.VideoInfo
import app.zircut.core.Word
import app.zircut.project.ExtraMedia
import app.zircut.project.ProjectData
import app.zircut.timeline.EdlMap
import app.zircut.timeline.EdlOps
import app.zircut.timeline.Layer
import app.zircut.timeline.LayerKeys
import app.zircut.timeline.LayerOps
import app.zircut.timeline.NamedStyle
import app.zircut.timeline.TextStyles
import app.zircut.timeline.MyTemplate
import app.zircut.timeline.SavedTitle
import app.zircut.timeline.VideoTemplates
import app.zircut.timeline.Src
import app.zircut.timeline.captionRanges
import app.zircut.project.ProjectMeta
import app.zircut.project.ProjectStore
import app.zircut.project.defaultProjectName
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

data class Recent(val title: String, val uri: String, val time: Long)

sealed interface Stage {
    data object Idle : Stage
    data class Working(val label: String, val progress: Float?) : Stage
    data object Review : Stage
    data class Done(val uri: Uri) : Stage
    data class Failed(val message: String) : Stage
}

data class UiState(
    val stage: Stage = Stage.Idle,
    val video: Uri? = null,
    val info: VideoInfo? = null,
    val phrases: List<Phrase> = emptyList(),
    val options: Options = Options(),
    val styleId: String = "zarbati",
    val layout: CapLayout = CapLayout(),
    val grade: ColorGrade = ColorGrade(),
    /** whole-video effects (part 6) */
    val fx: Fx = Fx(),
    /** export sheet choices (part 7) */
    val exportSettings: ExportSettings = ExportSettings(),
    val debug: String = "",
    val perf: String = "",
    /** stretches of the video's own sound (found by analysing the audio on the phone); the silence cut keeps these */
    val activity: List<Word> = emptyList(),
    val scanning: Boolean = false,
    /** id of the project whose sound analysis is in [activity] ("" = not analysed yet) */
    val activityFor: String = "",
    val original: List<Phrase> = emptyList(),
    val myTemplates: List<MyTemplate> = emptyList(),
    val fontId: String = "",
    val themeId: String = "violet_blue",
    val fonts: List<FontStore.Font> = emptyList(),
    val recent: List<Recent> = emptyList(),
    val notice: String = "",
    val projectId: String = "",
    val projectName: String = "",
    val projects: List<ProjectMeta> = emptyList(),
    /** "" none yet, "saving", "saved" or "error" */
    val saveStatus: String = "",
    /** manual clip list (split/trim/move/duplicate/delete). null = automatic cut */
    val segments: List<Range>? = null,
    val undoCount: Int = 0,
    val redoCount: Int = 0,
    /** extra videos/photos added to the project; clips reference them through app.zircut.timeline.Src */
    val extras: List<ExtraMedia> = emptyList(),
    /** text and picture layers over the video; times are on the edited timeline */
    val layers: List<Layer> = emptyList(),
    /** id of the layer being edited, -1 = none (not saved) */
    val selectedLayer: Int = -1,
    /** music / voice-over clips (part 5); times are on the edited timeline */
    val audio: List<AudioClip> = emptyList(),
    /** id of the audio clip being edited, -1 = none (not saved) */
    val selectedAudio: Int = -1,
    /** volume (0..1) and on/off of the video's own sound */
    val mainVolume: Float = 1f,
    val mainMuted: Boolean = false,
    /** music is turned down automatically while a voice-over plays (preview and export) */
    val duckMusic: Boolean = false,
    /** ripple editing: layers and audio clips after a clip change move with it (app setting, not saved in projects) */
    val ripple: Boolean = false,
    /** real waveforms (one byte per 20 ms) by audio file name */
    val waves: Map<String, ByteArray> = emptyMap(),
    /** short status/error line shown in the editor */
    val message: String = ""
)

@UnstableApi
class MainViewModel(app: Application) : AndroidViewModel(app) {
    private val POSE_KEYS = setOf("xf", "x", "y", "scale", "scale-preset", "opacity", "rot", "rotate90")
    private val ctx: Context = app.applicationContext
    private val prefs = ctx.getSharedPreferences("zircut", Context.MODE_PRIVATE)
    /** text looks the user saved; they belong to the app, not to one project */
    val customTextStyles = androidx.compose.runtime.mutableStateListOf<NamedStyle>().apply {
        addAll(TextStyles.fromJson(prefs.getString("text_styles", null)))
    }
    private val fontStore = FontStore(ctx)
    private val store = ProjectStore(ctx)
    private var lastSavedKey = 0
    private var saveJob: Job? = null
    /** one undo step = the clip list and the layers as they were before the edit */
    private class Snap(
        val segs: List<Range>?,
        val phrases: List<Phrase>,
        val original: List<Phrase>,
        val layers: List<Layer>,
        val audio: List<AudioClip>,
        val mainVol: Float,
        val mainMuted: Boolean,
        val duck: Boolean,
        val grade: ColorGrade,
        val fx: Fx,
        val exportSettings: ExportSettings,
        val options: Options,
        val styleId: String,
        val fontId: String,
        val layout: CapLayout
    )
    private val undoStack = ArrayDeque<Snap>()
    private val redoStack = ArrayDeque<Snap>()
    private var lastEditKey = ""
    private var lastEditAt = 0L

    private val _ui = MutableStateFlow(UiState())
    val ui: StateFlow<UiState> = _ui.asStateFlow()

    private var job: Job? = null
    private var exporter: Exporter? = null

    init {
        val fonts = fontStore.list()
        val savedFont = prefs.getString("font", null)
        val styleFont = Styles.byId(prefs.getString("style", "zarbati") ?: "zarbati").font
        val fontId = fonts.firstOrNull { it.id == savedFont }?.id
            ?: fonts.firstOrNull { it.name == styleFont }?.id ?: fonts.first().id
        _ui.update {
            it.copy(
                themeId = prefs.getString("theme", "violet_blue") ?: "violet_blue",
                styleId = prefs.getString("style", "zarbati") ?: "zarbati",
                ripple = prefs.getBoolean("ripple", false),
                myTemplates = loadMyTemplates(),
                layout = CapLayout(
                    x = prefs.getFloat("lx", 0.5f), y = prefs.getFloat("ly", 0.66f), mw = prefs.getFloat("lmw", 0.9f),
                    ls = prefs.getFloat("lls", 1.5f), lw = prefs.getInt("llw", 4), size = prefs.getFloat("lsz", 1f),
                    anim = prefs.getInt("lan", 0).coerceIn(0, 6)
                ),
                fonts = fonts,
                fontId = fontId
            )
        }
        viewModelScope.launch(Dispatchers.IO) {
            val all = readRecent()
            val alive = all.filter { r -> uriExists(r.uri) }
            if (alive.size != all.size) writeRecent(alive)
            _ui.update { it.copy(recent = alive) }
        }
        viewModelScope.launch(Dispatchers.IO) {
            val l = try { store.list() } catch (e: Exception) { emptyList() }
            _ui.update { it.copy(projects = l) }
        }
        _ui.onEach { autosave(it) }.launchIn(viewModelScope)
    }

    // ---------- recent projects ----------
    private fun uriExists(u: String): Boolean = try {
        ctx.contentResolver.openFileDescriptor(Uri.parse(u), "r")?.use { true } ?: false
    } catch (e: Exception) {
        false
    }

    private fun readRecent(): List<Recent> = try {
        val a = org.json.JSONArray(prefs.getString("recent", "[]") ?: "[]")
        (0 until a.length()).map { i ->
            val o = a.getJSONObject(i)
            Recent(o.getString("t"), o.getString("u"), o.getLong("m"))
        }
    } catch (e: Exception) {
        emptyList()
    }

    private fun writeRecent(l: List<Recent>) {
        val a = org.json.JSONArray()
        l.forEach { r -> a.put(org.json.JSONObject().put("t", r.title).put("u", r.uri).put("m", r.time)) }
        prefs.edit().putString("recent", a.toString()).apply()
    }

    fun openRecent(r: Recent) {
        _ui.update { it.copy(stage = Stage.Done(Uri.parse(r.uri)), debug = "") }
    }

    fun removeRecent(r: Recent) {
        val l = _ui.value.recent.filter { it.uri != r.uri }
        writeRecent(l)
        _ui.update { it.copy(recent = l) }
    }

    // ---------- projects ----------
    private fun keyOf(s: UiState): Int =
        java.util.Objects.hash(s.projectId, s.projectName, s.phrases, s.original, s.options, s.styleId, s.fontId, s.layout, s.grade, s.fx, s.segments, s.extras, s.layers, s.audio, s.mainVolume, s.mainMuted, s.fx, s.exportSettings, s.duckMusic)

    /** Called for every state change: saves the open project half a second after the last edit. */
    private fun autosave(s: UiState) {
        if (s.projectId.isEmpty() || s.info == null || s.video == null) return
        if (s.stage is Stage.Idle || s.stage is Stage.Working) return
        val k = keyOf(s)
        if (k == lastSavedKey) return
        lastSavedKey = k
        saveJob?.cancel()
        saveJob = viewModelScope.launch {
            _ui.update { it.copy(saveStatus = "saving") }
            delay(500)
            persist(s)
        }
    }

    private suspend fun persist(s: UiState) {
        val info = s.info ?: return
        val list = withContext(Dispatchers.IO) {
            try {
                val old = store.meta(s.projectId) ?: return@withContext null
                val m = old.copy(
                    name = s.projectName.ifBlank { old.name }, updatedAt = System.currentTimeMillis(),
                    durationMs = info.durationMs, width = info.width, height = info.height
                )
                store.save(ProjectData(m, s.phrases, s.original, s.options, s.styleId, s.fontId, s.layout, s.grade, s.segments, s.extras, s.layers, s.audio, s.mainVolume, s.mainMuted, s.fx, s.exportSettings, s.duckMusic))
                store.list()
            } catch (e: Exception) {
                null
            }
        }
        _ui.update { it.copy(saveStatus = if (list != null) "saved" else "error", projects = list ?: it.projects) }
    }

    private suspend fun refreshProjects() {
        val l = withContext(Dispatchers.IO) { try { store.list() } catch (e: Exception) { emptyList() } }
        _ui.update { it.copy(projects = l) }
    }

    // ---------- timeline history (clip edits and layer edits) ----------
    private fun snap() = _ui.value.let {
        Snap(it.segments, it.phrases, it.original, it.layers, it.audio, it.mainVolume, it.mainMuted, it.duckMusic, it.grade, it.fx, it.exportSettings, it.options, it.styleId, it.fontId, it.layout)
    }

    private fun pushHistory() {
        undoStack.addLast(snap())
        if (undoStack.size > 60) undoStack.removeFirst()
        redoStack.clear()
        lastEditKey = ""
    }

    private fun counts(s: UiState): UiState = s.copy(undoCount = undoStack.size, redoCount = redoStack.size)

    /** Replaces the clip list. Every call is one undo step. */
    fun setSegments(new: List<Range>?) {
        // a slider drag that only changes a clip's effects is ONE undo step; every other clip change is a step of its own
        val oldR = currentRanges(_ui.value)
        val lookOnly = oldR != null && new != null && oldR.size == new.size &&
            oldR.indices.all { oldR[it].copy(look = new[it].look) == new[it] } &&
            oldR.indices.any { oldR[it].look != new[it].look }
        if (lookOnly) keyedHistory("cliplook") else pushHistory()
        _ui.update { s ->
            var n = s.copy(segments = new)
            if (s.ripple && new != null) {
                val old = currentRanges(s)
                val ch = if (old == null) null else app.zircut.timeline.Ripple.diff(old, new)
                if (ch != null) {
                    n = n.copy(
                        layers = app.zircut.timeline.Ripple.layers(s.layers, ch),
                        audio = app.zircut.timeline.Ripple.audio(s.audio, ch),
                        fx = if (s.fx.vkeys.isEmpty()) s.fx else s.fx.copy(vkeys = app.zircut.timeline.Ripple.videoKeys(s.fx.vkeys, ch))
                    )
                }
            }
            counts(n)
        }
    }

    fun setRipple(on: Boolean) {
        prefs.edit().putBoolean("ripple", on).apply()
        _ui.update { it.copy(ripple = on) }
    }

    fun undo() {
        if (undoStack.isEmpty()) return
        val prev = undoStack.removeLast()
        redoStack.addLast(snap())
        lastEditKey = ""
        _ui.update {
            counts(it.copy(
                segments = prev.segs, phrases = prev.phrases, original = prev.original,
                layers = prev.layers, audio = prev.audio, mainVolume = prev.mainVol,
                mainMuted = prev.mainMuted, duckMusic = prev.duck, grade = prev.grade, fx = prev.fx, exportSettings = prev.exportSettings,
                options = prev.options, styleId = prev.styleId, fontId = prev.fontId, layout = prev.layout
            ))
        }
        persistCaptionLook()
    }

    fun redo() {
        if (redoStack.isEmpty()) return
        val next = redoStack.removeLast()
        undoStack.addLast(snap())
        lastEditKey = ""
        _ui.update {
            counts(it.copy(
                segments = next.segs, phrases = next.phrases, original = next.original,
                layers = next.layers, audio = next.audio, mainVolume = next.mainVol,
                mainMuted = next.mainMuted, duckMusic = next.duck, grade = next.grade, fx = next.fx, exportSettings = next.exportSettings,
                options = next.options, styleId = next.styleId, fontId = next.fontId, layout = next.layout
            ))
        }
        persistCaptionLook()
    }

    /** one undo step for a run of edits of the same kind that follow each other closely (sliders, typing) */
    private fun keyedHistory(key: String) {
        val now = System.currentTimeMillis()
        if (lastEditKey != key || now - lastEditAt > 900L) {
            pushHistory()
            lastEditKey = key
        }
        lastEditAt = now
    }

    /** subtitle style, font and position are also remembered between projects (preferences); keep them equal to the state after undo / redo */
    private fun persistCaptionLook() {
        val s = _ui.value
        val l = s.layout
        prefs.edit().putString("style", s.styleId).putString("font", s.fontId)
            .putFloat("lx", l.x).putFloat("ly", l.y).putFloat("lmw", l.mw).putFloat("lls", l.ls).putInt("llw", l.lw).putFloat("lsz", l.size).putInt("lan", l.anim).apply()
    }

    // ---------- layers (text and pictures over the video) ----------
    private fun setLayers(new: List<Layer>, selected: Int) {
        _ui.update { counts(it.copy(layers = new, selectedLayer = selected)) }
    }

    fun selectLayer(id: Int) {
        _ui.update { it.copy(selectedLayer = id) }
    }

    /** 3 s at the playhead; near the end of the video the layer is moved back so it still fits. */
    private fun defaultSpan(at: Long, total: Long): Pair<Long, Long> {
        val len = minOf(3000L, maxOf(Layer.MIN_MS, total))
        val a = if (at + len > total) maxOf(0L, total - len) else maxOf(0L, at)
        return a to (a + len)
    }

    fun addTextLayer(atMs: Long, totalMs: Long) {
        val s = _ui.value
        if (s.projectId.isEmpty()) return
        val (a, b) = defaultSpan(atMs, totalMs)
        val l = Layer(LayerOps.nextId(s.layers), Layer.KIND_TEXT, a, b, y = 0.4f, text = "متن جدید")
        pushHistory()
        setLayers(s.layers + l, l.id)
    }

    fun addImageLayer(uri: Uri, atMs: Long, totalMs: Long) {
        val s0 = _ui.value
        if (s0.projectId.isEmpty()) return
        viewModelScope.launch {
            _ui.update { it.copy(message = "در حال افزودن عکس…") }
            val r = try {
                withContext(Dispatchers.IO) { importLayerImage(s0.projectId, uri) }
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: Exception) {
                _ui.update { it.copy(message = "افزودن عکس ناموفق بود: ${e.message}") }
                return@launch
            }
            val cur = _ui.value
            if (cur.projectId != s0.projectId) {
                File(store.dir(s0.projectId), r.first).delete()
                return@launch
            }
            val (a, b) = defaultSpan(atMs, totalMs)
            val l = Layer(LayerOps.nextId(cur.layers), Layer.KIND_IMAGE, a, b, file = r.first, imgW = r.second, imgH = r.third)
            pushHistory()
            setLayers(cur.layers + l, l.id)
            _ui.update { it.copy(message = "") }
        }
    }

    /** Copies the picture into the project (max 2000 px, turned upright by its EXIF tag). Returns file name, width, height. */
    private fun importLayerImage(projectId: String, uri: Uri, prefix: String = "layer_"): Triple<String, Int, Int> {
        val dir = store.dir(projectId)
        val tmp = File(dir, "layer_tmp.bin")
        try {
            val input = ctx.contentResolver.openInputStream(uri) ?: throw java.io.IOException("فایل خوانده نشد")
            input.use { i -> tmp.outputStream().use { o -> i.copyTo(o) } }
            val bo = android.graphics.BitmapFactory.Options().apply { inJustDecodeBounds = true }
            android.graphics.BitmapFactory.decodeFile(tmp.absolutePath, bo)
            if (bo.outWidth <= 0 || bo.outHeight <= 0) throw java.io.IOException("تصویر نامعتبر است")
            var sample = 1
            while (bo.outWidth / sample > 2000 || bo.outHeight / sample > 2000) sample *= 2
            var bmp = android.graphics.BitmapFactory.decodeFile(
                tmp.absolutePath, android.graphics.BitmapFactory.Options().apply { inSampleSize = sample }
            ) ?: throw java.io.IOException("تصویر خوانده نشد")
            val deg = try {
                when (android.media.ExifInterface(tmp.absolutePath).getAttributeInt(
                    android.media.ExifInterface.TAG_ORIENTATION, android.media.ExifInterface.ORIENTATION_NORMAL
                )) {
                    android.media.ExifInterface.ORIENTATION_ROTATE_90 -> 90f
                    android.media.ExifInterface.ORIENTATION_ROTATE_180 -> 180f
                    android.media.ExifInterface.ORIENTATION_ROTATE_270 -> 270f
                    else -> 0f
                }
            } catch (_: Exception) {
                0f
            }
            if (deg != 0f) {
                val m = android.graphics.Matrix().apply { postRotate(deg) }
                bmp = android.graphics.Bitmap.createBitmap(bmp, 0, 0, bmp.width, bmp.height, m, true)
            }
            // pictures with transparency stay PNG, photos become JPEG (much smaller)
            val png = bmp.hasAlpha()
            val name = "$prefix${System.currentTimeMillis()}." + (if (png) "png" else "jpg")
            File(dir, name).outputStream().use { o ->
                bmp.compress(if (png) android.graphics.Bitmap.CompressFormat.PNG else android.graphics.Bitmap.CompressFormat.JPEG, 92, o)
            }
            return Triple(name, bmp.width, bmp.height)
        } finally {
            tmp.delete()
        }
    }

    /**
     * Changes one layer. Calls with the same [key] that follow each other closely (slider drags, typing,
     * finger gestures) become ONE undo step.
     */
    fun editLayer(id: Int, key: String, change: (Layer) -> Layer) {
        val cur = _ui.value
        val i = cur.layers.indexOfFirst { it.id == id }
        if (i < 0) return
        // a locked layer only accepts the lock / hide switches
        if (cur.layers[i].locked && key != "lock" && key != "hide") return
        val now = System.currentTimeMillis()
        val k = "$id|$key"
        if (k != lastEditKey || now - lastEditAt > 900L) {
            pushHistory()
            lastEditKey = k
        }
        lastEditAt = now
        val n = cur.layers.toMutableList()
        n[i] = change(cur.layers[i])
        setLayers(n, cur.selectedLayer)
    }

    /** Saves the look of a text layer (not its words, place or timing) so it can be applied to other texts. */
    fun saveTextStyle(layerId: Int) {
        val l = _ui.value.layers.firstOrNull { it.id == layerId && it.isText } ?: return
        val next = TextStyles.add(customTextStyles.toList(), TextStyles.of(l))
        customTextStyles.clear()
        customTextStyles.addAll(next)
        prefs.edit().putString("text_styles", TextStyles.toJson(next)).apply()
    }

    fun deleteTextStyle(name: String) {
        val next = customTextStyles.filter { it.name != name }
        customTextStyles.clear()
        customTextStyles.addAll(next)
        prefs.edit().putString("text_styles", TextStyles.toJson(next)).apply()
    }

    /**
     * Like [editLayer], but for a layer with keyframes a change of position, size or opacity is stored as a keyframe at the playhead
     * [atMs] (the look there is the animated one), so moving the layer while keyframes exist edits the animation.
     */
    fun editLayerAt(id: Int, key: String, atMs: Long, change: (Layer) -> Layer) {
        val l = _ui.value.layers.firstOrNull { it.id == id } ?: return
        if (l.keys.isEmpty() || key !in POSE_KEYS) editLayer(id, key, change)
        else editLayer(id, key) { cur -> LayerKeys.edited(cur, atMs, change) }
    }

    fun deleteLayer(id: Int) {
        val s = _ui.value
        val target = s.layers.firstOrNull { it.id == id } ?: return
        if (target.locked) return
        pushHistory()
        setLayers(s.layers.filter { it.id != id }, if (s.selectedLayer == id) -1 else s.selectedLayer)
    }

    fun duplicateLayer(id: Int) {
        val s = _ui.value
        val i = s.layers.indexOfFirst { it.id == id }
        if (i < 0) return
        val l = s.layers[i]
        val c = l.copy(id = LayerOps.nextId(s.layers), locked = false, x = (l.x + 0.05f).coerceAtMost(0.95f), y = (l.y + 0.05f).coerceAtMost(0.95f))
        val n = s.layers.toMutableList()
        n.add(i + 1, c)
        pushHistory()
        setLayers(n, c.id)
    }

    /** Cuts the layer in two at [atMs]. False when a piece would be too short. */
    fun splitLayer(id: Int, atMs: Long): Boolean {
        val s = _ui.value
        if (s.layers.firstOrNull { it.id == id }?.locked == true) return false
        val n = LayerOps.split(s.layers, id, atMs) ?: return false
        pushHistory()
        setLayers(n, id)
        return true
    }

    fun moveLayer(id: Int, dir: Int) {
        val s = _ui.value
        if (s.layers.firstOrNull { it.id == id }?.locked == true) return
        val n = LayerOps.move(s.layers, id, dir) ?: return
        pushHistory()
        setLayers(n, s.selectedLayer)
    }

    // ---------- audio (music, voice-over, volume of the original sound) ----------
    private fun setAudio(new: List<AudioClip>, selected: Int) {
        _ui.update { counts(it.copy(audio = new, selectedAudio = selected)) }
    }

    fun selectAudio(id: Int) {
        _ui.update { it.copy(selectedAudio = id) }
    }

    /** Builds missing waveforms in the background (also after opening a project). */
    private fun loadWaves(projectId: String, clips: List<AudioClip>) {
        if (clips.isEmpty()) return
        viewModelScope.launch(Dispatchers.IO) {
            val dir = store.dir(projectId)
            for (c in clips.distinctBy { it.file }) {
                if (_ui.value.projectId != projectId) return@launch
                if (_ui.value.waves.containsKey(c.file)) continue
                val pk = Waveform.ensure(ctx, dir, c.file, c.fileMs) ?: continue
                _ui.update { it.copy(waves = it.waves + (c.file to pk)) }
            }
        }
    }

    private fun faNum(n: Int): String = n.toString().map { '\u06F0' + (it - '0') }.joinToString("")

    private fun displayName(uri: Uri): String = try {
        ctx.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { c ->
            if (c.moveToFirst()) c.getString(0) else null
        } ?: "صدا"
    } catch (e: Exception) {
        "صدا"
    }

    private class AudioImport(val file: String, val name: String, val durationMs: Long)

    private fun copyAudio(projectId: String, uri: Uri): AudioImport {
        val name = displayName(uri)
        val mime = ctx.contentResolver.getType(uri) ?: ""
        val extName = name.substringAfterLast('.', "").lowercase()
        val known = setOf("mp3", "m4a", "aac", "wav", "ogg", "opus", "flac", "amr", "3gp", "mp4")
        val ext = if (extName in known) extName else when {
            mime.contains("mpeg") -> "mp3"
            mime.contains("wav") -> "wav"
            mime.contains("ogg") -> "ogg"
            mime.contains("flac") -> "flac"
            else -> "m4a"
        }
        val file = "audio_${System.currentTimeMillis()}.$ext"
        val dest = File(store.dir(projectId), file)
        val input = ctx.contentResolver.openInputStream(uri) ?: throw java.io.IOException("فایل خوانده نشد")
        input.use { i -> dest.outputStream().use { o -> i.copyTo(o) } }
        val du = Uri.fromFile(dest)
        val dur = PcmDecoder.durationMs(ctx, du)
        if (dur <= 0L || !PcmDecoder.hasAudio(ctx, du)) {
            dest.delete()
            throw java.io.IOException("این فایل صدای قابل خواندن ندارد")
        }
        return AudioImport(file, name.substringBeforeLast('.').take(40).ifBlank { "صدا" }, dur)
    }

    /** Adds the music / sound file the user picked as a clip at [atMs] (it is copied into the project). */
    fun importAudio(uri: Uri, atMs: Long, totalMs: Long) {
        val s0 = _ui.value
        if (s0.projectId.isEmpty()) return
        viewModelScope.launch {
            _ui.update { it.copy(message = "در حال افزودن صدا…") }
            val imp: AudioImport
            val peaks: ByteArray?
            try {
                val r = withContext(Dispatchers.IO) {
                    val a = copyAudio(s0.projectId, uri)
                    a to Waveform.ensure(ctx, store.dir(s0.projectId), a.file, a.durationMs)
                }
                imp = r.first
                peaks = r.second
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: Exception) {
                _ui.update { it.copy(message = "افزودن صدا ناموفق بود: ${e.message}") }
                return@launch
            }
            val cur = _ui.value
            val start = atMs.coerceIn(0L, maxOf(0L, totalMs - 500L))
            val len = minOf(imp.durationMs, maxOf(AudioClip.MIN_MS, totalMs - start))
            val c = AudioOps.fit(AudioClip(AudioOps.nextId(cur.audio), AudioClip.KIND_MUSIC, imp.file, imp.name, start, 0L, len, imp.durationMs))
            pushHistory()
            _ui.update {
                counts(it.copy(audio = it.audio + c, selectedAudio = c.id, message = "", waves = if (peaks != null) it.waves + (c.file to peaks) else it.waves))
            }
        }
    }

    /** Adds a finished voice recording (already inside the project folder) as a voice-over clip. */
    fun addRecording(file: File, startMs: Long, durationMs: Long) {
        val s0 = _ui.value
        if (s0.projectId.isEmpty() || durationMs < 300L) {
            file.delete()
            return
        }
        viewModelScope.launch {
            val peaks = withContext(Dispatchers.IO) { Waveform.ensure(ctx, store.dir(s0.projectId), file.name, durationMs) }
            val cur = _ui.value
            val n = cur.audio.count { it.isVoice } + 1
            val c = AudioOps.fit(
                AudioClip(AudioOps.nextId(cur.audio), AudioClip.KIND_VOICE, file.name, "ضبط صدا " + faNum(n), startMs.coerceAtLeast(0L), 0L, durationMs, durationMs)
            )
            pushHistory()
            _ui.update {
                counts(it.copy(audio = it.audio + c, selectedAudio = c.id, waves = if (peaks != null) it.waves + (c.file to peaks) else it.waves))
            }
        }
    }

    // ---------- part 8: SRT, scene detection, cleanup ----------
    private var sceneJob: Job? = null

    private fun currentRanges(s: UiState): List<Range>? {
        val info = s.info ?: return null
        return s.segments?.takeIf { it.isNotEmpty() } ?: Planner.ranges(s.activity, info.durationMs, s.options)
    }

    /** Saves the subtitles of the EDITED video as an .srt file in Downloads/ZirCut. */
    fun exportSrt() {
        val s = _ui.value
        val ranges = currentRanges(s) ?: return
        if (s.phrases.isEmpty()) { showMessage("زیرنویسی برای خروجی گرفتن وجود ندارد"); return }
        val text = app.zircut.core.Srt.build(s.phrases, app.zircut.timeline.EdlMap(ranges))
        if (text.isBlank()) { showMessage("هیچ زیرنویسی روی ویدیوی ادیت‌شده نمانده است"); return }
        viewModelScope.launch {
            val ok = withContext(Dispatchers.IO) {
                try {
                    val values = ContentValues().apply {
                        put(MediaStore.Downloads.DISPLAY_NAME, "ZirCut_${System.currentTimeMillis()}.srt")
                        put(MediaStore.Downloads.MIME_TYPE, "application/x-subrip")
                        put(MediaStore.Downloads.RELATIVE_PATH, "Download/ZirCut")
                    }
                    val uri = ctx.contentResolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values)
                    if (uri == null) false else {
                        ctx.contentResolver.openOutputStream(uri)!!.use { it.write(text.toByteArray(Charsets.UTF_8)) }
                        true
                    }
                } catch (e: Exception) {
                    false
                }
            }
            showMessage(if (ok) "فایل SRT در پوشه‌ی Download/ZirCut ذخیره شد" else "ذخیره‌ی SRT ناموفق بود")
        }
    }

    /** Replaces the subtitles with an .srt file whose times are on the edited timeline. */
    fun importSrt(uri: Uri) {
        val s = _ui.value
        val ranges = currentRanges(s) ?: return
        viewModelScope.launch {
            val res = withContext(Dispatchers.IO) {
                try {
                    val raw = ctx.contentResolver.openInputStream(uri)?.use { String(it.readBytes(), Charsets.UTF_8) }
                    if (raw == null) null else app.zircut.core.Srt.toPhrases(app.zircut.core.Srt.parse(raw), app.zircut.timeline.EdlMap(ranges))
                } catch (e: Exception) {
                    null
                }
            }
            if (res == null || res.phrases.isEmpty()) {
                showMessage("زیرنویس قابل استفاده‌ای در فایل پیدا نشد")
                return@launch
            }
            // the clips are frozen first: new words must not change the automatic silence cut the cue times refer to
            pushHistory()
            _ui.update {
                counts(it.copy(
                    segments = it.segments ?: ranges, phrases = res.phrases, original = res.phrases,
                    message = "${res.phrases.size} زیرنویس وارد شد" + (if (res.skipped > 0) " (${res.skipped} مورد روی عکس/ویدیوی اضافه یا خارج از زمان رد شد)" else "")
                ))
            }
        }
    }

    /** Finds hard cuts in the main video and splits the clips there (one undo step). */
    fun detectScenes() {
        val s = _ui.value
        val uri = s.video ?: return
        val info = s.info ?: return
        sceneJob?.cancel()
        sceneJob = viewModelScope.launch {
            showMessage("در حال تشخیص صحنه…")
            var lastPct = -1
            val cuts = try {
                withContext(Dispatchers.IO) {
                    app.zircut.core.SceneDetect.detect(ctx, uri, info.durationMs, alive = { isActive }, onProgress = { p ->
                        val pc = (p * 100f).toInt()
                        if (pc != lastPct) {
                            lastPct = pc
                            _ui.update { it.copy(message = "در حال تشخیص صحنه… ${pc}٪") }
                        }
                    })
                }
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: Exception) {
                emptyList()
            }
            val cur = _ui.value
            val base = currentRanges(cur) ?: return@launch
            val out = ArrayList<Range>()
            var added = 0
            for (r in base) {
                if (Src.idx(r.startMs) != 0) { out.add(r); continue }
                var a = r.startMs
                for (c in cuts) {
                    if (c > a + 300L && c < r.endMs - 300L) { out.add(r.copy(startMs = a, endMs = c)); a = c; added++ }
                }
                out.add(r.copy(startMs = a))
            }
            if (added == 0) {
                showMessage("تغییر صحنه‌ی مشخصی پیدا نشد")
            } else {
                setSegments(out)
                showMessage("ویدیو در $added محل تغییر صحنه برش خورد")
            }
        }
    }

    /** Deletes project files nothing uses any more (extra media not on the timeline, pictures/audio of deleted layers/clips). Clears undo history. */
    fun cleanUnused() {
        val s = _ui.value
        if (s.projectId.isEmpty()) return
        viewModelScope.launch {
            val usedExtra = (s.segments ?: emptyList()).map { Src.idx(it.startMs) }.toSet()
            val keepExtras = s.extras.filter { it.id in usedExtra }
            val keepFiles = HashSet<String>()
            keepExtras.forEach { keepFiles.add(it.file) }
            s.layers.forEach { if (!it.isText) keepFiles.add(it.file) }
            s.audio.forEach { keepFiles.add(it.file); keepFiles.add(it.file + ".pk") }
            if (s.fx.lutFile.isNotEmpty()) keepFiles.add(s.fx.lutFile)
            s.segments?.forEach { if (it.look.key.bgFile.isNotEmpty()) keepFiles.add(it.look.key.bgFile) }
            val freed = withContext(Dispatchers.IO) {
                var bytes = 0L
                store.dir(s.projectId).listFiles()?.forEach { f ->
                    val n = f.name
                    val ours = n.startsWith("extra_") || n.startsWith("layer_") || n.startsWith("audio_") || n.startsWith("rec_") || n.startsWith("lut_") || n.startsWith("keybg_")
                    if (f.isFile && ours && n !in keepFiles) {
                        bytes += f.length()
                        f.delete()
                    }
                }
                bytes
            }
            // the history may point at removed extras, so it is cleared
            undoStack.clear()
            redoStack.clear()
            _ui.update { counts(it.copy(extras = keepExtras, message = if (freed > 0) "فایل‌های بلااستفاده پاک شد: " + (freed / 1024 / 1024) + " مگابایت" else "فایل بلااستفاده‌ای پیدا نشد")) }
        }
    }

    /** Copies a .cube colour LUT into the project and switches it on (one undo step). */
    fun importLut(uri: Uri) {
        val s0 = _ui.value
        if (s0.projectId.isEmpty()) return
        viewModelScope.launch {
            val name = "lut_${System.currentTimeMillis()}.cube"
            val err: String? = withContext(Dispatchers.IO) {
                try {
                    val bytes = ctx.contentResolver.openInputStream(uri)?.use { it.readBytes() }
                    if (bytes == null || bytes.size > 8_000_000) return@withContext "فایل LUT خوانده نشد یا بزرگ‌تر از ۸ مگابایت است."
                    val text = String(bytes, Charsets.UTF_8)
                    if (app.zircut.core.CubeLut.parse(text) == null) return@withContext "این فایل یک LUT سه‌بعدی معتبر (.cube) نیست."
                    File(store.dir(s0.projectId), name).writeText(text)
                    null
                } catch (e: Exception) {
                    "خواندن LUT ناموفق بود: ${e.message}"
                }
            }
            if (err != null) {
                _ui.update { it.copy(message = err) }
                return@launch
            }
            setFx(_ui.value.fx.copy(lutFile = name, lutAmt = 1f))
        }
    }

    /**
     * Chroma key eyedropper: the source frame (no project transform, no grade) under the playhead, at most 540 px on the long side.
     * Null (with a message) when the clip is a photo or the frame cannot be read.
     */
    suspend fun keyFrame(atOutMs: Long): android.graphics.Bitmap? {
        val s0 = _ui.value
        val info = s0.info ?: return null
        if (s0.projectId.isEmpty()) return null
        val base = s0.segments?.takeIf { it.isNotEmpty() }
            ?: Planner.ranges(s0.activity, info.durationMs, s0.options)
        if (base.isEmpty()) return null
        val (ci, srcAbs) = EdlMap(base).locate(atOutMs)
        val clip = base[ci]
        val k = Src.idx(clip.startMs)
        val file: File = if (k == 0) {
            store.mediaFile(s0.projectId)
        } else {
            val x = s0.extras.firstOrNull { it.id == k } ?: return null
            if (x.image) return null
            File(store.dir(s0.projectId), x.file)
        }
        val lo = Src.local(clip.startMs)
        val hi = maxOf(lo, Src.local(clip.endMs) - 1L)
        val localMs = Src.local(srcAbs).coerceIn(lo, hi)
        return withContext(Dispatchers.IO) {
            val mmr = android.media.MediaMetadataRetriever()
            try {
                mmr.setDataSource(file.absolutePath)
                val b = mmr.getFrameAtTime(localMs * 1000L, android.media.MediaMetadataRetriever.OPTION_CLOSEST) ?: return@withContext null
                val kk = 540f / maxOf(b.width, b.height)
                if (kk >= 1f) b else android.graphics.Bitmap.createScaledBitmap(b, maxOf(1, (b.width * kk).toInt()), maxOf(1, (b.height * kk).toInt()), true)
            } catch (e: Exception) {
                null
            } finally {
                try { mmr.release() } catch (_: Exception) { }
            }
        }
    }

    /** Copies a picture into the project and sets it as the chroma key background of the clip under the playhead (key switched on, one undo step). */
    fun importKeyBackground(uri: Uri, atOutMs: Long) {
        val s0 = _ui.value
        if (s0.projectId.isEmpty()) return
        viewModelScope.launch {
            _ui.update { it.copy(message = "در حال افزودن پس‌زمینه…") }
            val r = try {
                withContext(Dispatchers.IO) { importLayerImage(s0.projectId, uri, "keybg_") }
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: Exception) {
                _ui.update { it.copy(message = "افزودن پس‌زمینه ناموفق بود: ${e.message}") }
                return@launch
            }
            val cur = _ui.value
            val info = cur.info
            if (cur.projectId != s0.projectId || info == null) {
                File(store.dir(s0.projectId), r.first).delete()
                return@launch
            }
            val base = cur.segments?.takeIf { it.isNotEmpty() }
                ?: Planner.ranges(cur.activity, info.durationMs, cur.options)
            if (base.isEmpty()) return@launch
            val ci = EdlMap(base).locate(atOutMs).first.coerceIn(0, base.size - 1)
            val cl = base[ci]
            val out = base.toMutableList()
            out[ci] = cl.copy(look = cl.look.copy(key = cl.look.key.copy(on = true, bgMode = 2, bgFile = r.first)))
            _ui.update { it.copy(message = "") }
            setSegments(out)
        }
    }

    fun showMessage(text: String) {
        _ui.update { it.copy(message = text) }
    }

    /** Changes one audio clip. Calls with the same [key] that follow each other closely (slider drags) are ONE undo step. */
    fun editAudio(id: Int, key: String, change: (AudioClip) -> AudioClip) {
        val cur = _ui.value
        val i = cur.audio.indexOfFirst { it.id == id }
        if (i < 0) return
        val now = System.currentTimeMillis()
        val k = "a$id|$key"
        if (k != lastEditKey || now - lastEditAt > 900L) {
            pushHistory()
            lastEditKey = k
        }
        lastEditAt = now
        val n = cur.audio.toMutableList()
        n[i] = AudioOps.fit(change(cur.audio[i]))
        setAudio(n, cur.selectedAudio)
    }

    private var cleaningAudio = false

    /**
     * Extracts the sound of the video as it is on the EDITED timeline (cuts, order, speed, extra videos) into an audio clip,
     * and mutes the original sound, so the result can get noise reduction / equaliser / volume / fades like any audio clip.
     * One undo step. The clip does not follow later cuts of the picture (same as any audio clip).
     */
    fun extractVideoAudio() {
        val s0 = _ui.value
        val uri = s0.video ?: return
        val ranges = currentRanges(s0) ?: return
        if (s0.projectId.isEmpty() || cleaningAudio) return
        val pieces = ArrayList<AudioMixer.Piece>()
        var at = 0L
        val dir = store.dir(s0.projectId)
        for (r in ranges) {
            val k = Src.idx(r.startMs)
            val x = if (k == 0) null else s0.extras.firstOrNull { it.id == k }
            if (k == 0 || (x != null && !x.image)) {
                val u = if (x == null) uri else Uri.fromFile(File(dir, x.file))
                pieces.add(AudioMixer.Piece(u, Src.local(r.startMs), Src.local(r.endMs), at, r.speed))
            }
            at += r.outLen
        }
        val total = at
        if (pieces.isEmpty() || total < 500L) {
            _ui.update { it.copy(message = "صدایی برای استخراج نیست") }
            return
        }
        if (total > 15 * 60_000L) {
            _ui.update { it.copy(message = "استخراج صدا برای ویدیوی بیشتر از ۱۵ دقیقه پشتیبانی نمی‌شود") }
            return
        }
        cleaningAudio = true
        viewModelScope.launch {
            _ui.update { it.copy(message = "در حال استخراج صدای ویدیو…") }
            val name = "audio_ext_${System.currentTimeMillis()}.wav"
            val dest = File(dir, name)
            try {
                val peaks = withContext(Dispatchers.IO) {
                    AudioMixer.mix(ctx, dest, total, pieces, 1f, emptyList(), dir, { }, { !isActive })
                    Waveform.ensure(ctx, dir, name, total)
                }
                val cur = _ui.value
                if (cur.projectId != s0.projectId) {
                    dest.delete()
                } else {
                    val n = cur.audio.count { !it.isVoice } + 1
                    val c = AudioOps.fit(AudioClip(AudioOps.nextId(cur.audio), AudioClip.KIND_MUSIC, name, "صدای ویدیو " + faNum(n), 0L, 0L, total, total))
                    pushHistory()
                    _ui.update {
                        counts(it.copy(
                            audio = it.audio + c, selectedAudio = c.id, mainMuted = true,
                            message = "صدای ویدیو به یک کلیپ صوتی تبدیل شد؛ صدای اصلی خاموش شد",
                            waves = if (peaks != null) it.waves + (name to peaks) else it.waves
                        ))
                    }
                }
            } catch (e: kotlinx.coroutines.CancellationException) {
                dest.delete()
                throw e
            } catch (e: Exception) {
                dest.delete()
                _ui.update { it.copy(message = "استخراج صدا ناموفق بود: ${e.message}") }
            } finally {
                cleaningAudio = false
            }
        }
    }

    /** Noise reduction of one audio clip (speech); see [AudioClean]. */
    fun cleanAudio(id: Int, strength: Float) =
        reprocessAudio(id, "در حال حذف نویز صدا… (چند ثانیه صبر کن)", "حذف نویز انجام شد", "حذف نویز") { src, dest, cancelled ->
            AudioClean.clean(ctx, src, dest, strength, cancelled)
        }

    /** Five-band equaliser on one audio clip; see [AudioEq]. */
    fun eqAudio(id: Int, bands: app.zircut.core.EqBands) {
        if (bands.isFlat) {
            _ui.update { it.copy(message = "همه‌ی باندها صفر است؛ چیزی تغییر نمی‌کند.") }
            return
        }
        reprocessAudio(id, "در حال اعمال اکولایزر…", "اکولایزر اعمال شد", "اکولایزر") { src, dest, cancelled ->
            AudioEq.apply(ctx, src, dest, bands, cancelled)
        }
    }

    /**
     * Runs [work] (source file -> new WAV) off the main thread and points the clip at the result (`audio_fx_*.wav`).
     * The old file stays in the project folder, so Undo works; preview and export play the same new file.
     */
    private fun reprocessAudio(id: Int, busy: String, done: String, what: String, work: (File, File, () -> Boolean) -> Boolean) {
        val s0 = _ui.value
        val clip = s0.audio.firstOrNull { it.id == id } ?: return
        if (cleaningAudio) return
        if (clip.fileMs > 15 * 60_000L) {
            _ui.update { it.copy(message = "فایل صوتی بیشتر از ۱۵ دقیقه است؛ برای $what اول آن را کوتاه کن.") }
            return
        }
        cleaningAudio = true
        viewModelScope.launch {
            _ui.update { it.copy(message = busy) }
            val name = "audio_fx_${System.currentTimeMillis()}.wav"
            val dir = store.dir(s0.projectId)
            try {
                val peaks = withContext(Dispatchers.IO) {
                    work(File(dir, clip.file), File(dir, name)) { !isActive }
                    Waveform.ensure(ctx, dir, name, clip.fileMs)
                }
                val cur = _ui.value
                val i = cur.audio.indexOfFirst { it.id == id }
                if (cur.projectId != s0.projectId || i < 0) {
                    File(dir, name).delete()
                    _ui.update { it.copy(message = "") }
                } else {
                    pushHistory()
                    val n = cur.audio.toMutableList()
                    n[i] = n[i].copy(file = name)
                    setAudio(n, cur.selectedAudio)
                    _ui.update { it.copy(message = done, waves = if (peaks != null) it.waves + (name to peaks) else it.waves) }
                }
            } catch (e: kotlinx.coroutines.CancellationException) {
                File(dir, name).delete()
                throw e
            } catch (e: Exception) {
                File(dir, name).delete()
                _ui.update { it.copy(message = "$what ناموفق بود: ${e.message}") }
            } finally {
                cleaningAudio = false
            }
        }
    }

    fun deleteAudio(id: Int) {
        val s = _ui.value
        if (s.audio.none { it.id == id }) return
        pushHistory()
        setAudio(s.audio.filter { it.id != id }, if (s.selectedAudio == id) -1 else s.selectedAudio)
    }

    fun duplicateAudio(id: Int) {
        val s = _ui.value
        val i = s.audio.indexOfFirst { it.id == id }
        if (i < 0) return
        val c = s.audio[i].let { it.copy(id = AudioOps.nextId(s.audio), startMs = it.endMs) }
        val n = s.audio.toMutableList()
        n.add(i + 1, c)
        pushHistory()
        setAudio(n, c.id)
    }

    /** Cuts the clip in two at [atMs] (edited timeline). False when a piece would be too short. */
    fun splitAudio(id: Int, atMs: Long): Boolean {
        val s = _ui.value
        val n = AudioOps.split(s.audio, id, atMs) ?: return false
        pushHistory()
        setAudio(n, id)
        return true
    }

    /** Cuts the silent parts out of one audio clip and closes the gaps (one undo step); [level] 0 = only very quiet, 2 = also soft background. */
    fun removeAudioSilence(id: Int, level: Int) {
        val s = _ui.value
        val clip = s.audio.firstOrNull { it.id == id } ?: return
        val pk = s.waves[clip.file]
        if (pk == null) {
            _ui.update { it.copy(message = "شکل موج این صدا هنوز ساخته نشده؛ چند ثانیه بعد دوباره امتحان کن") }
            return
        }
        val thr = when (level) { 0 -> 25; 1 -> 36; else -> 51 }
        val n = AudioOps.removeSilence(s.audio, id, pk, thr)
        if (n == null) {
            _ui.update { it.copy(message = "سکوت قابل‌حذفی پیدا نشد") }
            return
        }
        pushHistory()
        val before = s.audio.filter { it.id == id }.sumOf { it.lenMs }
        setAudio(n, id)
        val after = n.filter { c -> s.audio.none { it.id == c.id } || c.id == id }.sumOf { it.lenMs }
        _ui.update { it.copy(message = "سکوت حذف شد (حدود ${(before - after + 500L) / 1000L} ثانیه کوتاه‌تر)") }
    }

    fun setMainVolume(v: Float) {
        val now = System.currentTimeMillis()
        if (lastEditKey != "main|vol" || now - lastEditAt > 900L) {
            pushHistory()
            lastEditKey = "main|vol"
        }
        lastEditAt = now
        _ui.update { counts(it.copy(mainVolume = v.coerceIn(0f, 1f))) }
    }

    fun setDuckMusic(on: Boolean) {
        if (_ui.value.duckMusic == on) return
        pushHistory()
        _ui.update { counts(it.copy(duckMusic = on)) }
    }

    fun setMainMuted(m: Boolean) {
        if (_ui.value.mainMuted == m) return
        pushHistory()
        _ui.update { counts(it.copy(mainMuted = m)) }
    }

    // ---------- extra media (second video, photos) ----------
    fun mediaDir(projectId: String): File = store.dir(projectId)

    fun clearMessage() {
        _ui.update { if (it.message.isEmpty()) it else it.copy(message = "") }
    }

    /** Copies a picked video/photo into the project and inserts it as a clip at [atOutMs] on the edited timeline. */
    fun addMedia(uri: Uri, atOutMs: Long) {
        val s0 = _ui.value
        val info = s0.info ?: return
        if (s0.projectId.isEmpty()) return
        viewModelScope.launch {
            _ui.update { it.copy(message = "در حال افزودن رسانه…") }
            val extra = try {
                withContext(Dispatchers.IO) { importExtra(s0.projectId, uri, s0.extras) }
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: Exception) {
                _ui.update { it.copy(message = "افزودن رسانه ناموفق بود: ${e.message}") }
                return@launch
            }
            val cur = _ui.value
            val base = cur.segments?.takeIf { it.isNotEmpty() }
                ?: Planner.ranges(cur.activity, info.durationMs, cur.options)
            val r = EdlOps.insertAt(base, atOutMs, Range(Src.at(extra.id, 0L), Src.at(extra.id, extra.durationMs)))
            _ui.update { it.copy(extras = it.extras + extra, message = "") }
            setSegments(r.segs)
        }
    }

    /**
     * Freeze frame: the picture under the playhead is saved as a photo and put on the timeline as a photo clip of [lenMs],
     * exactly at the playhead (the clip under it is split there, so the video continues after the still).
     * It is a normal clip afterwards: it can be moved, trimmed, duplicated, deleted and undone. The still has no sound.
     */
    fun freezeFrame(atOutMs: Long, lenMs: Long) {
        val s0 = _ui.value
        val info = s0.info ?: return
        if (s0.projectId.isEmpty()) return
        val len = lenMs.coerceIn(200L, 30_000L)
        val base0 = s0.segments?.takeIf { it.isNotEmpty() }
            ?: Planner.ranges(s0.activity, info.durationMs, s0.options)
        if (base0.isEmpty()) return
        val (ci, srcAbs) = EdlMap(base0).locate(atOutMs)
        val clip = base0[ci]
        val k = Src.idx(clip.startMs)
        val file: File = if (k == 0) {
            store.mediaFile(s0.projectId)
        } else {
            val x = s0.extras.firstOrNull { it.id == k }
            if (x == null) {
                _ui.update { it.copy(message = "فایل این کلیپ پیدا نشد") }
                return
            }
            if (x.image) {
                _ui.update { it.copy(message = "این کلیپ عکسه؛ فریم ثابت فقط برای ویدیو است") }
                return
            }
            File(store.dir(s0.projectId), x.file)
        }
        val lo = Src.local(clip.startMs)
        val hi = maxOf(lo, Src.local(clip.endMs) - 1L)
        val localMs = Src.local(srcAbs).coerceIn(lo, hi)
        viewModelScope.launch {
            _ui.update { it.copy(message = "در حال ساخت فریم ثابت…") }
            val extra = try {
                withContext(Dispatchers.IO) { saveFrame(s0.projectId, file, localMs, _ui.value.extras, len) }
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: Exception) {
                _ui.update { it.copy(message = "ساخت فریم ثابت ناموفق بود: ${e.message}") }
                return@launch
            }
            val cur = _ui.value
            val base = cur.segments?.takeIf { it.isNotEmpty() }
                ?: Planner.ranges(cur.activity, info.durationMs, cur.options)
            val r = EdlOps.insertAt(base, atOutMs, Range(Src.at(extra.id, 0L), Src.at(extra.id, extra.durationMs)))
            _ui.update { it.copy(extras = it.extras + extra, message = "") }
            setSegments(r.segs)
        }
    }

    private fun saveFrame(projectId: String, video: File, atMs: Long, existing: List<ExtraMedia>, lenMs: Long): ExtraMedia {
        val id = (existing.maxOfOrNull { it.id } ?: 0) + 1
        val name = "extra_$id.jpg"
        val dest = File(store.dir(projectId), name)
        val mmr = android.media.MediaMetadataRetriever()
        try {
            mmr.setDataSource(video.absolutePath)
            val bmp = mmr.getFrameAtTime(atMs * 1000L, android.media.MediaMetadataRetriever.OPTION_CLOSEST)
                ?: throw java.io.IOException("فریم خوانده نشد")
            val w = bmp.width
            val h = bmp.height
            try {
                dest.outputStream().use { o -> bmp.compress(android.graphics.Bitmap.CompressFormat.JPEG, 95, o) }
            } catch (e: Exception) {
                dest.delete()
                throw e
            } finally {
                bmp.recycle()
            }
            return ExtraMedia(id, name, true, lenMs, w, h)
        } finally {
            try { mmr.release() } catch (_: Exception) {}
        }
    }

    private fun importExtra(projectId: String, uri: Uri, existing: List<ExtraMedia>): ExtraMedia {
        val mime = ctx.contentResolver.getType(uri) ?: ""
        val image = mime.startsWith("image/")
        val id = (existing.maxOfOrNull { it.id } ?: 0) + 1
        val ext = if (!image) "mp4" else if (mime.contains("png")) "png" else if (mime.contains("webp")) "webp" else "jpg"
        val name = "extra_$id.$ext"
        val dest = File(store.dir(projectId), name)
        val input = ctx.contentResolver.openInputStream(uri) ?: throw java.io.IOException("فایل خوانده نشد")
        input.use { i -> dest.outputStream().use { o -> i.copyTo(o) } }
        try {
            if (image) {
                val o = android.graphics.BitmapFactory.Options().apply { inJustDecodeBounds = true }
                android.graphics.BitmapFactory.decodeFile(dest.absolutePath, o)
                if (o.outWidth <= 0 || o.outHeight <= 0) throw java.io.IOException("تصویر نامعتبر است")
                return ExtraMedia(id, name, true, 5000L, o.outWidth, o.outHeight)
            }
            val vi = probe(Uri.fromFile(dest))
            return ExtraMedia(id, name, false, vi.durationMs, vi.width, vi.height)
        } catch (e: Exception) {
            dest.delete()
            throw e
        }
    }

    fun openProject(id: String) {
        viewModelScope.launch {
            val data = withContext(Dispatchers.IO) { store.load(id) }
            if (data == null || !store.mediaFile(id).exists()) {
                _ui.update { it.copy(stage = Stage.Failed("پروژه باز نشد؛ فایل پروژه یا ویدیوی آن پیدا نشد.")) }
                return@launch
            }
            _ui.update {
                it.copy(
                    video = Uri.fromFile(store.mediaFile(id)),
                    info = VideoInfo(data.meta.durationMs, data.meta.width, data.meta.height),
                    phrases = data.phrases, original = data.original, options = data.options,
                    styleId = data.styleId, layout = data.layout, grade = data.grade, fx = data.fx, exportSettings = data.export,
                    fontId = if (it.fonts.any { f -> f.id == data.fontId }) data.fontId else it.fontId,
                    projectId = id, projectName = data.meta.name, saveStatus = "saved",
                    segments = data.segments, undoCount = 0, redoCount = 0, extras = data.extras, layers = data.layers, selectedLayer = -1, audio = data.audio, selectedAudio = -1, mainVolume = data.mainVolume, mainMuted = data.mainMuted, duckMusic = data.duckMusic, waves = emptyMap(), message = "",
                    notice = "", debug = "", stage = Stage.Review, activity = emptyList(), activityFor = "", scanning = false
                )
            }
            undoStack.clear(); redoStack.clear()
            lastSavedKey = keyOf(_ui.value)
            loadWaves(id, data.audio)
            if (data.options.removeSilence) scanSilence()
        }
    }

    fun renameProject(id: String, name: String) {
        val n = name.trim()
        if (n.isEmpty()) return
        viewModelScope.launch {
            withContext(Dispatchers.IO) { store.rename(id, n) }
            if (_ui.value.projectId == id) _ui.update { it.copy(projectName = n) }
            refreshProjects()
        }
    }

    fun duplicateProject(id: String) {
        viewModelScope.launch {
            withContext(Dispatchers.IO) { try { store.duplicate(id) } catch (e: Exception) { null } }
            refreshProjects()
        }
    }

    fun deleteProject(id: String) {
        viewModelScope.launch {
            withContext(Dispatchers.IO) { store.delete(id) }
            refreshProjects()
        }
    }

    /** Folder of a project's poster frame, for the library cards. */
    fun thumbPath(id: String): String = store.thumbFile(id).absolutePath

    // ---------- settings ----------
    fun setTheme(id: String) { prefs.edit().putString("theme", id).apply(); _ui.update { it.copy(themeId = id) } }
    /** from here on the subtitle style, font, position and frame/options edits are part of Undo / Redo (only inside an open project) */
    private fun styleHistory(key: String) {
        if (_ui.value.projectId.isNotEmpty()) keyedHistory(key)
    }

    private fun applyStyle(id: String) {
        prefs.edit().putString("style", id).apply()
        val want = Styles.byId(id).font
        _ui.update { s ->
            val f = s.fonts.firstOrNull { it.name == want }?.id
            if (f != null) prefs.edit().putString("font", f).apply()
            counts(s.copy(styleId = id, fontId = f ?: s.fontId))
        }
    }

    fun setStyle(id: String) {
        if (_ui.value.styleId == id) return
        styleHistory("style")
        applyStyle(id)
    }

    fun setLayout(l: CapLayout) {
        if (_ui.value.layout == l) return
        styleHistory("layout")
        prefs.edit().putFloat("lx", l.x).putFloat("ly", l.y).putFloat("lmw", l.mw)
            .putFloat("lls", l.ls).putInt("llw", l.lw).putFloat("lsz", l.size).putInt("lan", l.anim).apply()
        _ui.update { counts(it.copy(layout = l)) }
    }

    fun setFont(id: String) {
        if (_ui.value.fontId == id) return
        styleHistory("font")
        prefs.edit().putString("font", id).apply()
        _ui.update { counts(it.copy(fontId = id)) }
    }
    fun setOptions(o: Options) {
        if (_ui.value.options == o) return
        styleHistory("options")
        _ui.update { counts(it.copy(options = o)) }
        if (o.removeSilence) scanSilence()
    }

    /** Analyses the video's own sound on the phone (loudness only, no model, no internet) so silences can be cut. Cached per project. */
    fun scanSilence() {
        val st = _ui.value
        val uri = st.video ?: return
        val pid = st.projectId
        if (pid.isEmpty() || st.scanning || st.activityFor == pid) return
        val busyText = "در حال تحلیل صدا…"
        _ui.update { it.copy(scanning = true, message = busyText) }
        viewModelScope.launch(Dispatchers.Default) {
            val cache = File(store.dir(pid), "activity.txt")
            var noAudio = false
            val spans: List<Word> = try {
                readActivity(cache) ?: run {
                    val audio = AudioDecoder.decode(ctx, uri)
                    if (audio.isEmpty()) noAudio = true
                    val found = SilenceScan.spans(audio)
                    try { writeActivity(cache, found) } catch (_: Exception) { }
                    found
                }
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: Throwable) {
                noAudio = true
                emptyList()
            }
            _ui.update { s ->
                if (s.projectId != pid) s.copy(scanning = false)
                else s.copy(
                    activity = spans, activityFor = pid, scanning = false,
                    message = if (noAudio) "این ویدیو صدا ندارد" else if (s.message == busyText) "" else s.message
                )
            }
        }
    }

    private fun readActivity(f: File): List<Word>? = try {
        if (!f.exists()) null else f.readLines().mapNotNull { l ->
            val p = l.split(' ')
            if (p.size < 2) null else Word("", p[0].toLong(), p[1].toLong())
        }
    } catch (e: Exception) { null }

    private fun writeActivity(f: File, l: List<Word>) {
        val tmp = File(f.parentFile, f.name + ".tmp")
        tmp.writeText(l.joinToString("\n") { it.startMs.toString() + " " + it.endMs })
        if (!tmp.renameTo(f)) { f.writeText(tmp.readText()); tmp.delete() }
    }

    private fun loadMyTemplates(): List<MyTemplate> = try {
        val a = org.json.JSONArray(prefs.getString("my_templates", "[]") ?: "[]")
        (0 until a.length()).mapNotNull { MyTemplate.fromJson(a.getJSONObject(it)) }
    } catch (e: Exception) {
        emptyList()
    }

    private fun storeMyTemplates(l: List<MyTemplate>) {
        val a = org.json.JSONArray()
        l.forEach { a.put(it.toJson()) }
        prefs.edit().putString("my_templates", a.toString()).apply()
        _ui.update { it.copy(myTemplates = l) }
    }

    /**
     * Applies a template recipe: frame shape, subtitle style, colour look and an optional title text layer at the start.
     * Everything (frame shape, subtitle style, look, title) is ONE undo step.
     */
    private fun applyRecipe(name: String, ratio: String, styleId: String, lookId: String, strength: Float, makeTitle: ((Int, Long, Long) -> Layer)?) {
        val s = _ui.value
        if (s.projectId.isEmpty()) return
        val total = currentRanges(s)?.let { EdlMap(it).total } ?: s.info?.durationMs ?: return
        val (a, b) = defaultSpan(0L, total)
        val title = makeTitle?.invoke(LayerOps.nextId(s.layers), a, b)
        pushHistory()
        applyStyle(styleId)
        _ui.update {
            counts(it.copy(
                options = it.options.copy(ratio = ratio, vertical = ratio != "orig"),
                grade = it.grade.copy(lookId = lookId, strength = strength),
                layers = if (title != null) it.layers + title else it.layers,
                selectedLayer = title?.id ?: it.selectedLayer,
                message = "قالب «$name» اعمال شد" + (if (title != null) "؛ متن عنوان را در تب لایه‌ها عوض کن" else "")
            ))
        }
    }

    fun applyTemplate(id: String) {
        val t = VideoTemplates.byId(id) ?: return
        applyRecipe(t.name, t.ratio, t.styleId, t.lookId, t.lookStrength) { lid, a, b ->
            val base = Layer(lid, Layer.KIND_TEXT, a, b, y = t.title.y, scale = t.title.scale, text = t.title.text, animIn = t.title.animIn)
            val st = TextStyles.presets.firstOrNull { it.name == t.title.styleName }?.style
            if (st != null) TextStyles.apply(base, st) else base
        }
    }

    fun applyMyTemplate(index: Int) {
        val t = _ui.value.myTemplates.getOrNull(index) ?: return
        val tt = t.title
        applyRecipe(t.name, t.ratio, t.styleId, t.lookId, t.lookStrength, if (tt == null) null else { lid, a, b ->
            Layer(lid, Layer.KIND_TEXT, a, b, y = tt.y, scale = tt.scale, text = tt.text, animIn = tt.animIn, color = tt.color, bgColor = tt.bgColor,
                stroke = tt.stroke, bold = tt.bold, italic = tt.italic, outlineColor = tt.outlineColor, fontId = tt.fontId)
        })
    }

    /** Saves the frame shape, subtitle style, look and the selected text layer (if one is selected) as a template of your own. */
    fun saveMyTemplate() {
        val s = _ui.value
        if (s.myTemplates.size >= 12) {
            _ui.update { it.copy(message = "حداکثر ۱۲ قالب شخصی؛ اول یکی را حذف کن") }
            return
        }
        val l = s.layers.firstOrNull { it.id == s.selectedLayer && it.isText && it.text.isNotBlank() }
        val title = l?.let { SavedTitle(it.text, it.y, it.scale, it.animIn, it.color, it.bgColor, it.stroke, it.bold, it.italic, it.outlineColor, it.fontId) }
        val n = "قالب من " + faNum(s.myTemplates.size + 1)
        storeMyTemplates(s.myTemplates + MyTemplate(n, Frame.id(s.options), s.styleId, s.grade.lookId, s.grade.strength, title))
        _ui.update { it.copy(message = "«$n» ذخیره شد" + (if (title != null) " (با متن انتخاب‌شده)" else " (بدون متن؛ برای ذخیره‌ی متن اول یک لایه‌ی متن را انتخاب کن)")) }
    }

    fun deleteMyTemplate(index: Int) {
        val l = _ui.value.myTemplates
        if (index !in l.indices) return
        storeMyTemplates(l.filterIndexed { i, _ -> i != index })
    }
    fun setGrade(g: ColorGrade) {
        if (_ui.value.grade == g) return
        val now = System.currentTimeMillis()
        if (lastEditKey != "grade" || now - lastEditAt > 900L) {
            pushHistory()
            lastEditKey = "grade"
        }
        lastEditAt = now
        _ui.update { counts(it.copy(grade = g)) }
    }
    fun setFx(f: Fx) {
        if (_ui.value.fx == f) return
        val now = System.currentTimeMillis()
        if (lastEditKey != "fx" || now - lastEditAt > 900L) {
            pushHistory()
            lastEditKey = "fx"
        }
        lastEditAt = now
        _ui.update { counts(it.copy(fx = f)) }
    }
    fun setExportSettings(e: ExportSettings) {
        if (_ui.value.exportSettings == e) return
        pushHistory()
        _ui.update { counts(it.copy(exportSettings = e)) }
    }
    fun addFont(uri: Uri) {
        viewModelScope.launch(Dispatchers.IO) {
            val name = ctx.contentResolver.query(uri, null, null, null, null)?.use { c ->
                val i = c.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                if (c.moveToFirst() && i >= 0) c.getString(i) else null
            }
            val id = fontStore.import(uri, name)
            val fonts = fontStore.list()
            _ui.update { it.copy(fonts = fonts, fontId = id ?: it.fontId) }
            if (id != null) prefs.edit().putString("font", id).apply()
        }
    }

    // ---------- flow ----------
    fun pickVideo(uri: Uri) {
        viewModelScope.launch {
            _ui.update { it.copy(stage = Stage.Working("در حال آماده‌سازی ویدیو…", 0f), video = uri, phrases = emptyList(), notice = "", grade = ColorGrade(), fx = Fx()) }
            // The picked file is copied into the app first: a video that lives in a cloud gallery (Google Photos, Mi Cloud)
            // would otherwise be read over the internet, which only works with a VPN.
            val local = try {
                copyToCache(uri) { p -> _ui.update { s -> s.copy(stage = Stage.Working("در حال آماده‌سازی ویدیو…", p)) } }
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: Exception) {
                _ui.update { it.copy(stage = Stage.Failed("ویدیو از حافظه‌ی گوشی خونده نشد (${e.message}). اگه ویدیو توی گوگل‌فوتوز یا گالری ابری بود، اول توی حافظه‌ی گوشی ذخیره‌ش کن و دوباره انتخابش کن.")) }
                return@launch
            }
            _ui.update { it.copy(video = local) }
            val info = try {
                withContext(Dispatchers.IO) { probe(local) }
            } catch (e: Exception) {
                _ui.update { it.copy(stage = Stage.Failed("این ویدیو باز نشد: ${e.message}")) }
                return@launch
            }
            // The editor opens right away. Speech recognition only starts when the user taps «زیرنویس خودکار».
            // Every picked video becomes a saved project: the video is kept inside the app, the edit is stored next to it.
            _ui.update { it.copy(stage = Stage.Working("در حال ساخت پروژه…", null)) }
            val created = try {
                withContext(Dispatchers.IO) {
                    store.create(File(local.path ?: throw java.io.IOException("مسیر فایل نامعتبر")), info, defaultProjectName(store.list()))
                }
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: Exception) {
                _ui.update { it.copy(stage = Stage.Failed("ساخت پروژه ناموفق بود: ${e.message}")) }
                return@launch
            }
            _ui.update {
                it.copy(
                    video = Uri.fromFile(store.mediaFile(created.id)), info = info, phrases = emptyList(), original = emptyList(),
                    options = it.options.copy(removeSilence = false), activity = emptyList(), activityFor = "", scanning = false,
                    perf = "", stage = Stage.Review, projectId = created.id, projectName = created.name, saveStatus = "saved",
                    segments = null, undoCount = 0, redoCount = 0, extras = emptyList(), layers = emptyList(), selectedLayer = -1, audio = emptyList(), selectedAudio = -1, mainVolume = 1f, mainMuted = false, waves = emptyMap(), message = ""
                )
            }
            undoStack.clear(); redoStack.clear()
            lastSavedKey = keyOf(_ui.value)
            persist(_ui.value)
        }
    }

    /** Copies the picked video into the app's cache (with progress) and gives back the local file. Gives up after 30 s without a single new byte. */
    private suspend fun copyToCache(src: Uri, onProgress: (Float) -> Unit): Uri {
        if (src.scheme == "file") return src
        val size = try {
            ctx.contentResolver.query(src, arrayOf(android.provider.OpenableColumns.SIZE), null, null, null)?.use { c -> if (c.moveToFirst()) c.getLong(0) else -1L } ?: -1L
        } catch (e: Exception) {
            -1L
        }
        val dir = java.io.File(ctx.cacheDir, "src").apply { mkdirs() }
        val f = java.io.File(dir, "v_" + (src.toString().hashCode().toLong() and 0xffffffffL) + "_" + size + ".mp4")
        if (f.exists() && f.length() > 0 && (size <= 0 || f.length() == size)) return Uri.fromFile(f)
        dir.listFiles()?.forEach { if (it != f) it.delete() }
        val tmp = java.io.File(dir, f.name + ".part")
        val copied = java.util.concurrent.atomic.AtomicLong(0)
        val failure = java.util.concurrent.atomic.AtomicReference<Throwable?>(null)
        val stream = java.util.concurrent.atomic.AtomicReference<java.io.InputStream?>(null)
        val worker = Thread {
            try {
                val ins = ctx.contentResolver.openInputStream(src) ?: throw java.io.IOException("فایل باز نشد")
                stream.set(ins)
                ins.use { i ->
                    tmp.outputStream().use { out ->
                        val buf = ByteArray(256 * 1024)
                        while (true) {
                            val n = i.read(buf)
                            if (n < 0) break
                            out.write(buf, 0, n)
                            copied.addAndGet(n.toLong())
                        }
                    }
                }
            } catch (t: Throwable) {
                failure.set(t)
            }
        }
        worker.isDaemon = true
        worker.start()
        var last = -1L
        var lastChange = System.currentTimeMillis()
        while (worker.isAlive) {
            kotlinx.coroutines.delay(400)
            val v = copied.get()
            if (v != last) {
                last = v
                lastChange = System.currentTimeMillis()
                if (size > 0) onProgress((v.toFloat() / size).coerceIn(0f, 1f))
            }
            if (System.currentTimeMillis() - lastChange > 30_000) {
                try { stream.get()?.close() } catch (_: Exception) { }
                tmp.delete()
                throw java.io.IOException("۳۰ ثانیه هیچ داده‌ای نیومد")
            }
        }
        failure.get()?.let {
            tmp.delete()
            throw java.io.IOException(it.message ?: it.javaClass.simpleName)
        }
        if (size > 0 && tmp.length() != size) {
            tmp.delete()
            throw java.io.IOException("فایل ناقص کپی شد")
        }
        if (!tmp.renameTo(f)) throw java.io.IOException("ذخیره‌ی فایل نشد")
        return Uri.fromFile(f)
    }

    private fun probe(uri: Uri): VideoInfo {
        val r = MediaMetadataRetriever()
        try {
            r.setDataSource(ctx, uri)
            val dur = r.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull() ?: 0L
            var w = r.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH)?.toIntOrNull() ?: 0
            var h = r.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT)?.toIntOrNull() ?: 0
            val rot = r.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_ROTATION)?.toIntOrNull() ?: 0
            if (rot == 90 || rot == 270) { val t = w; w = h; h = t }
            if (dur <= 0 || w <= 0 || h <= 0) throw IllegalStateException("اطلاعات ویدیو ناقصه")
            return VideoInfo(dur, w, h)
        } finally {
            r.release()
        }
    }

    private var wake: android.os.PowerManager.WakeLock? = null

    private fun lockOn() {
        try {
            if (wake == null) {
                val pm = ctx.getSystemService(Context.POWER_SERVICE) as android.os.PowerManager
                wake = pm.newWakeLock(android.os.PowerManager.PARTIAL_WAKE_LOCK, "zircut:work")
            }
            if (wake?.isHeld != true) wake?.acquire(30 * 60 * 1000L)
        } catch (_: Exception) {
        }
    }

    private fun lockOff() {
        try {
            if (wake?.isHeld == true) wake?.release()
        } catch (_: Exception) {
        }
    }


    /** Common recognition mistakes that are always wrong; the user's own corrections are added on top of these. */
    private fun builtinFixes(): Map<String, String> = mapOf(
        "سانیه" to "ثانیه",
        "سانیه‌ی" to "ثانیه‌ی",
        "ثانیع" to "ثانیه",
        "دقیغه" to "دقیقه",
        "پونسد" to "پونصد",
        "استفا" to "استعفا",
        "حیام" to "پیام",
        "میخچاهیم" to "می‌خوام",
        "می‌خچاهیم" to "می‌خوام",
        "کنچکاوت" to "کنجکاوت",
        "کنچکاو" to "کنجکاو",
        "پوریس" to "پلیس"
    )

    /** Two-word mistakes that are never real Persian. */
    private val pairFixes: Map<String, Pair<String, String>> = mapOf(
        "ته سانیه" to ("بیست" to "ثانیه"),
        "ته ثانیه" to ("بیست" to "ثانیه"),
        "چجی پیتی" to ("چت‌جی" to "پی‌تی")
    )

    private fun fixKey(t: String): String = t.trim().trim { !it.isLetterOrDigit() }.lowercase()

    private fun loadFixes(): Map<String, String> = try {
        val o = org.json.JSONObject(prefs.getString("fixes", "{}") ?: "{}")
        o.keys().asSequence().associateWith { o.getString(it) }
    } catch (e: Exception) {
        emptyMap()
    }

    /** Remembers the words the user corrected, so the same mistake is fixed automatically next time. */
    private fun learnFixes(orig: List<Phrase>, now: List<Phrase>) {
        if (orig.size != now.size) return
        val m = loadFixes().toMutableMap()
        for (i in orig.indices) {
            if (orig[i].words.size != now[i].words.size) continue
            for (j in orig[i].words.indices) {
                val a = fixKey(orig[i].words[j].text)
                val b = fixKey(now[i].words[j].text)
                if (a.length >= 2 && b.length >= 2 && a != b && !b.contains(' ')) m[a] = b
            }
        }
        while (m.size > 500) m.remove(m.keys.first())
        prefs.edit().putString("fixes", org.json.JSONObject(m as Map<*, *>).toString()).apply()
    }

    fun editPhrase(index: Int, text: String) {
        // typing is ONE undo step while the keys follow each other closely
        val now = System.currentTimeMillis()
        if (lastEditKey != "phrase$index" || now - lastEditAt > 900L) {
            pushHistory()
            lastEditKey = "phrase$index"
        }
        lastEditAt = now
        _ui.update { s ->
            if (index !in s.phrases.indices) return@update s
            val list = s.phrases.toMutableList()
            list[index] = Planner.rebuild(list[index], text)
            counts(s.copy(phrases = list))
        }
    }

    fun mergeWithNext(i: Int) {
        if (i < 0 || i >= _ui.value.phrases.size - 1) return
        pushHistory()
        _ui.update { s ->
            if (i < 0 || i >= s.phrases.size - 1) s else {
                val l = s.phrases.toMutableList()
                l[i] = Phrase(l[i].words + l[i + 1].words)
                l.removeAt(i + 1)
                counts(s.copy(phrases = l))
            }
        }
    }

    /** Cuts every subtitle into pieces of at most [maxWords] words (the words keep their own timing). One undo step. */
    fun splitShort(maxWords: Int) {
        val s0 = _ui.value
        val n = maxWords.coerceIn(1, 12)
        if (s0.phrases.none { it.words.size > n }) {
            _ui.update { it.copy(message = "همه‌ی زیرنویس‌ها از این کوتاه‌ترند") }
            return
        }
        pushHistory()
        val out = ArrayList<Phrase>()
        for (p in s0.phrases) {
            if (p.words.size <= n) { out.add(p); continue }
            // equal-sized pieces (7 words with limit 4 -> 4 + 3, not 4 + 3 + ... odd leftovers)
            val parts = (p.words.size + n - 1) / n
            val size = (p.words.size + parts - 1) / parts
            p.words.chunked(size).forEach { out.add(Phrase(it)) }
        }
        _ui.update { counts(it.copy(phrases = out, message = "زیرنویس‌ها به بخش‌های کوتاه تقسیم شد")) }
    }

    /** Moves the start and/or end of one subtitle by a few milliseconds (first word start, last word end). */
    fun nudgePhrase(i: Int, startDelta: Long, endDelta: Long) {
        val s = _ui.value
        if (i !in s.phrases.indices) return
        val p = s.phrases[i]
        val w = p.words.toMutableList()
        val f = w.first()
        val l = w.last()
        if (w.size == 1) {
            val ns = (f.startMs + startDelta).coerceAtLeast(0L)
            val ne = (f.endMs + endDelta).coerceAtLeast(ns + 100L)
            w[0] = f.copy(startMs = ns, endMs = ne)
        } else {
            w[0] = f.copy(startMs = (f.startMs + startDelta).coerceIn(0L, maxOf(0L, f.endMs - 30L)))
            w[w.size - 1] = l.copy(endMs = (l.endMs + endDelta).coerceAtLeast(l.startMs + 30L))
        }
        val now = System.currentTimeMillis()
        if (lastEditKey != "nudge$i" || now - lastEditAt > 900L) {
            pushHistory()
            lastEditKey = "nudge$i"
        }
        lastEditAt = now
        _ui.update { st -> counts(st.copy(phrases = st.phrases.toMutableList().also { it[i] = Phrase(w) })) }
    }

    /** Drag of a subtitle's start ([edge] 0) or end ([edge] 1) on the timeline: [outMs] is the new place on the EDITED timeline. */
    fun trimPhraseAtOut(i: Int, edge: Int, outMs: Long) {
        val s = _ui.value
        val ranges = currentRanges(s) ?: return
        if (i !in s.phrases.indices) return
        val map = EdlMap(ranges)
        val op = app.zircut.timeline.outPhrasesOf(s.phrases, map).firstOrNull { it.idx == i } ?: return
        val origOut = if (edge == 0) op.startMs else op.endMs
        // a point just inside the subtitle tells which clip it is drawn on
        val probe = (if (edge == 0) origOut + 1L else origOut - 1L).coerceIn(0L, map.total)
        val ci = map.locate(probe).first
        val r = map.segs[ci]
        if (app.zircut.timeline.Src.idx(r.startMs) != 0) return
        val os = map.outStart(ci)
        fun srcOf(out: Long): Long = r.startMs + Math.round((out - os).coerceIn(0L, r.outLen) * r.speed.toDouble())
        val delta = srcOf(outMs) - srcOf(origOut)
        if (delta == 0L) return
        if (edge == 0) nudgePhrase(i, delta, 0L) else nudgePhrase(i, 0L, delta)
    }

    /** Moves the start and/or end of ONE word of a subtitle (the highlight follows the word's start). Neighbouring words keep their order. */
    fun nudgeWord(pi: Int, wi: Int, startDelta: Long, endDelta: Long) {
        val s = _ui.value
        if (pi !in s.phrases.indices) return
        val w = s.phrases[pi].words.toMutableList()
        if (wi !in w.indices) return
        val c = w[wi]
        val minStart = if (wi > 0) w[wi - 1].startMs + 30L else 0L
        val ns = (c.startMs + startDelta).coerceIn(minStart, maxOf(minStart, c.endMs - 30L))
        val maxEnd = if (wi < w.size - 1) w[wi + 1].endMs - 30L else Long.MAX_VALUE
        val ne = (c.endMs + endDelta).coerceIn(ns + 30L, maxOf(ns + 30L, maxEnd))
        if (ns == c.startMs && ne == c.endMs) return
        w[wi] = c.copy(startMs = ns, endMs = ne)
        val now = System.currentTimeMillis()
        if (lastEditKey != "nudgew$pi" || now - lastEditAt > 900L) {
            pushHistory()
            lastEditKey = "nudgew$pi"
        }
        lastEditAt = now
        _ui.update { st -> counts(st.copy(phrases = st.phrases.toMutableList().also { it[pi] = Phrase(w) })) }
    }

    fun deletePhrase(i: Int) {
        if (i !in _ui.value.phrases.indices) return
        pushHistory()
        _ui.update { s ->
            if (i !in s.phrases.indices) s else counts(s.copy(phrases = s.phrases.toMutableList().also { it.removeAt(i) }))
        }
    }

    fun cancel() {
        exporter?.cancel()
        job?.cancel()
        _ui.update { it.copy(stage = if (it.phrases.isNotEmpty() || (it.video != null && it.info != null)) Stage.Review else Stage.Idle) }
    }

    fun reset() {
        job?.cancel()
        _ui.update { it.copy(stage = Stage.Idle, video = null, info = null, phrases = emptyList(), projectId = "", projectName = "", saveStatus = "", segments = null, undoCount = 0, redoCount = 0, extras = emptyList(), layers = emptyList(), selectedLayer = -1, audio = emptyList(), selectedAudio = -1, mainVolume = 1f, mainMuted = false, waves = emptyMap(), message = "") }
    }

    fun backToReview() { _ui.update { it.copy(stage = Stage.Review) } }

    // ---------- export ----------
    fun export() {
        val st = _ui.value
        val uri = st.video ?: return
        val info = st.info ?: return
        val words = st.phrases.flatMap { it.words }
        val ranges = st.segments?.takeIf { it.isNotEmpty() } ?: Planner.ranges(st.activity, info.durationMs, st.options)
        val missing = ranges.any { r -> val k = Src.idx(r.startMs); k != 0 && st.extras.none { it.id == k } }
        if (missing) {
            _ui.update { it.copy(stage = Stage.Failed("رسانه‌ی یکی از کلیپ‌ها پیدا نشد. آن کلیپ را از تایم‌لاین حذف کن.")) }
            return
        }
        val layerDir = store.dir(st.projectId)
        if (st.layers.any { !it.isText && !File(layerDir, it.file).exists() }) {
            _ui.update { it.copy(stage = Stage.Failed("فایل عکس یکی از لایه‌ها پیدا نشد. آن لایه را از تب «لایه» حذف کن.")) }
            return
        }
        // clip times are source-tagged (Src); the exporter wants plain times inside each clip's own file
        var localRanges = ranges.map { it.copy(startMs = Src.local(it.startMs), endMs = Src.local(it.endMs), speed = 1f) }
        var sources: List<Exporter.ClipSource>? =
            if (ranges.any { Src.idx(it.startMs) != 0 }) {
                ranges.map { r ->
                    val k = Src.idx(r.startMs)
                    val x = st.extras.firstOrNull { it.id == k }
                    if (k == 0 || x == null) Exporter.ClipSource(uri, false)
                    else Exporter.ClipSource(Uri.fromFile(File(store.dir(st.projectId), x.file)), x.image, x.width, x.height)
                }
            } else null
        val font = st.fonts.firstOrNull { it.id == st.fontId }?.typeface ?: Typeface.DEFAULT_BOLD
        val layerFont: (String) -> Typeface = { id -> st.fonts.firstOrNull { it.id == id }?.typeface ?: font }
        val out = File(ctx.cacheDir, "zircut_out.mp4").also { it.delete() }
        Diag.reset()
        try { learnFixes(st.original, st.phrases) } catch (_: Exception) { }
        cleanSpeedTemp()
        lockOn()
        val first = st.phrases.firstOrNull()
        val dbg = st.perf + "\n" + "عبارت‌ها ${st.phrases.size} · کلمه‌ها ${words.size} · بازه‌ها ${ranges.size} · مجموع ${ranges.sumOf { it.len }}ms از ${info.durationMs}ms · زیرنویس=${st.options.captions} · عمودی=${st.options.vertical}" +
            (if (first != null) "\nاولین عبارت: «${first.words.joinToString(" ") { it.text }}» از ${first.startMs} تا ${first.endMs}ms" else "\nهیچ عبارتی وجود ندارد")
        _ui.update { it.copy(debug = dbg, stage = Stage.Working("در حال ساخت ویدیو…", 0f)) }

        fun startEx(mix: File?) {
        val ex = Exporter(ctx)
        exporter = ex
        ex.start(
            Exporter.Params(uri, info, localRanges, st.phrases, Styles.byId(st.styleId), font, st.options, out, st.layout, st.grade, sources, captionRanges(ranges), st.layers, layerDir, layerFont, mix, st.fx, st.exportSettings),
            onProgress = { p -> _ui.update { s -> if (s.stage is Stage.Working) s.copy(stage = Stage.Working("در حال ساخت ویدیو…", p)) else s } },
            onDone = {
                lockOff()
                mix?.delete()
                cleanSpeedTemp()
                viewModelScope.launch {
                    try {
                        val saved = withContext(Dispatchers.IO) { saveToGallery(out) }
                        val title = st.phrases.firstOrNull()?.text?.take(60) ?: "ویدیوی بدون گفتار"
                        val list = (listOf(Recent(title, saved.toString(), System.currentTimeMillis())) + _ui.value.recent)
                            .distinctBy { it.uri }.take(20)
                        writeRecent(list)
                        _ui.update { it.copy(debug = it.debug + "\n" + Diag.text(), stage = Stage.Done(saved), recent = list) }
                    } catch (e: Exception) {
                        _ui.update { it.copy(stage = Stage.Failed("ذخیره ویدیو نشد: ${e.message}")) }
                    }
                }
            },
            onError = { msg -> lockOff(); mix?.delete(); cleanSpeedTemp(); _ui.update { it.copy(stage = Stage.Failed(msg)) } }
        )
        }

        // The video's sound and every music / voice-over clip are mixed into one WAV first; with no audio edits the
        // exporter keeps using the original sound of the clips exactly like before part 5.
        val mg = if (st.mainMuted) 0f else st.mainVolume
        val hasSpeed = ranges.any { it.speed != 1f }
        if (st.audio.isEmpty() && mg == 1f && !hasSpeed) {
            startEx(null)
            return
        }
        val dirNow = store.dir(st.projectId)
        val pieces = ArrayList<AudioMixer.Piece>()
        var at = 0L
        for (r in ranges) {
            val k = Src.idx(r.startMs)
            val x = if (k == 0) null else st.extras.firstOrNull { it.id == k }
            if (k == 0 || (x != null && !x.image)) {
                val u = if (x == null) uri else Uri.fromFile(File(dirNow, x.file))
                pieces.add(AudioMixer.Piece(u, Src.local(r.startMs), Src.local(r.endMs), at, r.speed))
            }
            at += r.outLen
        }
        val totalMs = at
        val mixFile = File(ctx.cacheDir, "zircut_mix.wav").also { it.delete() }
        _ui.update { it.copy(stage = Stage.Working("در حال میکس صدا…", 0f)) }
        job?.cancel()
        job = viewModelScope.launch {
            val cj = coroutineContext[Job]
            try {
                if (hasSpeed) {
                    // clips with a speed get a picture-only copy with scaled time stamps first (no re-encoding, fast)
                    _ui.update { it.copy(stage = Stage.Working("در حال اعمال سرعت کلیپ‌ها…", 0f)) }
                    val rr = ArrayList<Range>()
                    val ss = ArrayList<Exporter.ClipSource>()
                    withContext(Dispatchers.IO) {
                        ranges.forEachIndexed { i, r ->
                            val lr = localRanges[i]
                            val cs = sources?.getOrNull(i) ?: Exporter.ClipSource(uri, false)
                            if (r.speed == 1f || cs.image) {
                                rr.add(lr); ss.add(cs)
                            } else {
                                val f = File(ctx.cacheDir, "zircut_speed_$i.mp4")
                                val res = SpeedRemux.make(ctx, cs.uri, lr.startMs, lr.endMs, r.speed, f) { cj?.isActive == false }
                                rr.add(lr.copy(startMs = res.startMs, endMs = res.endMs, speed = 1f))
                                ss.add(Exporter.ClipSource(Uri.fromFile(f), false, cs.w, cs.h))
                            }
                        }
                    }
                    localRanges = rr
                    sources = ss
                    _ui.update { it.copy(stage = Stage.Working("در حال میکس صدا…", 0f)) }
                }
                withContext(Dispatchers.IO) {
                    AudioMixer.mix(
                        ctx, mixFile, totalMs, pieces, mg, st.audio, dirNow,
                        { p -> _ui.update { s -> if (s.stage is Stage.Working) s.copy(stage = Stage.Working("در حال میکس صدا…", p)) else s } },
                        { cj?.isActive == false },
                        duck = st.duckMusic,
                        speech = if (st.duckMusic && mg > 0f) app.zircut.timeline.SpeechMap.of(
                            app.zircut.timeline.outPhrasesOf(st.phrases, EdlMap(ranges)).map { it.startMs to it.endMs }
                        ) else null
                    )
                }
            } catch (e: kotlinx.coroutines.CancellationException) {
                lockOff()
                mixFile.delete()
                cleanSpeedTemp()
                throw e
            } catch (e: Throwable) {
                lockOff()
                mixFile.delete()
                cleanSpeedTemp()
                _ui.update { it.copy(stage = Stage.Failed("میکس صدا ناموفق بود: ${e.message}")) }
                return@launch
            }
            startEx(mixFile)
        }
    }

    private fun cleanSpeedTemp() {
        try { ctx.cacheDir.listFiles()?.filter { it.name.startsWith("zircut_speed_") }?.forEach { it.delete() } } catch (_: Exception) { }
    }

    private fun saveToGallery(file: File): Uri {
        val resolver = ctx.contentResolver
        val values = ContentValues().apply {
            put(MediaStore.Video.Media.DISPLAY_NAME, "ZirCut_${System.currentTimeMillis()}.mp4")
            put(MediaStore.Video.Media.MIME_TYPE, "video/mp4")
            put(MediaStore.Video.Media.RELATIVE_PATH, "Movies/ZirCut")
            put(MediaStore.Video.Media.IS_PENDING, 1)
        }
        val uri = resolver.insert(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, values)
            ?: throw IllegalStateException("دسترسی به گالری ممکن نشد")
        resolver.openOutputStream(uri)!!.use { o -> file.inputStream().use { it.copyTo(o) } }
        values.clear()
        values.put(MediaStore.Video.Media.IS_PENDING, 0)
        resolver.update(uri, values, null, null)
        file.delete()
        return uri
    }
}
