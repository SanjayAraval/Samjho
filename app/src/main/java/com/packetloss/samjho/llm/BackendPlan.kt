package com.packetloss.samjho.llm

/** Where the model computes. Tried in this order, and the fastest one that works is kept. */
enum class LlmBackend { NPU, GPU, CPU }

/** What happened when a backend was tried. Remembered on disk so a bad backend is never retried. */
sealed interface ProbeResult {
    /** It loaded and answered. Speeds are tokens per second measured on a fixed probe prompt. */
    data class Ok(val decodeTps: Double, val prefillTps: Double, val initSeconds: Double) : ProbeResult

    /** It refused to load or to answer, with the reason it gave. */
    data class Failed(val reason: String) : ProbeResult

    /** The process died while using it (a native crash or an out-of-memory kill). */
    data object Crashed : ProbeResult
}

/**
 * Choosing a backend and remembering the outcome. Pure, so it is tested without a phone.
 *
 * The cache is keyed on the model file and the library version: a different model or an upgraded
 * runtime invalidates every earlier result, because drivers and kernels change.
 */
object BackendPlan {

    val ORDER: List<LlmBackend> = listOf(LlmBackend.NPU, LlmBackend.GPU, LlmBackend.CPU)

    /** Backends not yet tried, in the order to try them. */
    fun pending(results: Map<LlmBackend, ProbeResult>): List<LlmBackend> = ORDER.filter { it !in results }

    /** The fastest backend that worked; on a tie, the earlier one in [ORDER]. Null when none worked. */
    fun best(results: Map<LlmBackend, ProbeResult>): LlmBackend? =
        ORDER.filter { results[it] is ProbeResult.Ok }
            .maxWithOrNull(compareBy<LlmBackend> { (results[it] as ProbeResult.Ok).decodeTps }.thenBy { -it.ordinal })

    /** Every backend that worked, fastest first; the order to fall back through if the best stops working. */
    fun rankedWorking(results: Map<LlmBackend, ProbeResult>): List<LlmBackend> =
        ORDER.filter { results[it] is ProbeResult.Ok }
            .sortedWith(compareBy<LlmBackend> { -(results[it] as ProbeResult.Ok).decodeTps }.thenBy { it.ordinal })

    /** One line per backend, for the log and the report: "GPU 21.4 tok/s, CPU 12.0 tok/s, NPU failed (...)". */
    fun summary(results: Map<LlmBackend, ProbeResult>): String =
        ORDER.filter { it in results }.joinToString(", ") { b ->
            when (val r = results.getValue(b)) {
                is ProbeResult.Ok -> "$b ${"%.1f".format(java.util.Locale.ROOT, r.decodeTps)} tok/s"
                is ProbeResult.Failed -> "$b failed (${r.reason})"
                ProbeResult.Crashed -> "$b crashed"
            }
        }

    // ---------------------------------------------------------------- cache format

    fun serialize(key: String, results: Map<LlmBackend, ProbeResult>): String = buildString {
        appendLine("key=$key")
        for (b in ORDER) {
            val r = results[b] ?: continue
            appendLine(
                when (r) {
                    is ProbeResult.Ok -> "$b=ok|${r.decodeTps}|${r.prefillTps}|${r.initSeconds}"
                    is ProbeResult.Failed -> "$b=failed|${r.reason.replace('\n', ' ').replace('\r', ' ')}"
                    ProbeResult.Crashed -> "$b=crashed"
                },
            )
        }
    }

    /** The remembered results, or an empty map when the text is unreadable or was made for another [key]. */
    fun parse(text: String?, key: String): Map<LlmBackend, ProbeResult> {
        if (text == null) return emptyMap()
        val lines = text.lines().map { it.trim() }.filter { it.isNotEmpty() }
        if (lines.firstOrNull() != "key=$key") return emptyMap()
        val out = LinkedHashMap<LlmBackend, ProbeResult>()
        for (line in lines.drop(1)) {
            val backend = LlmBackend.entries.firstOrNull { line.startsWith("$it=") } ?: continue
            val parts = line.substringAfter('=').split('|', limit = 4)
            val result: ProbeResult? = when (parts[0]) {
                "ok" -> {
                    val d = parts.getOrNull(1)?.toDoubleOrNull()
                    val p = parts.getOrNull(2)?.toDoubleOrNull()
                    val i = parts.getOrNull(3)?.toDoubleOrNull()
                    if (d != null && p != null && i != null) ProbeResult.Ok(d, p, i) else null
                }
                "failed" -> ProbeResult.Failed(parts.drop(1).joinToString("|"))
                "crashed" -> ProbeResult.Crashed
                else -> null
            }
            if (result != null) out[backend] = result
        }
        return out
    }
}

/** What the app shows about the model. */
sealed interface LlmStatus {
    /** Not asked to load yet. */
    data object Idle : LlmStatus

    data class Loading(val detail: String) : LlmStatus

    /** [decodeTps] is the latest measured speed, from the probe until a real request replaces it. */
    data class Ready(val backend: LlmBackend, val decodeTps: Double, val summary: String) : LlmStatus

    data class Failed(val reason: String) : LlmStatus
}

object LlmStatusText {
    /** "GPU · 21.4 tok/s", the short form shown next to the result and on the home screen. */
    fun backendLine(ready: LlmStatus.Ready): String =
        if (ready.decodeTps > 0) "${ready.backend} · ${"%.1f".format(java.util.Locale.ROOT, ready.decodeTps)} tok/s" else ready.backend.name

    fun line(status: LlmStatus): String = when (status) {
        LlmStatus.Idle -> "AI model: not started"
        is LlmStatus.Loading -> "AI model: ${status.detail}"
        is LlmStatus.Ready -> "AI model: ${backendLine(status)}"
        is LlmStatus.Failed -> "AI model unavailable: ${status.reason}"
    }
}
