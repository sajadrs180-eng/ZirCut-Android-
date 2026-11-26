package app.zircut

import app.zircut.core.Frame
import app.zircut.core.ChromaKeyMath
import app.zircut.core.ClipLook
import app.zircut.core.ColorGrade
import app.zircut.core.KeyLook
import app.zircut.core.CapAnim
import app.zircut.core.Styles
import app.zircut.core.CubeLut
import app.zircut.core.Cuts
import app.zircut.core.CutTr
import app.zircut.core.TrKind
import app.zircut.core.TrMath
import app.zircut.core.Fx
import app.zircut.core.VideoKeys
import app.zircut.core.VideoPose
import app.zircut.core.Options
import app.zircut.core.Range
import app.zircut.core.fineCover
import app.zircut.timeline.AudioClip
import app.zircut.timeline.AudioOps
import app.zircut.timeline.EdlOps
import app.zircut.timeline.SpeechMap
import app.zircut.timeline.Layer
import app.zircut.timeline.Ripple
import app.zircut.timeline.TextStyles
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Pure-logic unit tests (no Android needed): run with `./gradlew testDebugUnitTest`.
 * WRITTEN WITHOUT A COMPILER / NEVER RUN in the environment where they were authored; if one fails to compile or fails,
 * fix the test or the code from the real message.
 */
class LogicTest {
    private fun segs() = listOf(Range(0, 1000), Range(1000, 3000), Range(3000, 4000))

    @Test fun deleteManyKeepsAtLeastOneClip() {
        assertNull(EdlOps.deleteMany(segs(), setOf(0, 1, 2)))
        val r = EdlOps.deleteMany(segs(), setOf(0, 2))
        assertNotNull(r)
        assertEquals(1, r!!.segs.size)
        assertEquals(1000L, r.segs[0].startMs)
    }

    @Test fun duplicateManyPutsCopiesAfterEachChosenClip() {
        val r = EdlOps.duplicateMany(segs(), setOf(0, 2))
        assertNotNull(r)
        assertEquals(5, r!!.segs.size)
        assertEquals(r.segs[0], r.segs[1])
        assertEquals(r.segs[3], r.segs[4])
    }

    @Test fun speedManySkipsPhotosAndSpeedsUpTheTimeline() {
        val r = EdlOps.setSpeedMany(segs(), setOf(0, 1), 2f, skip = setOf(0))
        assertNotNull(r)
        assertEquals(1f, r!!.segs[0].speed, 0.001f)
        assertEquals(2f, r.segs[1].speed, 0.001f)
        assertNull(EdlOps.setSpeedMany(segs(), setOf(0), 2f, skip = setOf(0)))
    }

    @Test fun preciseSpeedChangesOnlyTheClipAndKeepsPlayheadPosition() {
        val input = segs()
        val r = EdlOps.setSpeed(input, 500L, 2.3f)
        assertNotNull(r)
        assertEquals(2.3f, r!!.segs[0].speed, 0.001f)
        assertEquals(1f, r.segs[1].speed, 0.001f)
        assertEquals(217L, r.playhead)
        assertEquals(435L, r.segs[0].outLen)
    }

    @Test fun preciseSpeedIsClampedToSupportedRange() {
        val slow = EdlOps.setSpeed(segs(), 500L, 0.01f)
        val fast = EdlOps.setSpeed(segs(), 500L, 20f)
        assertEquals(0.1f, slow!!.segs[0].speed, 0.001f)
        assertEquals(8f, fast!!.segs[0].speed, 0.001f)
    }

    @Test fun moveManyKeepsOrderAndTracksPicked() {
        val l = listOf(Range(0, 1000), Range(1000, 2000), Range(2000, 3000), Range(3000, 4000))
        val toEnd = EdlOps.moveMany(l, setOf(0, 2), 3)!!
        assertEquals(listOf(1000L, 3000L, 0L, 2000L), toEnd.result.segs.map { it.startMs })
        assertEquals(setOf(2, 3), toEnd.picked)
        val earlier = EdlOps.moveMany(l, setOf(2), 0)!!
        assertEquals(listOf(0L, 2000L, 1000L, 3000L), earlier.result.segs.map { it.startMs })
        assertEquals(setOf(1), earlier.picked)
        assertNull(EdlOps.moveMany(l, setOf(0), 0))
        assertNull(EdlOps.moveMany(l, setOf(0, 1, 2, 3), 2))
    }

