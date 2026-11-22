package app.zircut.core

import android.content.Context
import android.media.MediaCodecInfo
import android.media.MediaCodecList
import android.media.MediaExtractor
import android.media.MediaFormat
import android.media.MediaMetadataRetriever
import android.net.Uri
import androidx.media3.common.MimeTypes

/** What this phone's video encoders can really do (asked from the system, never assumed). */
object DeviceCaps {
    private val cache = HashMap<String, Boolean>()

    private fun encoders(mime: String): List<MediaCodecInfo> = try {
        MediaCodecList(MediaCodecList.REGULAR_CODECS).codecInfos
            .filter { it.isEncoder && it.supportedTypes.any { t -> t.equals(mime, ignoreCase = true) } }
    } catch (e: Exception) {
        emptyList()
    }

    private fun caps(info: MediaCodecInfo, mime: String): MediaCodecInfo.VideoCapabilities? = try {
        info.getCapabilitiesForType(mime).videoCapabilities
    } catch (e: Exception) {
        null
    }

    /** true when an encoder for [mime] accepts this frame size (and frame rate when [fps] > 0) */
    @Synchronized
    fun supports(mime: String, w: Int, h: Int, fps: Int = 0): Boolean {
        val key = "$mime|$w|$h|$fps"
        cache[key]?.let { return it }
        val ok = encoders(mime).any { info ->
            val vc = caps(info, mime) ?: return@any false
            try {
                val sizeOk = vc.isSizeSupported(w, h) || vc.isSizeSupported(h, w)
                sizeOk && (fps <= 0 || vc.areSizeAndRateSupported(w, h, fps.toDouble()) || vc.areSizeAndRateSupported(h, w, fps.toDouble()))
            } catch (e: Exception) {
                false
            }
        }
        cache[key] = ok
        return ok
    }

    fun hasHardware(mime: String): Boolean = encoders(mime).any { !it.isSoftwareOnly }

    /** highest bitrate (bit/s) an encoder of this type reports, 0 when none */
    fun maxBitrate(mime: String): Int = encoders(mime).maxOfOrNull { caps(it, mime)?.bitrateRange?.upper ?: 0 } ?: 0

    /** Largest side any encoder of this type accepts, e.g. 3840 (0 when none). */
    fun maxSide(mime: String): Int = encoders(mime).maxOfOrNull {
        val vc = caps(it, mime)
        if (vc == null) 0 else maxOf(vc.supportedWidths.upper, vc.supportedHeights.upper)
    } ?: 0

    val h264 = MimeTypes.VIDEO_H264
    val hevc = MimeTypes.VIDEO_H265

    /** Frame rate of the video file, 30 when unknown. Reads the file: call off the main thread. */
    fun sourceFps(ctx: Context, uri: Uri): Float {
        try {
            val r = MediaMetadataRetriever()
            try {
                r.setDataSource(ctx, uri)
                r.extractMetadata(MediaMetadataRetriever.METADATA_KEY_CAPTURE_FRAMERATE)?.toFloatOrNull()?.let { if (it > 1f) return it }
            } finally {
                r.release()
            }
        } catch (_: Exception) {
        }
        val ex = MediaExtractor()
        try {
            ex.setDataSource(ctx, uri, null)
            for (i in 0 until ex.trackCount) {
                val f = ex.getTrackFormat(i)
                if ((f.getString(MediaFormat.KEY_MIME) ?: "").startsWith("video/") && f.containsKey(MediaFormat.KEY_FRAME_RATE)) {
                    return f.getInteger(MediaFormat.KEY_FRAME_RATE).toFloat().takeIf { it > 1f } ?: 30f
                }
            }
        } catch (_: Exception) {
        } finally {
            ex.release()
        }
        return 30f
    }
}
