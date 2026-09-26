package com.packetloss.samjho.llm

import android.content.Context
import android.util.Log
import com.google.ai.edge.litertlm.Backend
import com.google.ai.edge.litertlm.Content
import com.google.ai.edge.litertlm.ConversationConfig
import com.google.ai.edge.litertlm.Engine
import com.google.ai.edge.litertlm.EngineConfig
import com.google.ai.edge.litertlm.ExperimentalApi
import com.google.ai.edge.litertlm.ExperimentalFlags
import com.google.ai.edge.litertlm.Message
import com.google.ai.edge.litertlm.SamplerConfig
import java.io.File

/**
 * Runs Gemma through LiteRT-LM. Only ever used inside the :llm process (see [LlmService]).
 *
 * On the first run it tries NPU, then GPU, then CPU, times each on the same short prompt and keeps the
 * fastest one that works. The outcome is written to disk, so later runs load only the winner. A file
 * marks "this backend is being started or used": if the process dies with the marker present, that
 * backend is recorded as crashed and never tried again, so a bad driver costs one restart, not a loop.
 */
@OptIn(ExperimentalApi::class)
class LiteRtRunner(private val context: Context) {

    class Reply(val text: String, val decodeTps: Double, val prefillTps: Double, val millis: Long)

    private val dir = File(context.filesDir, "llm").apply { mkdirs() }
    private val cacheFile = File(dir, "backend-probe.txt")
    private val marker = File(dir, "in-use.txt")
    private val strikes = File(dir, "strikes.txt")

    private var engine: Engine? = null
    private var results = LinkedHashMap<LlmBackend, ProbeResult>()

    @Volatile
    var active: LlmBackend? = null
        private set

    @Volatile
    var summary: String = ""
        private set

    /** Blocking, and slow the first time. Reports progress, then returns with [active] set or an error. */
    fun load(modelPath: String, report: (LlmStatus) -> Unit) {
        try {
            val model = File(modelPath)
            if (!model.isFile) {
                report(LlmStatus.Failed("model file not found: $modelPath"))
                return
            }
            ExperimentalFlags.enableBenchmark = true
            val key = "${model.length()}:${model.lastModified()}:litertlm-$RUNTIME_VERSION"
            results = LinkedHashMap(BackendPlan.parse(cacheFile.takeIf { it.isFile }?.readText(), key))
            recoverFromCrash()

            var held: Pair<LlmBackend, Engine>? = null
            for (backend in BackendPlan.pending(results)) {
                report(LlmStatus.Loading("trying $backend (first run only)…"))
                val (result, opened) = probe(backend, model)
                results[backend] = result
                if (result is ProbeResult.Ok) clearStrikes(backend)
                save(key)
                Log.i(TAG, "probe $backend -> $result")
                val heldTps = held?.let { (results[it.first] as? ProbeResult.Ok)?.decodeTps }
                if (opened != null && result is ProbeResult.Ok && (heldTps == null || result.decodeTps > heldTps)) {
                    held?.second?.closeQuietly()
                    held = backend to opened
                } else {
                    opened?.closeQuietly()
                }
            }
            summary = BackendPlan.summary(results)

            // Walk the working backends fastest first; the first that opens is the one used.
            var chosen: Pair<LlmBackend, Engine>? = null
            for (backend in BackendPlan.rankedWorking(results)) {
                if (held?.first == backend) { chosen = held; held = null; break }
                report(LlmStatus.Loading("starting $backend…"))
                val started = openEngine(backend, model)
                if (started.second != null) { chosen = backend to started.second!!; break }
                results[backend] = ProbeResult.Failed(started.first ?: "could not start")
                save(key)
                summary = BackendPlan.summary(results)
            }
            held?.second?.closeQuietly()

            if (chosen == null) {
                report(LlmStatus.Failed("no backend could run the model (${BackendPlan.summary(results)})"))
                return
            }
            engine = chosen.second
            active = chosen.first
            clearStrikes(chosen.first)
            val tps = (results[chosen.first] as? ProbeResult.Ok)?.decodeTps ?: 0.0
            Log.i(TAG, "ready on ${chosen.first} ${"%.1f".format(tps)} tok/s; tried: $summary")
            report(LlmStatus.Ready(chosen.first, tps, summary))
        } catch (t: Throwable) {
            Log.w(TAG, "load failed: ${t.message}", t)
            report(LlmStatus.Failed(t.message ?: t.javaClass.simpleName))
        }
    }

    /** One prompt, one answer, in a fresh conversation so nothing carries over between lines. */
    @Synchronized
    fun complete(prompt: String, maxTokens: Int): Reply {
        val e = engine ?: error("the model is not loaded")
        val backend = active ?: error("the model is not loaded")
        marker.writeText(backend.name)
        val start = System.nanoTime()
        val out = e.createConversation(conversationConfig(maxTokens)).use { conversation ->
            val message = conversation.sendMessage(prompt)
            val info = runCatching { conversation.getBenchmarkInfo() }.getOrNull()
            Triple(textOf(message), info?.lastDecodeTokensPerSecond ?: 0.0, info?.lastPrefillTokensPerSecond ?: 0.0)
        }
        marker.delete()
        val millis = (System.nanoTime() - start) / 1_000_000
        Log.i(TAG, "complete on $backend in $millis ms, decode ${"%.1f".format(out.second)} tok/s")
        return Reply(out.first, out.second, out.third, millis)
    }

    fun close() {
        engine?.closeQuietly()
        engine = null
        active = null
    }

