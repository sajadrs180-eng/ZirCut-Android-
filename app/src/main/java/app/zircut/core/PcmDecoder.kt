package app.zircut.core

import android.content.Context
import android.media.AudioFormat
import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import android.net.Uri
import java.io.File
import java.io.RandomAccessFile
import java.nio.ByteOrder

/**
 * Decodes any audio (or the sound of a video) to one fixed format: 44.1 kHz, stereo, 16-bit, interleaved.
 * The audio mixer and the waveform builder both work on this format. Data is handed over in small blocks,
 * so nothing big is kept in memory.
 */
object PcmDecoder {
    const val SR = 44100

    /** Receives interleaved stereo frames: [count] frames = 2 * count shorts. */
    fun interface Sink {
        fun write(buf: ShortArray, frames: Int)
    }

    /** linear-interpolating sample-rate converter that keeps its state between blocks */
    private class Resampler(srcSr: Int, private val out: Sink) {
        private val step = srcSr.toDouble() / SR
        private var pos = 0.0
        private var prevL = 0f
        private var prevR = 0f
        private val ob = ShortArray(8192 * 2)
        private var on = 0

        private fun put(l: Float, r: Float) {
            ob[on * 2] = (l.coerceIn(-1f, 1f) * 32767f).toInt().toShort()
            ob[on * 2 + 1] = (r.coerceIn(-1f, 1f) * 32767f).toInt().toShort()
            on++
            if (on == 8192) flush()
        }

        fun flush() {
            if (on > 0) out.write(ob, on)
            on = 0
        }

        fun push(l: FloatArray, r: FloatArray, n: Int) {
            if (n <= 0) return
            while (pos < n - 1) {
                val k0 = Math.floor(pos).toInt()
                val f = (pos - k0).toFloat()
                val l0 = if (k0 < 0) prevL else l[k0]
                val r0 = if (k0 < 0) prevR else r[k0]
                val l1 = l[k0 + 1]
                val r1 = r[k0 + 1]
                put(l0 + (l1 - l0) * f, r0 + (r1 - r0) * f)
                pos += step
            }
            pos -= n
            prevL = l[n - 1]
            prevR = r[n - 1]
        }
    }

