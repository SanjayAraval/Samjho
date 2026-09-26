package com.packetloss.samjho.llm

import kotlin.math.max
import kotlin.system.measureTimeMillis

/** Device-independent benchmark calculations; execution is performed by the LLM service later. */
data class BenchmarkResult(
    val backend: String,
    val output: String,
    val totalTimeMs: Long,
    val tokenCount: Int,
) {
    val tokensPerSecond: Double
        get() = if (totalTimeMs <= 0L) 0.0 else tokenCount * 1000.0 / totalTimeMs
}

object LlmBenchmark {
    const val FIXED_PROMPT =
        "In one short sentence, explain what Samjho does for a patient after a doctor consultation."

    fun countApproximateTokens(text: String): Int =
        text.trim().split(Regex("\\s+")).count { it.isNotEmpty() }

    suspend fun run(repository: LlmRepository): BenchmarkResult {
        var output = ""
        val elapsed = measureTimeMillis {
            output = repository.ask(FIXED_PROMPT)
        }
        val backend = (repository.status.value as? LlmStatus.Ready)?.backend ?: "Unavailable"
        return BenchmarkResult(
            backend = backend,
            output = output,
            totalTimeMs = max(1L, elapsed),
            tokenCount = countApproximateTokens(output),
        )
    }
}
