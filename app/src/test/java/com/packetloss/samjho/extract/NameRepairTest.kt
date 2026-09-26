package com.packetloss.samjho.extract

import com.packetloss.samjho.llm.LlmEngine
import com.packetloss.samjho.model.Basis
import com.packetloss.samjho.model.Confirmation
import com.packetloss.samjho.model.Extraction
import com.packetloss.samjho.model.Provenance
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Layer 2, on the real transcripts. The model is faked so every reply, hostile ones included, is under test. */
class NameRepairTest {

    private class FakeLlm(private val answer: (String) -> String?) : LlmEngine {
        val prompts = mutableListOf<String>()
        override fun unavailableReason(): String? = null
        override fun complete(prompt: String): String? {
            prompts += prompt
            return answer(prompt)
        }
    }

    // The unedited offline transcript from RealAndroidTranscriptTest.
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

    // The unedited Vosk transcript from RealVoskTranscriptTest.
    private val vosk = listOf(
        "gonna do", "martyrdom", "at three times a day after vote for five days", "and my son",
        "in the morning on an empty stomach for three days", "they go on at night", "cold water and food",
        "if the above one hundred and to go to the hospital immediately", "come back after five days",
        "tomorrow", "and my son",
    )

    /** The real Android result as if the sound-alike rules had missed azithromycin and cetirizine. */
    private fun rulesMissedTwo(): Extraction {
        val full = RuleExtractor.extract(android)
        return full.copy(medicines = full.medicines.filter { it.key == "paracetamol" })
    }

    // ---- which lines are asked about

    @Test
    fun onlyDosingLinesThatNoItemCitesAreAsked() {
        val targets = NameRepair.targets(rulesMissedTwo())
        assertEquals(listOf(1, 2), targets.map { it.line.index })
    }

    @Test
    fun lineWithNoDosingWordsIsNeverAsked() {
        // "Also dollar 650" and "and crossing if the fever comes back" are the real dolo and crocin lines.
        val asked = NameRepair.targets(rulesMissedTwo()).map { it.line.text }
        assertTrue(asked.none { it.contains("dollar") || it.contains("crossing") })
    }

    @Test
    fun offersTheSoundAlikeForEachMangledWord() {
        val byLine = NameRepair.targets(rulesMissedTwo()).associate { it.line.index to it.candidates }
        assertEquals("azithromycin", byLine.getValue(1).first().key)
        assertEquals("thromison", byLine.getValue(1).first().heard)
        assertEquals("cetirizine", byLine.getValue(2).first().key)
    }

    @Test
    fun neverMoreThanFiveCandidatesAndNeverOneAlreadyListed() {
        NameRepair.targets(rulesMissedTwo()).forEach { t ->
            assertTrue(t.candidates.size <= NameRepair.MAX_CANDIDATES)
            assertTrue(t.candidates.none { it.key == "paracetamol" })
        }
    }

    @Test
    fun ordinaryWordsHaveNoCandidatesSoNothingIsAskedAtAll() {
        listOf("hospital", "doctor please", "temperature", "immediately", "morning").forEach { w ->
            assertEquals("'$w' got candidates", emptyList<NameRepair.Candidate>(), NameRepair.candidatesFor(w))
        }
    }

    @Test
    fun theRealVoskTranscriptNeverCallsTheModel() {
        val llm = FakeLlm { "paracetamol" }
        val added = NameRepair.repair(RuleExtractor.extract(vosk), llm)
        assertEquals(0, llm.prompts.size) // its one dosing line has no word with enough sound to compare
        assertTrue(added.isEmpty())
    }

    @Test
    fun whenTheRulesAlreadyFoundEverythingTheModelIsNotAsked() {
        val llm = FakeLlm { "azithromycin" }
        assertTrue(NameRepair.repair(RuleExtractor.extract(android), llm).isEmpty())
        assertEquals(0, llm.prompts.size)
    }

    // ---- what the model is told

    @Test
    fun theModelSeesOnlyTheLineAndTheCandidatesAndIsToldToAnswerOneWord() {
        val target = NameRepair.targets(rulesMissedTwo()).first()
        val p = NameRepair.prompt(target)
        assertTrue(p.contains(target.line.text))
        target.candidates.forEach { assertTrue(p.contains(it.key)) }
        assertTrue(p.contains("UNKNOWN"))
        assertTrue(p.contains("exactly one candidate"))
        assertTrue(p.contains("nothing else"))
        assertTrue(p.contains("Do not give medical advice"))
    }

    // ---- what the model may answer

