package app.zircut.ui

import android.content.Context
import android.media.MediaPlayer
import android.media.PlaybackParams
import android.net.Uri
import android.view.Surface

/**
 * One MediaPlayer that can show any video of the project (main video or an extra one).
 * Switching to another file re-prepares the player; staying inside the same file is just a seek.
 * Photo clips never use it (the editor shows the picture and counts time itself).
 */
class PreviewPlayer(private val ctx: Context) {
    /** source index -> file; set by the editor on every composition so it is always current */
    var uriOf: (Int) -> Uri? = { null }

    private var surface: Surface? = null
    private var mp: MediaPlayer? = null
    private var loaded = -1
    private var ready = false
    private var wantSeek = 0L
    private var wantPlay = false

    /** volume of the video's own sound (0..1); kept here so it survives switching files */
    private var volume = 1f

    /** playback speed of the clip that is showing (1 = normal); the sound keeps its pitch */
    private var speed = 1f

    fun setSpeed(s: Float) {
        val ns = s.coerceIn(0.25f, 4f)
        if (ns == speed) return
        speed = ns
        val m = mp ?: return
        // only a running player takes the new speed now; a stopped one gets it when it starts
        try { if (ready && m.isPlaying) m.playbackParams = PlaybackParams().setSpeed(speed) } catch (_: Exception) { }
    }

    fun setVolume(v: Float) {
        volume = v.coerceIn(0f, 1f)
        try { mp?.setVolume(volume, volume) } catch (_: Exception) { }
    }

    val isReady: Boolean get() = ready
    val loadedIdx: Int get() = loaded

    fun setSurface(s: Surface?) {
        surface = s
        try { mp?.setSurface(s) } catch (_: Exception) { }
    }

    /** Show source [idx] at [localMs]; keep playing/start playing if [play]. */
    fun enter(idx: Int, localMs: Long, play: Boolean) {
        wantSeek = localMs
        wantPlay = play
        if (loaded == idx && ready) apply() else load(idx)
    }

    private fun apply() {
        val m = mp ?: return
        try {
            m.setVolume(volume, volume)
            m.seekTo(wantSeek, MediaPlayer.SEEK_CLOSEST)
            if (wantPlay) {
                if (!m.isPlaying) {
                    try { m.playbackParams = PlaybackParams().setSpeed(speed) } catch (_: Exception) { }
                    m.start()
                }
            } else if (m.isPlaying) {
                m.pause()
            }
        } catch (_: Exception) {
        }
    }

    private fun load(idx: Int) {
        val uri = uriOf(idx) ?: return
        ready = false
        loaded = idx
        try {
            val existing = mp
            val m: MediaPlayer
            if (existing == null) {
                m = MediaPlayer()
                mp = m
            } else {
                existing.reset()
                m = existing
            }
            m.setDataSource(ctx, uri)
            m.setSurface(surface)
            m.setOnPreparedListener {
                ready = true
                apply()
            }
            m.prepareAsync()
        } catch (_: Exception) {
            ready = false
        }
    }

    fun pause() {
        wantPlay = false
        try {
            val m = mp
            if (m != null && ready && m.isPlaying) m.pause()
        } catch (_: Exception) {
        }
    }

    /** position inside the loaded file in ms, or -1 when nothing is ready */
    fun positionMs(): Long = try {
        val m = mp
        if (m != null && ready) m.currentPosition.toLong() else -1L
    } catch (_: Exception) {
        -1L
    }

    fun release() {
        ready = false
        loaded = -1
        try { mp?.release() } catch (_: Exception) { }
        mp = null
    }
}
