package com.batoh.core.conversion

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class ChainLimitsTest {
    private fun level(frames: Int, bytes: Long) = ChainLimits.assess(frames, bytes).level

    @Test fun okAtWarnBoundary() {
        assertEquals(ChainLevel.OK, level(96, 453 * 1024L))
        assertNull(ChainLimits.assess(1, 1).message)
    }

    @Test fun warnAboveFramesOrBytes() {
        assertEquals(ChainLevel.WARN, level(97, 1))
        assertEquals(ChainLevel.WARN, level(1, 453 * 1024L + 1))
        assertEquals(true, ChainLimits.assess(97, 1).message!!.contains("not verified on hardware"))
    }

    @Test fun blockAboveHardLimits() {
        assertEquals(ChainLevel.WARN, level(600, 20L * 1024 * 1024))
        assertEquals(ChainLevel.BLOCK, level(601, 1))
        assertEquals(ChainLevel.BLOCK, level(1, 20L * 1024 * 1024 + 1))
        assertNotNull(ChainLimits.assess(601, 1).message)
    }
}
