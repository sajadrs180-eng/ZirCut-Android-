package app.zircut

import app.zircut.timeline.AudioClip
import app.zircut.timeline.Keyframe
import app.zircut.timeline.Layer
import app.zircut.timeline.TimelineSnap
import app.zircut.timeline.TrimOps
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** 0.1.246–0.1.247: magnetic snapping and precise audio / layer trimming (pure logic, no Android). */
class TimelineSnapTest {
    private fun clip(id: Int, start: Long, inMs: Long, len: Long, file: Long = 10_000L) =
        AudioClip(id, AudioClip.KIND_MUSIC, "a$id.wav", "a$id", start, inMs, len, file)

    private fun layer(id: Int, s: Long, e: Long, keys: List<Keyframe> = emptyList()) =
        Layer(id = id, kind = Layer.KIND_TEXT, startMs = s, endMs = e, keys = keys)

    // ---------- window ----------
    @Test fun windowGrowsWhenZoomedOutAndShrinksWhenZoomedIn() {
        val zoomedIn = TimelineSnap.windowMs(220f)
        val normal = TimelineSnap.windowMs(80f)
        val zoomedOut = TimelineSnap.windowMs(14f)
        assertTrue(zoomedIn < normal && normal < zoomedOut)
        assertTrue(zoomedIn >= TimelineSnap.MIN_WINDOW_MS)
        assertTrue(zoomedOut <= TimelineSnap.MAX_WINDOW_MS)
        assertEquals(125L, TimelineSnap.windowMs(80f)) // 10 dp at 80 dp/s
    }

    // ---------- targets ----------
    @Test fun targetsCoverPlayheadProjectVideoLayersAndAudioButNotTheDraggedItem() {
        val t = TimelineSnap.targets(
            total = 10_000L, playheadMs = 4_000L, videoEdges = listOf(0L, 3_000L, 10_000L),
            layers = listOf(layer(1, 1_000L, 2_000L), layer(2, 5_000L, 6_000L)),
            audio = listOf(clip(7, 6_500L, 0L, 1_500L)), skipLayer = 2
        )
        val ms = t.map { it.ms }
        assertEquals(ms.sorted(), ms)
        assertEquals(ms.distinct(), ms)
        assertTrue(listOf(0L, 1_000L, 2_000L, 3_000L, 4_000L, 6_500L, 8_000L, 10_000L).all { it in ms })
        assertTrue(5_000L !in ms && 6_000L !in ms) // layer 2 is the dragged one
        assertEquals(TimelineSnap.Kind.PLAYHEAD, t.first { it.ms == 4_000L }.kind)
        assertEquals(TimelineSnap.Kind.PROJECT, t.first { it.ms == 0L }.kind) // project beats video at the same time
    }

    @Test fun targetsUseStablePriorityWhenDifferentItemsShareOneTime() {
        val t = TimelineSnap.targets(
            total = 10_000L,
            playheadMs = 2_000L,
            videoEdges = listOf(2_000L, 3_000L),
            layers = listOf(layer(1, 2_000L, 4_000L)),
            audio = listOf(clip(7, 2_000L, 0L, 1_000L))
        )
        assertEquals(TimelineSnap.Kind.PLAYHEAD, t.first { it.ms == 2_000L }.kind)
        assertEquals(TimelineSnap.Kind.VIDEO, t.first { it.ms == 3_000L }.kind)
        assertEquals(1, t.count { it.ms == 2_000L })
    }

    @Test fun targetsCanExcludeTheDraggedAudioWithoutExcludingOtherAudio() {
        val t = TimelineSnap.targets(
            total = 10_000L,
            playheadMs = 0L,
            videoEdges = emptyList(),
            layers = emptyList(),
            audio = listOf(
                clip(7, 1_000L, 0L, 1_000L),
                clip(8, 4_000L, 0L, 1_000L)
            ),
            skipAudio = 7
        )
        val times = t.map { it.ms }
        assertTrue(1_000L !in times && 2_000L !in times)
        assertTrue(4_000L in times && 5_000L in times)
    }

    // ---------- nearest ----------
    @Test fun nearestPicksTheClosestInsideTheWindowOnly() {
        val t = listOf(TimelineSnap.Target(1_000L, TimelineSnap.Kind.VIDEO), TimelineSnap.Target(1_100L, TimelineSnap.Kind.LAYER))
        assertEquals(1_100L, TimelineSnap.nearest(1_090L, t, 50L)!!.ms)
        assertNull(TimelineSnap.nearest(1_300L, t, 50L))
    }

    @Test fun stuckTargetIsHeldUntilTheFingerIsFarEnough() {
        val t = listOf(TimelineSnap.Target(1_000L, TimelineSnap.Kind.PLAYHEAD))
        val window = 100L
        // 130 ms away: outside the window, but inside the release distance (160): still stuck
        assertEquals(1_000L, TimelineSnap.nearest(1_130L, t, window, stuckMs = 1_000L)!!.ms)
        assertNull(TimelineSnap.nearest(1_130L, t, window))
        // 170 ms away: released
        assertNull(TimelineSnap.nearest(1_170L, t, window, stuckMs = 1_000L))
    }

