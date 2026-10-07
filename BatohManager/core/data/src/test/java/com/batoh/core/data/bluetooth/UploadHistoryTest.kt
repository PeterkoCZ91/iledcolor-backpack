package com.batoh.core.data.bluetooth

import org.junit.Assert.*
import org.junit.Test

class UploadHistoryTest {
    @Test fun roundTripKeepsNamesWithSpecialCharacters() {
        val history = listOf(
            UploadHistoryEntry("cat\tnew\nline ✓.gif", 1_759_000_000_000, UploadOutcome.Confirmed),
            UploadHistoryEntry("Built-in program 3", 1_759_000_001_000, UploadOutcome.AlreadyPresent),
            UploadHistoryEntry("a%b+c", 2, UploadOutcome.Failed),
            UploadHistoryEntry("", 3, UploadOutcome.Cancelled),
        )
        assertEquals(history, UploadHistory.decode(UploadHistory.encode(history)))
    }

    @Test fun addPutsNewestFirstAndKeepsTen() {
        var history = emptyList<UploadHistoryEntry>()
        repeat(12) { history = UploadHistory.add(history, UploadHistoryEntry("gif $it", it.toLong(), UploadOutcome.Confirmed)) }
        assertEquals(10, history.size)
        assertEquals("gif 11", history.first().name)
        assertEquals("gif 2", history.last().name)
    }

    @Test fun malformedLinesAreSkipped() {
        val text = "1\tConfirmed\tok\nnotanumber\tFailed\tx\n2\tUnknown\tx\n3\tFailed\n4\tFailed\tlast"
        assertEquals(
            listOf(UploadHistoryEntry("ok", 1, UploadOutcome.Confirmed), UploadHistoryEntry("last", 4, UploadOutcome.Failed)),
            UploadHistory.decode(text)
        )
        assertTrue(UploadHistory.decode(null).isEmpty())
        assertTrue(UploadHistory.decode("").isEmpty())
    }

    @Test fun failureReasonsRoundTrip() {
        val history = listOf(
            UploadHistoryEntry("a", 1, UploadOutcome.Failed, UploadFailure.InsufficientSpace),
            UploadHistoryEntry("b", 2, UploadOutcome.Failed, UploadFailure.ChunkRejected(12, 177, 0)),
            UploadHistoryEntry("c", 3, UploadOutcome.Failed, UploadFailure.HeaderRejected(-1)),
            UploadHistoryEntry("d", 4, UploadOutcome.Cancelled, UploadFailure.Cancelled),
            UploadHistoryEntry("e", 5, UploadOutcome.Confirmed),
        )
        val text = UploadHistory.encode(history)
        assertEquals("4\tCancelled\td\tcancelled", text.lines()[3])
        assertEquals("5\tConfirmed\te", text.lines()[4])
        assertEquals(history, UploadHistory.decode(text))
    }

    @Test fun entriesSavedBeforeReasonsExistedStillDecode() {
        val old = "1759000000000\tFailed\tcat.gif\n1759000001000\tConfirmed\tok"
        assertEquals(
            listOf(
                UploadHistoryEntry("cat.gif", 1_759_000_000_000, UploadOutcome.Failed, null),
                UploadHistoryEntry("ok", 1_759_000_001_000, UploadOutcome.Confirmed, null),
            ),
            UploadHistory.decode(old)
        )
    }

    @Test fun unknownOrMalformedReasonBecomesUnknown() {
        val text = "1\tFailed\ta\tfuture_reason:7\n2\tFailed\tb\tchunk_timeout:x:3\n3\tFailed\tc\tchunk_timeout:1\n4\tFailed\td\t"
        val decoded = UploadHistory.decode(text)
        assertEquals(listOf(UploadFailure.Unknown, UploadFailure.Unknown, UploadFailure.Unknown, null), decoded.map { it.failure })
    }

    @Test fun everyFailureCodeParsesBack() {
        val all = listOf(
            UploadFailure.NotConnected, UploadFailure.ConnectionLost, UploadFailure.AuthFailed,
            UploadFailure.HeaderTimeout, UploadFailure.HeaderRejected(4), UploadFailure.InsufficientSpace,
            UploadFailure.ChunkWriteFailed(0, 5), UploadFailure.ChunkRejected(1, 5, 2), UploadFailure.ChunkTimeout(3, 5),
            UploadFailure.EndTimeout, UploadFailure.EndRejected(0), UploadFailure.MtuTooSmall(23),
            UploadFailure.GifTooLarge, UploadFailure.GifUnreadable, UploadFailure.NotAGif, UploadFailure.GifInvalid,
            UploadFailure.PayloadInvalid, UploadFailure.Cancelled, UploadFailure.Unknown,
        )
        all.forEach { assertEquals(it, UploadFailure.fromCode(it.code)) }
        assertEquals("codes are unique", all.size, all.map { it.code }.toSet().size)
    }
}
