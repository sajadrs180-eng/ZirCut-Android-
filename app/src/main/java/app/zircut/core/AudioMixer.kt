package app.zircut.core

import android.content.Context
import android.net.Uri
import app.zircut.timeline.AudioClip
import app.zircut.timeline.AudioOps
import java.io.BufferedOutputStream
import java.io.File
import java.io.FileOutputStream
import java.io.RandomAccessFile
import kotlin.math.abs
import kotlin.math.tanh

/**
 * Builds the sound track of the export: the original sound of every clip (laid end to end like the picture),
 * plus all music / voice-over clips with their volume, fades and positions, mixed into ONE stereo WAV file
 * (44.1 kHz, 16-bit). Transformer then simply carries that file as the audio of the video.
 *
 * Each source is first decoded into its own small temp file, then all of them are mixed block by block,
 * so memory use stays tiny however long the project is.
 */
object AudioMixer {
    private const val SR = PcmDecoder.SR
    private const val BLOCK = 8192

    /** The sound of one clip of the video timeline: [fromMs, toMs) of a media file, placed at [outStartMs]. */
    class Piece(val uri: Uri, val fromMs: Long, val toMs: Long, val outStartMs: Long, val speed: Float = 1f)

    private class Source(val file: File, val frames: Long, val startFrame: Long, val clip: AudioClip?, val gain: Float) {
        var raf: RandomAccessFile? = null
    }

    private fun frameOf(ms: Long): Long = ms * SR / 1000L

    /**
     * Time stretch by overlap-add of Hann grains (50 % overlap): the result lasts frames / speed and keeps the pitch.
     * Input and output are raw 16-bit stereo; returns the number of frames written. Streams through the files.
     */
    private fun stretch(inp: File, frames: Long, speed: Float, outFile: File, cancelled: () -> Boolean): Long {
        val sp = speed.toDouble().coerceIn(0.1, 8.0)
        val grain = 2048
        val hop = grain / 2
        val outFrames = Math.round(frames / sp).coerceAtLeast(1L)
        val win = FloatArray(grain) { k -> (0.5 - 0.5 * Math.cos(2.0 * Math.PI * k / grain)).toFloat() }
        val acc = FloatArray(grain * 2)
        val raw = ByteArray(grain * 4)
        var written = 0L
        RandomAccessFile(inp, "r").use { raf ->
            BufferedOutputStream(FileOutputStream(outFile), 1 shl 16).use { os ->
                val ob = ByteArray(hop * 4)
                var k = 0L
                // acc holds the grain being finished (first half) and the next one (second half)
                while (written < outFrames) {
                    if (cancelled()) throw java.util.concurrent.CancellationException("cancelled")
                    val inPos = Math.round(k * hop * sp)
                    // read one grain from the input (zeros past its end)
                    java.util.Arrays.fill(raw, 0.toByte())
                    if (inPos < frames) {
                        val cnt = minOf(grain.toLong(), frames - inPos).toInt()
                        raf.seek(inPos * 4L)
                        raf.readFully(raw, 0, cnt * 4)
                    }
                    for (j in 0 until grain) {
                        val o = j * 4
                        val l = ((raw[o].toInt() and 0xFF) or (raw[o + 1].toInt() shl 8)).toShort() / 32768f
                        val r = ((raw[o + 2].toInt() and 0xFF) or (raw[o + 3].toInt() shl 8)).toShort() / 32768f
                        acc[j * 2] += l * win[j]
                        acc[j * 2 + 1] += r * win[j]
                    }
                    // the first hop of acc is now complete: write it, then slide the rest down
                    val take = minOf(hop.toLong(), outFrames - written).toInt()
                    for (j in 0 until take) {
                        val sl = (acc[j * 2].coerceIn(-1f, 1f) * 32767f).toInt()
                        val sr = (acc[j * 2 + 1].coerceIn(-1f, 1f) * 32767f).toInt()
                        ob[j * 4] = (sl and 0xFF).toByte(); ob[j * 4 + 1] = ((sl shr 8) and 0xFF).toByte()
                        ob[j * 4 + 2] = (sr and 0xFF).toByte(); ob[j * 4 + 3] = ((sr shr 8) and 0xFF).toByte()
                    }
                    os.write(ob, 0, take * 4)
                    written += take
                    System.arraycopy(acc, hop * 2, acc, 0, (grain - hop) * 2)
                    java.util.Arrays.fill(acc, (grain - hop) * 2, grain * 2, 0f)
                    k++
                }
            }
        }
        return written
    }

