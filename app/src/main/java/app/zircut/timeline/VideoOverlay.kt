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
    /** Width as a fraction of output frame width. */
    val width: Float = 0.32f,
    val muted: Boolean = true,
    val hidden: Boolean = false
) {
    val len: Long get() = endMs - startMs
    fun valid(): Boolean = id > 0 && file == "overlay_$id.mp4" &&
        sourceDurationMs >= 200L && sourceWidth > 0 && sourceHeight > 0 && startMs >= 0L &&
        endMs > startMs && endMs - startMs >= 200L && endMs - startMs <= sourceDurationMs &&
        x in 0f..1f && y in 0f..1f && width in 0.08f..1f

    companion object {
        /** Limit concurrent decoder/compositor inputs for predictable performance on phones. */
        const val MAX_LAYERS = 3

        fun canAdd(currentLayerCount: Int): Boolean = currentLayerCount in 0 until MAX_LAYERS
    }
}
