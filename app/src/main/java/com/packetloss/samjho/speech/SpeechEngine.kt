package com.packetloss.samjho.speech

import android.util.Log
import com.packetloss.samjho.model.Hypothesis
import com.packetloss.samjho.model.Language

enum class EngineId(val label: String) {
    ANDROID("Android"),
    VOSK("Vosk"),
}

/**
 * A microphone-to-transcript-lines source. Each pause in speech closes one utterance, and each
 * utterance becomes one transcript line, which is what every extracted item later cites.
 *
 * All [Listener] callbacks arrive on the main thread.
 */
interface SpeechEngine {

    val id: EngineId

    /** Null when this engine can transcribe [language] right now; otherwise a reason to show. */
    fun unavailableReason(language: Language): String?

    fun start(language: Language, listener: Listener)

    /**
     * Stops listening and returns any words still in flight, so the last sentence spoken before
     * stop is not lost.
     */
    fun stop(): String

    interface Listener {
        /** [detail] says how it is listening, e.g. "on-device", so the screen can show it honestly. */
        fun onListening(detail: String)
        fun onPartial(text: String)

        /** [text] is the top hypothesis; [hypotheses] is the recogniser's full N-best list, top first. */
        fun onLine(text: String, hypotheses: List<Hypothesis>)
        fun onError(message: String)
    }
}

/** One line per finished transcript line, so a logcat run shows which engine heard what. */
object SpeechLog {
    const val TAG = "SamjhoSpeech"

    fun started(engine: EngineId, language: Language, detail: String = "") {
        Log.i(TAG, "engine=${engine.name} lang=$language START $detail".trim())
    }

    fun line(engine: EngineId, language: Language, index: Int, text: String, mode: String = "") {
        val how = if (mode.isEmpty()) "" else " mode=$mode"
        Log.i(TAG, "engine=${engine.name}$how lang=$language line=${index + 1} text=\"$text\"")
    }

    /** Every N-best hypothesis for a line, so a run shows exactly what the recogniser considered. */
    fun hypotheses(engine: EngineId, language: Language, index: Int, hypotheses: List<Hypothesis>) {
        hypotheses.forEachIndexed { k, h ->
            val conf = h.confidence?.let { " conf=%.2f".format(it) }.orEmpty()
            Log.i(TAG, "engine=${engine.name} lang=$language line=${index + 1} hyp=${k + 1}/${hypotheses.size}$conf text=\"${h.text}\"")
        }
    }

    /** A recogniser callback or a restart, with no transcript text, so a run shows exactly what happened during a pause. */
    fun event(engine: EngineId, message: String) {
        Log.i(TAG, "engine=${engine.name} EVENT $message")
    }

    fun error(engine: EngineId, language: Language, message: String) {
        Log.w(TAG, "engine=${engine.name} lang=$language ERROR $message")
    }
}