    @Test fun fineCoverIsOneWithoutRotationAndGrows() {
        assertEquals(1f, fineCover(0f, 1080f, 1920f), 0.001f)
        assertTrue(fineCover(10f, 1080f, 1920f) > 1f)
        assertTrue(fineCover(30f, 1080f, 1920f) > fineCover(10f, 1080f, 1920f))
        assertEquals(fineCover(15f, 100f, 100f), fineCover(-15f, 100f, 100f), 0.0001f)
    }

    @Test fun freeAspectRatioRoundTrips() {
        val o = Options(ratio = Frame.customId(1.5f))
        assertEquals(1.5f, Frame.aspect(o), 0.001f)
        assertEquals(1.5f, Frame.free(o)!!, 0.001f)
        assertEquals(Frame.MAX_FREE, Frame.free(Options(ratio = Frame.customId(99f)))!!, 0.001f)
    }

    private fun clip(inMs: Long, lenMs: Long) = AudioClip(1, AudioClip.KIND_VOICE, "a.wav", "a", 1000L, inMs, lenMs, 10_000L)

    @Test fun removeSilenceClosesGapsAndKeepsOrder() {
        // 10 s at 50 peaks/s: loud 0-2 s, silent 2-5 s, loud 5-10 s
        val peaks = ByteArray(500) { i -> if (i in 100 until 250) 0 else 200.toByte() }
        val r = AudioOps.removeSilence(listOf(clip(0, 10_000)), 1, peaks, 36)
        assertNotNull(r)
        assertEquals(2, r!!.size)
        assertEquals(1000L, r[0].startMs)
        assertEquals(r[0].endMs, r[1].startMs)
        assertTrue(r.sumOf { it.lenMs } < 10_000L - 2000L)
    }

    @Test fun removeSilenceReturnsNullWhenNothingIsQuiet() {
        val peaks = ByteArray(500) { 200.toByte() }
        assertNull(AudioOps.removeSilence(listOf(clip(0, 10_000)), 1, peaks, 36))
    }

    @Test fun speechMapJoinsCloseSpeechAndMeasuresDistance() {
        val m = SpeechMap.of(listOf(1000L to 2000L, 2300L to 3000L, 9000L to 9500L))
        assertEquals(0.0, m.distance(1500.0), 0.001)
        assertEquals(0.0, m.distance(2150.0), 0.001) // the 300 ms gap is joined
        assertEquals(500.0, m.distance(3500.0), 0.001)
        assertEquals(400.0, m.distance(600.0), 0.001)
        assertTrue(m.distance(5000.0) >= 2000.0)
        assertTrue(SpeechMap.of(emptyList()).distance(100.0) > 1e9)
    }

    @Test fun speechDucksMusicButNotTheExtractedVideoSound() {
        val music = AudioClip(1, AudioClip.KIND_MUSIC, "a.wav", "music", 0L, 0L, 20000L, 20000L)
        val ext = music.copy(id = 2, name = "صدای ویدیو ۱")
        val m = SpeechMap.of(listOf(5000L to 6000L))
        assertEquals(AudioOps.DUCK_LEVEL, AudioOps.duckFactor(listOf(music), music, 5500.0, m), 0.001f)
        assertEquals(1f, AudioOps.duckFactor(listOf(ext), ext, 5500.0, m), 0.001f)
        assertEquals(1f, AudioOps.duckFactor(listOf(music), music, 1000.0, m), 0.001f)
        assertEquals(1f, AudioOps.duckFactor(listOf(music), music, 5500.0, null), 0.001f)
    }

    @Test fun rippleShiftsLaterItemsWhenAClipIsDeleted() {
        val old = listOf(Range(0, 1000), Range(1000, 3000), Range(3000, 4000))
        val new = listOf(Range(0, 1000), Range(3000, 4000))
        val ch = Ripple.diff(old, new)!!
        assertEquals(1000L, ch.from)
        assertEquals(3000L, ch.oldEnd)
        assertEquals(-2000L, ch.delta)
        assertEquals(500L, Ripple.shift(500L, ch))      // before: untouched
        assertEquals(1000L, Ripple.shift(2000L, ch))    // inside the deleted stretch: pulled to its start
        assertEquals(1500L, Ripple.shift(3500L, ch))    // after: moves back by 2 s
    }

