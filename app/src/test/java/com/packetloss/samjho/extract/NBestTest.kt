package com.packetloss.samjho.extract

import com.packetloss.samjho.model.Basis
import com.packetloss.samjho.model.Confirmation
import com.packetloss.samjho.model.Hypothesis
import com.packetloss.samjho.model.Utterance
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Layer 1: the recogniser's lower-ranked hypotheses.
 *
 * The TOP line of every case below is a line the phone really transcribed (see
 * RealVoskTranscriptTest and RealAndroidTranscriptTest). The runs so far only asked the recogniser
 * for one result, so the ALTERNATIVES here are hand-written stand-ins for what a real N-best list
 * could contain; they exist to prove the rules around them, not to claim what the phone returns.
 */
class NBestTest {

    private fun hyps(vararg texts: String) = texts.map { Hypothesis(it) }

    private fun utterance(vararg texts: String) = Utterance(texts.first(), hyps(*texts))

    // A real Vosk line: dosing words, no medicine, because "paracetamol" is out of its vocabulary.
    private val realTop = "at three times a day after vote for five days"

    @Test
    fun aLowerHypothesisWithAKnownMedicineInDosingContextIsUsed() {
        val r = RuleExtractor.extractUtterances(
            listOf(utterance(realTop, "paracetamol three times a day after food for five days")),
        )
        val m = r.medicines.single()
        assertEquals("paracetamol", m.key)
        assertEquals(Basis.ALTERNATE_HEARING, m.basis)
        assertEquals(1, m.hypothesis)
        assertEquals(Confirmation.UNCONFIRMED, m.confirmation)
    }

    @Test
    fun theTopHypothesisStaysAsTheDisplayedLine() {
        val r = RuleExtractor.extractUtterances(
            listOf(utterance(realTop, "paracetamol three times a day after food for five days")),
        )
        assertEquals(realTop, r.lines.single().text)
        assertEquals(listOf(0), r.medicines.single().sourceLines)
    }

    @Test
    fun dosingIsReadFromTheDisplayedLineNotFromTheAlternative() {
        val r = RuleExtractor.extractUtterances(
            listOf(utterance(realTop, "paracetamol three times a day after food for five days")),
        )
        val m = r.medicines.single()
        assertEquals(3, m.timesPerDay)
        assertEquals(5, m.durationDays)
        // "after food" only exists in the alternative, so the displayed line has no food detail.
        assertNull(m.foodRelation)
    }

    @Test
    fun recordsWhichHypothesisProducedTheMatch() {
        val r = RuleExtractor.extractUtterances(
            listOf(
                utterance(
                    realTop,
                    "at three times a day after vote for five days please",
                    "ibuprofen tablet three times a day for five days",
                    "paracetamol three times a day for five days",
                ),
            ),
        )
        val m = r.medicines.single()
        assertEquals("ibuprofen", m.key) // the best-ranked hypothesis that yields a medicine wins
        assertEquals(2, m.hypothesis)
    }

    @Test
    fun differentHearingsNeverAddUpToSeveralMedicines() {
        val r = RuleExtractor.extractUtterances(
            listOf(
                utterance(
                    realTop,
                    "ibuprofen tablet three times a day",
                    "paracetamol tablet three times a day",
                    "cetirizine tablet three times a day",
                ),
            ),
        )
        assertEquals(listOf("ibuprofen"), r.medicines.map { it.key })
    }

    @Test
    fun anAlternativeWithNoDosingContextIsIgnored() {
        // "Also dollar 650" is a real Android line. Even if a lower hearing spelled "dolo", nothing
        // beside it says how it is taken, so it must not become a medicine.
        val r = RuleExtractor.extractUtterances(listOf(utterance("Also dollar 650", "also dolo 650", "also dolo six fifty")))
        assertTrue(r.medicines.isEmpty())
    }

    @Test
    fun alternativesAreNotConsultedWhenTheTopHearingAlreadyNamedAMedicine() {
        val r = RuleExtractor.extractUtterances(
            listOf(utterance("Take the paracetamol tablet three times a day", "take the ibuprofen tablet three times a day")),
        )
        assertEquals(listOf("paracetamol"), r.medicines.map { it.key })
        assertEquals(Basis.HEARD, r.medicines.single().basis)
        assertEquals(Confirmation.NOT_NEEDED, r.medicines.single().confirmation)
    }

    @Test
    fun ordinaryWordsInAlternativesNeverBecomeAMedicine() {
        val r = RuleExtractor.extractUtterances(
            listOf(utterance(realTop, "doctor please three times a day after food for five days", "practice three times a day")),
        )
        assertTrue(r.medicines.isEmpty())
    }

    @Test
    fun nothingHappensWhenTheRecogniserGaveOnlyOneHypothesis() {
        val one = RuleExtractor.extractUtterances(listOf(utterance(realTop)))
        val plain = RuleExtractor.extract(listOf(realTop))
        assertEquals(plain.medicines, one.medicines)
    }

    @Test
    fun aMatchFromAnAlternativeIsAlwaysCitedToARealLine() {
        val r = RuleExtractor.extractUtterances(
            listOf(
                Utterance("gonna do", hyps("gonna do")),
                utterance(realTop, "paracetamol three times a day after food for five days"),
            ),
        )
        val m = r.medicines.single()
        assertEquals(listOf(1), m.sourceLines)
        assertEquals(realTop, r.quotes(m.sourceLines).single().text)
    }

    @Test
    fun theRealVoskTranscriptStillInventsNothingWithAFullNBestList() {
        // Same real lines as RealVoskTranscriptTest, each with junk lower hypotheses added.
        val real = listOf(
            "gonna do", "martyrdom", "at three times a day after vote for five days", "and my son",
            "in the morning on an empty stomach for three days", "they go on at night", "cold water and food",
            "if the above one hundred and to go to the hospital immediately", "come back after five days",
            "tomorrow", "and my son",
        )
        val r = RuleExtractor.extractUtterances(real.map { utterance(it, "$it please", "the $it", "a $it", "$it again") })
        assertEquals(emptyList<String>(), r.medicines.map { it.name })
    }
}
