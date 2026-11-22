package app.zircut.core

import android.content.Context
import android.net.Uri
import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.RandomAccessFile
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.math.tanh

/**
 * Five-band equaliser (bass shelf 120 Hz, low-mid bell 350 Hz, mid bell 1 kHz, high-mid bell 3.5 kHz, treble shelf 6 kHz), each -12..+12 dB (maths in [EqMath]).
 * Like [AudioClean] it writes a NEW 44.1 kHz stereo WAV and the clip points at it, so preview and export are identical and Undo works.
 */
object AudioEq {
    private const val SR = PcmDecoder.SR.toDouble()

    /** Three bands (the old call). */
    fun apply(ctx: Context, src: File, dest: File, bassDb: Float, midDb: Float, trebleDb: Float, cancelled: () -> Boolean): Boolean =
        apply(ctx, src, dest, EqBands(bass = bassDb, mid = midDb, treble = trebleDb), cancelled)

    /** Gains in dB of the five bands. Returns false when cancelled. */
    fun apply(ctx: Context, src: File, dest: File, bands: EqBands, cancelled: () -> Boolean): Boolean {
        val raw = File(dest.parentFile, dest.name + ".raw")
        try {
            var frames = 0L
            BufferedOutputStream(FileOutputStream(raw), 1 shl 16).use { os ->
                var bytes = ByteArray(8192 * 4)
                frames = PcmDecoder.decode(ctx, Uri.fromFile(src), 0L, Long.MAX_VALUE / 4, { buf, n ->
                    if (bytes.size < n * 4) bytes = ByteArray(n * 4)
                    for (k in 0 until n * 2) {
                        val v = buf[k].toInt()
                        bytes[k * 2] = (v and 0xFF).toByte()
                        bytes[k * 2 + 1] = ((v shr 8) and 0xFF).toByte()
                    }
                    os.write(bytes, 0, n * 4)
                }, cancelled)
            }
            if (cancelled()) return false
            if (frames <= 0L) throw IllegalStateException("صدایی برای پردازش پیدا نشد")

            val specs = EqMath.specs(bands)
            val chain = Array(2) { specs.map { sp -> EqMath.make(sp, SR) } }
            FileOutputStream(dest, false).use { it.write(AudioMixer.wavHeader(0L)) }
            var written = 0L
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
                            for (ch in 0..1) {
                                val o = i * 4 + ch * 2
                                var x = ((buf[o].toInt() and 0xFF) or (buf[o + 1].toInt() shl 8)).toShort() / 32768f
                                for (f in chain[ch]) x = f.p(x)
                                // boosts must not crack: values above 0.9 are squeezed smoothly
                                val ax = abs(x)
                                if (ax > 0.9f) {
                                    val y = 0.9f + 0.1f * tanh((ax - 0.9f) / 0.1f)
                                    x = if (x < 0f) -y else y
                                }
                                val a = (x.coerceIn(-1f, 1f) * 32767f).toInt()
                                out[o] = (a and 0xFF).toByte()
                                out[o + 1] = ((a shr 8) and 0xFF).toByte()
                            }
                        }
                        os.write(out, 0, n * 4)
                        written += n * 4L
                        carry = total - n * 4
                        if (carry > 0) System.arraycopy(buf, n * 4, buf, 0, carry)
                    }
                    os.flush()
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
