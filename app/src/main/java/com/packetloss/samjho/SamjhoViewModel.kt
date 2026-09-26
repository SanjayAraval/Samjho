package com.packetloss.samjho

import android.app.Application
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import com.packetloss.samjho.demo.DemoConsultation
import com.packetloss.samjho.extract.RuleExtractor
import com.packetloss.samjho.model.Extraction
import com.packetloss.samjho.model.Language
import com.packetloss.samjho.speech.AndroidSpeechEngine
import com.packetloss.samjho.speech.EngineId
import com.packetloss.samjho.speech.SpeechEngine
import com.packetloss.samjho.speech.SpeechPrefs
import com.packetloss.samjho.speech.VoskEngine

data class RecordingState(
    val language: Language,
    val engine: EngineId,
    /** True until the recogniser is ready and the microphone is actually open. */
    val loading: Boolean = true,
    /** How the engine is listening, e.g. "on-device". Shown so the mode is never hidden. */
    val detail: String = "",
    val lines: List<String> = emptyList(),
    val partial: String = "",
    val error: String? = null,
)

data class UiState(
    val extraction: Extraction? = null,
    /** How long the deterministic pass took. Shown on screen: the budget is 1-2 seconds. */
    val ruleMillis: Long = 0,
    val sourceLabel: String = "",
    val recording: RecordingState? = null,
    val engine: EngineId = SpeechPrefs.DEFAULT,
    /** Why the selected engine cannot record a language right now; absent means it can. */
    val unavailable: Map<Language, String> = emptyMap(),
)

class SamjhoViewModel(app: Application) : AndroidViewModel(app) {

    private val engines: Map<EngineId, SpeechEngine> = mapOf(
        EngineId.ANDROID to AndroidSpeechEngine(app),
        EngineId.VOSK to VoskEngine(app),
    )

    var state by mutableStateOf(UiState())
        private set

    init {
        selectEngine(SpeechPrefs.engine(app), persist = false)
    }

    fun selectEngine(id: EngineId, persist: Boolean = true) {
        if (persist) SpeechPrefs.setEngine(getApplication(), id)
        val engine = engines.getValue(id)
        state = state.copy(
            engine = id,
            unavailable = Language.entries.mapNotNull { l -> engine.unavailableReason(l)?.let { l to it } }.toMap(),
        )
    }

    fun runDemo(demo: DemoConsultation) = showResult(demo.lines, demo.label)

    fun startRecording(language: Language) {
        val id = state.engine
        val engine = engines.getValue(id)
        val blocked = engine.unavailableReason(language)
        if (blocked != null) {
            state = state.copy(recording = RecordingState(language, id, loading = false, error = blocked))
            return
        }
        state = state.copy(recording = RecordingState(language, id))
        engine.start(language, object : SpeechEngine.Listener {
            override fun onListening(detail: String) =
                updateRecording { it.copy(loading = false, detail = detail) }

            override fun onPartial(text: String) = updateRecording { it.copy(partial = text) }

            override fun onLine(text: String) =
                updateRecording { it.copy(lines = it.lines + text, partial = "") }

            override fun onError(message: String) =
                updateRecording { it.copy(loading = false, error = message) }
        })
    }

    fun stopRecording() {
        val rec = state.recording ?: return
        val pending = engines.getValue(rec.engine).stop()
        val lines = if (pending.isNotEmpty()) rec.lines + pending else rec.lines
        if (lines.isEmpty()) {
            state = state.copy(recording = null)
        } else {
            val what = if (rec.language == Language.HINDI) "Hindi recording" else "English recording"
            val how = state.recording?.detail?.takeIf { it.isNotEmpty() }?.let { " ($it)" }.orEmpty()
            showResult(lines, "$what · ${rec.engine.label}$how")
        }
    }

    fun cancelRecording() {
        state.recording?.let { engines.getValue(it.engine).stop() }
        state = state.copy(recording = null)
    }

    fun back() {
        state = state.copy(extraction = null, recording = null, ruleMillis = 0, sourceLabel = "")
    }

    private fun showResult(lines: List<String>, label: String) {
        val start = System.nanoTime()
        val extraction = RuleExtractor.extract(lines)
        val millis = (System.nanoTime() - start) / 1_000_000
        state = state.copy(
            extraction = extraction,
            ruleMillis = millis,
            sourceLabel = label,
            recording = null,
        )
    }

    private fun updateRecording(change: (RecordingState) -> RecordingState) {
        val rec = state.recording ?: return
        state = state.copy(recording = change(rec))
    }

    override fun onCleared() {
        engines.values.forEach { it.stop() }
    }
}