    fun mix(
        ctx: Context,
        out: File,
        totalMs: Long,
        pieces: List<Piece>,
        mainGain: Float,
        clips: List<AudioClip>,
        projectDir: File,
        onProgress: (Float) -> Unit,
        cancelled: () -> Boolean,
        duck: Boolean = false,
        speech: app.zircut.timeline.SpeechMap? = null
    ) {
        val tmp = File(ctx.cacheDir, "mix").apply { deleteRecursively(); mkdirs() }
        val sources = ArrayList<Source>()
        try {
            val totalFrames = frameOf(totalMs)
            val jobs = (if (mainGain > 0f) pieces.size else 0) + clips.count { !it.muted && it.volume > 0f }
            var done = 0
            fun tick() { onProgress(0.6f * done / maxOf(1, jobs)) }
            tick()

            if (mainGain > 0f) {
                for ((i, p) in pieces.withIndex()) {
                    val f = File(tmp, "m$i.pcm")
                    var n = decodeToFile(ctx, p.uri, p.fromMs, p.toMs, f, cancelled)
                    var file = f
                    if (n > 0L && p.speed != 1f) {
                        // the clip plays faster / slower: stretch its sound in time (pitch is kept)
                        val g = File(tmp, "m${i}s.pcm")
                        n = stretch(f, n, p.speed, g, cancelled)
                        f.delete()
                        file = g
                    }
                    if (n > 0L) sources.add(Source(file, n, frameOf(p.outStartMs), null, mainGain)) else file.delete()
                    done++; tick()
                }
            }
            for ((i, c) in clips.withIndex()) {
                if (c.muted || c.volume <= 0f) continue
                val f = File(tmp, "a$i.pcm")
                val n = decodeToFile(ctx, Uri.fromFile(File(projectDir, c.file)), c.inMs, c.inMs + c.lenMs, f, cancelled)
                if (n > 0L) sources.add(Source(f, n, frameOf(c.startMs), c, 1f)) else f.delete()
                done++; tick()
            }

            // ---- mix, block by block, straight into the WAV ----
            BufferedOutputStream(FileOutputStream(out), 1 shl 16).use { os ->
                os.write(wavHeader(totalFrames * 4L))
                val acc = FloatArray(BLOCK * 2)
                val rd = ByteArray(BLOCK * 4)
                val ob = ByteArray(BLOCK * 4)
                var b0 = 0L
                while (b0 < totalFrames) {
                    if (cancelled()) throw java.util.concurrent.CancellationException("cancelled")
                    val n = minOf(BLOCK.toLong(), totalFrames - b0).toInt()
                    java.util.Arrays.fill(acc, 0, n * 2, 0f)
                    for (s in sources) {
                        val lo = maxOf(b0, s.startFrame)
                        val hi = minOf(b0 + n, s.startFrame + s.frames)
                        if (hi <= lo) continue
                        val cnt = (hi - lo).toInt()
                        var raf = s.raf
                        if (raf == null) { raf = RandomAccessFile(s.file, "r"); s.raf = raf }
                        raf.seek((lo - s.startFrame) * 4L)
                        raf.readFully(rd, 0, cnt * 4)
                        val at = (lo - b0).toInt()
                        val clip = s.clip
                        for (k in 0 until cnt) {
                            val g = if (clip == null) s.gain
                            else {
                                val tc = (lo - s.startFrame + k) * 1000.0 / SR
                                AudioOps.gainAt(clip, tc) * (if (duck && !clip.isVoice) AudioOps.duckFactor(clips, clip, clip.startMs + tc, speech) else 1f)
                            }
                            val o = k * 4
                            val l = ((rd[o].toInt() and 0xFF) or (rd[o + 1].toInt() shl 8)).toShort() / 32768f
                            val r = ((rd[o + 2].toInt() and 0xFF) or (rd[o + 3].toInt() shl 8)).toShort() / 32768f
                            acc[(at + k) * 2] += l * g
                            acc[(at + k) * 2 + 1] += r * g
                        }
                    }
                    for (k in 0 until n * 2) {
                        val v = softClip(acc[k])
                        val s16 = (v * 32767f).toInt()
                        ob[k * 2] = (s16 and 0xFF).toByte()
                        ob[k * 2 + 1] = ((s16 shr 8) and 0xFF).toByte()
                    }
                    os.write(ob, 0, n * 4)
                    b0 += n
                    onProgress(0.6f + 0.4f * b0 / maxOf(1L, totalFrames))
                }
            }
            onProgress(1f)
        } catch (t: Throwable) {
            out.delete()
            throw t
        } finally {
            for (s in sources) try { s.raf?.close() } catch (_: Exception) { }
            tmp.deleteRecursively()
        }
    }

    /** Peaks stay untouched below 0.95; louder sums (music + voice) are squeezed smoothly instead of cracking. */
    private fun softClip(x: Float): Float {
        val a = abs(x)
        if (a <= 0.95f) return x
        val y = 0.95f + 0.05f * tanh((a - 0.95f) / 0.05f)
        return if (x < 0f) -y else y
    }

    private fun decodeToFile(ctx: Context, uri: Uri, fromMs: Long, toMs: Long, f: File, cancelled: () -> Boolean): Long {
        var frames = 0L
        BufferedOutputStream(FileOutputStream(f), 1 shl 16).use { os ->
            var bytes = ByteArray(8192 * 4)
            frames = PcmDecoder.decode(ctx, uri, fromMs, toMs, { buf, n ->
                if (bytes.size < n * 4) bytes = ByteArray(n * 4)
                for (k in 0 until n * 2) {
                    val v = buf[k].toInt()
                    bytes[k * 2] = (v and 0xFF).toByte()
                    bytes[k * 2 + 1] = ((v shr 8) and 0xFF).toByte()
                }
                os.write(bytes, 0, n * 4)
            }, cancelled)
        }
        return frames
    }

    fun wavHeader(dataBytes: Long, channels: Int = 2, sampleRate: Int = SR): ByteArray {
        val h = java.nio.ByteBuffer.allocate(44).order(java.nio.ByteOrder.LITTLE_ENDIAN)
        h.put("RIFF".toByteArray()); h.putInt((36L + dataBytes).toInt()); h.put("WAVE".toByteArray())
        h.put("fmt ".toByteArray()); h.putInt(16); h.putShort(1); h.putShort(channels.toShort())
        h.putInt(sampleRate); h.putInt(sampleRate * channels * 2); h.putShort((channels * 2).toShort()); h.putShort(16)
        h.put("data".toByteArray()); h.putInt(dataBytes.toInt())
        return h.array()
    }
}
