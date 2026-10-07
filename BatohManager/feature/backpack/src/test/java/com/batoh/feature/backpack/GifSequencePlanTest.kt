package com.batoh.feature.backpack

import com.batoh.core.conversion.GifConcatException
import com.batoh.core.data.bluetooth.UploadFailure
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

class GifSequencePlanTest {
    private fun status(count: Int, bytesEach: Int, framesEach: Int = 10) =
        GifSequencePlan.assess(List(count) { bytesEach }, List(count) { framesEach })

    @Test fun smallSequenceIsOk() {
        val s = status(3, 10_000)
        assertEquals(ChainStatusKind.OK, s.kind)
        assertEquals(3, s.programmes)
        assertEquals(30_000L, s.totalBytes)
        assertEquals(30, s.totalFrames)
        assertTrue(s.allowsOutput)
    }

    @Test fun totalsAreSummedAcrossProgrammes() {
        val s = GifSequencePlan.assess(listOf(100, 200, 300), listOf(1, 2, 3))
        assertEquals(600L, s.totalBytes)
        assertEquals(6, s.totalFrames)
    }

    @Test fun warnsOverVerifiedTotalBytesOnly() {
        val atLimit = GifSequencePlan.assess(listOf(453 * 1024 / 2, 453 * 1024 / 2), listOf(5, 5))
        assertEquals(ChainStatusKind.OK, atLimit.kind)
        val over = GifSequencePlan.assess(listOf(453 * 1024 / 2 + 1, 453 * 1024 / 2 + 1), listOf(5, 5))
        assertEquals(ChainStatusKind.WARN, over.kind)
        assertTrue(over.overVerifiedBytes)
        assertFalse(over.overVerifiedCount)
        assertTrue(over.allowsOutput)
    }

    @Test fun warnsAboveEightProgrammes() {
        assertEquals(ChainStatusKind.OK, status(8, 1000).kind)
        val nine = status(9, 1000)
        assertEquals(ChainStatusKind.WARN, nine.kind)
        assertTrue(nine.overVerifiedCount)
        assertFalse(nine.overVerifiedBytes)
    }

    @Test fun manyFramesAloneDoNotWarnButOver600Blocks() {
        assertEquals(ChainStatusKind.OK, status(2, 1000, 300).kind)
        val blocked = status(2, 1000, 301)
        assertEquals(ChainStatusKind.BLOCK, blocked.kind)
        assertFalse(blocked.allowsOutput)
    }

    @Test fun blocksOver20MiB() {
        assertEquals(ChainStatusKind.WARN, GifSequencePlan.assess(listOf(20 * 1024 * 1024, 0), listOf(1, 1)).kind)
        assertEquals(ChainStatusKind.BLOCK, GifSequencePlan.assess(listOf(20 * 1024 * 1024, 1), listOf(1, 1)).kind)
    }

    @Test fun blocksTooFewOrTooManyProgrammes() {
        assertEquals(ChainStatusKind.BLOCK, status(1, 1000).kind)
        assertEquals(ChainStatusKind.BLOCK, status(0, 1000).kind)
        assertEquals(ChainStatusKind.BLOCK, status(256, 1, 1).kind)
        assertFalse(GifSequencePlan.canPrepare(1))
        assertTrue(GifSequencePlan.canPrepare(2))
    }

    @Test fun mismatchedListsAreRejected() {
        try {
            GifSequencePlan.assess(listOf(1, 2), listOf(1))
            fail("Expected IllegalArgumentException")
        } catch (_: IllegalArgumentException) {
        }
    }

    @Test fun prepareAllKeepsGivenOrderAndPassesIndexes() = runBlocking {
        val seen = mutableListOf<Pair<Int, String>>()
        val result = GifSequencePlan.prepareAll(listOf("c", "a", "b")) { index, uri ->
            seen += index to uri
            uri.uppercase()
        }
        assertEquals(listOf("C", "A", "B"), result)
        assertEquals(listOf(0 to "c", 1 to "a", 2 to "b"), seen)
    }

    @Test fun prepareAllStopsAtFirstFailureAndKeepsItsIndex() = runBlocking {
        val calls = mutableListOf<Int>()
        try {
            GifSequencePlan.prepareAll(listOf("a", "b", "c")) { index, _ ->
                calls += index
                if (index == 1) throw ChainSourceException(index)
            }
            fail("Expected ChainSourceException")
        } catch (e: ChainSourceException) {
            assertEquals(1, e.index)
        }
        assertEquals(listOf(0, 1), calls)
    }

    @Test fun prepareAllRejectsEmptyList() = runBlocking {
        try {
            GifSequencePlan.prepareAll(emptyList<String>()) { _, _ -> 1 }
            fail("Expected EmptyInput")
        } catch (e: GifConcatException.EmptyInput) {
            assertEquals(GifChainError.Empty, GifChainPlan.errorFor(e))
        }
    }

    @Test fun uploadFailuresMapToTypedErrorsWithIndex() {
        assertEquals(GifChainError.SourceTooLarge(2), GifSequencePlan.errorFor(2, UploadFailure.GifTooLarge))
        assertEquals(GifChainError.SourceUnreadable(0), GifSequencePlan.errorFor(0, UploadFailure.GifUnreadable))
        assertEquals(GifChainError.SourceInvalid(1), GifSequencePlan.errorFor(1, UploadFailure.NotAGif))
        assertEquals(GifChainError.SourceInvalid(1), GifSequencePlan.errorFor(1, UploadFailure.GifInvalid))
        assertEquals(GifChainError.SourceInvalid(1), GifSequencePlan.errorFor(1, UploadFailure.PayloadInvalid))
        assertEquals(GifChainError.Generic, GifSequencePlan.errorFor(1, UploadFailure.Unknown))
    }

    @Test fun throwablesMapToTypedErrors() {
        assertEquals(GifChainError.SourceInvalid(3),
            GifSequencePlan.errorFor(UploadFailureException(UploadFailure.GifInvalid), 3))
        assertEquals(GifChainError.SourceUnreadable(4), GifSequencePlan.errorFor(ChainSourceException(4), 9))
        assertEquals(GifChainError.Generic, GifSequencePlan.errorFor(IllegalStateException("x"), 0))
    }

    @Test fun cancellationIsPropagated() {
        try {
            GifSequencePlan.errorFor(CancellationException("c"), 0)
            fail("Expected CancellationException")
        } catch (_: CancellationException) {
        }
    }
}
