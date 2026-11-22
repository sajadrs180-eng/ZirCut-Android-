package app.zircut.core

import android.content.Context
import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import android.net.Uri
import java.nio.ByteOrder
import kotlin.math.sqrt

object AudioDecoder {
    private const val TRIM_SILENCE = false
    private const val TARGET_SR = 16000

    private class Growable {
        var data = FloatArray(1 shl 20)
        var size = 0
        fun add(v: Float) {
            if (size == data.size) data = data.copyOf(data.size * 2)
            data[size++] = v
        }
        fun toArray(): FloatArray = data.copyOf(size)
    }

    /**
     * Brings the audio down to 16 kHz with a proper low-pass filter first. Plain interpolation (used before)
     * folds everything above 8 kHz back into the speech band, which smears consonants (ص/س, ع, پ/ح, گ/ر ...).
     */
    private class Resampler(sr: Int, private val out: Growable) {
        private val step = sr.toDouble() / TARGET_SR
        private val pass = step <= 1.001
        private val hw = if (pass) 0 else Math.ceil(12 * step).toInt()
        private val taps = 2 * hw
        private val phases = 64
        private val table = Array(phases) { FloatArray(taps) }
        private var pend = FloatArray(1 shl 16)
        private var pendLen = 0
        private var base = 0L
        private var m = 0L
        private var prev = 0f
        private var phase = 0.0

        init {
            if (!pass) {
                val fc = 0.43 / step
                for (ph in 0 until phases) {
                    val frac = ph.toDouble() / phases
                    var sum = 0.0
                    val row = DoubleArray(taps)
                    for (k in 0 until taps) {
                        val t = (k - hw + 1) - frac
                        val x = 2 * fc * t
                        val sinc = if (Math.abs(x) < 1e-9) 1.0 else Math.sin(Math.PI * x) / (Math.PI * x)
                        val w = 0.54 + 0.46 * Math.cos(Math.PI * t / hw)
                        row[k] = 2 * fc * sinc * w
                        sum += row[k]
                    }
                    for (k in 0 until taps) table[ph][k] = (row[k] / sum).toFloat()
                }
            }
        }

        fun push(mono: FloatArray) {
            if (pass) {
                val n = mono.size
                var p = phase
                while (true) {
                    val i = p.toInt()
                    if (i + 1 > n) break
                    val a = if (i == 0) prev else mono[i - 1]
                    val b = mono[i]
                    out.add(a + (b - a) * (p - i).toFloat())
                    p += step
                }
                phase = p - n
                if (n > 0) prev = mono[n - 1]
                return
            }
            if (pendLen + mono.size > pend.size) pend = pend.copyOf(maxOf(pend.size * 2, pendLen + mono.size))
            System.arraycopy(mono, 0, pend, pendLen, mono.size)
            pendLen += mono.size
            while (true) {
                val pos = m * step
                val i = pos.toLong()
                if (i + hw >= base + pendLen) break
                val ph = minOf(phases - 1, ((pos - i) * phases).toInt())
                val start = (i - hw + 1 - base).toInt()
                val t = table[ph]
                var acc = 0f
                for (k in 0 until taps) {
                    val idx = start + k
                    if (idx >= 0) acc += pend[idx] * t[k]
                }
                out.add(acc)
                m++
            }
            val keepFrom = (m * step).toLong() - hw + 1
            val drop = minOf((keepFrom - base).toInt(), pendLen)
            if (drop > 0) {
                System.arraycopy(pend, drop, pend, 0, pendLen - drop)
                pendLen -= drop
                base += drop
            }
        }
    }