    // ---------------------------------------------------------------- probing

    private fun probe(backend: LlmBackend, model: File): Pair<ProbeResult, Engine?> {
        // Without the vendor's dispatch library the runtime quietly falls back to a CPU delegate while still
        // being asked for the NPU. That would be reported as "NPU" and be a lie, so the NPU is only tried
        // when the library it needs is actually packaged with the app.
        if (backend == LlmBackend.NPU && !hasNpuDispatchLibrary()) {
            return ProbeResult.Failed("no NPU runtime is packaged with the app (and the model is not NPU-compiled)") to null
        }
        val started = openEngine(backend, model)
        val e = started.second ?: return ProbeResult.Failed(started.first ?: "could not start") to null
        val initSeconds = started.third
        marker.writeText(backend.name)
        return try {
            val result: ProbeResult = e.createConversation(conversationConfig(PROBE_TOKENS)).use { conversation ->
                val text = textOf(conversation.sendMessage(PROBE_PROMPT))
                val info = conversation.getBenchmarkInfo()
                val tps = info.lastDecodeTokensPerSecond
                when {
                    text.isBlank() -> ProbeResult.Failed("it produced no text")
                    tps <= 0.0 -> ProbeResult.Failed("it reported no speed")
                    else -> ProbeResult.Ok(tps, info.lastPrefillTokensPerSecond, initSeconds)
                }
            }
            marker.delete()
            if (result is ProbeResult.Ok) {
                result to e
            } else {
                e.closeQuietly()
                result to null
            }
        } catch (t: Throwable) {
            marker.delete()
            e.closeQuietly()
            ProbeResult.Failed(reasonOf(t)) to null
        }
    }

    /** (error, engine, seconds). Exactly one of error and engine is set. */
    private fun openEngine(backend: LlmBackend, model: File): Triple<String?, Engine?, Double> {
        marker.writeText(backend.name)
        val t0 = System.nanoTime()
        return try {
            val kernels = File(dir, "kernels-${backend.name}").apply { mkdirs() }
            val e = Engine(
                EngineConfig(
                    modelPath = model.absolutePath,
                    backend = backendOf(backend),
                    visionBackend = null,
                    audioBackend = null,
                    maxNumTokens = null,
                    maxNumImages = null,
                    cacheDir = kernels.absolutePath,
                ),
            )
            e.initialize()
            marker.delete()
            Triple(null, e, (System.nanoTime() - t0) / 1e9)
        } catch (t: Throwable) {
            marker.delete()
            Log.w(TAG, "$backend failed to start: ${t.message}")
            Triple(reasonOf(t), null, 0.0)
        }
    }

    private fun hasNpuDispatchLibrary(): Boolean =
        File(context.applicationInfo.nativeLibraryDir).list().orEmpty().any { it.startsWith("libLiteRtDispatch") }

    private fun backendOf(b: LlmBackend): Backend = when (b) {
        LlmBackend.NPU -> Backend.NPU(context.applicationInfo.nativeLibraryDir)
        LlmBackend.GPU -> Backend.GPU()
        LlmBackend.CPU -> Backend.CPU()
    }

    private fun conversationConfig(maxTokens: Int) = ConversationConfig(
        samplerConfig = SamplerConfig(topK = 1, topP = 1.0, temperature = 0.1, seed = 0),
        maxOutputToken = maxTokens,
    )

    private fun textOf(message: Message): String =
        message.contents.contents.filterIsInstance<Content.Text>().joinToString("") { it.text }.trim()

    private fun reasonOf(t: Throwable): String =
        (t.message ?: t.javaClass.simpleName).lineSequence().firstOrNull { it.isNotBlank() }?.take(160) ?: t.javaClass.simpleName

    // ---------------------------------------------------------------- crash memory

    /**
     * A marker left behind means the previous process ended while using that backend. That could be a real
     * native crash, but it could equally be the user swiping the app away or the phone freezing it, and
     * writing off the only working backend for that would switch the AI off for good. So a backend is
     * only recorded as crashed on its second strike, and a strike is cleared by any success.
     */
    private fun recoverFromCrash() {
        if (!marker.isFile) return
        val suspect = LlmBackend.entries.firstOrNull { it.name == marker.readText().trim() }
        marker.delete()
        if (suspect == null) return
        val previous = strikes.takeIf { it.isFile }?.readLines().orEmpty().count { it == suspect.name }
        if (previous + 1 >= MAX_STRIKES) {
            Log.w(TAG, "the model process ended twice while using $suspect; it will not be used again")
            results[suspect] = ProbeResult.Crashed
        } else {
            Log.w(TAG, "the model process ended while using $suspect (strike ${previous + 1} of $MAX_STRIKES); it will be tried again")
            strikes.appendText(suspect.name + System.lineSeparator())
        }
    }

    private fun clearStrikes(backend: LlmBackend) {
        if (strikes.isFile) strikes.writeText(strikes.readLines().filter { it != backend.name }.joinToString("") { it + System.lineSeparator() })
    }

    private fun save(key: String) {
        cacheFile.writeText(BackendPlan.serialize(key, results))
    }

    private fun Engine.closeQuietly() {
        try { close() } catch (_: Throwable) { }
    }

    companion object {
        const val TAG = "SamjhoLlm"
        const val RUNTIME_VERSION = "0.17.1"
        private const val MAX_STRIKES = 2
        private const val PROBE_TOKENS = 48
        private const val PROBE_PROMPT = "Count from one to twenty in words, separated by commas."
    }
}
