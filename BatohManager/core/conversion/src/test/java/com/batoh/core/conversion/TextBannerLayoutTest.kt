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

    @Test
    fun plan_usesPanelWidth() {
        val plan = TextBannerLayout.plan(100, 2, 80, panelWidth = 32)
        assertEquals(66, plan.frameCount) // ceil(132 / 2)
        assertEquals(32, plan.offsetX(0))
    }

    @Test
    fun plan_widePanelStaysWithinFrameCap() {
        val plan = TextBannerLayout.plan(60 * 40, 1, 80, panelWidth = 448)
        assertTrue(plan.frameCount <= 300)
    }

    @Test
    fun baselineCentresInPanelHeight() {
        assertEquals(19f, TextBannerLayout.baseline(-10f, 4f, panelHeight = 32), 0.001f)
    }

    @Test
    fun leftToRightStartsOffLeftEdgeAndEndsOffRightEdge() {
        val plan = TextBannerLayout.plan(36, 2, 80, direction = TextBannerDirection.LeftToRight)
        assertEquals(-36, plan.offsetX(0))
        assertTrue(plan.offsetX(plan.frameCount - 1) < 64) // last frame still partly visible
        assertTrue(plan.offsetX(plan.frameCount) >= 64) // next frame would be blank = frame 0 loop
    }

    @Test
    fun directionDoesNotChangeFrameCountAndOffsetsAreMonotonic() {
        for (speed in TextBannerSpeed.values()) for (width in listOf(0, 40, 700, 60 * 48)) {
            val rtl = TextBannerLayout.plan(width, speed.stepPx, speed.delayMs)
            val ltr = TextBannerLayout.plan(width, speed.stepPx, speed.delayMs, direction = TextBannerDirection.LeftToRight)
            assertEquals(rtl.frameCount, ltr.frameCount)
            assertEquals(rtl.stepPx, ltr.stepPx)
            assertTrue(ltr.frameCount in 1..TextBannerLayout.MAX_FRAMES)
            for (f in 1 until ltr.frameCount) {
                assertTrue(ltr.offsetX(f) > ltr.offsetX(f - 1))
                assertTrue(rtl.offsetX(f) < rtl.offsetX(f - 1))
            }
        }
    }

    @Test
    fun defaultDirectionIsRightToLeft() {
        assertEquals(TextBannerDirection.RightToLeft, TextBannerOptions(text = "x").direction)
        assertEquals(TextBannerDirection.RightToLeft, TextBannerLayout.plan(10, 1, 100).direction)
    }

    @Test
    fun fasterSpeedNeverNeedsMoreFramesAndSlowIsWithinLimit() {
        val frames = TextBannerSpeed.values().map { TextBannerLayout.plan(500, it.stepPx, it.delayMs).frameCount }
        assertTrue(frames.zipWithNext().all { (a, b) -> a >= b })
        assertTrue(frames.all { it <= TextBannerLayout.MAX_FRAMES })
    }
}