    fun decode(ctx: Context, uri: Uri, onProgress: (Float) -> Unit = {}): FloatArray {
        val ex = MediaExtractor()
        ex.setDataSource(ctx, uri, null)
        var track = -1
        var fmt: MediaFormat? = null
        for (i in 0 until ex.trackCount) {
            val f = ex.getTrackFormat(i)
            val m = f.getString(MediaFormat.KEY_MIME) ?: continue
            if (m.startsWith("audio/")) {
                track = i
                fmt = f
                break
            }
        }
        if (track < 0 || fmt == null) {
            ex.release()
            throw IllegalStateException("این ویدیو صدا ندارد")
        }
        ex.selectTrack(track)
        val mime = fmt.getString(MediaFormat.KEY_MIME)!!
        val durationUs = if (fmt.containsKey(MediaFormat.KEY_DURATION)) fmt.getLong(MediaFormat.KEY_DURATION) else 0L
        var sr = fmt.getInteger(MediaFormat.KEY_SAMPLE_RATE)
        var ch = fmt.getInteger(MediaFormat.KEY_CHANNEL_COUNT)

        val codec = MediaCodec.createDecoderByType(mime)
        codec.configure(fmt, null, null, 0)
        codec.start()

        val out = Growable()
        val info = MediaCodec.BufferInfo()
        var inputDone = false
        var outputDone = false
        var rs: Resampler? = null
        var idle = 0

        try {
            while (!outputDone) {
                if (!inputDone) {
                    val idx = codec.dequeueInputBuffer(10_000)
                    if (idx >= 0) {
                        val buf = codec.getInputBuffer(idx)!!
                        val n = ex.readSampleData(buf, 0)
                        if (n < 0) {
                            codec.queueInputBuffer(idx, 0, 0, 0, MediaCodec.BUFFER_FLAG_END_OF_STREAM)
                            inputDone = true
                        } else {
                            val t = ex.sampleTime
                            codec.queueInputBuffer(idx, 0, n, t, 0)
                            if (durationUs > 0) onProgress((t.toFloat() / durationUs).coerceIn(0f, 1f))
                            ex.advance()
                        }
                    }
                }
                val oi = codec.dequeueOutputBuffer(info, 10_000)
                // Some phones never send the end-of-stream flag for certain AAC tracks and the loop would wait forever at 99%.
                if (oi == MediaCodec.INFO_TRY_AGAIN_LATER) {
                    if (inputDone && ++idle > 300) break
                } else {
                    idle = 0
                }
                if (oi == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
                    val nf = codec.outputFormat
                    sr = nf.getInteger(MediaFormat.KEY_SAMPLE_RATE)
                    ch = nf.getInteger(MediaFormat.KEY_CHANNEL_COUNT)
                    rs = null
                } else if (oi >= 0) {
                    if (info.size > 0) {
                        val ob = codec.getOutputBuffer(oi)!!
                        ob.position(info.offset)
                        ob.limit(info.offset + info.size)
                        val sb = ob.order(ByteOrder.nativeOrder()).asShortBuffer()
                        val frames = sb.remaining() / ch
                        val mono = FloatArray(frames)
                        for (f in 0 until frames) {
                            var s = 0f
                            for (c in 0 until ch) s += sb.get() / 32768f
                            mono[f] = s / ch
                        }
                        val r = rs ?: Resampler(sr, out).also { rs = it }
                        r.push(mono)
                    }
                    codec.releaseOutputBuffer(oi, false)
                    if (info.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0) outputDone = true
                }
            }
        } finally {
            try { codec.stop() } catch (_: Exception) {}
            codec.release()
            ex.release()
        }
        return out.toArray()
    }

    /** A block of audio for recognition. [map] holds {sourceStartMs, packedStartMs, lenMs} when speech parts were packed together. */
    class Chunk(val startMs: Long, val samples: FloatArray, val map: List<LongArray>? = null) {
        fun toSrc(t: Long): Long {
            val m = map ?: return startMs + t
            if (m.isEmpty()) return startMs + t
            var e = m[0]
            for (x in m) if (x[1] <= t) e = x else break
            return e[0] + minOf(maxOf(0L, t - e[1]), e[2])
        }
    }