    @Test fun rippleIgnoresReorderAndShiftsLockedNever() {
        val a = listOf(Range(0, 1000), Range(1000, 3000))
        val b = listOf(Range(1000, 3000), Range(0, 1000))
        assertNull(Ripple.diff(a, b))
        val ch = Ripple.diff(a, a + Range(3000, 4000))!!
        val free = Layer(1, 0, 3000L, 3500L)
        val locked = free.copy(id = 2, locked = true)
        val r = Ripple.layers(listOf(free, locked), ch)
        assertEquals(4000L, r[0].startMs)
        assertEquals(3000L, r[1].startMs)
    }

    @Test fun videoKeysInterpolateSmoothlyAndClamp() {
        var fx = Fx()
        fx = VideoKeys.upsert(fx, 1000L, VideoPose(1f, 0f, 0f))
        fx = VideoKeys.upsert(fx, 3000L, VideoPose(3f, 0.5f, -0.5f))
        assertEquals(2, fx.vkeys.size)
        assertEquals(1f, VideoKeys.pose(fx, 0L).zoom, 0.001f)        // before the first key
        assertEquals(3f, VideoKeys.pose(fx, 9000L).zoom, 0.001f)     // after the last key
        assertEquals(2f, VideoKeys.pose(fx, 2000L).zoom, 0.001f)     // halfway = halfway (ease is symmetric)
        assertEquals(0.25f, VideoKeys.pose(fx, 2000L).panX, 0.001f)
        // a key close to an existing one replaces it
        fx = VideoKeys.upsert(fx, 3030L, VideoPose(4f, 0f, 0f))
        assertEquals(2, fx.vkeys.size)
        assertEquals(4f, VideoKeys.pose(fx, 3030L).zoom, 0.001f)
        assertEquals(1, VideoKeys.remove(fx, 3000L).vkeys.size)
        assertTrue(Fx(vkeys = fx.vkeys).hasTransform)
    }

    @Test fun videoWindowCentreIsMiddleWithoutPanAndMovesWithPan() {
        val c0 = VideoKeys.centre(2f, 0f, 0f, 1920, 1080, 1080, 1920)
        assertEquals(0.5f, c0[0], 0.001f)
        assertEquals(0.5f, c0[1], 0.001f)
        val c1 = VideoKeys.centre(2f, 1f, 0f, 1920, 1080, 1080, 1920)
        assertTrue(c1[0] < 0.5f)   // the picture moves right, so the window moves left
        assertEquals(0.5f, c1[1], 0.001f)
    }

    @Test fun cutsUseTheClipsOwnTransitionElseTheProjects() {
        val fx = Fx(trKind = "fade", trMs = 400L)
        val r = listOf(Range(0, 2000), Range(2000, 4000, 1f, TrKind.ZOOM.ordinal, 300L), Range(4000, 6000, 1f, 0))
        assertEquals(TrKind.NONE, Cuts.enter(r, 0, fx).kind)
        assertEquals(TrKind.ZOOM, Cuts.enter(r, 1, fx).kind)
        assertEquals(300L, Cuts.enter(r, 1, fx).ms)
        assertEquals(TrKind.NONE, Cuts.enter(r, 2, fx).kind)       // tr = 0 means "no transition here"
        assertEquals(TrKind.ZOOM, Cuts.exit(r, 0, fx).kind)         // the exit of a clip is the entrance of the next
        assertEquals(TrKind.NONE, Cuts.exit(r, 2, fx).kind)
        val plain = listOf(Range(0, 2000), Range(2000, 4000))
        assertEquals(TrKind.FADE, Cuts.enter(plain, 1, fx).kind)    // project default
        assertEquals(400L, Cuts.enter(plain, 1, fx).ms)
    }

    @Test fun trMathPlaysExitAtTheEndAndEnterAtTheStart() {
        val enter = CutTr(TrKind.FADE, 400L)
        val exit = CutTr(TrKind.SLIDE, 400L)
        val end = TrMath.at(1900L, 2000L, false, false, enter, exit)!!
        assertEquals(TrKind.SLIDE, end.kind)
        assertEquals(1f, end.dir, 0.001f)
        val start = TrMath.at(100L, 2000L, false, false, enter, exit)!!
        assertEquals(TrKind.FADE, start.kind)
        assertEquals(-1f, start.dir, 0.001f)
        assertNull(TrMath.at(1000L, 2000L, false, false, enter, exit))
        assertNull(TrMath.at(100L, 2000L, true, false, enter, exit))   // first clip: no entrance
    }

