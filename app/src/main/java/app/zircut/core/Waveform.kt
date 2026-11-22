package app.zircut.core

import android.content.Context
import android.net.Uri
import java.io.File
import kotlin.math.abs
import kotlin.math.sqrt

/**
 * Real waveform of an audio file: the file is decoded and the loudest sample of every 20 ms is kept
 * (one byte each, 50 per second). The result is stored next to the audio file in the project folder (`<file>.pk`),
 * so it is built only once.
 */
object Waveform {
    const val PER_SEC = 50
    private const val BUCKET = PcmDecoder.SR / PER_SEC

    fun peaksFile(dir: File, audioFile: String) = File(dir, "$audioFile.pk")

    fun load(dir: File, audioFile: String): ByteArray? = try {
        val f = peaksFile(dir, audioFile)
        if (f.exists() && f.length() > 0L) f.readBytes() else null
    } catch (e: Exception) {
        null
    }

    /** Builds (or reads) the peaks of one file. Heavy: call off the main thread. */
    fun ensure(ctx: Context, dir: File, audioFile: String, durationMs: Long): ByteArray? {
        load(dir, audioFile)?.let { return it }
        return try {
            val peaks = build(ctx, Uri.fromFile(File(dir, audioFile)), durationMs)
            if (peaks.isNotEmpty()) peaksFile(dir, audioFile).writeBytes(peaks)
            peaks.takeIf { it.isNotEmpty() }
        } catch (e: Exception) {
            null
        }
    }

    private fun build(ctx: Context, uri: Uri, durationMs: Long): ByteArray {
        var out = ByteArray(maxOf(256, (durationMs / 1000L * PER_SEC).toInt() + 64))
        var n = 0
        var cur = 0
        var inBucket = 0
        PcmDecoder.decode(ctx, uri, 0L, if (durationMs > 0L) durationMs + 500L else 36_000_000L, { buf, frames ->
            for (k in 0 until frames) {
                val a = maxOf(abs(buf[k * 2].toInt()), abs(buf[k * 2 + 1].toInt()))
                if (a > cur) cur = a
                if (++inBucket == BUCKET) {
                    if (n == out.size) out = out.copyOf(out.size * 2)
                    // square root: quiet parts stay visible instead of looking like a flat line
                    out[n++] = (sqrt(cur / 32768f) * 255f).toInt().coerceIn(0, 255).toByte()
                    cur = 0
                    inBucket = 0
                }
            }
        })
        if (inBucket > 0) {
            if (n == out.size) out = out.copyOf(out.size + 1)
            out[n++] = (sqrt(cur / 32768f) * 255f).toInt().coerceIn(0, 255).toByte()
        }
        return out.copyOf(n)
    }
}