    /**
     * Drops silence before recognition and packs the speech parts into full windows,
     * so Whisper spends time only on speech. Falls back to plain chunking when there is little to save.
     */
    fun chunkSpeech(audio: FloatArray): List<Chunk> {
        // Silence trimming is switched off: plain chunking never cuts speech by mistake.
        if (!TRIM_SILENCE) return chunk(audio)
        val sr = TARGET_SR
        val frame = sr * 30 / 1000
        val n = audio.size / frame
        if (n < 50) return chunk(audio)
        val e = FloatArray(n)
        for (i in 0 until n) {
            var s = 0f
            for (k in i * frame until (i + 1) * frame) s += audio[k] * audio[k]
            e[i] = sqrt(s / frame)
        }
        val sorted = e.copyOf().also { it.sort() }
        val floor = sorted[(n * 0.2f).toInt().coerceIn(0, n - 1)]
        val thr = maxOf(0.006f, floor * 2.5f)
        // speech frames -> segments, closing short gaps and dropping tiny blips
        val segs = ArrayList<IntArray>()
        var i = 0
        while (i < n) {
            if (e[i] > thr) {
                var j = i
                var last = i
                while (j < n && j - last <= 15) {
                    if (e[j] > thr) last = j
                    j++
                }
                if (last - i >= 6) segs.add(intArrayOf(i, last + 1))
                i = last + 1
            } else {
                i++
            }
        }
        if (segs.isEmpty()) return chunk(audio)
        val pad = 7
        val padded = ArrayList<IntArray>()
        for (s in segs) {
            val a = maxOf(0, s[0] - pad)
            val b = minOf(n, s[1] + pad)
            if (padded.isNotEmpty() && a <= padded.last()[1]) padded.last()[1] = b else padded.add(intArrayOf(a, b))
        }
        val speechFrames = padded.sumOf { it[1] - it[0] }
        if (speechFrames > n * 0.9) return chunk(audio)

        val maxLen = 28 * sr
        val gap = sr * 3 / 10
        val res = ArrayList<Chunk>()
        var cur = ArrayList<FloatArray>()
        var curMap = ArrayList<LongArray>()
        var curLen = 0
        fun flush() {
            if (curLen > 0) {
                val out = FloatArray(curLen)
                var p = 0
                for (c in cur) {
                    System.arraycopy(c, 0, out, p, c.size)
                    p += c.size
                }
                res.add(Chunk(0L, out, curMap))
            }
            cur = ArrayList()
            curMap = ArrayList()
            curLen = 0
        }
        for (s in padded) {
            val from = s[0] * frame
            val to = minOf(audio.size, s[1] * frame)
            val len = to - from
            if (len > maxLen) {
                flush()
                for (c in chunk(audio.copyOfRange(from, to))) {
                    res.add(Chunk(from * 1000L / sr + c.startMs, c.samples))
                }
                continue
            }
            if (curLen > 0 && curLen + gap + len > maxLen) flush()
            if (curLen > 0) {
                cur.add(FloatArray(gap))
                curLen += gap
            }
            curMap.add(longArrayOf(from * 1000L / sr, curLen * 1000L / sr, len * 1000L / sr))
            cur.add(audio.copyOfRange(from, to))
            curLen += len
        }
        flush()
        return res
    }

    /** Splits audio into ~28s windows, cutting at the quietest moment near the end. Silent windows are skipped. */
    /** Removes low rumble and brings quiet speech up to a good level before recognition. */
    private fun cleanAudio(x: FloatArray): FloatArray {
        val y = FloatArray(x.size)
        var px = 0f
        var py = 0f
        var peak = 0f
        for (i in x.indices) {
            val v = x[i] - px + 0.995f * py
            px = x[i]
            py = v
            y[i] = v
            val a = if (v < 0f) -v else v
            if (a > peak) peak = a
        }
        val g = if (peak > 0.001f) minOf(0.9f / peak, 8f) else 1f
        if (g > 1.05f) for (i in y.indices) y[i] *= g
        return y
    }

    fun chunk(audio: FloatArray, seconds: Int = 28): List<Chunk> {
        val sr = TARGET_SR
        val target = seconds * sr
        val search = minOf(4, maxOf(1, seconds / 3)) * sr
        val frame = sr / 10
        val res = ArrayList<Chunk>()
        var start = 0
        while (start < audio.size) {
            var end = minOf(start + target, audio.size)
            if (end < audio.size) {
                var bestPos = end
                var bestE = Float.MAX_VALUE
                var p = end - search
                while (p + frame <= end) {
                    var e = 0f
                    for (k in p until p + frame) e += audio[k] * audio[k]
                    if (e < bestE) { bestE = e; bestPos = p + frame / 2 }
                    p += frame
                }
                if (bestPos > start + sr) end = bestPos
            }
            val seg = cleanAudio(audio.copyOfRange(start, end))
            var sum = 0.0
            for (v in seg) sum += (v * v).toDouble()
            val rms = sqrt(sum / maxOf(1, seg.size))
            if (rms > 0.003) res.add(Chunk(start * 1000L / sr, seg))
            start = end
        }
        return res
    }
}
