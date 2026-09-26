package com.packetloss.samjho.scan

import com.packetloss.samjho.extract.NameRepair
import com.packetloss.samjho.llm.LlmEngine
import com.packetloss.samjho.model.Confirmation
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ScanRepairTest {

    private class FakeLlm(private val answer: (String) -> String?) : LlmEngine {
        val prompts = mutableListOf<String>()
        override fun unavailableReason(): String? = null
        override fun complete(prompt: String): String? {
            prompts += prompt
            return answer(prompt)
        }
    }

    private fun matched(vararg lines: String) = ScanMatcher.match(lines.toList())

    private fun targets(vararg lines: String, existing: List<ScanItem> = matched(*lines)) =
        ScanRepair.targets(lines.toList(), existing)

    // "Thrombison" is the real word speech mangled azithromycin into; a reader can mangle it the same way.
    private val garbled = "Tab. Thrombison 500 mg once daily"

    // ---- which words are asked about

    @Test
    fun aWordTheRulesMissedButThatLooksLikeAMedicineBecomesATarget() {
        assertEquals(emptyList<String>(), matched(garbled).map { it.key }) // the rules found nothing
        val t = targets(garbled).single()
        assertEquals("Thrombison", t.readAs)
        assertTrue("azithromycin" in t.candidates.map { it.key })
    }

    @Test
    fun theShortlistComesFromTheSpeechPathsOwnCandidatesBoundedAndNoLooserThanItsCutoff() {
        val t = targets(garbled).single()
        assertTrue(NameRepair.candidatesFor("Thrombison").map { it.key }.containsAll(t.candidates.map { it.key }))
        assertTrue(t.candidates.size <= NameRepair.MAX_CANDIDATES)
        assertTrue(t.candidates.all { it.distance <= ScanRepair.MAX_DISTANCE })
        assertTrue(ScanRepair.MAX_DISTANCE < NameRepair.MAX_DISTANCE)
    }

    @Test
    fun theMisreadsTheRulesAreTooStrictForGoToTheModelWithTheRightCandidate() {
        val expected = mapOf(
            "Thrombison" to "azithromycin", "Cetrin" to "cetirizine", "Omepraz" to "omeprazole",
            "Ranitin" to "ranitidine", "Paracet" to "paracetamol",
        )
        for ((word, key) in expected) {
            assertEquals("$word is not matched by the rules", emptyList<String>(), matched("Tab $word 500").map { it.key })
            val t = targets("Tab $word 500").singleOrNull()
            assertTrue("$word should reach the model", t != null && key in t.candidates.map { it.key })
        }
    }

    @Test
    fun aBrandThatIsNotInTheLexiconIsNotForcedOntoAnUnrelatedName() {
        // "Metrogyl" is a real brand, but the only look-alike on the list is omeprazole, which it is not.
        assertEquals(emptyList<ScanRepair.Target>(), targets("Tab Metrogyl 400"))
    }

    @Test
    fun aWordTheRulesAlreadyMatchedIsNeverAskedAbout() {
        val lines = arrayOf("Tab Paracetamol 500", "Cap Cetirizlne", garbled)
        assertEquals(listOf("Thrombison"), targets(*lines).map { it.readAs })
    }

    @Test
    fun aMedicineAlreadyListedIsNotOfferedAgainAsACandidate() {
        val existing = listOf(ScanItem.picked(0, "azithromycin"))
        assertEquals(emptyList<ScanRepair.Target>(), ScanRepair.targets(listOf("Thrombison 500"), existing))
    }

    @Test
    fun everydayPrescriptionWordsAreNeverTargets() {
        for (w in ScanFixtures.EVERYDAY_WORDS) {
            assertEquals("\"$w\" must not be sent to the model", emptyList<ScanRepair.Target>(), targets(w))
        }
    }

    @Test
    fun aWholeHeaderAndFooterOfOrdinaryTextHasNoTargets() {
        assertEquals(emptyList<ScanRepair.Target>(), ScanRepair.targets(ScanFixtures.HEADER_AND_FOOTER, emptyList()))
    }

    @Test
    fun numbersDosesAndShortWordsAreNeverTargets() {
        assertEquals(emptyList<ScanRepair.Target>(), targets("500 mg", "1-0-1", "650mg", "x 5 days", "Tab", "Cap", "OD", "BD", "HS"))
    }

    @Test
    fun unreadableGarbageHasNoLookAlikeSoItIsNeverSentToTheModel() {
        assertEquals(emptyList<ScanRepair.Target>(), targets("Xlqpz mnbvw", "Zzzxq wvvvb", "ooo lll iii"))
    }

    @Test
    fun neighbouringWordsAreNeverJoinedIntoATargetSoTwoOrdinaryWordsCannotBecomeADrug() {
        // A real limitation: a name split across a gap ("Thromb ison") is left to the rules and to the
        // patient's manual list, because "City Clinic" joins into something drug-shaped just as easily.
        assertEquals(emptyList<ScanRepair.Target>(), targets("Tab Thromb ison 500", "City Clinic", "if does"))
    }

    @Test
    fun aWordNeedsEnoughConsonantsAndTheSameStartAsTheMedicineItMightBe() {
        assertEquals(emptyList<ScanRepair.Target>(), targets("settle")) // three consonants
        assertEquals(emptyList<ScanRepair.Target>(), targets("clinic")) // starts like nothing it resembles
        assertEquals(listOf("Thrombison"), targets("Thrombison").map { it.readAs }) // azithromycin, first syllable clipped
    }

    @Test
    fun theSameWordTwiceIsOneTarget() {
        assertEquals(1, targets("Thrombison 500", "Tab Thrombison after food").size)
    }

    @Test
    fun aPageWithManyUnreadWordsAsksAboutAtMostAFew() {
        val lines = (1..20).map { "Thrombison$it Cetrin$it Pantaprazol$it" }
        assertTrue(ScanRepair.targets(lines, emptyList()).size <= ScanRepair.MAX_TARGETS)
    }

    // ---- what the model is told

    @Test
    fun theModelSeesOnlyTheOneWordAndItsShortlistNeverTheRestOfThePage() {
        val page = listOf("Patient Name: Ramesh Patil  Age: 45", "Dr. Anil Kumar MBBS", garbled)
        val llm = FakeLlm { "UNKNOWN" }
        ScanRepair.repair(page, matched(*page.toTypedArray()), llm)
        val prompt = llm.prompts.single()
        assertTrue(prompt.contains("Thrombison"))
        assertFalse(prompt.contains("Ramesh"))
        assertFalse(prompt.contains("Anil"))
        assertFalse(prompt.contains("500 mg"))
    }

    @Test
    fun theModelIsToldTheTextCameFromAPhotoAndMayContainLetterMistakes() {
        val llm = FakeLlm { "UNKNOWN" }
        ScanRepair.repair(listOf(garbled), emptyList(), llm)
        val p = llm.prompts.single()
        assertTrue(p.contains("photo of a prescription"))
        assertTrue(p.contains("letter mistakes"))
        assertTrue(p.contains("UNKNOWN"))
        assertTrue(p.contains("exactly one candidate"))
        assertTrue(p.contains("Do not give medical advice"))
    }

    @Test
    fun theSpeechPromptIsUnchangedByTheSharedPath() {
        val p = NameRepair.prompt("Is it thrombison once a day", NameRepair.candidatesFor("thrombison"), NameRepair.Source.SPEECH)
        assertTrue(p.startsWith("Match a spoken medicine name to a fixed list."))
        assertTrue(p.contains("Sentence: \"Is it thrombison once a day\""))
        assertTrue(p.contains("sounds like"))
    }

    // ---- what the model may answer

    @Test
    fun aValidAnswerBecomesAnUnconfirmedAiMatchedSuggestionAndNeverConfirmed() {
        val items = ScanRepair.repair(listOf(garbled), emptyList(), FakeLlm { "azithromycin" })
        val item = items.single()
        assertEquals("azithromycin", item.key)
        assertEquals("Thrombison", item.readAs)
        assertEquals(ScanBasis.AI_MATCHED, item.basis)
        assertEquals(Confirmation.UNCONFIRMED, item.confirmation)
        assertFalse(item.key in item.candidates)
        assertTrue(item.candidates.size <= NameRepair.MAX_CANDIDATES)
    }

    @Test
    fun unknownMeansNothingIsAdded() {
        assertEquals(emptyList<ScanItem>(), ScanRepair.repair(listOf(garbled), emptyList(), FakeLlm { "UNKNOWN" }))
    }

    @Test
    fun aRealMedicineThatWasNotOnTheShortlistIsDiscarded() {
        assertEquals(emptyList<ScanItem>(), ScanRepair.repair(listOf(garbled), emptyList(), FakeLlm { "ibuprofen" }))
    }

    @Test
    fun anythingThatIsNotExactlyOneCandidateIsDiscarded() {
        for (reply in listOf("azithromycin or cetirizine", "I think it is azithromycin", "", "  ", "The answer: azithromycin", "no")) {
            assertEquals("\"$reply\"", emptyList<ScanItem>(), ScanRepair.repair(listOf(garbled), emptyList(), FakeLlm { reply }))
        }
    }

    @Test
    fun aReplyInDifferentCaseOrQuotesIsStillAccepted() {
        for (reply in listOf("Azithromycin.", "  \"azithromycin\"  ", "AZITHROMYCIN")) {
            assertEquals(reply, "azithromycin", ScanRepair.repair(listOf(garbled), emptyList(), FakeLlm { reply }).single().key)
        }
    }

    @Test
    fun aModelThatIsMissingFailsOrCrashesLeavesTheScanExactlyAsItWas() {
        assertEquals(emptyList<ScanItem>(), ScanRepair.repair(listOf(garbled), emptyList(), FakeLlm { null }))
        val crashing = object : LlmEngine {
            override fun unavailableReason(): String? = null
            override fun complete(prompt: String): String? = error("native crash")
        }
        assertEquals(emptyList<ScanItem>(), ScanRepair.repair(listOf(garbled), emptyList(), crashing))
    }

    @Test
    fun twoWordsThatTheModelMapsToTheSameMedicineGiveOneSuggestion() {
        val items = ScanRepair.repair(listOf("Thrombison 500", "Tab Thromison after food"), emptyList(), FakeLlm { "azithromycin" })
        assertEquals(1, items.size)
    }

    // ---- adding to the scan

    private val page = OcrRead(latin = listOf("Tab Paracetamol 500", garbled), devanagari = emptyList())

    private fun scanned() = ScanUi().reading().withRead(page, ScanMatcher.match(page.allLines()), 100)

    @Test
    fun theAiSuggestionIsAppendedAfterTheRulesResultAndNothingThereIsChanged() {
        val ui = scanned()
        val added = ScanRepair.repair(page.allLines(), ui.items, FakeLlm { "azithromycin" })
        val next = ui.withAdded(added)
        assertEquals(ui.items, next.items.take(ui.items.size))
        assertEquals("azithromycin", next.items.last().key)
        assertEquals(Confirmation.UNCONFIRMED, next.items.last().confirmation)
    }

    @Test
    fun aSuggestionForAMedicineAlreadyListedIsDroppedWhateverItsState() {
        var ui = scanned()
        for (change in listOf<(ScanUi, Int) -> ScanUi>({ u, i -> u.confirm(i) }, { u, i -> u.reject(i) })) {
            ui = scanned()
            val id = ui.items.single { it.key == "paracetamol" }.id
            val decided = change(ui, id)
            val ai = ScanItem(0, "Paracetamool", "paracetamol", "paracetamol", emptyList(), ScanBasis.AI_MATCHED, Confirmation.UNCONFIRMED)
            assertEquals(decided.items, decided.withAdded(listOf(ai)).items)
        }
    }

    @Test
    fun anAiSuggestionForAHandPickedMedicineIsDropped() {
        val ui = ScanUi().pick("azithromycin")
        val ai = ScanItem(0, "Thrombison", "azithromycin", "azithromycin", emptyList(), ScanBasis.AI_MATCHED, Confirmation.UNCONFIRMED)
        assertEquals(ui.items, ui.withAdded(listOf(ai)).items)
    }

    @Test
    fun addedSuggestionsGetUniqueIdsAndNothingIsEverAutoConfirmed() {
        val ui = scanned().pick("metformin")
        val added = ScanRepair.repair(page.allLines(), ui.items, FakeLlm { "azithromycin" })
        val next = ui.withAdded(added)
        assertEquals(next.items.size, next.items.map { it.id }.toSet().size)
        assertTrue(next.items.filter { it.basis == ScanBasis.AI_MATCHED }.all { it.confirmation == Confirmation.UNCONFIRMED })
    }

    @Test
    fun aRescanReplacesAnAiSuggestionNobodyAnsweredAndKeepsOneThePatientConfirmed() {
        val ui = scanned()
        val ai = ui.withAdded(ScanRepair.repair(page.allLines(), ui.items, FakeLlm { "azithromycin" }))
        val id = ai.items.single { it.key == "azithromycin" }.id

        val unanswered = ai.reading().withRead(page, ScanMatcher.match(page.allLines()), 5)
        assertFalse(unanswered.items.any { it.key == "azithromycin" })

        val answered = ai.confirm(id).reading().withRead(page, ScanMatcher.match(page.allLines()), 5)
        assertEquals(Confirmation.CONFIRMED, answered.items.single { it.key == "azithromycin" }.confirmation)
    }

    @Test
    fun aConfirmedAiSuggestionGoesIntoTheSummaryLikeAnyOtherConfirmedName() {
        val ui = scanned()
        val ai = ui.withAdded(ScanRepair.repair(page.allLines(), ui.items, FakeLlm { "azithromycin" }))
        val confirmed = ai.confirm(ai.items.single { it.key == "azithromycin" }.id)
        val consultation = com.packetloss.samjho.extract.RuleExtractor.extract(
            com.packetloss.samjho.demo.DemoConsultations.ENGLISH_AS_HEARD.lines,
        )
        val merged = PaperMerge.merge(consultation, confirmed.items).extraction
        val card = merged.medicines.single { it.key == "azithromycin" }
        assertEquals(Confirmation.CONFIRMED, card.confirmation)
        assertEquals("thrombison", card.name) // the doctor's word, with the doctor's dosing
    }

    @Test
    fun anUnansweredAiSuggestionNeverReachesTheSummary() {
        val ui = scanned()
        val ai = ui.withAdded(ScanRepair.repair(page.allLines(), ui.items, FakeLlm { "azithromycin" }))
        val consultation = com.packetloss.samjho.extract.RuleExtractor.extract(
            com.packetloss.samjho.demo.DemoConsultations.ENGLISH.lines,
        )
        assertEquals(consultation, PaperMerge.merge(consultation, ai.items.filter { it.basis == ScanBasis.AI_MATCHED }).extraction)
    }
}
