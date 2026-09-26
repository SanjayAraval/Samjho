package com.packetloss.samjho.speech

import com.packetloss.samjho.speech.ContinuousTranscript.EndKind
import com.packetloss.samjho.speech.ContinuousTranscript.Step
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** The recording keeps going across the recogniser ending its windows, and never loses or replaces what was said. */
class ContinuousTranscriptTest {

    private val p = ContinuousTranscript()

    /** A tiny driver: a clock, and the transcript the engine would have built from the steps it is told to carry out. */
    private var now = 0L
    private val transcript = mutableListOf<String>()
    private val restarts = mutableListOf<Step.Restart>()
    private var failed: String? = null

    private fun run(steps: List<Step>): List<Step> {
        for (s in steps) when (s) {
            is Step.Commit -> transcript += s.text
            is Step.Restart -> restarts += s
            is Step.Fail -> failed = s.reason
        }
        return steps
    }

    private fun open() = p.onWindowOpened(now)
    private fun advance(ms: Long) { now += ms }
    private fun hear(text: String) = p.onPartial(text, now)
    private fun tick() = run(p.onTick(now))
    private fun results(text: String) = run(p.onResults(text, now))
    private fun ends(kind: EndKind = EndKind.SILENT) = run(p.onEnded(kind, now))

    /** Ticks once a second, as the engine does every half second or so, for [seconds]. */
    private fun quiet(seconds: Int) = repeat(seconds) { advance(1_000); tick() }

    // ---- the reported failure: a sentence, a pause, another sentence

    @Test
    fun aSentenceThenAThreeSecondPauseThenAnotherSentenceAreBothKept() {
        open()
        hear("take paracetamol for three days")
        advance(3_000) // a three second pause, inside the window
        tick()
        assertEquals(emptyList<String>(), transcript) // not yet a pause long enough to split the line
        hear("take paracetamol for three days after food")
        results("Take paracetamol for three days after food")
        assertEquals(listOf("Take paracetamol for three days after food"), transcript)
    }

    @Test
    fun aWindowThatEndsWithNoMatchKeepsTheWordsItHeard() {
        // What the phone actually did: the window ended with NO_MATCH and no result, and the text was thrown away.
        open()
        hear("take paracetamol for three days")
        advance(8_000)
        val steps = ends(EndKind.SILENT)
        assertEquals(listOf("take paracetamol for three days"), transcript)
        assertTrue(steps.first() is Step.Commit && (steps.first() as Step.Commit).fromPartial)
        assertTrue(steps.last() is Step.Restart)
    }

    @Test
    fun aWindowThatEndsWithNoMatchAndNothingHeardAddsNothingAndListensAgain() {
        open()
        advance(5_000)
        ends(EndKind.SILENT)
        assertEquals(emptyList<String>(), transcript)
        assertEquals(1, restarts.size)
        assertEquals(60L, restarts.single().delayMs)
    }

    @Test
    fun theEightSecondPauseIsSurvivedAndTheTextAfterItIsAppended() {
        open()
        hear("take paracetamol for three days")
        // the words stop changing; after the stall time they become a line and the window is reopened
        advance(6_000); tick()
        assertEquals(listOf("take paracetamol for three days"), transcript)
        assertTrue(restarts.last().cancelFirst)
        open()
        // eight silent seconds, then more speech: nothing fails, nothing is lost
        quiet(8)
        assertEquals(1, transcript.size)
        assertEquals(null, failed)
        hear("after food")
        results("After food")
        assertEquals(listOf("take paracetamol for three days", "After food"), transcript)
    }

    @Test
    fun linesOnlyEverAppendInTheOrderTheyWereSaid() {
        for (line in listOf("one", "two", "three", "four")) {
            open()
            hear(line)
            advance(2_000)
            results(line)
        }
        assertEquals(listOf("one", "two", "three", "four"), transcript)
    }

    @Test
    fun aFinalResultReplacesItsOwnPartialAndIsNotCountedTwice() {
        open()
        hear("take paracetamol")
        results("Take paracetamol for three days")
        assertEquals(listOf("Take paracetamol for three days"), transcript)
        // and the stall check must not commit the already-finished partial a second time
        advance(10_000); tick()
        assertEquals(1, transcript.size)
    }

    @Test
    fun anEmptyFinalResultKeepsTheLastPartialInsteadOfLosingIt() {
        open()
        hear("after food")
        results("")
        assertEquals(listOf("after food"), transcript)
    }

    // ---- pauses become lines without waiting for the recogniser

    @Test
    fun wordsThatHaveStoppedChangingBecomeALineAndTheWindowIsCancelledAndReopened() {
        open()
        hear("avoid cold water")
        advance(5_999); tick()
        assertEquals(emptyList<String>(), transcript)
        advance(1); val steps = tick()
        assertEquals(listOf("avoid cold water"), transcript)
        assertEquals(Step.Restart(delayMs = 60, cancelFirst = true, recreate = true), steps.last())
    }

