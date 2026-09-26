package com.packetloss.samjho

import android.app.Application
import android.graphics.Bitmap
import android.os.Handler
import android.os.Looper
import android.util.Log
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import com.packetloss.samjho.demo.DemoConsultation
import com.packetloss.samjho.extract.NameRepair
import com.packetloss.samjho.extract.RuleExtractor
import com.packetloss.samjho.llm.LlmEngine
import com.packetloss.samjho.llm.LlmEngines
import com.packetloss.samjho.llm.LlmStatus
import com.packetloss.samjho.llm.LlmStatusSource
import com.packetloss.samjho.model.Extraction
import com.packetloss.samjho.model.Hypothesis
import com.packetloss.samjho.model.Language
import com.packetloss.samjho.model.Medicine
import com.packetloss.samjho.model.Utterance
import com.packetloss.samjho.scan.ScanMatcher
import com.packetloss.samjho.scan.ScanStage
import com.packetloss.samjho.scan.ScanUi
import com.packetloss.samjho.scan.TextScanner
import java.util.concurrent.Executors
import com.packetloss.samjho.speech.AndroidSpeechEngine
import com.packetloss.samjho.speech.EngineId
import com.packetloss.samjho.speech.SpeechEngine
import com.packetloss.samjho.speech.SpeechPrefs
import com.packetloss.samjho.speech.VoskEngine
import com.packetloss.samjho.ui.Strings
import com.packetloss.samjho.voice.ReadAloudScript
import com.packetloss.samjho.voice.Speaker

/** Logcat tag for how each medicine was identified and what the patient decided about it. */
private const val NAMES = "SamjhoNames"

data class RecordingState(
    val language: Language,
    val engine: EngineId,
    /** True until the recogniser is ready and the microphone is actually open. */
    val loading: Boolean = true,
    /** How the engine is listening, e.g. "on-device". Shown so the mode is never hidden. */
    val detail: String = "",
    val lines: List<Utterance> = emptyList(),
    val partial: String = "",
    val error: String? = null,
)

/** Where the language-model pass over the result stands. The rules result is on screen before it starts. */
sealed interface AiState {
    data object Idle : AiState
    data object Checking : AiState
    data class Done(val added: Int) : AiState
    data class Unavailable(val reason: String) : AiState
}

