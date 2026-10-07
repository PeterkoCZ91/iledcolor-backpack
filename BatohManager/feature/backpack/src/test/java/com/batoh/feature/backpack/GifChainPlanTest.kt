package com.batoh.feature.backpack

import com.batoh.core.conversion.GifConcatException
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import kotlin.coroutines.cancellation.CancellationException

class GifChainPlanTest {
    @Test fun moveUpAndDownSwapNeighbours() {
        val list = listOf("a", "b", "c")
        assertEquals(listOf("b", "a", "c"), GifChainPlan.moveUp(list, 1))
        assertEquals(listOf("a", "c", "b"), GifChainPlan.moveDown(list, 1))
    }

    @Test fun moveAtEdgesAndOutOfRangeIsNoOp() {
        val list = listOf("a", "b", "c")
        assertEquals(list, GifChainPlan.moveUp(list, 0))
        assertEquals(list, GifChainPlan.moveDown(list, 2))
        assertEquals(list, GifChainPlan.moveUp(list, 9))
        assertEquals(list, GifChainPlan.moveDown(list, -1))
    }

    @Test fun removeDropsOnlyThatIndexAndKeepsOriginalImmutable() {
        val list = listOf("a", "b", "c")
        assertEquals(listOf("a", "c"), GifChainPlan.remove(list, 1))
        assertEquals(list, GifChainPlan.remove(list, 3))
        assertEquals(listOf("a", "b", "c"), list)
    }

    @Test fun parseUrisDecodesSplitsAndCaps() {
        val a = "content://media/external/images/media/1"
        val b = "content://x/a+b c.gif"
        val arg = listOf(a, b).joinToString(",") { java.net.URLEncoder.encode(it, "UTF-8").replace("+", "%20") }
        assertEquals(listOf(a, b), GifChainPlan.parseUris(arg))
        assertEquals(emptyList<String>(), GifChainPlan.parseUris(null))
        assertEquals(emptyList<String>(), GifChainPlan.parseUris(",,"))
        assertEquals(GifChainPlan.MAX_ITEMS, GifChainPlan.parseUris(List(50) { "u$it" }.joinToString(",")).size)
    }

    @Test fun parseUrisKeepsCommaInsideUri() {
        val uri = "content://x/a,b.gif"
        val arg = java.net.URLEncoder.encode(uri, "UTF-8")
        assertEquals(listOf(uri), GifChainPlan.parseUris(arg))
    }

    @Test fun renderNeedsAtLeastTwoItems() {
        assertFalse(GifChainPlan.canRender(1))
        assertTrue(GifChainPlan.canRender(2))
    }

    @Test fun statusLevels() {
        assertEquals(ChainStatusKind.OK, GifChainPlan.status(96, 453 * 1024L).kind)
        assertEquals(ChainStatusKind.WARN, GifChainPlan.status(97, 1000).kind)
        assertEquals(ChainStatusKind.WARN, GifChainPlan.status(10, 453 * 1024L + 1).kind)
        assertEquals(ChainStatusKind.BLOCK, GifChainPlan.status(601, 1000).kind)
        assertEquals(ChainStatusKind.BLOCK, GifChainPlan.status(10, 21L * 1024 * 1024).kind)
        assertFalse(GifChainPlan.status(601, 1).allowsOutput)
        assertTrue(GifChainPlan.status(97, 1).allowsOutput)
    }

    @Test fun sizeEstimateRoundsUpToKb() {
        assertEquals(0, GifChainPlan.sizeKb(0))
        assertEquals(1, GifChainPlan.sizeKb(1))
        assertEquals(2, GifChainPlan.sizeKb(1025))
    }

    @Test fun clampsPauseAndSpeed() {
        assertEquals(0, GifChainPlan.clampPause(-5))
        assertEquals(GifChainPlan.MAX_PAUSE_MS, GifChainPlan.clampPause(99999))
        assertEquals(GifChainPlan.MIN_SPEED_MS, GifChainPlan.clampSpeed(0))
        assertEquals(GifChainPlan.MAX_SPEED_MS, GifChainPlan.clampSpeed(5000))
    }

    @Test fun mapsTypedErrors() {
        assertEquals(GifChainError.Empty, GifChainPlan.errorFor(GifConcatException.EmptyInput()))
        assertEquals(GifChainError.SourceInvalid(2),
            GifChainPlan.errorFor(GifConcatException.InvalidSource(2, IllegalArgumentException("x"))))
        assertEquals(GifChainError.TooManyFrames(600), GifChainPlan.errorFor(GifConcatException.TooManyFrames(600)))
        assertEquals(GifChainError.TooLarge, GifChainPlan.errorFor(GifConcatException.TooLarge(1)))
        assertEquals(GifChainError.SourceUnreadable(1), GifChainPlan.errorFor(ChainSourceException(1)))
        assertEquals(GifChainError.SourceTooLarge(0), GifChainPlan.errorFor(ChainSourceException(0, tooLarge = true)))
        assertEquals(GifChainError.Generic, GifChainPlan.errorFor(IllegalStateException("raw secret text")))
        assertTrue(GifChainError.TooLarge.isLimit)
        assertFalse(GifChainError.Generic.isLimit)
    }

    @Test fun cancellationIsPropagatedNotMapped() {
        val c = CancellationException("stop")
        try {
            GifChainPlan.errorFor(c)
            fail("expected rethrow")
        } catch (e: CancellationException) {
            assertSame(c, e)
        }
    }
}