    @Test fun splitKeepsTheEntranceTransitionOnlyOnTheFirstHalf() {
        val segs = listOf(Range(0, 1000), Range(1000, 3000, 1f, 2, 300L))
        val r = EdlOps.split(segs, 2000L)!!
        assertEquals(2, r.segs[1].tr)
        assertEquals(300L, r.segs[1].trMs)
        assertEquals(-1, r.segs[2].tr)
        assertEquals(0L, r.segs[2].trMs)
    }

    private val identityCube2 = "TITLE \"id\"\nLUT_3D_SIZE 2\n0 0 0\n1 0 0\n0 1 0\n1 1 0\n0 0 1\n1 0 1\n0 1 1\n1 1 1\n"

    @Test fun cubeLutParsesAndRejectsBadFiles() {
        val lut = CubeLut.parse(identityCube2)
        assertNotNull(lut)
        assertEquals(2, lut!!.size)
        assertNull(CubeLut.parse("LUT_1D_SIZE 4\n0 0 0\n"))
        assertNull(CubeLut.parse("LUT_3D_SIZE 2\n0 0 0\n1 0 0\n"))   // too few rows
        assertNull(CubeLut.parse("hello"))
    }

    @Test fun cubeLutBuildsMedia3CubeAndFitsTheIdentity() {
        val lut = CubeLut.parse(identityCube2)!!
        val cube = lut.cube(1f)
        assertEquals(0xFFFF0000.toInt(), cube[1][0][0])   // red index 1 = full red
        assertEquals(0xFF0000FF.toInt(), cube[0][0][1])
        val f = lut.fit(1f)
        for (i in 0 until 9) assertEquals(if (i % 4 == 0) 1f else 0f, f.l[i], 0.01f)
        for (i in 0 until 3) assertEquals(0f, f.o[i], 0.01f)
    }

    @Test fun cubeLutAmountBlendsWithNoChange() {
        // a LUT that makes everything half as bright
        val half = identityCube2.replace("1", "0.5")
            .replace("LUT_3D_SIZE 2", "LUT_3D_SIZE 2")
        val lut = CubeLut.parse(half.replace("TITLE \"id\"", "TITLE \"half\""))!!
        val full = lut.fit(1f)
        assertEquals(0.5f, full.l[0], 0.02f)
        val none = lut.fit(0f)
        assertEquals(1f, none.l[0], 0.02f)
        val mid = lut.cube(0.5f)[1][0][0]
        assertEquals(0xFF, mid ushr 24)
        assertEquals(0xBF, (mid shr 16) and 0xFF)   // 1.0 -> 0.5 blended halfway = 0.75
    }

    @Test fun textShadowTravelsWithTheStyle() {
        val a = Layer(id = 1, kind = Layer.KIND_TEXT, startMs = 0, endMs = 2000, text = "x", shadowMode = 4)
        val st = TextStyles.of(a)
        assertEquals(4, st.shadowMode)
        val b = TextStyles.apply(Layer(id = 2, kind = Layer.KIND_TEXT, startMs = 0, endMs = 1000, text = "y"), st)
        assertEquals(4, b.shadowMode)
        assertEquals("y", b.text)
    }

    @Test fun subtitleAnimationModes() {
        val st = Styles.all.first()
        // mode 0 = the style's own behaviour: zarbati pops from 0.88
        assertEquals(0.88f, CapAnim.pose(0, st, 0.88f, 0, 0).scale, 0.001f)
        // none = always fully visible
        val n = CapAnim.pose(1, st, 0.88f, 0, 0)
        assertEquals(1f, n.scale, 0.001f); assertEquals(1f, n.alpha, 0.001f)
        // slide starts lower and transparent, ends in place
        val s0 = CapAnim.pose(4, st, 1f, 0, 0); val s1 = CapAnim.pose(4, st, 1f, 400, 0)
        assertTrue(s0.dy > 0.04f && s0.alpha < 0.05f)
        assertEquals(0f, s1.dy, 0.001f); assertEquals(1f, s1.alpha, 0.001f)
        // pop settles at 1
        assertEquals(1f, CapAnim.pose(3, st, 1f, 500, 0).scale, 0.001f)
        // word bounce: bigger in the middle of the first 160 ms of a word, normal afterwards
        assertTrue(CapAnim.pose(6, st, 1f, 1000, 80).scale > 1.1f)
        assertEquals(1f, CapAnim.pose(6, st, 1f, 1000, 400).scale, 0.001f)
    }

