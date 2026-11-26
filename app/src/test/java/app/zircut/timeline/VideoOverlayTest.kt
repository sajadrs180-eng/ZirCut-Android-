package app.zircut.timeline

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class VideoOverlayTest {
    private fun validOverlay() = VideoOverlay(
        id = 1, file = "overlay_1.mp4", sourceDurationMs = 8_000L,
        sourceWidth = 1920, sourceHeight = 1080,
        startMs = 1_000L, endMs = 5_000L
    )

    @Test fun acceptsValidIndependentVideoLayer() {
        assertTrue(validOverlay().valid())
    }

    @Test fun supportsUpToThreeIndependentVideoLayers() {
        assertTrue(VideoOverlay.canAdd(0))
        assertTrue(VideoOverlay.canAdd(1))
        assertTrue(VideoOverlay.canAdd(2))
        assertFalse(VideoOverlay.canAdd(3))
        assertFalse(VideoOverlay.canAdd(4))
        assertFalse(VideoOverlay.canAdd(-1))
    }

    @Test fun rejectsOverlayLongerThanItsSourceVideo() {
        assertFalse(validOverlay().copy(endMs = 10_000L).valid())
    }

    @Test fun rejectsUnsafeProjectFileName() {
        assertFalse(validOverlay().copy(file = "../overlay_1.mp4").valid())
    }

    @Test fun rejectsInvalidTimelineAndGeometry() {
        assertFalse(validOverlay().copy(endMs = 500L).valid())
        assertFalse(validOverlay().copy(width = 0.01f).valid())
        assertFalse(validOverlay().copy(x = 1.5f).valid())
        assertFalse(validOverlay().copy(sourceWidth = 0).valid())
    }


    @Test fun interpolatesVideoOverlayKeyframesForTransform() {
        val overlay = validOverlay().copy(
            keys = listOf(
                Keyframe(0L, 0.2f, 0.3f, 1f, 1f, 0f, LayerKeys.EASE_LINEAR),
                Keyframe(2_000L, 0.8f, 0.7f, 2f, 0.4f, 90f, LayerKeys.EASE_LINEAR)
            )
        )
        val pose = VideoOverlayKeys.pose(overlay, 2_000L)
        assertTrue(kotlin.math.abs(pose.x - 0.5f) < 0.001f)
        assertTrue(kotlin.math.abs(pose.y - 0.5f) < 0.001f)
        assertTrue(kotlin.math.abs(pose.scale - 1.5f) < 0.001f)
        assertTrue(kotlin.math.abs(pose.opacity - 0.7f) < 0.001f)
        assertTrue(kotlin.math.abs(pose.rotation - 45f) < 0.001f)
    }

    @Test fun insertsAndRemovesVideoOverlayKeyframes() {
        val overlay = validOverlay()
        val pose = VideoOverlayPose(0.4f, 0.6f, 1.5f, 0.8f, 30f)
        val inserted = VideoOverlayKeys.upsert(overlay, 2_000L, pose)
        assertTrue(inserted.keys.size == 1)
        assertTrue(inserted.valid())
        val removed = VideoOverlayKeys.removeAt(inserted, 2_000L)
        assertTrue(removed.keys.isEmpty())
    }

    @Test fun rejectsOutOfRangeVideoOverlayKeyframes() {
        val overlay = validOverlay().copy(
            keys = listOf(Keyframe(9_000L, 0.5f, 0.5f, 1f, 1f))
        )
        assertFalse(overlay.valid())
    }

}
