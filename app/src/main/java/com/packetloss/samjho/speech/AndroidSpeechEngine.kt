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
import com.packetloss.samjho.model.Hypothesis
import com.packetloss.samjho.model.Language

/**
 * The phone's own speech recogniser, biased toward the medicine names in [Lexicon].
 *
 * It runs in one of two modes, and always says which:
 *  - ON_DEVICE: [SpeechRecognizer.createOnDeviceSpeechRecognizer], which by contract never uses
 *    the network. Preferred whenever the phone has one with the language installed.
 *  - SYSTEM: the ordinary recogniser with EXTRA_PREFER_OFFLINE. That extra is only a hint: if the
 *    offline pack is missing the recogniser may use a server, and this app having no INTERNET
 *    permission would not stop it, because the audio leaves from another app's process. The mode
 *    is therefore shown on screen and logged, never hidden.
 *
 * A recogniser ends after each utterance, so it is restarted on every result to capture a whole
 * consultation as many lines. Must be driven from the main thread.
 */
class AndroidSpeechEngine(context: Context) : SpeechEngine {

    override val id = EngineId.ANDROID

    private enum class Mode(val label: String) {
        ON_DEVICE("on-device"),
        SYSTEM("system recogniser, prefers offline"),
    }

    private val appContext = context.applicationContext
    private val main = Handler(Looper.getMainLooper())

    private var recognizer: SpeechRecognizer? = null
    private var listener: SpeechEngine.Listener? = null
    private var language = Language.ENGLISH
    private var intent: Intent? = null
    private var mode = Mode.SYSTEM

    private var session = 0
    private var announced = false
    private var lastPartial = ""
    private var lineCount = 0
    private var errorStreak = 0
    private var tagIndex = 0

    private fun onDeviceAvailable() =
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            SpeechRecognizer.isOnDeviceRecognitionAvailable(appContext)

    override fun unavailableReason(language: Language): String? =
        if (onDeviceAvailable() || SpeechRecognizer.isRecognitionAvailable(appContext)) null
        else "This phone has no speech recogniser. Pick Vosk in the speech setting."

    override fun start(language: Language, listener: SpeechEngine.Listener) {
        stopInternal()
        val mine = ++session
        this.language = language
        this.listener = listener
        announced = false
        lastPartial = ""
        lineCount = 0
        errorStreak = 0
        tagIndex = 0

        intent = buildIntent(languageTag(language))
        open(mine, if (onDeviceAvailable()) Mode.ON_DEVICE else Mode.SYSTEM)
    }

    override fun stop(): String {
        val pending = lastPartial.trim()
        stopInternal()
        return pending
    }

