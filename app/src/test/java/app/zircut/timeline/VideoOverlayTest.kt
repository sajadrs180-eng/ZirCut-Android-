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
}
