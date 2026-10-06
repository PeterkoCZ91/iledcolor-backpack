package com.batoh.core.data.bluetooth

import org.junit.Assert.*
import org.junit.Test

class UploadHistoryTest {
    @Test fun roundTripKeepsNamesWithSpecialCharacters() {
        val history = listOf(
            UploadHistoryEntry("kočka\tnový\nřádek ✓.gif", 1_759_000_000_000, UploadOutcome.Confirmed),
            UploadHistoryEntry("Vestavěný program 3", 1_759_000_001_000, UploadOutcome.AlreadyPresent),
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
}