    @Test
    fun aValidCandidateBecomesAnAiMatchedUnconfirmedItemCitingItsLine() {
        val llm = FakeLlm { prompt ->
            when {
                prompt.contains("thromison") -> "azithromycin"
                prompt.contains("citrus") -> "cetirizine"
                else -> "UNKNOWN"
            }
        }
        val added = NameRepair.repair(rulesMissedTwo(), llm)
        assertEquals(listOf("azithromycin", "cetirizine"), added.map { it.key })

        val azi = added.first()
        assertEquals("thromison", azi.name) // the heard word, not the drug name
        assertEquals(Basis.AI_MATCHED, azi.basis)
        assertEquals(Provenance.AI, azi.provenance)
        assertEquals(Confirmation.UNCONFIRMED, azi.confirmation)
        assertEquals(listOf(1), azi.sourceLines)
        // The model chose the name only: dosing is still read from the line by the rules.
        assertEquals(3, azi.durationDays)
        assertTrue(azi.candidates.contains("azithromycin"))
    }

    @Test
    fun unknownAddsNothing() {
        val llm = FakeLlm { "UNKNOWN" }
        assertTrue(NameRepair.repair(rulesMissedTwo(), llm).isEmpty())
        assertTrue(llm.prompts.isNotEmpty())
    }

    @Test
    fun aRealMedicineThatWasNotOnTheCandidateListIsDiscarded() {
        // amoxicillin is in the lexicon, but it was never offered for these lines.
        val llm = FakeLlm { "amoxicillin" }
        assertTrue(NameRepair.repair(rulesMissedTwo(), llm).isEmpty())
    }

    @Test
    fun anAlreadyListedMedicineNamedByTheModelIsDiscarded() {
        val llm = FakeLlm { "paracetamol" }
        assertTrue(NameRepair.repair(rulesMissedTwo(), llm).isEmpty())
    }

    @Test
    fun anythingButExactlyOneCandidateIsDiscarded() {
        val hostile = listOf(
            "The answer is azithromycin",
            "azithromycin, cetirizine",
            "azithromycin\ncetirizine",
            "azithromycin is an antibiotic taken twice a day",
            "I think azithromycin",
            "",
            "   ",
            "azith",
            "Take azithromycin 500mg",
            "azithromycin cetirizine",
        )
        hostile.forEach { reply ->
            val added = NameRepair.repair(rulesMissedTwo(), FakeLlm { reply })
            assertTrue("accepted: '$reply' -> ${added.map { it.key }}", added.isEmpty())
        }
    }

    @Test
    fun harmlessFormattingAroundASingleCandidateIsAccepted() {
        val target = NameRepair.targets(rulesMissedTwo()).first()
        assertEquals("azithromycin", NameRepair.parse("Azithromycin.", target)?.key)
        assertEquals("azithromycin", NameRepair.parse("  \"azithromycin\"  ", target)?.key)
        assertEquals("azithromycin", NameRepair.parse("AZITHROMYCIN", target)?.key)
        assertNull(NameRepair.parse("unknown", target))
        assertNull(NameRepair.parse(null, target))
    }

    @Test
    fun aModelThatCrashesOrReturnsNullChangesNothing() {
        val crash = object : LlmEngine {
            override fun unavailableReason(): String? = null
            override fun complete(prompt: String): String? = error("native crash")
        }
        assertTrue(NameRepair.repair(rulesMissedTwo(), crash).isEmpty())
        assertTrue(NameRepair.repair(rulesMissedTwo(), FakeLlm { null }).isEmpty())
    }

    // ---- additive only

    @Test
    fun mergingNeverChangesOrReordersWhatTheRulesFound() {
        val base = rulesMissedTwo()
        val llm = FakeLlm { if (it.contains("thromison")) "azithromycin" else "UNKNOWN" }
        val merged = base.withAdded(NameRepair.repair(base, llm))

        assertEquals(base.medicines.first(), merged.medicines.first())
        assertEquals(base.warnings, merged.warnings)
        assertEquals(base.avoid, merged.avoid)
        assertEquals(base.followUp, merged.followUp)
        assertEquals(base.lines, merged.lines)
        assertEquals(listOf("paracetamol", "azithromycin"), merged.medicines.map { it.key })
    }

    @Test
    fun aRepeatOfAMedicineAlreadyListedIsDroppedNotMerged() {
        val base = RuleExtractor.extract(android)
        val dup = RuleExtractor.medicineOnLine(
            base.lines[1], "thromison", "azithromycin", Basis.AI_MATCHED, Provenance.AI, emptyList(),
        )
        val merged = base.withAdded(listOf(dup))
        assertEquals(base.medicines, merged.medicines)
        assertNotNull(merged.medicines.first { it.key == "azithromycin" })
        assertEquals(Provenance.RULES, merged.medicines.first { it.key == "azithromycin" }.provenance)
    }
}