data class UiState(
    val ai: AiState = AiState.Idle,
    /** Whether the on-device model is loading or ready, and on which backend at what speed. */
    val llm: LlmStatus = LlmStatus.Idle,
    /** The prescription scan screen, or null when it is closed. */
    val scan: ScanUi? = null,
    val reading: Speaker.State = Speaker.State.Idle,
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

    private val speaker = Speaker(app)
    private val scanner = TextScanner()
    private val llm: LlmEngine = LlmEngines.create(app)
    private val aiWorker = Executors.newSingleThreadExecutor()
    private val main = Handler(Looper.getMainLooper())

    /** Bumped per result, so a slow model answer for a screen the patient already left is ignored. */
    private var resultId = 0

    init {
        selectEngine(SpeechPrefs.engine(app), persist = false)
        // Load the model in the background now, so it is usually ready before the first result appears.
        (llm as? LlmStatusSource)?.let { source ->
            source.addListener { status -> state = state.copy(llm = status) }
            source.start()
        }
    }

    fun selectEngine(id: EngineId, persist: Boolean = true) {
        if (persist) SpeechPrefs.setEngine(getApplication(), id)
        val engine = engines.getValue(id)
        state = state.copy(
            engine = id,
            unavailable = Language.entries.mapNotNull { l -> engine.unavailableReason(l)?.let { l to it } }.toMap(),
        )
    }

    fun runDemo(demo: DemoConsultation) = showResult(demo.lines.map { Utterance(it) }, demo.label)

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

            override fun onLine(text: String, hypotheses: List<Hypothesis>) =
                updateRecording { it.copy(lines = it.lines + Utterance(text, hypotheses), partial = "") }

            override fun onError(message: String) =
                updateRecording { it.copy(loading = false, error = message) }
        })
    }

    fun stopRecording() {
        val rec = state.recording ?: return
        val pending = engines.getValue(rec.engine).stop()
        val lines = if (pending.isNotEmpty()) rec.lines + Utterance(pending) else rec.lines
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
        speaker.stop()
        state = state.copy(extraction = null, recording = null, ruleMillis = 0, sourceLabel = "", reading = Speaker.State.Idle)
    }

    /** Reads the result on screen aloud, exactly as shown, using only an offline voice. */
    fun readAloud() {
        val e = state.extraction ?: return
        val sentences = ReadAloudScript.build(e, Strings(e.language))
        Log.i(NAMES, "read-aloud: ${sentences.size} sentences, language=${e.language}")
        speaker.speak(e.language, sentences) { s -> main.post { state = state.copy(reading = s) } }
    }

    fun stopReading() = speaker.stop()

    fun confirmMedicine(index: Int) = decide(index, "confirm") { it.confirmed() }

    fun rejectMedicine(index: Int) = decide(index, "reject") { it.rejected() }

    fun undoMedicine(index: Int) = decide(index, "undo") { it.reopened() }

    fun chooseMedicine(index: Int, key: String) = decide(index, "choose=$key") { it.choosing(key) }

    /** Every answer here is the patient's own; nothing else ever moves a medicine to CONFIRMED. */
    private fun decide(index: Int, what: String, change: (Medicine) -> Medicine) {
        val current = state.extraction ?: return
        val m = current.medicines.getOrNull(index) ?: return
        Log.i(NAMES, "decision=$what heard=\"${m.name}\" key=${m.key} basis=${m.basis}")
        state = state.copy(extraction = current.updateMedicine(index, change))
    }

    private fun showResult(lines: List<Utterance>, label: String) {
        val start = System.nanoTime()
        val extraction = RuleExtractor.extractUtterances(lines)
        val millis = (System.nanoTime() - start) / 1_000_000
        val mine = ++resultId
        state = state.copy(
            extraction = extraction,
            ruleMillis = millis,
            sourceLabel = label,
            recording = null,
            ai = AiState.Idle,
        )
        logMedicines("rules", extraction)
        repairNames(mine, extraction)
    }

    /**
     * Layer 2. Runs after the rules result is already on screen, off the main thread, and can only
     * append items to it. If the model is missing, slow, crashes or says nonsense, the result the
     * patient is already looking at simply stays as it is.
     */
    private fun repairNames(mine: Int, extraction: Extraction) {
        val reason = llm.unavailableReason()
        if (reason != null) {
            state = state.copy(ai = AiState.Unavailable(reason))
            return
        }
        if (NameRepair.targets(extraction).isEmpty()) {
            state = state.copy(ai = AiState.Done(0))
            return
        }
        state = state.copy(ai = AiState.Checking)
        aiWorker.execute {
            val added = NameRepair.repair(extraction, llm)
            main.post {
                if (mine != resultId) return@post
                val current = state.extraction ?: return@post
                val merged = current.withAdded(added)
                logMedicines("after-ai", merged)
                state = state.copy(extraction = merged, ai = AiState.Done(merged.medicines.size - current.medicines.size))
            }
        }
    }

    private fun logMedicines(stage: String, e: Extraction) {
        if (e.medicines.isEmpty()) Log.i(NAMES, "$stage: no medicines")
        e.medicines.forEachIndexed { i, m ->
            Log.i(
                NAMES,
                "$stage: #$i heard=\"${m.name}\" key=${m.key} basis=${m.basis} " +
                    "hyp=${m.hypothesis?.let { it + 1 } ?: "-"} provenance=${m.provenance} " +
                    "confirmation=${m.confirmation} lines=${m.sourceLines.map { it + 1 }}",
            )
        }
    }

    private fun updateRecording(change: (RecordingState) -> RecordingState) {
        val rec = state.recording ?: return
        state = state.copy(recording = change(rec))
    }

    // ---------------------------------------------------------------- prescription scan

    fun openScan() {
        Log.i(TextScanner.TAG, "scan opened")
        state = state.copy(scan = ScanUi())
    }

    fun closeScan() {
        Log.i(TextScanner.TAG, "scan closed")
        state = state.copy(scan = null)
    }

    fun scanAgain() = updateScan { it.copy(stage = ScanStage.Camera) }

    /** Reads a photo on the phone and turns what it read into suggestions. The bitmap is never kept or saved. */
    fun scanPhoto(bitmap: Bitmap) {
        val start = System.nanoTime()
        updateScan { it.reading() }
        scanner.read(bitmap) { result ->
            bitmap.recycle()
            result.fold(
                onSuccess = { read ->
                    val items = ScanMatcher.match(read.allLines())
                    val millis = (System.nanoTime() - start) / 1_000_000
                    // Counts and medicine keys only: what a prescription says is the patient's, not ours to log.
                    Log.i(
                        TextScanner.TAG,
                        "scan: ${read.latin.size} latin lines, ${read.devanagari.size} devanagari lines, " +
                            "${items.size} suggestions ${items.map { "${it.key}/${it.basis}" }} in $millis ms",
                    )
                    updateScan { it.withRead(read, items, millis) }
                },
                onFailure = { e ->
                    Log.w(TextScanner.TAG, "scan failed: ${e.message}")
                    updateScan { it.failed(e.message ?: "The text reader failed.") }
                },
            )
        }
    }

    fun scanFailed(message: String) {
        Log.w(TextScanner.TAG, "camera failed: $message")
        updateScan { it.failed(message) }
    }

    fun scanConfirm(id: Int) = decideScan(id, "confirm") { it.confirm(id) }

    fun scanReject(id: Int) = decideScan(id, "reject") { it.reject(id) }

    fun scanUndo(id: Int) = decideScan(id, "undo") { it.undo(id) }

    fun scanChoose(id: Int, key: String) = decideScan(id, "choose=$key") { it.choose(id, key) }

    /** Picked from the full list: the manual path, which needs neither the camera nor any text. */
    fun scanPick(key: String) {
        Log.i(TextScanner.TAG, "decision=pick key=$key")
        updateScan { it.pick(key) }
    }

    /** Every answer here is the patient's own; nothing else ever confirms a scanned medicine. */
    private fun decideScan(id: Int, what: String, change: (ScanUi) -> ScanUi) {
        val item = state.scan?.items?.firstOrNull { it.id == id } ?: return
        Log.i(TextScanner.TAG, "decision=$what key=${item.key} basis=${item.basis}")
        updateScan(change)
    }

    private fun updateScan(change: (ScanUi) -> ScanUi) {
        val current = state.scan ?: return
        state = state.copy(scan = change(current))
    }

    override fun onCleared() {
        scanner.close()
        speaker.shutdown()
        engines.values.forEach { it.stop() }
    }
}
