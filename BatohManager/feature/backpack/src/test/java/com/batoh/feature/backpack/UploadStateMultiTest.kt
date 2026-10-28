package com.batoh.feature.backpack

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class UploadStateMultiTest {
    private val multi = MultiPanelProgress(2, 2, null, emptyList())
    private val withMulti = UploadState(UploadStage.Success, "x", multi = multi)

    @Test fun singleUploadStageUpdateDropsStaleMulti() {
        val next = withMulti.withStage(UploadStage.Sending, 0.5f, null, null, multiRunActive = false)
        assertNull(next.multi)
        assertEquals(UploadStage.Sending, next.stage)
    }

    @Test fun multiRunKeepsItsOwnMulti() {
        val next = withMulti.withStage(UploadStage.Error, 1f, "m", null, multiRunActive = true)
        assertNotNull(next.multi)
    }
}
