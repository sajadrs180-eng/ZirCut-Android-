package app.zircut.core

import android.annotation.SuppressLint
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import java.io.File
import java.io.RandomAccessFile
import kotlin.math.abs

/**
 * Records the microphone (mono, 44.1 kHz, 16-bit) into a WAV file. The caller must already hold the RECORD_AUDIO permission.
 * [stop] and [cancel] always release the microphone.
 */
class VoiceRecorder(val file: File) {
    private var rec: AudioRecord? = null
    private var thread: Thread? = null
    @Volatile private var running = false
    @Volatile private var frames = 0L

    /** loudness of the last moment, 0..1 (for the level meter) */
    @Volatile var level = 0f
        private set

    val elapsedMs: Long get() = frames * 1000L / SR

    /** false when the microphone could not be opened (busy, no permission) */
    @SuppressLint("MissingPermission")
    fun start(): Boolean {
        if (running) return true
        try {
            val minBuf = AudioRecord.getMinBufferSize(SR, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT)
            if (minBuf <= 0) return false
            val bufSize = maxOf(minBuf * 2, SR / 5 * 2)
            val r = AudioRecord(MediaRecorder.AudioSource.MIC, SR, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT, bufSize)
            if (r.state != AudioRecord.STATE_INITIALIZED) {
                r.release()
                return false
            }
            val os = java.io.FileOutputStream(file)
            os.write(AudioMixer.wavHeader(0L, 1, SR))
            r.startRecording()
            rec = r
            running = true
            frames = 0L
            val t = Thread {
                val buf = ShortArray(bufSize / 2)
                val bytes = ByteArray(buf.size * 2)
                try {
                    while (running) {
                        val n = r.read(buf, 0, buf.size)
                        if (n <= 0) {
                            if (n < 0) break
                            continue
                        }
                        var peak = 0
                        for (k in 0 until n) {
                            val v = buf[k].toInt()
                            bytes[k * 2] = (v and 0xFF).toByte()
                            bytes[k * 2 + 1] = ((v shr 8) and 0xFF).toByte()
                            val a = abs(v)
                            if (a > peak) peak = a
                        }
                        os.write(bytes, 0, n * 2)
                        frames += n
                        level = (peak / 32768f).coerceIn(0f, 1f)
                    }
                } catch (_: Exception) {
                } finally {
                    try { os.close() } catch (_: Exception) { }
                }
            }
            thread = t
            t.start()
            return true
        } catch (e: Exception) {
            cleanup()
            return false
        }
    }

    private fun cleanup() {
        running = false
        try { rec?.stop() } catch (_: Exception) { }
        try { rec?.release() } catch (_: Exception) { }
        rec = null
    }

    /** Stops, fixes the WAV header and returns the recorded length in ms (0 when nothing was recorded). */
    fun stop(): Long {
        if (rec == null) return 0L
        cleanup()
        try { thread?.join(1500) } catch (_: Exception) { }
        thread = null
        level = 0f
        val bytes = frames * 2L
        if (frames <= 0L) {
            file.delete()
            return 0L
        }
        try {
            RandomAccessFile(file, "rw").use { raf ->
                raf.seek(0)
                raf.write(AudioMixer.wavHeader(bytes, 1, SR))
            }
        } catch (_: Exception) {
            return 0L
        }
        return frames * 1000L / SR
    }

    /** Stops and throws the recording away. */
    fun cancel() {
        if (rec != null) {
            cleanup()
            try { thread?.join(1500) } catch (_: Exception) { }
            thread = null
        }
        file.delete()
    }

    companion object {
        const val SR = 44100
    }
}