    private fun open(mine: Int, wanted: Mode) {
        mode = wanted
        recognizer?.let { it.cancel(); it.destroy() }
        val r = if (wanted == Mode.ON_DEVICE) {
            SpeechRecognizer.createOnDeviceSpeechRecognizer(appContext)
        } else {
            SpeechRecognizer.createSpeechRecognizer(appContext)
        }
        recognizer = r
        r.setRecognitionListener(callbacks(mine))
        SpeechLog.started(
            id, language,
            "mode=${wanted.label} tag=${languageTag(language)} preferOffline=true hints=${Lexicon.speechHints().size}",
        )
        logInstalledLanguages(r, intent!!)
        r.startListening(intent)
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
        putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, MAX_HYPOTHESES)
        putExtra(RecognizerIntent.EXTRA_CALLING_PACKAGE, appContext.packageName)
        putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_COMPLETE_SILENCE_LENGTH_MILLIS, PAUSE_MS)
        putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_POSSIBLY_COMPLETE_SILENCE_LENGTH_MILLIS, PAUSE_MS)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            putStringArrayListExtra(RecognizerIntent.EXTRA_BIASING_STRINGS, ArrayList(Lexicon.speechHints()))
        }
    }

    /** Diagnostic only: which offline language packs the phone actually has. */
    private fun logInstalledLanguages(r: SpeechRecognizer, forIntent: Intent) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return
        try {
            r.checkRecognitionSupport(forIntent, appContext.mainExecutor, object : RecognitionSupportCallback {
                override fun onSupportResult(support: RecognitionSupport) {
                    SpeechLog.started(
                        id, language,
                        "support[${mode.label}] installed=${support.installedOnDeviceLanguages} " +
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
            listener?.onListening("${mode.label} · ${languageTag(language)}")
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
            val hypotheses = hypothesesOf(results)
            val text = hypotheses.firstOrNull()?.text?.trim().orEmpty()
            if (text.isNotEmpty()) {
                SpeechLog.line(id, language, lineCount, text, mode.label)
                SpeechLog.hypotheses(id, language, lineCount, hypotheses)
                lineCount++
                listener?.onLine(text, hypotheses)
            }
            restart(mine, 60)
        }

        override fun onError(error: Int) {
            if (mine != session) return
            if (error != SpeechRecognizer.ERROR_NO_MATCH && error != SpeechRecognizer.ERROR_SPEECH_TIMEOUT) {
                SpeechLog.error(id, language, "recogniser error code=$error mode=${mode.label}")
            }
            when (error) {
                // Silence or nothing recognised is normal between sentences: just listen again.
                SpeechRecognizer.ERROR_NO_MATCH, SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> restart(mine, 60)
                SpeechRecognizer.ERROR_RECOGNIZER_BUSY, SpeechRecognizer.ERROR_CLIENT -> {
                    if (++errorStreak > MAX_ERROR_STREAK) fail(mine, describe(error)) else restart(mine, 400)
                }
                // The service dropped our connection, usually because a recogniser was torn down a
                // moment ago. A fresh recogniser after a pause normally reconnects.
                SpeechRecognizer.ERROR_SERVER_DISCONNECTED ->
                    if (++errorStreak > MAX_ERROR_STREAK) fail(mine, describe(error)) else reopen(mine, mode)
                SpeechRecognizer.ERROR_LANGUAGE_NOT_SUPPORTED, SpeechRecognizer.ERROR_LANGUAGE_UNAVAILABLE ->
                    if (mode == Mode.ON_DEVICE && SpeechRecognizer.isRecognitionAvailable(appContext)) {
                        SpeechLog.error(id, language, "on-device has no ${languageTag(language)} model; switching to system recogniser")
                        reopen(mine, Mode.SYSTEM)
                    } else if (tagIndex < tags(language).lastIndex) {
                        val from = languageTag(language)
                        tagIndex++
                        intent = buildIntent(languageTag(language))
                        SpeechLog.error(id, language, "no offline $from model; falling back to ${languageTag(language)}")
                        reopen(mine, Mode.SYSTEM)
                    } else fail(mine, describe(error))
                else -> fail(mine, describe(error))
            }
        }

        override fun onBeginningOfSpeech() = Unit
        override fun onRmsChanged(rmsdB: Float) = Unit
        override fun onBufferReceived(buffer: ByteArray?) = Unit
        override fun onEndOfSpeech() = Unit
        override fun onEvent(eventType: Int, params: Bundle?) = Unit
    }

    /** Swaps in a fresh recogniser after a pause, so the speech service can finish closing the old one. */
    private fun reopen(mine: Int, wanted: Mode) {
        recognizer?.let { it.cancel(); it.destroy() }
        recognizer = null
        main.postDelayed({ if (mine == session) open(mine, wanted) }, REOPEN_DELAY_MS)
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
        SpeechLog.error(id, language, "mode=${mode.label} $message")
        listener?.onError(message)
    }

    private fun firstResult(bundle: Bundle?): String? =
        bundle?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull()

    /**
     * The recogniser's whole N-best list, best first, with confidence where it gives one. A score
     * below zero means "not provided" (some recognisers send -1), so it is dropped, not kept.
     */
    private fun hypothesesOf(bundle: Bundle?): List<Hypothesis> {
        val texts = bundle?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION).orEmpty()
        val scores = bundle?.getFloatArray(SpeechRecognizer.CONFIDENCE_SCORES)
        return texts.mapIndexedNotNull { i, t ->
            if (t.isBlank()) null else Hypothesis(t.trim(), scores?.getOrNull(i)?.takeIf { it >= 0f })
        }
    }

    private fun describe(error: Int): String = when (error) {
        SpeechRecognizer.ERROR_LANGUAGE_NOT_SUPPORTED, SpeechRecognizer.ERROR_LANGUAGE_UNAVAILABLE ->
            "The ${languageTag(language)} speech pack is not available offline on this phone. Pick Vosk."
        SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> "Microphone permission is missing."
        SpeechRecognizer.ERROR_NETWORK, SpeechRecognizer.ERROR_NETWORK_TIMEOUT, SpeechRecognizer.ERROR_SERVER,
        SpeechRecognizer.ERROR_SERVER_DISCONNECTED ->
            "The recogniser needs an offline ${languageTag(language)} pack and has none (error $error). " +
                "Samjho stays offline, so pick Vosk or install the pack in Google's voice settings."
        SpeechRecognizer.ERROR_TOO_MANY_REQUESTS -> "The speech service is rate limited (error $error)."
        else -> "Speech recognition failed (error $error)."
    }

    /**
     * Languages to try in order. On the iQOO I2501 Google has an offline en-US model but no offline
     * en-IN or hi-IN one, so English falls back to en-US; every fallback is logged.
     */
    private fun tags(language: Language) =
        if (language == Language.HINDI) listOf("hi-IN") else listOf("en-IN", "en-US")

    private fun languageTag(language: Language) = tags(language)[tagIndex.coerceAtMost(tags(language).lastIndex)]

    companion object {
        private const val PAUSE_MS = 1500L
        private const val MAX_ERROR_STREAK = 8
        private const val MAX_HYPOTHESES = 5
        private const val REOPEN_DELAY_MS = 600L
    }
}
