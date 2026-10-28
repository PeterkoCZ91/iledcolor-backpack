package com.batoh.core.conversion

import java.time.LocalTime
import org.junit.Assert.assertEquals
import org.junit.Test

class ClockGifRendererTest {
    @Test fun labelIsTwentyFourHourWithLeadingZeros() {
        assertEquals("21:05", ClockGifRenderer.label(LocalTime.of(21, 5)))
        assertEquals("00:00", ClockGifRenderer.label(LocalTime.MIDNIGHT))
        assertEquals("09:30", ClockGifRenderer.label(LocalTime.of(9, 30, 59)))
    }
}
