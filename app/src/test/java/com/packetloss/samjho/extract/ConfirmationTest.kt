package com.packetloss.samjho.extract

import com.packetloss.samjho.demo.DemoConsultations
import com.packetloss.samjho.llm.LlmEngine
import com.packetloss.samjho.model.Basis
import com.packetloss.samjho.model.Confirmation
import com.packetloss.samjho.model.Extraction
import com.packetloss.samjho.model.Hypothesis
import com.packetloss.samjho.model.MissingSection
import com.packetloss.samjho.model.Provenance
import com.packetloss.samjho.model.Utterance
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Layer 3, and the rule under all three layers: nothing silently becomes a confirmed medicine. */
class ConfirmationTest {

    private val android = listOf(
        "Take the paracetamol tablet three times a day after food for 5 days",
        "and the thromison in the morning on an empty stomach for 3 days",
        "egg 1 tablet of citrus in at night",
        "avoid cold water and fried food",
        "the fever goes above 102 go to the hospital immediately",
        "Come back after 5 days",
        "Also dollar 650",
        "and crossing if the fever comes back",
        "Bluetooth",
    )

    private val vosk = listOf(
        "gonna do", "martyrdom", "at three times a day after vote for five days", "and my son",
        "in the morning on an empty stomach for three days", "they go on at night", "cold water and food",
        "if the above one hundred and to go to the hospital immediately", "come back after five days",
        "tomorrow", "and my son",
    )

    private val everyTranscriptWeHave: Map<String, Extraction> = mapOf(
        "hindi demo" to RuleExtractor.extract(DemoConsultations.HINDI.lines),
        "english demo" to RuleExtractor.extract(DemoConsultations.ENGLISH.lines),
        "real android" to RuleExtractor.extract(android),
        "real vosk" to RuleExtractor.extract(vosk),
        "real android + n-best" to RuleExtractor.extractUtterances(
            android.map { Utterance(it, listOf(Hypothesis(it), Hypothesis("take $it"), Hypothesis("$it please"))) },
        ),
    )

    // ---- the invariant

    @Test
    fun noExtractionEverCreatesAConfirmedOrRejectedMedicine() {
        everyTranscriptWeHave.forEach { (name, e) ->
            e.medicines.forEach { m ->
                assertTrue(
                    "$name: '${m.name}' starts ${m.confirmation}",
                    m.confirmation == Confirmation.NOT_NEEDED || m.confirmation == Confirmation.UNCONFIRMED,
                )
            }
        }
    }

    @Test
    fun everyInferredMedicineStartsUnconfirmedAndOnlyAHeardOneNeedsNoConfirmation() {
        everyTranscriptWeHave.forEach { (name, e) ->
            e.medicines.forEach { m ->
                // Only a word spelled like a known medicine is taken as heard; an unknown word beside
                // "tablet" is not evidence of a drug name and waits for the patient too.
                if (m.basis == Basis.HEARD && Lexicon.isKey(m.key)) {
                    assertEquals("$name: '${m.name}'", Confirmation.NOT_NEEDED, m.confirmation)
                } else {
                    assertEquals("$name: '${m.name}' (${m.basis})", Confirmation.UNCONFIRMED, m.confirmation)
                }
            }
        }
    }

    @Test
    fun everyUnconfirmedMedicineOffersCandidatesAndCitesARealLine() {
        everyTranscriptWeHave.forEach { (name, e) ->
            e.medicines.filter { it.isUnconfirmed }.forEach { m ->
                assertTrue("$name: '${m.name}' has no candidates", m.candidates.isNotEmpty())
                assertTrue(m.candidates.size <= 5)
                if (Lexicon.isKey(m.key)) assertEquals(m.key, m.candidates.first())
                assertTrue(m.sourceLines.isNotEmpty() && m.sourceLines.all { it in e.lines.indices })
            }
        }
    }

    // ---- the real transcripts

    @Test
    fun realAndroidParacetamolIsHeardOutrightAndTheMangledOnesNeedConfirming() {
        val byKey = everyTranscriptWeHave.getValue("real android").medicines.associateBy { it.key }
        assertEquals(Basis.HEARD, byKey.getValue("paracetamol").basis)
        assertEquals(Confirmation.NOT_NEEDED, byKey.getValue("paracetamol").confirmation)

        val azi = byKey.getValue("azithromycin")
        assertEquals("thromison", azi.name)
        assertEquals(Basis.SOUNDS_LIKE, azi.basis)
        assertEquals(Confirmation.UNCONFIRMED, azi.confirmation)

        val cet = byKey.getValue("cetirizine")
        assertEquals("citrus", cet.name)
        assertEquals(Confirmation.UNCONFIRMED, cet.confirmation)
    }

    @Test
    fun theDemoConsultsWithCleanNamesAsktheUserForNothing() {
        listOf("hindi demo", "english demo").forEach { name ->
            assertTrue(name, everyTranscriptWeHave.getValue(name).medicines.none { it.isUnconfirmed })
        }
    }

    @Test
    fun theRealVoskTranscriptStillHasNoMedicineToConfirm() {
        assertTrue(everyTranscriptWeHave.getValue("real vosk").medicines.isEmpty())
    }

    // ---- the patient's answers

    private fun realAndroid() = everyTranscriptWeHave.getValue("real android")

