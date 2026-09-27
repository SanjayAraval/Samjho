package com.packetloss.samjho.ui

import com.packetloss.samjho.AiState
import com.packetloss.samjho.llm.LlmBackend
import com.packetloss.samjho.llm.LlmStatus
import com.packetloss.samjho.model.Language
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** The line that shows judges the model is running must never say so while it is not. */
class AiIndicatorTest {

    private val en = Strings(Language.ENGLISH)
    private val ready = LlmStatus.Ready(LlmBackend.CPU, 44.3, "")

    @Test
    fun readyShowsTheBackendAndSpeed() {
        val i = en.aiIndicator(ready, AiState.Done(0))
        assertEquals("AI on device · CPU · 44.3 tok/s", i.text)
        assertTrue(i.active)
    }

    @Test
    fun whileTheModelIsAnsweringItSaysSo() {
        val i = en.aiIndicator(ready, AiState.Checking)
        assertEquals("AI on device · CPU · 44.3 tok/s · checking…", i.text)
        assertTrue(i.active)
    }

    @Test
    fun aGpuBackendIsNamed() {
        assertEquals("AI on device · GPU · 32.5 tok/s", en.aiIndicator(LlmStatus.Ready(LlmBackend.GPU, 32.5, ""), AiState.Idle).text)
    }

    @Test
    fun neverClaimsAiWhileItIsOff() {
        val off = listOf(
            en.aiIndicator(LlmStatus.Idle, AiState.Idle),
            en.aiIndicator(LlmStatus.Failed("no model"), AiState.Idle),
            en.aiIndicator(LlmStatus.Failed("no model"), AiState.Unavailable("no model")),
            // Even a model that was ready, once this summary's AI pass said it could not run.
            en.aiIndicator(ready, AiState.Unavailable("timed out")),
        )
        off.forEach {
            assertEquals("AI off · rules only", it.text)
            assertFalse(it.active)
            assertFalse(it.text.contains("on device"))
        }
    }

    @Test
    fun loadingIsNotSaidToBeRunning() {
        val i = en.aiIndicator(LlmStatus.Loading("trying GPU"), AiState.Idle)
        assertEquals("AI model loading…", i.text)
        assertFalse(i.active)
    }

    @Test
    fun hindiFollowsTheSameRules() {
        val hi = Strings(Language.HINDI)
        assertTrue(hi.aiIndicator(ready, AiState.Idle).text.startsWith("AI फ़ोन पर"))
        assertFalse(hi.aiIndicator(LlmStatus.Idle, AiState.Idle).active)
    }
}
