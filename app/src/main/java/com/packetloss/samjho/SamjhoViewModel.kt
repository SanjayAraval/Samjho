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
import com.packetloss.samjho.speech.ModelInstaller
import com.packetloss.samjho.speech.Transcriber

data class RecordingState(
    val language: Language,
    /** True until the model is loaded and the microphone is actually open. */
    val loading: Boolean = true,
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
    val bundledModels: Set<Language> = emptySet(),
)

class SamjhoViewModel(app: Application) : AndroidViewModel(app) {

    var state by mutableStateOf(UiState(bundledModels = detectBundledModels()))
        private set

    private val transcriber = Transcriber(app)

    private fun detectBundledModels(): Set<Language> = buildSet {
        val ctx = getApplication<Application>()
        if (ModelInstaller.isBundled(ctx, assetDir(Language.HINDI))) add(Language.HINDI)
        if (ModelInstaller.isBundled(ctx, assetDir(Language.ENGLISH))) add(Language.ENGLISH)
    }

    fun runDemo(demo: DemoConsultation) = showResult(demo.lines, demo.label)

    fun startRecording(language: Language) {
        state = state.copy(recording = RecordingState(language))
        transcriber.start(assetDir(language), object : Transcriber.Listener {
            override fun onListening() = updateRecording { it.copy(loading = false) }

            override fun onPartial(text: String) = updateRecording { it.copy(partial = text) }

            override fun onLine(text: String) =
                updateRecording { it.copy(lines = it.lines + text, partial = "") }

            override fun onError(message: String) =
                updateRecording { it.copy(loading = false, error = message) }
        })
    }

    fun stopRecording() {
        val rec = state.recording ?: return
        val pending = transcriber.stop()
        val lines = if (pending.isNotEmpty()) rec.lines + pending else rec.lines
        if (lines.isEmpty()) {
            state = state.copy(recording = null)
        } else {
            showResult(lines, if (rec.language == Language.HINDI) "हिंदी रिकॉर्डिंग" else "English recording")
        }
    }

    fun cancelRecording() {
        transcriber.stop()
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
        transcriber.stop()
    }

    companion object {
        fun assetDir(language: Language) =
            if (language == Language.HINDI) "model-hi" else "model-en-in"
    }
}
