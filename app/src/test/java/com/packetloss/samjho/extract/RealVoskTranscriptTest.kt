package com.packetloss.samjho.extract

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * What vosk-model-small-en-in-0.4 actually returned on the iQOO I2501 for the English demo
 * script, unedited. The model has no entry for paracetamol, azithromycin or cetirizine, so it
 * substitutes real words ("martyrdom", "and my son"). The rules must not turn any of that into
 * a medicine: showing nothing is correct, showing a guessed drug is not.
 */
class RealVoskTranscriptTest {

    private val heard = listOf(
        "gonna do",
        "martyrdom",
        "at three times a day after vote for five days",
        "and my son",
        "in the morning on an empty stomach for three days",
        "they go on at night",
        "cold water and food",
        "if the above one hundred and to go to the hospital immediately",
        "come back after five days",
        "tomorrow",
        "and my son",
    )

    private val result = RuleExtractor.extract(heard)

    @Test
    fun aMangledTranscriptNeverProducesAGuessedMedicine() {
        assertEquals(emptyList<String>(), result.medicines.map { it.name })
    }

    @Test
    fun stillFindsTheWarningAndTheFollowUpThatWereHeardCleanly() {
        assertEquals(listOf(7), result.warnings.flatMap { it.sourceLines })
        val followUp = result.followUp
        assertNotNull(followUp)
        assertEquals(5, followUp!!.inDays)
        assertEquals(listOf(8), followUp.sourceLines)
    }

    @Test
    fun linesWithDosingButNoMedicineNameStayUncoveredForALaterPass() {
        // Line 2 ("... three times a day after vote for five days") has dosing and no drug name.
        assertTrue(2 !in result.coveredLines)
        assertTrue(4 !in result.coveredLines)
    }
}
