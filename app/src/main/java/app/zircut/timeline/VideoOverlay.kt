package app.zircut.timeline

/** A non-destructive video layer placed above the edited main timeline (times are output-timeline ms). */
data class VideoOverlay(
    val id: Int,
    /** Project-relative copied source file. */
    val file: String,
    val sourceDurationMs: Long,
    val sourceWidth: Int,
    val sourceHeight: Int,
    val startMs: Long,
    val endMs: Long,
    /** Centre of the overlay as a fraction of the output frame. */
    val x: Float = 0.78f,
    val y: Float = 0.22f,
    /** Base width as a fraction of output frame width. */
    val width: Float = 0.32f,
    val muted: Boolean = true,
    val hidden: Boolean = false,
    /** Base rotation in degrees, clockwise. */
    val rotation: Float = 0f,
    /** Base opacity. */
    val opacity: Float = 1f,
    /** Keyframe times are relative to startMs. */
    val keys: List<Keyframe> = emptyList(),
    /** Alpha mask applied identically in preview and export: none, circle, rounded rectangle. */
    val maskType: Int = MASK_NONE
) {
    val len: Long get() = endMs - startMs

    fun valid(): Boolean =
        id > 0 && file == "overlay_$id.mp4" &&
            sourceDurationMs >= 200L && sourceWidth > 0 && sourceHeight > 0 &&
            startMs >= 0L && endMs > startMs && len >= 200L &&
            len <= sourceDurationMs &&
            x.isFinite() && x in 0f..1f &&
            y.isFinite() && y in 0f..1f &&
            width.isFinite() && width in 0.08f..1f &&
            rotation.isFinite() && rotation in -LayerKeys.ROT_MAX..LayerKeys.ROT_MAX &&
            opacity.isFinite() && opacity in 0.05f..1f &&
            maskType in MASK_NONE..MASK_ROUNDED_RECT &&
            keys.size <= LayerKeys.MAX_KEYS &&
            keys == keys.sortedBy { it.tMs } &&
            keys.all {
                it.tMs in 0L..len &&
                    it.x.isFinite() && it.x in 0f..1f &&
                    it.y.isFinite() && it.y in 0f..1f &&
                    it.scale.isFinite() && it.scale in 0.1f..6f &&
                    it.opacity.isFinite() && it.opacity in 0.05f..1f &&
                    it.rotation.isFinite() && it.rotation in -LayerKeys.ROT_MAX..LayerKeys.ROT_MAX &&
                    it.ease in LayerKeys.EASE_SMOOTH..LayerKeys.EASE_HOLD
            }

    companion object {
        /** Limit concurrent decoder/compositor inputs for predictable performance on phones. */
        const val MAX_LAYERS = 3
        const val MASK_NONE = 0
        const val MASK_CIRCLE = 1
        const val MASK_ROUNDED_RECT = 2
        fun canAdd(currentLayerCount: Int): Boolean = currentLayerCount in 0 until MAX_LAYERS
    }
}

/** Evaluated transform at an output-timeline position. */
data class VideoOverlayPose(
    val x: Float,
    val y: Float,
    val scale: Float,
    val opacity: Float,
    val rotation: Float
)

/** Keyframe interpolation and editing shared by preview and export. */
object VideoOverlayKeys {
    const val NEAR_MS = 60L

    fun pose(overlay: VideoOverlay, outputMs: Long): VideoOverlayPose {
        val keys = overlay.keys
        if (keys.isEmpty()) return VideoOverlayPose(
            overlay.x, overlay.y, 1f, overlay.opacity, overlay.rotation
        )
        val t = outputMs - overlay.startMs
        if (t <= keys.first().tMs) return keys.first().pose()
        if (t >= keys.last().tMs) return keys.last().pose()
        var i = 0
        while (i < keys.size - 2 && keys[i + 1].tMs <= t) i++
        val a = keys[i]
        val b = keys[i + 1]
        val span = (b.tMs - a.tMs).coerceAtLeast(1L).toFloat()
        val u = ((t - a.tMs) / span).coerceIn(0f, 1f)
        val f = when (a.ease) {
            LayerKeys.EASE_LINEAR -> u
            LayerKeys.EASE_HOLD -> 0f
            else -> u * u * (3f - 2f * u)
        }
        fun mix(x: Float, y: Float) = x + (y - x) * f
        return VideoOverlayPose(
            mix(a.x, b.x), mix(a.y, b.y), mix(a.scale, b.scale),
            mix(a.opacity, b.opacity), mix(a.rotation, b.rotation)
        )
    }

    fun upsert(overlay: VideoOverlay, outputMs: Long, pose: VideoOverlayPose): VideoOverlay {
        val t = (outputMs - overlay.startMs).coerceIn(0L, overlay.len)
        val old = overlay.keys.firstOrNull { kotlin.math.abs(it.tMs - t) <= NEAR_MS }
        val kept = overlay.keys.filter { kotlin.math.abs(it.tMs - t) > NEAR_MS }
        if (old == null && kept.size >= LayerKeys.MAX_KEYS) return overlay
        val key = Keyframe(
            t, pose.x.coerceIn(0f, 1f), pose.y.coerceIn(0f, 1f),
            pose.scale.coerceIn(0.1f, 6f), pose.opacity.coerceIn(0.05f, 1f),
            pose.rotation.coerceIn(-LayerKeys.ROT_MAX, LayerKeys.ROT_MAX),
            old?.ease ?: LayerKeys.EASE_SMOOTH
        )
        return overlay.copy(keys = (kept + key).sortedBy { it.tMs })
    }

    fun removeAt(overlay: VideoOverlay, outputMs: Long): VideoOverlay {
        val t = (outputMs - overlay.startMs).coerceIn(0L, overlay.len)
        val target = overlay.keys.minByOrNull { kotlin.math.abs(it.tMs - t) } ?: return overlay
        if (kotlin.math.abs(target.tMs - t) > NEAR_MS) return overlay
        return overlay.copy(keys = overlay.keys.filterNot { it == target })
    }

    private fun Keyframe.pose() = VideoOverlayPose(x, y, scale, opacity, rotation)
}
