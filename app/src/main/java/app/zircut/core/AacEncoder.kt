package app.zircut.core

import android.media.MediaCodec
import android.media.MediaCodecInfo
import android.media.MediaFormat
import android.media.MediaMuxer
import java.io.File

/**
 * Squeezes 16 kHz mono speech into a small AAC-LC stream inside an M4A (MP4) container,
 * about 2.5 KB per second (the 16-bit WAV is 32 KB per second). Small uploads survive filtered mobile networks,
 * and M4A is a format Groq accepts (a raw .aac / ADTS stream is not on its list).
 */
object AacEncoder {
    private const val SR = 16000
    private const val BITRATE = 24_000

    fun encode(samples: FloatArray): ByteArray {
        val tmp = File.createTempFile("zcut", ".m4a")
        var muxer: MediaMuxer? = null
        var muxStarted = false
        try {
            val fmt = MediaFormat.createAudioFormat(MediaFormat.MIMETYPE_AUDIO_AAC, SR, 1).apply {
                setInteger(MediaFormat.KEY_AAC_PROFILE, MediaCodecInfo.CodecProfileLevel.AACObjectLC)
                setInteger(MediaFormat.KEY_BIT_RATE, BITRATE)
                setInteger(MediaFormat.KEY_MAX_INPUT_SIZE, 16384)
            }
            val codec = MediaCodec.createEncoderByType(MediaFormat.MIMETYPE_AUDIO_AAC)
            muxer = MediaMuxer(tmp.absolutePath, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)
            var track = -1
            try {
                codec.configure(fmt, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE)
                codec.start()
                val info = MediaCodec.BufferInfo()
                var pos = 0
                var inputDone = false
                var outputDone = false
                var idle = 0
                while (!outputDone) {
                    if (!inputDone) {
                        val ii = codec.dequeueInputBuffer(10_000)
                        if (ii >= 0) {
                            val buf = codec.getInputBuffer(ii)!!
                            buf.clear()
                            val n = minOf(samples.size - pos, buf.capacity() / 2)
                            if (n <= 0) {
                                codec.queueInputBuffer(ii, 0, 0, pos * 1_000_000L / SR, MediaCodec.BUFFER_FLAG_END_OF_STREAM)
                                inputDone = true
                            } else {
                                buf.order(java.nio.ByteOrder.nativeOrder())
                                for (k in 0 until n) buf.putShort((samples[pos + k].coerceIn(-1f, 1f) * 32767f).toInt().toShort())
                                codec.queueInputBuffer(ii, 0, n * 2, pos * 1_000_000L / SR, 0)
                                pos += n
                            }
                        }
                    }
                    val oi = codec.dequeueOutputBuffer(info, 10_000)
                    if (oi == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
                        idle = 0
                        track = muxer.addTrack(codec.outputFormat)
                        muxer.start()
                        muxStarted = true
                    } else if (oi >= 0) {
                        idle = 0
                        val isConfig = (info.flags and MediaCodec.BUFFER_FLAG_CODEC_CONFIG) != 0
                        if (info.size > 0 && !isConfig && muxStarted) {
                            val ob = codec.getOutputBuffer(oi)!!
                            ob.position(info.offset)
                            ob.limit(info.offset + info.size)
                            muxer.writeSampleData(track, ob, info)
                        }
                        codec.releaseOutputBuffer(oi, false)
                        if ((info.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0) outputDone = true
                    } else if (oi == MediaCodec.INFO_TRY_AGAIN_LATER) {
                        if (++idle > 500) throw IllegalStateException("AAC encoder stalled")
                    }
                }
            } finally {
                try { codec.stop() } catch (_: Exception) { }
                try { codec.release() } catch (_: Exception) { }
            }
            if (!muxStarted) throw IllegalStateException("AAC encoder produced no audio")
            muxer.stop()
            muxStarted = false
            return tmp.readBytes()
        } finally {
            try { if (muxStarted) muxer?.stop() } catch (_: Exception) { }
            try { muxer?.release() } catch (_: Exception) { }
            tmp.delete()
        }
    }
}
