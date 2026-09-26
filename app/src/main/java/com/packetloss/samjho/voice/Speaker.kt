package com.packetloss.samjho.voice

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.speech.tts.Voice
import android.util.Log
import com.packetloss.samjho.model.Language

/**
 * Read-aloud with the phone's own text-to-speech engine, restricted to voices that are installed on
 * the phone. A voice that needs the network is never used, so reading a result aloud cannot send
 * the patient's words anywhere; if only online voices exist, it says so instead of speaking.
 */
class Speaker(context: Context) {

    sealed interface State {
        data object Idle : State
        data object Speaking : State
        data class Unavailable(val reason: String) : State
    }

    private val appContext = context.applicationContext
    private val main = Handler(Looper.getMainLooper())
    private var tts: TextToSpeech? = null
    private var ready = false
    private var pending: Job? = null
    private var onState: (State) -> Unit = {}
    private var generation = 0

    private class Job(val language: Language, val sentences: List<String>)

    fun speak(language: Language, sentences: List<String>, onState: (State) -> Unit) {
        this.onState = onState
        pending = Job(language, sentences)
        val engine = tts
        if (engine == null) {
            tts = TextToSpeech(appContext) { status ->
                main.post {
                    if (status == TextToSpeech.SUCCESS) {
                        ready = true
                        tts?.setOnUtteranceProgressListener(progress())
                        startPending()
                    } else {
                        fail("The phone's text-to-speech engine did not start (status $status).")
                    }
                }
            }
        } else if (ready) {
            startPending()
        }
    }

    fun stop() {
        generation++
        pending = null
        tts?.stop()
        onState(State.Idle)
    }

    fun shutdown() {
        generation++
        tts?.stop()
        tts?.shutdown()
        tts = null
        ready = false
    }

    private fun startPending() {
        val job = pending ?: return
        val engine = tts ?: return
        val voice = pickVoice(engine, job.language)
        if (voice == null) {
            fail(noVoice(job.language))
            return
        }
        engine.voice = voice
        Log.i(TAG, "voice=${voice.name} locale=${voice.locale} networkRequired=${voice.isNetworkConnectionRequired} sentences=${job.sentences.size}")
        generation++
        val mine = generation
        onState(State.Speaking)
        job.sentences.forEachIndexed { i, text ->
            val id = "samjho-$mine-$i-${job.sentences.size}"
            engine.speak(text, if (i == 0) TextToSpeech.QUEUE_FLUSH else TextToSpeech.QUEUE_ADD, null, id)
        }
    }

    /** The best voice that is installed and does not need the network, or null. */
    private fun pickVoice(engine: TextToSpeech, language: Language): Voice? {
        val lang = if (language == Language.HINDI) "hi" else "en"
        val preferred = if (language == Language.HINDI) listOf("IN") else listOf("IN", "US", "GB")
        val usable = try {
            engine.voices.orEmpty().filter {
                it.locale.language == lang &&
                    !it.isNetworkConnectionRequired &&
                    !it.features.contains(TextToSpeech.Engine.KEY_FEATURE_NOT_INSTALLED)
            }
        } catch (t: Throwable) {
            Log.w(TAG, "could not list voices: ${t.message}")
            emptyList()
        }
        Log.i(TAG, "offline $lang voices: ${usable.map { it.name }}")
        return usable.sortedWith(
            compareBy<Voice> { preferred.indexOf(it.locale.country).let { i -> if (i < 0) 99 else i } }
                .thenByDescending { it.quality },
        ).firstOrNull()
    }

    private fun noVoice(language: Language): String {
        val name = if (language == Language.HINDI) "Hindi" else "English"
        return "No offline $name voice is installed, and Samjho will not use an online one. " +
            "Install one in Settings > Text-to-speech."
    }

    private fun fail(reason: String) {
        Log.w(TAG, "unavailable: $reason")
        onState(State.Unavailable(reason))
    }

    private fun progress() = object : UtteranceProgressListener() {
        override fun onStart(utteranceId: String) {
            Log.i(TAG, "start $utteranceId")
        }

        override fun onDone(utteranceId: String) {
            Log.i(TAG, "done $utteranceId")
            val parts = utteranceId.split("-")
            val index = parts.getOrNull(2)?.toIntOrNull() ?: return
            val count = parts.getOrNull(3)?.toIntOrNull() ?: return
            val mine = parts.getOrNull(1)?.toIntOrNull() ?: return
            if (index == count - 1) main.post { if (mine == generation) onState(State.Idle) }
        }

        @Deprecated("Required by the interface")
        override fun onError(utteranceId: String) {
            Log.w(TAG, "error $utteranceId")
            main.post { onState(State.Idle) }
        }
    }

    private companion object {
        const val TAG = "SamjhoVoice"
    }
}
