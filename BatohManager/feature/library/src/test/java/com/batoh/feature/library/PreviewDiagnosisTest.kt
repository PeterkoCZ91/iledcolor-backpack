package com.batoh.feature.library

import com.batoh.core.domain.model.GifFileProblem
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PreviewDiagnosisTest {
    @Test
    fun editAndSendOnlyWhenTheFileIsUsable() {
        assertTrue((null as PreviewDiagnosis?).allowsEditAndSend())
        assertTrue(PreviewDiagnosis.PreviewOnly.allowsEditAndSend())
        assertFalse(PreviewDiagnosis.Checking.allowsEditAndSend())
        GifFileProblem.values().forEach { assertFalse(PreviewDiagnosis.Broken(it).allowsEditAndSend()) }
    }

    @Test
    fun inspectionResultMapsToDiagnosis() {
        assertEquals(PreviewDiagnosis.PreviewOnly, (null as GifFileProblem?).toDiagnosis())
        assertEquals(PreviewDiagnosis.Broken(GifFileProblem.NOT_GIF), GifFileProblem.NOT_GIF.toDiagnosis())
    }

    @Test
    fun everyProblemHasItsOwnReason() {
        val reasons = GifFileProblem.values().map { it.reasonRes() }
        assertEquals(reasons.size, reasons.toSet().size)
    }
}
