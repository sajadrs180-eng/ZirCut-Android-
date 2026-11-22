package app.zircut.core

import android.content.Context
import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import android.media.MediaMuxer
import android.net.Uri
import java.io.File
import java.io.IOException
import java.nio.ByteBuffer

/**
 * Speed of a video clip for the export: the compressed picture of [fromMs, toMs) is copied (no re-encoding) into a new,
 * picture-only MP4 whose time stamps are divided by the speed. Transformer then trims and encodes that file like any other clip.
 * Fast clips (2x) simply have their frames closer together; slow motion (0.5x) keeps every frame longer (no frame blending).
 * The sound is NOT copied: it is rebuilt by [AudioMixer] (same speed, pitch kept).
 */
object SpeedRemux {
    /** [startMs, endMs) is the wanted part inside the new file (the file also holds the frames from the key frame before it). */
    class Result(val startMs: Long, val endMs: Long)

    fun make(ctx: Context, src: Uri, fromMs: Long, toMs: Long, speed: Float, out: File, cancelled: () -> Boolean): Result {
        val sp = speed.toDouble().coerceIn(0.1, 8.0)
        val ex = MediaExtractor()
        var mux: MediaMuxer? = null
        var started = false
        try {
            ex.setDataSource(ctx, src, null)
            var vt = -1
            for (i in 0 until ex.trackCount) {
                val mime = ex.getTrackFormat(i).getString(MediaFormat.KEY_MIME) ?: ""
                if (mime.startsWith("video/")) { vt = i; break }
            }
            if (vt < 0) throw IOException("ویدیو برای اعمال سرعت پیدا نشد")
            ex.selectTrack(vt)
            val fmt = ex.getTrackFormat(vt)
            ex.seekTo(fromMs * 1000L, MediaExtractor.SEEK_TO_PREVIOUS_SYNC)
            val syncUs = maxOf(0L, ex.sampleTime)

            out.delete()
            val m = MediaMuxer(out.absolutePath, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)
            mux = m
            if (fmt.containsKey(MediaFormat.KEY_ROTATION)) {
                try { m.setOrientationHint(fmt.getInteger(MediaFormat.KEY_ROTATION)) } catch (_: Exception) { }
            }
            val ti = m.addTrack(fmt)
            m.start()
            started = true

            val cap = maxOf(1 shl 20, if (fmt.containsKey(MediaFormat.KEY_MAX_INPUT_SIZE)) fmt.getInteger(MediaFormat.KEY_MAX_INPUT_SIZE) else 0)
            val buf = ByteBuffer.allocate(cap)
            val info = MediaCodec.BufferInfo()
            // frames a little after the end are kept too: they come before the last wanted frame in display order
            val endUs = toMs * 1000L + 1_000_000L
            var last = -1L
            var n = 0
            while (true) {
                if (cancelled()) throw java.util.concurrent.CancellationException("cancelled")
                val t = ex.sampleTime
                if (t < 0L || t > endUs) break
                buf.clear()
                val size = ex.readSampleData(buf, 0)
                if (size < 0) break
                var pts = Math.round((t - syncUs) / sp)
                if (pts <= last) pts = last + 1L
                last = pts
                val flags = if ((ex.sampleFlags and MediaExtractor.SAMPLE_FLAG_SYNC) != 0) MediaCodec.BUFFER_FLAG_KEY_FRAME else 0
                info.set(0, size, pts, flags)
                m.writeSampleData(ti, buf, info)
                n++
                if (!ex.advance()) break
            }
            if (n == 0) throw IOException("هیچ فریمی برای اعمال سرعت خوانده نشد")
            val startOut = Math.round((fromMs * 1000L - syncUs) / sp / 1000.0).coerceAtLeast(0L)
            val lenOut = Math.round((toMs - fromMs) / sp).coerceAtLeast(1L)
            return Result(startOut, startOut + lenOut)
        } finally {
            try { if (started) mux?.stop() } catch (_: Exception) { }
            try { mux?.release() } catch (_: Exception) { }
            try { ex.release() } catch (_: Exception) { }
        }
    }
}
