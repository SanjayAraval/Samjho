package com.packetloss.samjho.extract

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * A lexicon entry with only two consonants (dolo = "dl", ors = "rs", pan = "pn", iron = "rn", amoxil) carries almost
 * no sound, so a heard word must also be close to it letter by letter. "resin" once became ORS on the strength of two
 * shared consonants and hid a cetirizine line from the language model.
 */
class ShortEntryMatchingTest {

    private fun phonetic(word: String) = Lexicon.matchPhonetic(Normalize.text(word))

    @Test
    fun wordsThatOnlyShareTwoConsonantsWithOrsAreNotOrs() {
        for (w in listOf("resin", "reason", "risen", "raisin", "russian", "rusty", "roast", "roost", "rescue", "resume", "resist", "reset", "residue", "arson")) {
            assertNotEquals("'$w'", "ors", phonetic(w))
        }
    }

    @Test
    fun theSameGoesForTheOtherVeryShortEntries() {
        // dolo
        for (w in listOf("deals", "delays", "delete", "dealer", "deadline", "headline", "dulce")) assertNull("'$w'", phonetic(w))
        // amoxil
        for (w in listOf("million", "meals", "multi", "millis")) assertNull("'$w'", phonetic(w))
        // pan
        for (w in listOf("opened", "opens", "opening", "happened", "happens", "append", "appended")) assertNull("'$w'", phonetic(w))
        // iron
        for (w in listOf("orange", "orient", "running", "runner", "rinse", "round", "ruins", "runes", "hearing")) assertNull("'$w'", phonetic(w))
    }

    @Test
    fun dollarIsStillDoloBecauseItIsExactlyHalfWayThere() {
        // The real Android line "Also dollar 650".
        assertEquals("paracetamol", phonetic("dollar"))
    }

    @Test
    fun aLongerWordThatContainsTheWholeEntryStillCounts() {
        assertEquals("pantoprazole", phonetic("panel"))
        assertEquals("pantoprazole", phonetic("paint"))
    }

    @Test
    fun theExactNamesAreUnaffected() {
        assertEquals("ors", Lexicon.exact("ors"))
        assertEquals("ors", Lexicon.exact(Normalize.text("ओआरएस")))
        assertEquals("paracetamol", Lexicon.exact("dolo"))
        assertEquals("pantoprazole", Lexicon.exact("pan"))
        assertEquals("iron", Lexicon.exact("iron"))
        assertEquals("amoxicillin", Lexicon.exact("amoxil"))
    }

    @Test
    fun aSpokenOrsIsStillAnOrsCardAndResinNextToADoseIsNot() {
        val ors = RuleExtractor.extract(listOf("Take ORS after every loose motion")).medicines.map { it.key }
        assertTrue("ors" in ors)
        val resin = RuleExtractor.extract(listOf("Once it resin at night before sleeping")).medicines.map { it.key }
        assertTrue("ors" !in resin)
    }

    @Test
    fun theShortlistThatFeedsTheModelIsNotChanged() {
        // MAX_DISTANCE is untouched: cetirizine sits exactly on it for "resin".
        assertEquals(0.4, NameRepair.MAX_DISTANCE, 0.0)
        val keys = Lexicon.closest("resin", 8, NameRepair.MAX_DISTANCE).map { it.key }
        assertTrue("cetirizine" in keys)
    }
}
