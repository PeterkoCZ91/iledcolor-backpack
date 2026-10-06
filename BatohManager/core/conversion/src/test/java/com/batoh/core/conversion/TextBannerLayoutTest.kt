package com.batoh.core.conversion

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TextBannerLayoutTest {
    @Test
    fun shortTextScrollsAcrossWholePanelWithRequestedStep() {
        val plan = TextBannerLayout.plan(textWidthPx = 36, requestedStepPx = 1, delayMs = 100)
        assertEquals(1, plan.stepPx)
        assertEquals(100, plan.frameCount) // 64 + 36
        assertEquals(64, plan.offsetX(0))
        assertEquals(-35, plan.offsetX(plan.frameCount - 1)) // last pixel column still visible
        assertEquals(10_000L, plan.durationMs)
    }

    @Test
    fun partialLastStepRoundsUp() {
        val plan = TextBannerLayout.plan(textWidthPx = 37, requestedStepPx = 2, delayMs = 80)
        assertEquals(51, plan.frameCount) // ceil(101 / 2)
        assertTrue(plan.offsetX(plan.frameCount - 1) > -37)
        assertTrue(plan.offsetX(plan.frameCount) <= -37)
    }

    @Test
    fun longTextIncreasesStepToStayWithinFrameLimit() {
        val plan = TextBannerLayout.plan(textWidthPx = 2000, requestedStepPx = 1, delayMs = 100)
        assertTrue(plan.frameCount <= TextBannerLayout.MAX_FRAMES)
        assertEquals(7, plan.stepPx) // ceil(2064 / 300)
        assertTrue(plan.offsetX(plan.frameCount) <= -2000)
    }

    @Test
    fun degenerateInputsStaySane() {
        val plan = TextBannerLayout.plan(textWidthPx = -5, requestedStepPx = 0, delayMs = 0)
        assertEquals(1, plan.stepPx)
        assertEquals(64, plan.frameCount)
        assertEquals(TextBannerLayout.MIN_DELAY_MS, plan.delayMs)
    }

    @Test
    fun everySpeedPresetStaysWithinLimitsForMaximumText() {
        for (speed in TextBannerSpeed.values()) {
            val plan = TextBannerLayout.plan(TextBannerLayout.MAX_TEXT_LENGTH * 48, speed.stepPx, speed.delayMs)
            assertTrue(plan.frameCount in 1..TextBannerLayout.MAX_FRAMES)
            assertTrue(plan.stepPx >= speed.stepPx)
        }
    }

    @Test
    fun baselineCentresTextVertically() {
        // ascent -30, descent 10 → 40 px line, 12 px margin top and bottom.
        assertEquals(42f, TextBannerLayout.baseline(-30f, 10f), 0.001f)
    }
}
