package com.packetloss.samjho.speech

import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.speech.RecognitionListener
import android.speech.RecognitionSupport
import android.speech.RecognitionSupportCallback
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import com.packetloss.samjho.extract.Lexicon
import com.packetloss.samjho.model.Language

/**
 * The phone's own on-device speech recogniser, biased toward the medicine names in [Lexicon].
 *
 * It is created with [SpeechRecognizer.createOnDeviceSpeechRecognizer], which by contract never
 * uses the network. EXTRA_PREFER_OFFLINE is also set, but on its own that is only a hint: the
 * ordinary recogniser may fall back to a server when an offline pack is missing, and this app's
 * lack of an INTERNET permission would not stop that, because the audio would leave from another
 * app's process. So if an on-device recogniser is not available, this engine reports itself
 * unavailable instead of quietly going online.
 *
 * A recogniser ends after each utterance, so it is restarted on every result to capture a whole
 * consultation as many lines. Must be driven from the main thread.
 */
class AndroidSpeechEngine(context: Context) : SpeechEngine {

    override val id = EngineId.ANDROID

    private val appContext = context.applicationContext
    private val main = Handler(Looper.getMainLooper())

    private var recognizer: SpeechRecognizer? = null
    private var listener: SpeechEngine.Listener? = null
    private var language = Language.ENGLISH
    private var intent: Intent? = null

    private var session = 0
    private var announced = false
    private var lastPartial = ""
    private var lineCount = 0
    private var errorStreak = 0

    override fun unavailableReason(language: Language): String? = when {
        Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ->
            "On-device recognition needs Android 13 or newer. Pick Vosk in the speech setting."
        !SpeechRecognizer.isOnDeviceRecognitionAvailable(appContext) ->
            "This phone has no on-device speech recogniser. Pick Vosk in the speech setting."
        else -> null
    }

    override fun start(language: Language, listener: SpeechEngine.Listener) {
        stopInternal()
        val mine = ++session
        this.language = language
        this.listener = listener
        announced = false
        lastPartial = ""
        lineCount = 0
        errorStreak = 0

        val tag = languageTag(language)
        val recognitionIntent = buildIntent(tag)
        intent = recognitionIntent
        SpeechLog.started(id, language, "tag=$tag preferOffline=true onDevice=true hints=${Lexicon.speechHints().size}")

        val r = SpeechRecognizer.createOnDeviceSpeechRecognizer(appContext)
        recognizer = r
        r.setRecognitionListener(callbacks(mine))
        logInstalledLanguages(r, recognitionIntent)
        r.startListening(recognitionIntent)
    }

    override fun stop(): String {
        val pending = lastPartial.trim()
        stopInternal()
        return pending
    }

    private fun stopInternal() {
        session++
        lastPartial = ""
        main.removeCallbacksAndMessages(null)
        recognizer?.let {
            it.cancel()
            it.destroy()
        }
        recognizer = null
        listener = null
    }