    @Test
    fun aRecogniserWhoseTextLagsBehindSpeechIsNotCutOffAfterAFewSeconds() {
        // On the phone the partial was "take me" while "paracetamol for three days" was still being recognised.
        open()
        hear("take me")
        advance(3_600); tick()
        advance(1_000); tick()
        assertEquals(emptyList<String>(), transcript)
        hear("take paracetamol for three days")
        results("Take paracetamol for three days")
        assertEquals(listOf("Take paracetamol for three days"), transcript)
    }

    @Test
    fun aPartialThatKeepsChangingIsNeverSplitMidSentence() {
        open()
        for (w in listOf("take", "take one", "take one tablet", "take one tablet at", "take one tablet at night")) {
            hear(w); advance(2_000); tick()
        }
        assertEquals(emptyList<String>(), transcript)
    }

    @Test
    fun endOfSpeechWithNoFollowUpIsForcedAfterAShortGrace() {
        open()
        hear("come back after five days")
        p.onEndOfSpeech(now)
        advance(2_000); tick()
        assertEquals(emptyList<String>(), transcript)
        advance(600); tick()
        assertEquals(listOf("come back after five days"), transcript)
    }

    @Test
    fun aRecogniserThatStopsAnsweringIsRebuiltAfterAWhile() {
        open()
        advance(19_000); tick()
        assertTrue(restarts.isEmpty())
        advance(1_500); tick()
        assertTrue(restarts.single().recreate)
    }

    // ---- a spinning restart loop backs off, and only very persistent failure gives up

    @Test
    fun windowsThatEndAtOnceBackOffInsteadOfSpinning() {
        val delays = mutableListOf<Long>()
        repeat(8) {
            open(); advance(50)
            ends(EndKind.SILENT)
            delays += restarts.last().delayMs
        }
        assertEquals(listOf(120L, 240L, 480L, 960L, 1920L, 2000L, 2000L, 2000L), delays)
    }

    @Test
    fun aRealWindowResetsTheBackOff() {
        repeat(4) { open(); advance(50); ends() }
        assertTrue(restarts.last().delayMs > 60)
        open(); advance(5_000); ends()
        assertEquals(60L, restarts.last().delayMs)
    }

    @Test
    fun aQuietRoomIsNeverAFailure() {
        // Fifteen minutes of ordinary silent windows, each ending with NO_MATCH or a timeout.
        repeat(180) { open(); advance(5_000); ends(EndKind.SILENT) }
        assertEquals(null, failed)
        assertEquals(180, restarts.size)
    }

    @Test
    fun onlyAnEndlessInstantLoopGivesUp() {
        repeat(ContinuousTranscript.MAX_QUICK_ENDS + 2) { open(); advance(10); ends(EndKind.NOT_READY) }
        assertTrue(failed != null)
    }

    @Test
    fun afterSeveralNotReadyAnswersInARowAFreshRecogniserIsUsed() {
        open(); advance(5_000); ends(EndKind.NOT_READY)
        assertFalse(restarts.last().recreate)
        assertEquals(200L, restarts.last().delayMs)
        open(); advance(5_000); ends(EndKind.NOT_READY)
        assertTrue(restarts.last().recreate)
    }

    @Test
    fun aPauseIsNotMistakenForAFailingRecogniser() {
        repeat(40) {
            open(); hear("sentence $it"); advance(7_000); tick()
        }
        assertEquals(null, failed)
        assertEquals(40, transcript.size)
        assertTrue(restarts.all { it.delayMs == 60L })
        assertTrue(restarts.all { it.recreate })
    }

    // ---- stop

    @Test
    fun stopReturnsTheWordsStillInFlight() {
        open()
        hear("go to the hospital immediately")
        assertEquals("go to the hospital immediately", p.stop())
    }

    @Test
    fun afterStopNothingRestartsAndNothingIsAdded() {
        open()
        hear("some words")
        p.stop()
        val before = restarts.size
        assertEquals(emptyList<Step>(), results("late result"))
        assertEquals(emptyList<Step>(), ends())
        advance(60_000)
        assertEquals(emptyList<Step>(), tick())
        assertEquals(before, restarts.size)
        assertEquals(emptyList<String>(), transcript)
    }

    @Test
    fun wordsAlreadyMadeIntoALineAreNotReturnedAgainByStop() {
        open()
        hear("avoid cold water")
        advance(7_000); tick()
        assertEquals("", p.stop())
        assertEquals(listOf("avoid cold water"), transcript)
    }

    @Test
    fun aWholeConsultationWithPausesAndDroppedWindowsLosesNothing() {
        // Windows that end four different ways, as the phone really does, across a single recording.
        open(); hear("take paracetamol"); advance(1_000); hear("take paracetamol for three days"); advance(8_000); ends(EndKind.SILENT)
        open(); hear("after food"); advance(4_000); ends(EndKind.SILENT)
        open(); advance(9_000); ends(EndKind.SILENT)
        open(); hear("and azithromycin in the morning"); advance(1_500); results("And azithromycin in the morning")
        open(); hear("come back in five days"); advance(500)
        assertEquals("come back in five days", p.stop())
        assertEquals(
            listOf("take paracetamol for three days", "after food", "And azithromycin in the morning"),
            transcript,
        )
    }
}
