package com.batoh.feature.backpack

import com.batoh.core.data.bluetooth.UploadFailure
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class SendFailurePlanTest {
    private fun prompt(
        failure: UploadFailure?, stage: UploadStage = UploadStage.Error, hasSource: Boolean = true,
        multi: Boolean = false, onPanel: Boolean = false,
    ) = SendFailurePlan.prompt(stage, failure, hasSource, multi, onPanel)

    @Test fun unreachablePanel_offersAnotherPanel() {
        assertEquals(SendFailurePrompt(true), prompt(UploadFailure.NotConnected))
        assertEquals(SendFailurePrompt(true), prompt(UploadFailure.ConnectionLost))
    }

    @Test fun otherFailure_onlyTryAgain() {
        assertEquals(SendFailurePrompt(false), prompt(UploadFailure.ChunkTimeout(1, 5)))
        assertEquals(SendFailurePrompt(false), prompt(UploadFailure.HeaderTimeout))
    }

    @Test fun noSourceUri_noAnotherPanel() {
        assertEquals(SendFailurePrompt(false), prompt(UploadFailure.NotConnected, hasSource = false))
    }

    @Test fun nothingShownWhenNotAFailureOrAlreadyVisible() {
        assertNull(prompt(UploadFailure.NotConnected, stage = UploadStage.Success))
        assertNull(prompt(UploadFailure.NotConnected, stage = UploadStage.Cancelled))
        assertNull(prompt(null))
        assertNull(prompt(UploadFailure.Cancelled))
        assertNull(prompt(UploadFailure.NotConnected, multi = true))
        assertNull(prompt(UploadFailure.NotConnected, onPanel = true))
    }
}
