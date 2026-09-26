package com.packetloss.samjho.llm

import android.app.ActivityManager
import android.content.Context
import android.util.Log
import com.google.ai.edge.litertlm.Conversation
import com.google.ai.edge.litertlm.Engine
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.concurrent.atomic.AtomicBoolean

/** Device-local LLM engine. The implementation lives in the isolated :llm process. */
class LlmEngine(
    context: Context,
    private val modelPath: String = DEFAULT_MODEL_PATH,
) {
    companion object {
        const val DEFAULT_MODEL_PATH =
            "/data/local/tmp/llm/Gemma3-1B-IT_multi-prefill-seq_q4_ekv2048.task"
        private const val TAG = "SamjhoLlm"
    }

    private val appContext = context.applicationContext
    private val _status = MutableStateFlow<LlmStatus>(LlmStatus.Loading)
    val status: StateFlow<LlmStatus> = _status.asStateFlow()

    private var engine: Engine? = null
    private var conversation: Conversation? = null
    private val closed = AtomicBoolean(false)

    suspend fun initialize(): LlmStatus = withContext(Dispatchers.IO) {
        if (closed.get()) return@withContext LlmStatus.Unavailable("Engine is closed")

        val am = appContext.getSystemService(ActivityManager::class.java)
        val info = ActivityManager.MemoryInfo().also { am.getMemoryInfo(it) }
        val memoryGb = info.totalMem / (1024L * 1024L * 1024L)
        Log.i(TAG, "Available device RAM: ${memoryGb} GB")

        val resolvedModelPath = ModelPathResolver(appContext, modelPath).resolve()
            ?: run {
                val status = LlmStatus.Unavailable(
                    "Model file is not readable from $modelPath and could not be copied to app storage"
                )
                _status.value = status
                return@withContext status
            }

        Log.i(TAG, "Using model path: $resolvedModelPath")
        val selector = BackendSelector(appContext, resolvedModelPath)
        selector.validateModelFile()?.let {
            val status = LlmStatus.Unavailable(it)
            _status.value = status
            return@withContext status
        }

        try {
            val result = selector.initialize()
            engine = result.engine
            conversation = result.engine.createConversation()
            val status = LlmStatus.Ready(result.backend)
            _status.value = status
            status
        } catch (t: Throwable) {
            Log.e(TAG, "LLM initialization failed", t)
            val status = LlmStatus.Unavailable(t.message ?: "LiteRT-LM initialization failed")
            _status.value = status
            status
        }
    }

    suspend fun ask(prompt: String): String = withContext(Dispatchers.IO) {
        check(!closed.get()) { "LLM engine is closed" }
        check(_status.value is LlmStatus.Ready) { "LLM unavailable: ${_status.value}" }
        require(prompt.isNotBlank()) { "Prompt must not be blank" }

        // LiteRT-LM's synchronous API is called off the main thread.
        val activeConversation = conversation
            ?: error("LLM conversation is not initialized")
        activeConversation.sendMessage(prompt).toString()
    }

    fun close() {
        if (!closed.compareAndSet(false, true)) return
        runCatching { conversation?.close() }
        runCatching { engine?.close() }
        conversation = null
        engine = null
        _status.value = LlmStatus.Unavailable("Engine closed")
    }
}