    @Test fun anotherCloserTargetWinsOverTheStuckOne() {
        val t = listOf(TimelineSnap.Target(1_000L, TimelineSnap.Kind.VIDEO), TimelineSnap.Target(1_150L, TimelineSnap.Kind.LAYER))
        assertEquals(1_150L, TimelineSnap.nearest(1_140L, t, 100L, stuckMs = 1_000L)!!.ms)
    }

    // ---------- trim ----------
    @Test fun trimSnapsToThePlayhead() {
        val t = listOf(TimelineSnap.Target(3_000L, TimelineSnap.Kind.PLAYHEAD))
        val r = TimelineSnap.trim(3_060L, 500L, 9_000L, t, 100L)
        assertEquals(3_000L, r.ms)
        assertNotNull(r.hit)
        assertEquals(TimelineSnap.Kind.PLAYHEAD, r.hit!!.kind)
    }

    @Test fun trimNeverLeavesTheAllowedRangeAndIgnoresTargetsOutsideIt() {
        val t = listOf(TimelineSnap.Target(100L, TimelineSnap.Kind.PROJECT), TimelineSnap.Target(9_500L, TimelineSnap.Kind.PROJECT))
        val low = TimelineSnap.trim(50L, 500L, 9_000L, t, 600L)
        assertEquals(500L, low.ms) // the target at 100 is below the limit and must not be used
        assertNull(low.hit)
        val high = TimelineSnap.trim(20_000L, 500L, 9_000L, t, 600L)
        assertEquals(9_000L, high.ms)
        assertNull(high.hit)
    }

    @Test fun trimWithoutTargetsIsPlainClamping() {
        val r = TimelineSnap.trim(1_234L, 0L, 5_000L, emptyList(), 100L)
        assertEquals(1_234L, r.ms)
        assertNull(r.hit)
    }

    // ---------- move ----------
    @Test fun moveSnapsTheStartEdge() {
        val t = listOf(TimelineSnap.Target(2_000L, TimelineSnap.Kind.VIDEO))
        // item 1000..1500 dragged 960 ms: start at 1960, 40 ms from 2000
        val r = TimelineSnap.move(1_000L, 1_500L, 960L, -1_000L, 8_500L, t, 100L)
        assertEquals(1_000L, r.delta)
        assertEquals(0, r.edge)
        assertEquals(2_000L, r.hit!!.ms)
    }

    @Test fun moveSnapsTheEndEdgeAndPrefersTheCloserEdge() {
        val t = listOf(TimelineSnap.Target(2_000L, TimelineSnap.Kind.PLAYHEAD), TimelineSnap.Target(5_050L, TimelineSnap.Kind.AUDIO))
        // item 1000..4000 dragged 1000: start 2000 (distance 0), end 5000 (distance 50) -> start wins
        val r = TimelineSnap.move(1_000L, 4_000L, 1_000L, -1_000L, 6_000L, t, 100L)
        assertEquals(0, r.edge)
        assertEquals(1_000L, r.delta)
        // drag 1040: start 2040 (40), end 5040 (10) -> end wins
        val r2 = TimelineSnap.move(1_000L, 4_000L, 1_040L, -1_000L, 6_000L, t, 100L)
        assertEquals(1, r2.edge)
        assertEquals(1_050L, r2.delta)
        // The drag readout must follow the end edge that actually snapped, not the item's start.
        assertEquals(5_050L, r2.anchorMs(1_000L, 4_000L))
        assertEquals(2_000L, r.anchorMs(1_000L, 4_000L))
    }

    @Test fun moveStaysInsideTheProjectAndDropsASnapThatTheLimitWouldUndo() {
        // the only landmark is at 12_000, past the project end (10_000): the limit wins and no snap is reported
        val t = listOf(TimelineSnap.Target(12_000L, TimelineSnap.Kind.LAYER))
        val r = TimelineSnap.move(8_000L, 9_000L, 2_500L, -8_000L, 1_000L, t, 3_000L)
        assertEquals(1_000L, r.delta)
        assertNull(r.hit)
        assertEquals(-1, r.edge)
    }

    @Test fun moveWithoutSnapTargetsFollowsTheFinger() {
        val r = TimelineSnap.move(1_000L, 2_000L, 333L, -1_000L, 5_000L, emptyList(), 100L)
        assertEquals(333L, r.delta)
        assertNull(r.hit)
        assertEquals(1_333L, r.anchorMs(1_000L, 2_000L))
    }

    @Test fun dragIsStableWhileHoveringNearALandmark() {
        // the finger wobbles +-30 ms around a landmark: the edge must stay on it (no jump back and forth)
        val t = listOf(TimelineSnap.Target(2_000L, TimelineSnap.Kind.PLAYHEAD))
        var stuck: Long? = null
        val seen = ArrayList<Long>()
        for (raw in listOf(1_970L, 2_020L, 1_990L, 2_030L, 1_975L)) {
            val r = TimelineSnap.trim(raw, 0L, 10_000L, t, 40L, stuck)
            stuck = r.hit?.ms
            seen.add(r.ms)
        }
        assertTrue(seen.all { it == 2_000L })
    }

