package com.r0ybt.taleframe

import com.r0ybt.taleframe.data.*
import kotlinx.coroutines.*
import org.junit.Assert.*
import org.junit.Test

class SequencePlaybackTest {
    @Test fun actionThenSlideExceptionsWinOverProjectAndDefaultsRecover() {
        val project = ProjectPlayback(Transition("fade", 500))
        val slide = Slide(1, 1, "A", transition = Transition("none"), useDefaultTransition = false)
        val action = Element(1, 1, "button", "Ir", transition = Transition("right", 700))
        assertEquals(action.transition, effectiveTransition(project, slide, action))
        assertEquals(Transition("none"), effectiveTransition(project, slide, action.copy(useDefaultTransition = true)))
        assertEquals(project.transition, effectiveTransition(project, slide.copy(useDefaultTransition = true), action.copy(useDefaultTransition = true)))
        assertEquals(0, effectiveTransition(project, slide).duration)
    }
    @Test fun albumSequenceUsesUserOrderLoopAndPerSlideTiming() {
        val a = Slide(80, 1, "A", order = 0, autoTargetId = 300)
        val b = Slide(20, 1, "B", order = 1)
        val c = Slide(300, 1, "C", order = 2)
        val album = listOf(c, a, b)
        assertEquals(b, nextSequenceSlide(a, album, false))
        assertNull(nextSequenceSlide(c, album, false))
        assertEquals(a, nextSequenceSlide(c, album, true))
        assertEquals(c, nextSequenceSlide(a, album, false, setOf(b.id)))
        assertEquals(2f, slideSeconds(ProjectPlayback(seconds = 2f), a.copy(useDefaultSequenceTime = true)), 0f)
        assertEquals(.5f, slideSeconds(ProjectPlayback(seconds = 2f), a.copy(autoSeconds = .5f)), 0f)
    }
    @Test fun repeatedExpressionsKeepTimingAndIdsTogetherWhenReorderedOrRemoved() {
        val options = MediaOptions("slideshow", frames = listOf("open", "closed", "open"), frameSeconds = listOf(1f, .2f, 1f))
        val e = Element(1, 1, "image", "", width = .4f, height = .5f, x = .3f, y = .2f, rotation = 45f, opacity = .5f,
            expressionFrames = listOf(10, 20, 10), media = options)
        val moved = reorderExpressionFrames(e, 1, 0)
        assertEquals(listOf(20L, 10L, 10L), moved.expressionFrames)
        assertEquals(listOf(.2f, 1f, 1f), moved.media.frameSeconds)
        assertEquals(e.copy(media = moved.media, expressionFrames = moved.expressionFrames), moved)
        val removed = removeExpressionFrame(moved, 1)
        assertEquals(listOf("closed", "open"), removed.media.frames)
        assertEquals(listOf(20L, 10L), removed.expressionFrames)
        assertEquals(listOf(.2f, 1f), removed.media.frameSeconds)
    }
    @Test fun expressionLoopUsesEachDurationAndOnceStopsOnLastFrame() = runBlocking {
        val once = MediaOptions("slideshow", loop = false, frames = listOf("open", "closed", "open"), frameSeconds = listOf(1f, .2f, 1f))
        val delays = mutableListOf<Long>(); val frames = mutableListOf<Long>()
        runMediaSequence(once, wait = { delays += it }) { frames += it }
        assertEquals(listOf(1000L, 200L), delays)
        assertEquals(listOf(1L, 2L), frames)
        delays.clear(); frames.clear()
        try {
            runMediaSequence(once.copy(loop = true), wait = { if (delays.size == 4) throw CancellationException(); delays += it }) { frames += it }
        } catch (_: CancellationException) { }
        assertEquals(listOf(1000L, 200L, 1000L, 1000L), delays)
        assertEquals(listOf(1L, 2L, 3L, 4L), frames)
    }
    @Test fun singleFrameSequenceHasNoTimerEvenWhenLoopIsEnabled() = runBlocking {
        runMediaSequence(MediaOptions("slideshow", frames = listOf("A")), wait = { fail("No timer needed") }) { fail("No frame change needed") }
    }
    @Test fun cancellingPlaybackStopsSuspendedTimerWithoutPublishingAnotherFrame() = runBlocking {
        var frames = 0
        val enteredWait = CompletableDeferred<Unit>()
        val options = MediaOptions("slideshow", frames = listOf("A", "B"), loop = true)
        val job = launch {
            runMediaSequence(options, wait = { enteredWait.complete(Unit); delay(it) }) { frames++ }
        }
        enteredWait.await(); job.cancelAndJoin()
        assertTrue(job.isCancelled && job.isCompleted)
        assertEquals(0, frames)
    }
}
