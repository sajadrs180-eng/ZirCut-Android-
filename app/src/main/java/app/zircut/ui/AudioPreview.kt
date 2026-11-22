package app.zircut.ui

import android.content.Context
import android.media.MediaPlayer
import android.net.Uri
import app.zircut.timeline.AudioClip
import app.zircut.timeline.AudioOps
import java.io.File

/**
 * Plays the music / voice-over clips next to the video while the editor plays.
 * One MediaPlayer per clip that is (about to be) audible; players are created just before a clip starts and
 * released as soon as it is over or the playhead leaves it, so memory stays small.
 * The volume of every moment comes from [AudioOps.gainAt], the same function the export mixer uses.
 */
class AudioPreview(private val ctx: Context) {
    private class Voice(val mp: MediaPlayer) {
        var ready = false
        var started = false
        var graceUntil = 0L
        var clip: AudioClip? = null
    }

    private val voices = HashMap<Int, Voice>()

    /** Call every frame of the editor loop. Cheap when there is no audio. */
    fun update(clips: List<AudioClip>, posMs: Long, playing: Boolean, dir: File, duck: Boolean = false, speech: app.zircut.timeline.SpeechMap? = null) {
        if (clips.isEmpty() && voices.isEmpty()) return
        val alive = HashSet<Int>()
        for (c in clips) {
            val inWindow = playing && !c.muted && c.volume > 0f && posMs >= c.startMs - LOOKAHEAD && posMs < c.endMs
            if (!inWindow) continue
            alive.add(c.id)
            var v = voices[c.id]
            // the clip was edited (other file part or position): start over with a fresh player
            if (v != null && v.clip != null && (v.clip!!.file != c.file || v.clip!!.inMs != c.inMs || v.clip!!.startMs != c.startMs)) {
                release(c.id)
                v = null
            }
            if (v == null) {
                v = open(c, dir) ?: continue
                voices[c.id] = v
            }
            v.clip = c
            if (!v.ready) continue
            val t = posMs - c.startMs
            val expected = (t + c.inMs).coerceAtLeast(0L)
            try {
                if (t < 0L) {
                    // not yet its turn: wait at the clip's first sample
                    if (v.started) { v.mp.pause(); v.started = false }
                    continue
                }
                if (!v.started) {
                    v.mp.seekTo(expected, MediaPlayer.SEEK_CLOSEST)
                    v.mp.start()
                    v.started = true
                    v.graceUntil = System.currentTimeMillis() + 600L
                } else if (System.currentTimeMillis() > v.graceUntil && Math.abs(v.mp.currentPosition - expected) > DRIFT) {
                    v.mp.seekTo(expected, MediaPlayer.SEEK_CLOSEST)
                    v.graceUntil = System.currentTimeMillis() + 600L
                }
                val g = AudioOps.gainAt(c, t.toDouble()) * (if (duck) AudioOps.duckFactor(clips, c, posMs.toDouble(), speech) else 1f)
                v.mp.setVolume(g, g)
            } catch (_: Exception) {
            }
        }
        for (id in voices.keys.toList()) if (id !in alive) release(id)
    }

    private fun open(c: AudioClip, dir: File): Voice? {
        val f = File(dir, c.file)
        if (!f.exists()) return null
        return try {
            val mp = MediaPlayer()
            val v = Voice(mp)
            v.clip = c
            mp.setDataSource(ctx, Uri.fromFile(f))
            mp.setVolume(0f, 0f)
            mp.setOnPreparedListener { v.ready = true }
            mp.setOnErrorListener { _, _, _ -> v.ready = false; true }
            mp.prepareAsync()
            v
        } catch (e: Exception) {
            null
        }
    }

    private fun release(id: Int) {
        val v = voices.remove(id) ?: return
        try { v.mp.release() } catch (_: Exception) { }
    }

    fun release() {
        for (id in voices.keys.toList()) release(id)
    }

    companion object {
        /** a player is prepared this long before its clip begins */
        private const val LOOKAHEAD = 1500L
        /** the player may be this far off before it is moved back to the right place */
        private const val DRIFT = 220L
    }
}