    private fun indexOf(e: Extraction, key: String) = e.medicines.indexOfFirst { it.key == key }

    @Test
    fun yesConfirmsAndKeepsWhatWasHeard() {
        val e = realAndroid()
        val i = indexOf(e, "azithromycin")
        val after = e.updateMedicine(i) { it.confirmed() }
        assertEquals(Confirmation.CONFIRMED, after.medicines[i].confirmation)
        assertEquals("thromison", after.medicines[i].name)
        assertEquals("azithromycin", after.medicines[i].key)
    }

    @Test
    fun noRejectsAndTheSectionStaysHonestWhenNothingIsLeft() {
        val e = RuleExtractor.extract(listOf("and the thromison in the morning for 3 days"))
        val only = e.medicines.single()
        assertTrue(only.isUnconfirmed)
        val after = e.updateMedicine(0) { it.rejected() }
        assertTrue(after.medicines[0].isRejected)
        assertTrue(MissingSection.MEDICINES in after.missingSections)
    }

    @Test
    fun chooseAnotherReplacesTheIdentityAndCountsAsConfirmed() {
        val e = realAndroid()
        val i = indexOf(e, "cetirizine")
        val options = e.medicines[i].candidates.filter { it != "cetirizine" }
        assertTrue(options.isNotEmpty())
        val after = e.updateMedicine(i) { it.choosing(options.first()) }
        assertEquals(options.first(), after.medicines[i].key)
        assertEquals(Confirmation.CONFIRMED, after.medicines[i].confirmation)
        assertEquals("citrus", after.medicines[i].name) // still shows what was actually heard
    }

    @Test
    fun undoReturnsAnInferredMedicineToUnconfirmedAndAHeardOneToNotNeeded() {
        val e = realAndroid()
        val azi = indexOf(e, "azithromycin")
        val para = indexOf(e, "paracetamol")
        assertEquals(Confirmation.UNCONFIRMED, e.updateMedicine(azi) { it.confirmed().reopened() }.medicines[azi].confirmation)
        assertEquals(Confirmation.UNCONFIRMED, e.updateMedicine(azi) { it.rejected().reopened() }.medicines[azi].confirmation)
        assertEquals(Confirmation.NOT_NEEDED, e.updateMedicine(para) { it.rejected().reopened() }.medicines[para].confirmation)
    }

    @Test
    fun undoingAChoiceRestoresTheOriginalSuggestionNotThePatientsPick() {
        // Found on the phone: choose Levocetirizine, press Undo, and it said "Possibly: Levocetirizine".
        val e = realAndroid()
        val i = indexOf(e, "cetirizine")
        val chosen = e.updateMedicine(i) { it.choosing("levocetirizine") }
        assertEquals("levocetirizine", chosen.medicines[i].key)

        val undone = chosen.updateMedicine(i) { it.reopened() }
        assertEquals("cetirizine", undone.medicines[i].key)
        assertEquals(Confirmation.UNCONFIRMED, undone.medicines[i].confirmation)
        assertEquals(e.medicines[i], undone.medicines[i]) // exactly as first extracted
    }

    @Test
    fun choosingTwiceThenUndoStillReturnsToTheOriginalSuggestion() {
        val e = realAndroid()
        val i = indexOf(e, "cetirizine")
        val after = e.updateMedicine(i) { it.choosing("levocetirizine") }
            .updateMedicine(i) { it.choosing("salbutamol") }
            .updateMedicine(i) { it.reopened() }
        assertEquals("cetirizine", after.medicines[i].key)
    }

    @Test
    fun answeringOneMedicineNeverTouchesAnotherOrTheRestOfTheResult() {
        val e = realAndroid()
        val i = indexOf(e, "azithromycin")
        val after = e.updateMedicine(i) { it.confirmed() }
        e.medicines.forEachIndexed { j, m -> if (j != i) assertEquals(m, after.medicines[j]) }
        assertEquals(e.warnings, after.warnings)
        assertEquals(e.avoid, after.avoid)
        assertEquals(e.followUp, after.followUp)
        assertEquals(e.lines, after.lines)
    }

    // ---- all three layers together

    @Test
    fun anAiMatchAlsoStartsUnconfirmedAndCanBeConfirmedLikeAnyOther() {
        val full = RuleExtractor.extract(android)
        val missed = full.copy(medicines = full.medicines.filter { it.key == "paracetamol" })
        val llm = object : LlmEngine {
            override fun unavailableReason(): String? = null
            override fun complete(prompt: String): String? = if (prompt.contains("thromison")) "azithromycin" else "UNKNOWN"
        }
        val merged = missed.withAdded(NameRepair.repair(missed, llm))

        val ai = merged.medicines.single { it.key == "azithromycin" }
        assertEquals(Basis.AI_MATCHED, ai.basis)
        assertEquals(Provenance.AI, ai.provenance)
        assertEquals(Confirmation.UNCONFIRMED, ai.confirmation)
        assertFalse(merged.medicines.first { it.key == "paracetamol" }.isUnconfirmed)

        val i = merged.medicines.indexOf(ai)
        assertEquals(Confirmation.CONFIRMED, merged.updateMedicine(i) { it.confirmed() }.medicines[i].confirmation)
    }
}