    // ---------- audio trim ----------
    @Test fun audioEndTrimCanShortenAndLengthenUpToTheFile() {
        val c = clip(1, start = 2_000L, inMs = 1_000L, len = 3_000L, file = 6_000L) // plays file 1000..4000, ends at 5000
        assertEquals(1_000L, TrimOps.audio(c, 1, 3_000L, 20_000L).lenMs)
        // the file has 2000 ms left after 4000: the end can grow to 7000 on the timeline
        assertEquals(5_000L, TrimOps.audio(c, 1, 99_000L, 20_000L).lenMs)
        // ...but never past the project end
        assertEquals(4_000L, TrimOps.audio(c, 1, 99_000L, 6_000L).lenMs)
    }

    @Test fun audioStartTrimMovesTheInPointAndKeepsTheSoundInPlace() {
        val c = clip(1, start = 2_000L, inMs = 1_000L, len = 3_000L)
        val t = TrimOps.audio(c, 0, 3_000L, 20_000L)
        assertEquals(3_000L, t.startMs)
        assertEquals(2_000L, t.inMs)
        assertEquals(c.endMs, t.endMs)
        // sound that was at timeline 4000 is still the same file position: file time = inMs + (time - startMs)
        assertEquals(1_000L + (4_000L - 2_000L), t.inMs + (4_000L - t.startMs))
    }

    @Test fun audioStartTrimCannotGoBeforeTheStartOfTheFileOrTheTimeline() {
        val c = clip(1, start = 2_000L, inMs = 500L, len = 3_000L)
        val t = TrimOps.audio(c, 0, 0L, 20_000L)
        assertEquals(1_500L, t.startMs) // only 500 ms of file lie before the in-point
        assertEquals(0L, t.inMs)
        val c2 = clip(2, start = 300L, inMs = 2_000L, len = 3_000L)
        assertEquals(0L, TrimOps.audio(c2, 0, 0L, 20_000L).startMs) // the timeline starts at 0
    }

    @Test fun audioTrimKeepsTheMinimumLength() {
        val c = clip(1, start = 2_000L, inMs = 0L, len = 3_000L)
        assertEquals(AudioClip.MIN_MS, TrimOps.audio(c, 1, 0L, 20_000L).lenMs)
        val l = TrimOps.audio(c, 0, 99_000L, 20_000L)
        assertEquals(AudioClip.MIN_MS, l.lenMs)
        assertEquals(c.endMs, l.endMs)
    }

    // ---------- layer trim ----------
    @Test fun layerTrimKeepsMinimumLengthAndProjectBounds() {
        val l = layer(1, 1_000L, 3_000L)
        assertEquals(Layer.MIN_MS, TrimOps.layer(l, 1, 0L, 10_000L).len)
        assertEquals(10_000L, TrimOps.layer(l, 1, 99_000L, 10_000L).endMs)
        assertEquals(0L, TrimOps.layer(l, 0, -500L, 10_000L).startMs)
        assertEquals(3_000L - Layer.MIN_MS, TrimOps.layer(l, 0, 99_000L, 10_000L).startMs)
    }

    @Test fun layerStartTrimEarlierKeepsKeyframesOnTheSameTimelineTime() {
        val keys = listOf(Keyframe(0L, 0.2f, 0.2f, 1f, 1f), Keyframe(1_000L, 0.8f, 0.8f, 1f, 1f))
        val l = layer(1, 2_000L, 5_000L, keys)
        val t = TrimOps.layer(l, 0, 1_500L, 10_000L)
        assertEquals(1_500L, t.startMs)
        assertEquals(listOf(500L, 1_500L), t.keys.map { it.tMs })
        // absolute times 2000 and 3000 are unchanged
        assertEquals(l.keys.map { l.startMs + it.tMs }, t.keys.map { t.startMs + it.tMs })
    }

    @Test fun layerStartTrimLaterKeepsThePoseAtTheNewStart() {
        val keys = listOf(Keyframe(0L, 0.0f, 0.5f, 1f, 1f, ease = 1), Keyframe(1_000L, 1.0f, 0.5f, 1f, 1f))
        val l = layer(1, 2_000L, 5_000L, keys)
        val t = TrimOps.layer(l, 0, 2_500L, 10_000L) // cut the first half second: the layer was at x = 0.5 there
        assertEquals(2_500L, t.startMs)
        assertEquals(0L, t.keys.first().tMs)
        assertEquals(0.5f, t.keys.first().x, 0.001f)
        assertEquals(500L, t.keys.last().tMs)
        assertEquals(1.0f, t.keys.last().x, 0.001f)
    }

    @Test fun layerRightTrimDoesNotTouchKeyframes() {
        val keys = listOf(Keyframe(0L, 0.2f, 0.2f, 1f, 1f), Keyframe(1_000L, 0.8f, 0.8f, 1f, 1f))
        val l = layer(1, 2_000L, 5_000L, keys)
        assertEquals(keys, TrimOps.layer(l, 1, 4_000L, 10_000L).keys)
    }
}