    @Test fun clipLookIsNeutralByDefaultAndKeptByEdits() {
        assertTrue(ClipLook().isNone)
        val id = ClipLook().affine()
        assertEquals(1f, id.l[0], 0.001f); assertEquals(0f, id.o[0], 0.001f)
        val bright = ClipLook(brightness = 0.1f)
        assertTrue(bright.hasColor)
        assertEquals(0.1f, bright.affine().o[0], 0.001f)
        // clips with different looks are not merged back into one
        val a = Range(0, 1000, 1f, -1, 0L, bright)
        val b = Range(1000, 2000, 1f, -1, 0L, ClipLook())
        assertNull(EdlOps.mergeAdjacent(listOf(a, b), 0L))
        val c = Range(1000, 2000, 1f, -1, 0L, bright)
        assertNotNull(EdlOps.mergeAdjacent(listOf(a, c), 0L))
        // a split keeps the look on both halves
        val split = EdlOps.split(listOf(a), 500L)!!
        assertTrue(split.segs.all { it.look == bright })
    }

    @Test
    fun chromaKeyRemovesTheKeyColourAndKeepsTheSubject() {
        val key = floatArrayOf(0f, 177f / 255f, 64f / 255f)
        fun a(r: Float, g: Float, b: Float, tol: Float = 0.30f, soft: Float = 0.25f) =
            ChromaKeyMath.key(r, g, b, key[0], key[1], key[2], tol, soft, 0.6f)[3]
        // the screen itself (also a darker and a lighter green) disappears
        assertEquals(0f, a(0f, 177f / 255f, 64f / 255f), 0.001f)
        assertTrue(a(0f, 0.4f, 0.15f) < 0.1f)
        assertEquals(0f, a(0.3f, 0.8f, 0.4f), 0.001f)
        // skin, a white shirt, red and blue stay
        assertEquals(1f, a(0.85f, 0.65f, 0.55f), 0.001f)
        assertEquals(1f, a(0.95f, 0.95f, 0.95f), 0.001f)
        assertEquals(1f, a(0.8f, 0.1f, 0.1f), 0.001f)
        assertEquals(1f, a(0.2f, 0.3f, 0.6f), 0.001f)
        // a higher tolerance removes more: a yellow-green is kept at 0 but removed at 100 %
        val yg = floatArrayOf(0.55f, 0.75f, 0.2f)
        assertEquals(1f, a(yg[0], yg[1], yg[2], tol = 0.0f, soft = 0f), 0.001f)
        assertEquals(0f, a(yg[0], yg[1], yg[2], tol = 1.0f), 0.001f)
        // softer edge = a pixel at the same distance gets a smaller alpha jump (alpha rises smoothly)
        val hard = a(0.5f, 0.62f, 0.3f, tol = 0.3f, soft = 0f)
        val soft = a(0.5f, 0.62f, 0.3f, tol = 0.3f, soft = 1f)
        assertTrue(soft in 0f..1f && hard in 0f..1f)
    }

    @Test
    fun chromaKeySpillSuppressionRemovesGreenCastOnly() {
        val key = floatArrayOf(0f, 177f / 255f, 64f / 255f)
        // greenish skin edge: green goes down with spill on, nothing changes with spill off
        val off = ChromaKeyMath.key(0.7f, 0.78f, 0.55f, key[0], key[1], key[2], 0.1f, 0.2f, 0f)
        val on = ChromaKeyMath.key(0.7f, 0.78f, 0.55f, key[0], key[1], key[2], 0.1f, 0.2f, 1f)
        assertEquals(0.78f, off[1], 0.001f)
        assertTrue(on[1] < off[1])
        // a normal skin tone has no green cast: spill does not touch it
        val skin = ChromaKeyMath.key(0.85f, 0.65f, 0.55f, key[0], key[1], key[2], 0.3f, 0.25f, 1f)
        assertEquals(0.85f, skin[0], 0.001f)
        assertEquals(0.65f, skin[1], 0.001f)
        assertEquals(0.55f, skin[2], 0.001f)
    }