    private fun buildIntent(tag: String) = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
        putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
        putExtra(RecognizerIntent.EXTRA_LANGUAGE, tag)
        putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
        putExtra(RecognizerIntent.EXTRA_PREFER_OFFLINE, true)
        putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1)
        putExtra(RecognizerIntent.EXTRA_CALLING_PACKAGE, appContext.packageName)
        putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_COMPLETE_SILENCE_LENGTH_MILLIS, PAUSE_MS)
        putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_POSSIBLY_COMPLETE_SILENCE_LENGTH_MILLIS, PAUSE_MS)
        putStringArrayListExtra(RecognizerIntent.EXTRA_BIASING_STRINGS, ArrayList(Lexicon.speechHints()))
    }

    /** Diagnostic only: which offline language packs the phone actually has. */
    private fun logInstalledLanguages(r: SpeechRecognizer, forIntent: Intent) {
        try {
            r.checkRecognitionSupport(forIntent, appContext.mainExecutor, object : RecognitionSupportCallback {
                override fun onSupportResult(support: RecognitionSupport) {
                    SpeechLog.started(
                        id, language,
                        "installed=${support.installedOnDeviceLanguages} " +
                            "downloadable=${support.supportedOnDeviceLanguages} " +
                            "pending=${support.pendingOnDeviceLanguages}",
                    )
                }

                override fun onError(error: Int) {
                    SpeechLog.error(id, language, "support check error=$error")
                }
            })
        } catch (t: Throwable) {
            SpeechLog.error(id, language, "support check failed: ${t.message}")
        }
    }

    private fun callbacks(mine: Int) = object : RecognitionListener {
        override fun onReadyForSpeech(params: Bundle?) {
            if (mine != session || announced) return
            announced = true
            listener?.onListening()
        }

        override fun onPartialResults(partialResults: Bundle?) {
            if (mine != session) return
            val text = firstResult(partialResults) ?: return
            lastPartial = text
            listener?.onPartial(text)
        }

        override fun onResults(results: Bundle?) {
            if (mine != session) return
            errorStreak = 0
            lastPartial = ""
            val text = firstResult(results)?.trim().orEmpty()
            if (text.isNotEmpty()) {
                SpeechLog.line(id, language, lineCount++, text)
                listener?.onLine(text)
            }
            restart(mine, 60)
        }

        override fun onError(error: Int) {
            if (mine != session) return
            when (error) {
                // Silence or nothing recognised is normal between sentences: just listen again.
                SpeechRecognizer.ERROR_NO_MATCH, SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> restart(mine, 60)
                SpeechRecognizer.ERROR_RECOGNIZER_BUSY, SpeechRecognizer.ERROR_CLIENT -> {
                    if (++errorStreak > MAX_ERROR_STREAK) fail(mine, describe(error)) else restart(mine, 400)
                }
                else -> fail(mine, describe(error))
            }
        }

        override fun onBeginningOfSpeech() = Unit
        override fun onRmsChanged(rmsdB: Float) = Unit
        override fun onBufferReceived(buffer: ByteArray?) = Unit
        override fun onEndOfSpeech() = Unit
        override fun onEvent(eventType: Int, params: Bundle?) = Unit
    }

    private fun restart(mine: Int, delayMs: Long) {
        main.postDelayed({
            if (mine != session) return@postDelayed
            val i = intent ?: return@postDelayed
            try {
                recognizer?.startListening(i)
            } catch (t: Throwable) {
                fail(mine, t.message ?: "Could not restart listening")
            }
        }, delayMs)
    }

    private fun fail(mine: Int, message: String) {
        if (mine != session) return
        SpeechLog.error(id, language, message)
        listener?.onError(message)
    }

    private fun firstResult(bundle: Bundle?): String? =
        bundle?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull()

    private fun describe(error: Int): String = when (error) {
        SpeechRecognizer.ERROR_LANGUAGE_NOT_SUPPORTED, SpeechRecognizer.ERROR_LANGUAGE_UNAVAILABLE ->
            "The ${languageTag(language)} offline speech pack is not installed on this phone. " +
                "Install it once in the Google speech settings, or pick Vosk."
        SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> "Microphone permission is missing."
        SpeechRecognizer.ERROR_NETWORK, SpeechRecognizer.ERROR_NETWORK_TIMEOUT, SpeechRecognizer.ERROR_SERVER,
        SpeechRecognizer.ERROR_SERVER_DISCONNECTED ->
            "The recogniser wanted the network (error $error). Samjho stays offline, so pick Vosk."
        SpeechRecognizer.ERROR_TOO_MANY_REQUESTS -> "The speech service is rate limited (error $error)."
        else -> "Speech recognition failed (error $error)."
    }

    private fun languageTag(language: Language) = if (language == Language.HINDI) "hi-IN" else "en-IN"

    companion object {
        private const val PAUSE_MS = 1500L
        private const val MAX_ERROR_STREAK = 8
    }
}
