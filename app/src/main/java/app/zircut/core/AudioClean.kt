package app.zircut.core

import android.content.Context
import android.net.Uri
import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.RandomAccessFile
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow

/**
 * Noise reduction for speech: low-cut filter (80 Hz rumble) + a smooth downward expander (noise gate).
 * The noise level is measured from the quietest parts of the file itself, so no settings are needed except the strength.
 * The result is a NEW 44.1 kHz stereo WAV file (the original stays in the project folder so Undo works), and the clip
 * simply points at it, so preview and export play exactly the same sound. Everything streams through temp files:
 * memory use stays small for long recordings. Not suitable for music (quiet passages would be reduced too).
 */
object AudioClean {
    private const val SR = PcmDecoder.SR
    private const val BLOCK = 1024

    /** [strength] 0..1. Returns false when cancelled (the output file is deleted then). */
    fun clean(ctx: Context, src: File, dest: File, strength: Float, cancelled: () -> Boolean): Boolean {
        val raw = File(dest.parentFile, dest.name + ".raw")
        try {
            // pass 1: decode to raw 16-bit stereo and measure the loudness of every block
            val levels = ArrayList<Float>()
            var frames = 0L
            BufferedOutputStream(FileOutputStream(raw), 1 shl 16).use { os ->
                var bytes = ByteArray(8192 * 4)
                var acc = 0.0
                var cnt = 0
                frames = PcmDecoder.decode(ctx, Uri.fromFile(src), 0L, Long.MAX_VALUE / 4, { buf, n ->
                    if (bytes.size < n * 4) bytes = ByteArray(n * 4)
                    for (k in 0 until n * 2) {
                        val v = buf[k].toInt()
                        bytes[k * 2] = (v and 0xFF).toByte()
                        bytes[k * 2 + 1] = ((v shr 8) and 0xFF).toByte()
                    }
                    os.write(bytes, 0, n * 4)
                    for (i in 0 until n) {
                        val m = (buf[i * 2] + buf[i * 2 + 1]) / 65536f
                        acc += m * m
                        if (++cnt == BLOCK) {
                            levels.add(Math.sqrt(acc / BLOCK).toFloat())
                            acc = 0.0
                            cnt = 0
                        }
                    }
                }, cancelled)
            }
            if (cancelled()) return false
            if (frames <= 0L || levels.isEmpty()) throw IllegalStateException("صدایی برای پردازش پیدا نشد")

            val sorted = levels.sorted()
            val floor = max(sorted[(sorted.size * 0.15f).toInt().coerceIn(0, sorted.size - 1)], 0.0005f)
            val loud = sorted[(sorted.size * 0.9f).toInt().coerceIn(0, sorted.size - 1)]
            // the gate opens a little above the noise floor, but never above 40 % of the loud speech level
            val thr = min(floor * 2.5f, max(loud * 0.4f, floor * 1.2f))
            val s = strength.coerceIn(0f, 1f)
            val minGain = 0.5f - 0.45f * s
            val ratio = 1f + 1.5f * s

            // pass 2: filter + expander, written as WAV
            val hpA = Math.exp(-2.0 * Math.PI * 80.0 / SR).toFloat()
            var lx = 0f; var ly = 0f; var rx = 0f; var ry = 0f
            var env = 0f
            var gain = 1f
            val atk = 1f - Math.exp(-1.0 / (SR * 0.004)).toFloat()
            val rel = 1f - Math.exp(-1.0 / (SR * 0.150)).toFloat()
            val genv = 1f - Math.exp(-1.0 / (SR * 0.010)).toFloat()
            var written = 0L
            FileOutputStream(dest, false).use { it.write(AudioMixer.wavHeader(0L)) }
            run {
                BufferedInputStream(FileInputStream(raw), 1 shl 16).use { ins ->
                    BufferedOutputStream(FileOutputStream(dest, true), 1 shl 16).use { os ->
                        val buf = ByteArray(4096 * 4)
                        val out = ByteArray(4096 * 4)
                        var carry = 0
                        while (true) {
                            if (cancelled()) return false
                            val got = ins.read(buf, carry, buf.size - carry)
                            if (got <= 0) break
                            val total = carry + got
                            val n = total / 4
                            for (i in 0 until n) {
                                val l = ((buf[i * 4].toInt() and 0xFF) or (buf[i * 4 + 1].toInt() shl 8)).toShort() / 32768f
                                val r = ((buf[i * 4 + 2].toInt() and 0xFF) or (buf[i * 4 + 3].toInt() shl 8)).toShort() / 32768f
                                // one-pole high-pass, 80 Hz
                                val fl = hpA * (ly + l - lx); lx = l; ly = fl
                                val fr = hpA * (ry + r - rx); rx = r; ry = fr
                                val lev = abs(fl + fr) * 0.5f
                                env += (if (lev > env) atk else rel) * (lev - env)
                                val target = if (env >= thr) 1f else max(minGain, (env / thr).pow(ratio))
                                gain += genv * (target - gain)
                                val ol = (fl * gain).coerceIn(-1f, 1f)
                                val orr = (fr * gain).coerceIn(-1f, 1f)
                                val a = (ol * 32767f).toInt()
                                val b = (orr * 32767f).toInt()
                                out[i * 4] = (a and 0xFF).toByte(); out[i * 4 + 1] = ((a shr 8) and 0xFF).toByte()
                                out[i * 4 + 2] = (b and 0xFF).toByte(); out[i * 4 + 3] = ((b shr 8) and 0xFF).toByte()
                            }
                            os.write(out, 0, n * 4)
                            written += n * 4L
                            carry = total - n * 4
                            if (carry > 0) System.arraycopy(buf, n * 4, buf, 0, carry)
                        }
                        os.flush()
                    }
                }
            }
            RandomAccessFile(dest, "rw").use { raf ->
                raf.seek(0L)
                raf.write(AudioMixer.wavHeader(written))
            }
            return true
        } catch (e: Exception) {
            dest.delete()
            throw e
        } finally {
            raw.delete()
            if (cancelled()) dest.delete()
        }
    }
}