    @Test
    fun chromaKeyColourGoesThroughTheSameGradeAsThePicture() {
        // no grade, no LUT, no clip colour = the picked colour is used as it is
        val k = KeyLook(on = true, color = 0xFF00B140.toInt())
        val raw = ChromaKeyMath.gradedKey(k, ColorGrade(), null, ClipLook())
        assertEquals(0f, raw[0], 0.001f)
        assertEquals(177f / 255f, raw[1], 0.002f)
        assertNull(ChromaKeyMath.total(ColorGrade(), null, ClipLook()))
        // a clip brightness change moves the key colour the same way it moves the picture
        val look = ClipLook(brightness = 0.1f)
        val g = ChromaKeyMath.gradedKey(k, ColorGrade(), null, look)
        assertTrue(g[1] > raw[1])
        // the key switches a clip look on; it survives a split like every other look
        val keyed = ClipLook(key = k)
        assertTrue(!keyed.isNone)
        val split = EdlOps.split(listOf(Range(0, 1000, 1f, -1, 0L, keyed)), 500L)!!
        assertTrue(split.segs.all { it.look.key == k })
    }

    // ---- fix229: layer keyframes with rotation, easing and key time ----
    @Test
    fun layerKeyRotationAndEase() {
        val base = Layer(id = 1, kind = Layer.KIND_TEXT, startMs = 1000, endMs = 5000, rotation = 10f)
        var l = app.zircut.timeline.LayerKeys.upsert(base, 1000, app.zircut.timeline.LayerPose(0.2f, 0.5f, 1f, 1f, 0f))
        l = app.zircut.timeline.LayerKeys.upsert(l, 3000, app.zircut.timeline.LayerPose(0.8f, 0.5f, 2f, 0.5f, 90f))
        assertEquals(45f, app.zircut.timeline.LayerKeys.pose(l, 2000).rotation, 0.01f)
        val lin = app.zircut.timeline.LayerKeys.setEase(l, 1000, app.zircut.timeline.LayerKeys.EASE_LINEAR)
        assertEquals(0.35f, app.zircut.timeline.LayerKeys.pose(lin, 1500).x, 0.01f)
        val hold = app.zircut.timeline.LayerKeys.setEase(l, 1000, app.zircut.timeline.LayerKeys.EASE_HOLD)
        assertEquals(0f, app.zircut.timeline.LayerKeys.pose(hold, 2999).rotation, 0.01f)
        assertEquals(90f, app.zircut.timeline.LayerKeys.pose(hold, 3000).rotation, 0.01f)
        assertEquals(true, app.zircut.timeline.LayerKeys.rotationAnimated(l))
        assertEquals(false, app.zircut.timeline.LayerKeys.rotationAnimated(base))
    }

    @Test
    fun layerKeyNudge() {
        val base = Layer(id = 1, kind = Layer.KIND_TEXT, startMs = 1000, endMs = 5000)
        var l = app.zircut.timeline.LayerKeys.upsert(base, 1000, app.zircut.timeline.LayerPose(0.2f, 0.5f, 1f, 1f, 0f))
        l = app.zircut.timeline.LayerKeys.upsert(l, 3000, app.zircut.timeline.LayerPose(0.8f, 0.5f, 1f, 1f, 0f))
        assertEquals(true, app.zircut.timeline.LayerKeys.nudge(l, 3000, -500) !== l)
        assertEquals(true, app.zircut.timeline.LayerKeys.nudge(l, 3000, -2000) === l)
    }

    // ---- fix230: video picture keyframes carry the free-angle turn ----
    @Test
    fun videoKeyRotation() {
        var fx = Fx(fine = 10f)
        assertEquals(10f, VideoKeys.pose(fx, 500).rot, 0.01f)
        fx = VideoKeys.upsert(fx, 0, VideoPose(1f, 0f, 0f, 0f))
        fx = VideoKeys.upsert(fx, 2000, VideoPose(2f, 0.5f, 0f, 30f))
        assertEquals(15f, VideoKeys.pose(fx, 1000).rot, 0.01f)
        assertEquals(1.5f, VideoKeys.pose(fx, 1000).zoom, 0.01f)
        assertEquals(true, fx.hasFine)
    }

    // ---- fix231: video picture opacity keyframes ----
    @Test
    fun videoKeyOpacity() {
        var fx = Fx()
        fx = VideoKeys.upsert(fx, 0, VideoPose(1f, 0f, 0f, 0f, 1f))
        fx = VideoKeys.upsert(fx, 2000, VideoPose(1f, 0f, 0f, 0f, 0f))
        assertEquals(0.5f, VideoKeys.pose(fx, 1000).op, 0.01f)
        assertEquals(true, fx.hasFade)
    }

