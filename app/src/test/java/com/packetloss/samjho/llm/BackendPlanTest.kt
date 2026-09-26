package com.packetloss.samjho.llm

import com.packetloss.samjho.llm.LlmBackend.CPU
import com.packetloss.samjho.llm.LlmBackend.GPU
import com.packetloss.samjho.llm.LlmBackend.NPU
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class BackendPlanTest {

    private fun ok(tps: Double) = ProbeResult.Ok(tps, 100.0, 3.0)

    @Test
    fun backendsAreTriedNpuThenGpuThenCpu() {
        assertEquals(listOf(NPU, GPU, CPU), BackendPlan.pending(emptyMap()))
    }

    @Test
    fun aBackendAlreadyTriedIsNotTriedAgainWhateverItsOutcome() {
        val done = mapOf(NPU to ProbeResult.Crashed, GPU to ProbeResult.Failed("no driver"))
        assertEquals(listOf(CPU), BackendPlan.pending(done))
        assertEquals(emptyList<LlmBackend>(), BackendPlan.pending(done + (CPU to ok(10.0))))
    }

    @Test
    fun theFastestBackendThatWorksIsKept() {
        assertEquals(GPU, BackendPlan.best(mapOf(GPU to ok(22.0), CPU to ok(12.0))))
        assertEquals(CPU, BackendPlan.best(mapOf(GPU to ok(8.0), CPU to ok(12.0))))
    }

    @Test
    fun aFailedOrCrashedBackendIsNeverChosenEvenIfItWasListedFirst() {
        val r = mapOf(NPU to ProbeResult.Crashed, GPU to ProbeResult.Failed("x"), CPU to ok(5.0))
        assertEquals(CPU, BackendPlan.best(r))
        assertEquals(listOf(CPU), BackendPlan.rankedWorking(r))
    }

    @Test
    fun withNothingWorkingThereIsNoBackend() {
        assertNull(BackendPlan.best(mapOf(NPU to ProbeResult.Crashed, GPU to ProbeResult.Failed("x"), CPU to ProbeResult.Failed("y"))))
        assertNull(BackendPlan.best(emptyMap()))
    }

    @Test
    fun aTieGoesToTheEarlierBackend() {
        assertEquals(GPU, BackendPlan.best(mapOf(GPU to ok(10.0), CPU to ok(10.0))))
    }

    @Test
    fun theFallbackOrderIsFastestFirst() {
        assertEquals(listOf(CPU, GPU), BackendPlan.rankedWorking(mapOf(GPU to ok(8.0), CPU to ok(12.0))))
    }

    // ---- the cache

    @Test
    fun theCacheRoundTripsEveryKindOfResult() {
        val results = linkedMapOf(
            NPU to ProbeResult.Failed("needs a Qualcomm model | with a pipe"),
            GPU to ok(21.5),
            CPU to ProbeResult.Crashed,
        )
        val parsed = BackendPlan.parse(BackendPlan.serialize("k1", results), "k1")
        assertEquals(results, parsed)
    }

    @Test
    fun aCacheMadeForAnotherModelOrRuntimeIsIgnored() {
        val text = BackendPlan.serialize("size1:0.17.1", mapOf(GPU to ok(20.0)))
        assertTrue(BackendPlan.parse(text, "size2:0.17.1").isEmpty())
        assertTrue(BackendPlan.parse(text, "size1:0.18.0").isEmpty())
    }

    @Test
    fun anUnreadableCacheIsTreatedAsEmptyNotAsAnError() {
        assertTrue(BackendPlan.parse(null, "k").isEmpty())
        assertTrue(BackendPlan.parse("", "k").isEmpty())
        assertTrue(BackendPlan.parse("garbage\nGPU=ok|nan|x", "k").isEmpty())
        // A damaged line is dropped; the good ones around it survive.
        val mixed = "key=k\nGPU=ok|abc|1|2\nCPU=ok|9.5|50.0|2.0\n"
        assertEquals(mapOf(CPU to ProbeResult.Ok(9.5, 50.0, 2.0)), BackendPlan.parse(mixed, "k"))
    }

    @Test
    fun aFailureReasonWithNewlinesCannotBreakTheFile() {
        val text = BackendPlan.serialize("k", mapOf(GPU to ProbeResult.Failed("line one\nline two")))
        assertEquals(mapOf(GPU to ProbeResult.Failed("line one line two")), BackendPlan.parse(text, "k"))
    }

    // ---- what the screen says

    @Test
    fun theStatusLineNamesTheBackendAndItsSpeed() {
        assertEquals("AI model: GPU · 21.4 tok/s", LlmStatusText.line(LlmStatus.Ready(GPU, 21.4, "")))
        assertEquals("AI model: CPU", LlmStatusText.line(LlmStatus.Ready(CPU, 0.0, "")))
        assertTrue(LlmStatusText.line(LlmStatus.Failed("no file")).contains("unavailable: no file"))
        assertTrue(LlmStatusText.line(LlmStatus.Loading("trying GPU")).contains("trying GPU"))
    }

    @Test
    fun theSummaryReadsBackWhatHappenedToEachBackend() {
        val s = BackendPlan.summary(mapOf(NPU to ProbeResult.Failed("no NPU model"), GPU to ok(21.44), CPU to ProbeResult.Crashed))
        assertEquals("NPU failed (no NPU model), GPU 21.4 tok/s, CPU crashed", s)
    }
}