    /** Length of an audio file or video in ms, 0 when unknown. */
    fun durationMs(ctx: Context, uri: Uri): Long = try {
        val r = android.media.MediaMetadataRetriever()
        try {
            r.setDataSource(ctx, uri)
            r.extractMetadata(android.media.MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull() ?: 0L
        } finally {
            r.release()
        }
    } catch (e: Exception) {
        0L
    }

    /** true when the file has an audio track */
    fun hasAudio(ctx: Context, uri: Uri): Boolean {
        val ex = MediaExtractor()
        return try {
            ex.setDataSource(ctx, uri, null)
            (0 until ex.trackCount).any { (ex.getTrackFormat(it).getString(MediaFormat.KEY_MIME) ?: "").startsWith("audio/") }
        } catch (e: Exception) {
            false
        } finally {
            ex.release()
        }
    }

    /**
     * Decodes [fromMs, toMs) of the file's audio into [sink]. Returns the number of frames written (0 when the file
     * has no audio). [cancelled] is polled often. Throws on a real decoder failure.
     */
    fun decode(ctx: Context, uri: Uri, fromMs: Long, toMs: Long, sink: Sink, cancelled: () -> Boolean = { false }): Long {
        val path = uri.path
        if (uri.scheme == "file" && path != null && path.endsWith(".wav", ignoreCase = true)) {
            val n = decodeWav(File(path), fromMs, toMs, sink, cancelled)
            if (n >= 0L) return n
        }
        return decodeCodec(ctx, uri, fromMs, toMs, sink, cancelled)
    }

    private fun decodeCodec(ctx: Context, uri: Uri, fromMs: Long, toMs: Long, sink: Sink, cancelled: () -> Boolean): Long {
        val ex = MediaExtractor()
        var codec: MediaCodec? = null
        var written = 0L
        try {
            ex.setDataSource(ctx, uri, null)
            var track = -1
            var fmt: MediaFormat? = null
            for (i in 0 until ex.trackCount) {
                val f = ex.getTrackFormat(i)
                if ((f.getString(MediaFormat.KEY_MIME) ?: "").startsWith("audio/")) {
                    track = i
                    fmt = f
                    break
                }
            }
            if (track < 0 || fmt == null) return 0L
            ex.selectTrack(track)
            if (fromMs > 0L) ex.seekTo(fromMs * 1000L, MediaExtractor.SEEK_TO_PREVIOUS_SYNC)
            val mime = fmt.getString(MediaFormat.KEY_MIME)!!
            val c = MediaCodec.createDecoderByType(mime)
            codec = c
            c.configure(fmt, null, null, 0)
            c.start()

            var sr = if (fmt.containsKey(MediaFormat.KEY_SAMPLE_RATE)) fmt.getInteger(MediaFormat.KEY_SAMPLE_RATE) else SR
            var ch = if (fmt.containsKey(MediaFormat.KEY_CHANNEL_COUNT)) fmt.getInteger(MediaFormat.KEY_CHANNEL_COUNT) else 2
            var floatPcm = false
            val counting = Sink { b, n ->
                sink.write(b, n)
                written += n
            }
            var rs = Resampler(sr, counting)

            val fromUs = fromMs * 1000L
            val toUs = toMs * 1000L
            val info = MediaCodec.BufferInfo()
            var inputDone = false
            var outputDone = false
            var idle = 0
            var lf = FloatArray(4096)
            var rf = FloatArray(4096)
            while (!outputDone) {
                if (cancelled()) throw java.util.concurrent.CancellationException("cancelled")
                if (!inputDone) {
                    val ii = c.dequeueInputBuffer(10_000)
                    if (ii >= 0) {
                        val buf = c.getInputBuffer(ii)!!
                        val n = ex.readSampleData(buf, 0)
                        if (n < 0 || ex.sampleTime > toUs + 2_000_000L) {
                            c.queueInputBuffer(ii, 0, 0, 0, MediaCodec.BUFFER_FLAG_END_OF_STREAM)
                            inputDone = true
                        } else {
                            c.queueInputBuffer(ii, 0, n, ex.sampleTime, 0)
                            ex.advance()
                        }
                    }
                }
                val oi = c.dequeueOutputBuffer(info, 10_000)
                if (oi == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
                    val of = c.outputFormat
                    if (of.containsKey(MediaFormat.KEY_SAMPLE_RATE)) {
                        val nsr = of.getInteger(MediaFormat.KEY_SAMPLE_RATE)
                        if (nsr != sr) {
                            rs.flush()
                            sr = nsr
                            rs = Resampler(sr, counting)
                        }
                    }
                    if (of.containsKey(MediaFormat.KEY_CHANNEL_COUNT)) ch = of.getInteger(MediaFormat.KEY_CHANNEL_COUNT)
                    floatPcm = of.containsKey(MediaFormat.KEY_PCM_ENCODING) &&
                        of.getInteger(MediaFormat.KEY_PCM_ENCODING) == AudioFormat.ENCODING_PCM_FLOAT
                    idle = 0
                } else if (oi >= 0) {
                    idle = 0
                    var reached = false
                    if (info.size > 0 && ch >= 1) {
                        val ob = c.getOutputBuffer(oi)!!
                        ob.position(info.offset)
                        ob.limit(info.offset + info.size)
                        ob.order(ByteOrder.nativeOrder())
                        val bytesPer = if (floatPcm) 4 else 2
                        val frames = info.size / (bytesPer * ch)
                        // frames before the wanted start (the extractor seeks to the previous sync frame) are dropped
                        val skip = if (info.presentationTimeUs < fromUs)
                            Math.round((fromUs - info.presentationTimeUs) * sr / 1_000_000.0).toInt() else 0
                        val end = minOf(
                            frames.toLong(),
                            Math.ceil((toUs - info.presentationTimeUs) * sr / 1_000_000.0).toLong().coerceAtLeast(0L)
                        ).toInt()
                        if (end > skip) {
                            val cnt = end - skip
                            if (lf.size < cnt) { lf = FloatArray(cnt); rf = FloatArray(cnt) }
                            if (floatPcm) {
                                val fb = ob.asFloatBuffer()
                                for (k in 0 until cnt) {
                                    val base = (skip + k) * ch
                                    lf[k] = fb.get(base)
                                    rf[k] = if (ch >= 2) fb.get(base + 1) else lf[k]
                                }
                            } else {
                                val sb = ob.asShortBuffer()
                                for (k in 0 until cnt) {
                                    val base = (skip + k) * ch
                                    lf[k] = sb.get(base) / 32768f
                                    rf[k] = if (ch >= 2) sb.get(base + 1) / 32768f else lf[k]
                                }
                            }
                            rs.push(lf, rf, cnt)
                        }
                        if (info.presentationTimeUs + frames * 1_000_000L / sr >= toUs) reached = true
                    }
                    c.releaseOutputBuffer(oi, false)
                    if (reached || (info.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0 || info.presentationTimeUs >= toUs) outputDone = true
                } else if (oi == MediaCodec.INFO_TRY_AGAIN_LATER) {
                    if (inputDone && ++idle > 300) outputDone = true
                }
            }
            rs.flush()
            return written
        } finally {
            try { codec?.stop() } catch (_: Exception) { }
            try { codec?.release() } catch (_: Exception) { }
            ex.release()
        }
    }

    /** Plain 16-bit PCM WAV read directly (the recorder writes these). Returns -1 when the file is not such a WAV. */
    private fun decodeWav(f: File, fromMs: Long, toMs: Long, sink: Sink, cancelled: () -> Boolean): Long {
        RandomAccessFile(f, "r").use { raf ->
            val h = ByteArray(12)
            if (raf.length() < 44) return -1L
            raf.readFully(h)
            if (String(h, 0, 4) != "RIFF" || String(h, 8, 4) != "WAVE") return -1L
            var ch = 0
            var sr = 0
            var bits = 0
            var fmtTag = 0
            var dataPos = -1L
            var dataLen = 0L
            while (raf.filePointer + 8 <= raf.length()) {
                val ck = ByteArray(8)
                raf.readFully(ck)
                val id = String(ck, 0, 4)
                val sz = le32(ck, 4)
                if (id == "fmt ") {
                    val b = ByteArray(minOf(sz.toInt(), 16))
                    raf.readFully(b)
                    fmtTag = le16(b, 0)
                    ch = le16(b, 2)
                    sr = le32(b, 4).toInt()
                    bits = le16(b, 14)
                    if (sz > 16) raf.seek(raf.filePointer + (sz - 16))
                } else if (id == "data") {
                    dataPos = raf.filePointer
                    dataLen = minOf(sz, raf.length() - dataPos)
                    break
                } else {
                    raf.seek(raf.filePointer + sz + (sz and 1L))
                }
            }
            if (dataPos < 0 || fmtTag != 1 || bits != 16 || ch !in 1..2 || sr < 8000) return -1L
            val bytesPerFrame = 2 * ch
            val totalFrames = dataLen / bytesPerFrame
            val startF = (fromMs * sr / 1000L).coerceIn(0L, totalFrames)
            val endF = (toMs * sr / 1000L).coerceIn(startF, totalFrames)
            raf.seek(dataPos + startF * bytesPerFrame)
            var written = 0L
            val rs = Resampler(sr, Sink { b, n -> sink.write(b, n); written += n })
            val chunk = 4096
            val bytes = ByteArray(chunk * bytesPerFrame)
            val lf = FloatArray(chunk)
            val rf = FloatArray(chunk)
            var left = endF - startF
            while (left > 0) {
                if (cancelled()) throw java.util.concurrent.CancellationException("cancelled")
                val n = minOf(left, chunk.toLong()).toInt()
                raf.readFully(bytes, 0, n * bytesPerFrame)
                for (k in 0 until n) {
                    val o = k * bytesPerFrame
                    val l = (bytes[o].toInt() and 0xFF) or (bytes[o + 1].toInt() shl 8)
                    lf[k] = l.toShort() / 32768f
                    rf[k] = if (ch == 2) (((bytes[o + 2].toInt() and 0xFF) or (bytes[o + 3].toInt() shl 8)).toShort() / 32768f) else lf[k]
                }
                rs.push(lf, rf, n)
                left -= n
            }
            rs.flush()
            return written
        }
    }

    private fun le16(b: ByteArray, o: Int): Int = (b[o].toInt() and 0xFF) or ((b[o + 1].toInt() and 0xFF) shl 8)
    private fun le32(b: ByteArray, o: Int): Long =
        (le16(b, o).toLong()) or (le16(b, o + 2).toLong() shl 16)
}