    // ---- fix232: video key easing, nudge, ripple ----
    @Test
    fun videoKeyEaseNudgeRipple() {
        var fx = Fx()
        fx = VideoKeys.upsert(fx, 0, VideoPose(1f, 0f, 0f, 0f, 1f))
        fx = VideoKeys.upsert(fx, 2000, VideoPose(2f, 0f, 0f, 0f, 1f))
        assertEquals(1.25f, VideoKeys.pose(VideoKeys.setEase(fx, 0, VideoKeys.EASE_LINEAR), 500).zoom, 0.01f)
        assertEquals(1500L, VideoKeys.nudge(fx, 2000, -500, 5000).vkeys.last().tMs)
        assertEquals(true, VideoKeys.nudge(fx, 2000, -1990, 5000) === fx)
        val keys = listOf(app.zircut.core.VideoKey(0, 1f, 0f, 0f), app.zircut.core.VideoKey(1000, 1f, 0f, 0f), app.zircut.core.VideoKey(2000, 2f, 0f, 0f))
        val r = Ripple.videoKeys(keys, Ripple.Change(500, 1500, 500, -1000))
        assertEquals(listOf(0L, 500L, 1000L), r.map { it.tMs })
    }

    // ---- fix233: five-band equaliser maths ----
    @Test
    fun eqFiveBands() {
        val sr = 44100.0
        assertEquals(true, app.zircut.core.EqMath.specs(app.zircut.core.EqBands()).isEmpty())
        assertEquals(6.0, app.zircut.core.EqMath.responseDb(app.zircut.core.EqBands(lowMid = 6f), 350.0, sr), 0.3)
        assertEquals(0.0, app.zircut.core.EqMath.responseDb(app.zircut.core.EqBands(lowMid = 6f), 8000.0, sr), 0.5)
        assertEquals(12.0, app.zircut.core.EqMath.specs(app.zircut.core.EqBands(mid = 40f))[0].gainDb, 0.001)
    }

    @org.junit.Test
    fun aiMixParts() {
        // one pixel: background .1, hair .6, body .2, face .1, clothes 0, others 0
        val pr = listOf(floatArrayOf(0.1f), floatArrayOf(0.6f), floatArrayOf(0.2f), floatArrayOf(0.1f), floatArrayOf(0f), floatArrayOf(0f))
        org.junit.Assert.assertEquals(0.9f, app.zircut.core.AiMaskMath.mixParts(pr, 0)[0], 1e-4f)   // whole person = 1 - background
        org.junit.Assert.assertEquals(0.6f, app.zircut.core.AiMaskMath.mixParts(pr, 1)[0], 1e-4f)   // hair only
        org.junit.Assert.assertEquals(0.8f, app.zircut.core.AiMaskMath.mixParts(pr, 1 or 2)[0], 1e-4f) // hair + body skin
        org.junit.Assert.assertEquals(0f, app.zircut.core.AiMaskMath.mixParts(pr, 8)[0], 1e-4f)     // clothes only: none here
        org.junit.Assert.assertEquals(0.9f, app.zircut.core.AiMaskMath.mixParts(pr, 7)[0], 1e-4f)    // hair + body + face = .6+.2+.1
        val over = listOf(floatArrayOf(0f), floatArrayOf(0.8f), floatArrayOf(0.8f), floatArrayOf(0f), floatArrayOf(0f), floatArrayOf(0f))
        org.junit.Assert.assertEquals(1f, app.zircut.core.AiMaskMath.mixParts(over, 3)[0], 1e-4f)   // clamp at 1
        val k = app.zircut.core.KeyLook()
        org.junit.Assert.assertEquals(0, k.aiParts)
    }

    @org.junit.Test
    fun aiMaskShape() {
        // solid subject stays 1, solid background stays 0, invert swaps them, middle stays about 0.5
        org.junit.Assert.assertEquals(1f, app.zircut.core.AiMaskMath.shape(1f, false, 0.5f), 1e-4f)
        org.junit.Assert.assertEquals(0f, app.zircut.core.AiMaskMath.shape(0f, false, 0.5f), 1e-4f)
        org.junit.Assert.assertEquals(0f, app.zircut.core.AiMaskMath.shape(1f, true, 0.5f), 1e-4f)
        org.junit.Assert.assertEquals(0.5f, app.zircut.core.AiMaskMath.shape(0.5f, false, 0.2f), 1e-3f)
    }
}
