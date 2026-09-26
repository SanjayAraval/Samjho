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
import android.os.SystemClock

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
 * A recogniser works in windows that end by themselves (on this phone about eight seconds in, often with
 * NO_MATCH and no result). [ContinuousTranscript] turns that into one continuous recording: the words of a
 * window are never discarded, a pause becomes a line, every end of a window restarts listening, and only
 * [stop] ends the session. Must be driven from the main thread.
 */
class AndroidSpeechEngine(context: Context) : SpeechEngine {

    override val id = EngineId.ANDROID

    private enum class Mode(val label: String) {
        ON_DEVICE("on-device"),
        SYSTEM("system recogniser, prefers offline"),
    }

    private val appContext = context.applicationContext
    private val main = Handler(Looper.getMainLooper())
    private val tones = TonesMuter(context)

    init {
        // If the app was killed in the middle of a recording, the phone may still be muted from it.
        tones.restore()
    }

    private var recognizer: SpeechRecognizer? = null
    private var listener: SpeechEngine.Listener? = null
    private var language = Language.ENGLISH
    private var intent: Intent? = null
    private var mode = Mode.SYSTEM

    private var session = 0
    private var announced = false
    private var lineCount = 0
    private var tagIndex = 0

    /** Decides what happens each time the recogniser ends a window; see [ContinuousTranscript]. */
    private var transcript = ContinuousTranscript()

    private fun now() = SystemClock.elapsedRealtime()

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
        lineCount = 0
        tagIndex = 0
        transcript = ContinuousTranscript()
        tones.mute()

        intent = buildIntent(languageTag(language))
        open(mine, if (onDeviceAvailable()) Mode.ON_DEVICE else Mode.SYSTEM)
        tickLater(mine)
    }

    /** Only the user ends a recording. This returns the words still in flight and guarantees nothing restarts. */
    override fun stop(): String {
        val pending = transcript.stop()
        SpeechLog.event(id, "stop requested, ${pending.length} chars in flight")
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
        transcript.onWindowOpened(now())
    }

    private fun stopInternal() {
        session++
        tones.restore()
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
            if (mine != session) return
            SpeechLog.event(id, "ready")
            transcript.onReady(now())
            if (announced) return
            announced = true
            listener?.onListening("${mode.label} · ${languageTag(language)}")
        }

        override fun onPartialResults(partialResults: Bundle?) {
            if (mine != session) return
            val text = firstResult(partialResults) ?: return
            transcript.onPartial(text, now())
            listener?.onPartial(text)
        }

        override fun onResults(results: Bundle?) {
            if (mine != session) return
            val hypotheses = hypothesesOf(results)
            val text = hypotheses.firstOrNull()?.text?.trim().orEmpty()
            SpeechLog.event(id, "results (${text.length} chars)")
            apply(mine, transcript.onResults(text, now()), hypotheses)
        }

        override fun onError(error: Int) {
            if (mine != session) return
            SpeechLog.event(id, "error code=$error")
            when (error) {
                // Silence or nothing recognised is normal, and must never end the recording. Whatever was heard in the
                // window is kept: NO_MATCH after a long window is exactly how sentences used to be lost.
                SpeechRecognizer.ERROR_NO_MATCH, SpeechRecognizer.ERROR_SPEECH_TIMEOUT ->
                    apply(mine, transcript.onEnded(ContinuousTranscript.EndKind.SILENT, now()))
                SpeechRecognizer.ERROR_RECOGNIZER_BUSY, SpeechRecognizer.ERROR_CLIENT -> {
                    SpeechLog.error(id, language, "recogniser not ready code=$error mode=${mode.label}")
                    apply(mine, transcript.onEnded(ContinuousTranscript.EndKind.NOT_READY, now()))
                }
                // The service dropped our connection, usually because a recogniser was torn down a moment ago. A
                // fresh recogniser after a pause normally reconnects; what was heard is committed first.
                SpeechRecognizer.ERROR_SERVER_DISCONNECTED -> {
                    SpeechLog.error(id, language, "recogniser error code=$error mode=${mode.label}")
                    commitOnly(mine, transcript.onEnded(ContinuousTranscript.EndKind.NOT_READY, now()))
                    reopen(mine, mode)
                }
                SpeechRecognizer.ERROR_LANGUAGE_NOT_SUPPORTED, SpeechRecognizer.ERROR_LANGUAGE_UNAVAILABLE -> {
                    SpeechLog.error(id, language, "recogniser error code=$error mode=${mode.label}")
                    commitOnly(mine, transcript.onEnded(ContinuousTranscript.EndKind.SILENT, now()))
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
                }
                else -> {
                    SpeechLog.error(id, language, "recogniser error code=$error mode=${mode.label}")
                    commitOnly(mine, transcript.onEnded(ContinuousTranscript.EndKind.SILENT, now()))
                    fail(mine, describe(error))
                }
            }
        }

        override fun onBeginningOfSpeech() {
            if (mine == session) SpeechLog.event(id, "beginning-of-speech")
        }

        override fun onEndOfSpeech() {
            if (mine != session) return
            SpeechLog.event(id, "end-of-speech")
            transcript.onEndOfSpeech(now())
        }

        override fun onRmsChanged(rmsdB: Float) = Unit
        override fun onBufferReceived(buffer: ByteArray?) = Unit
        override fun onEvent(eventType: Int, params: Bundle?) = Unit
    }

    /**
     * Carries out what [ContinuousTranscript] decided. [finalHypotheses] is the N-best list of a real final result,
     * used for the line that result produced; a line rescued from a partial has only its one reading.
     */
    private fun apply(mine: Int, steps: List<ContinuousTranscript.Step>, finalHypotheses: List<Hypothesis> = emptyList()) {
        for (step in steps) {
            if (mine != session) return
            when (step) {
                is ContinuousTranscript.Step.Commit -> commit(step, finalHypotheses)
                is ContinuousTranscript.Step.Restart -> restart(mine, step)
                is ContinuousTranscript.Step.Fail -> fail(mine, step.reason)
            }
        }
    }

    /** Lines only, no restart: used when the caller is about to rebuild the recogniser or give up. */
    private fun commitOnly(mine: Int, steps: List<ContinuousTranscript.Step>) {
        steps.filterIsInstance<ContinuousTranscript.Step.Commit>().forEach { if (mine == session) commit(it, emptyList()) }
    }

    private fun commit(step: ContinuousTranscript.Step.Commit, finalHypotheses: List<Hypothesis>) {
        val hypotheses = if (!step.fromPartial && finalHypotheses.isNotEmpty()) finalHypotheses else Hypotheses.build(listOf(step.text), null)
        SpeechLog.event(id, "commit ${if (step.fromPartial) "from partial" else "final"} (${step.text.length} chars)")
        SpeechLog.line(id, language, lineCount, step.text, mode.label)
        SpeechLog.hypotheses(id, language, lineCount, hypotheses)
        lineCount++
        listener?.onLine(step.text, hypotheses)
    }

    /** Checks every half second for a window that has stalled, hung or gone quiet. Stops by itself when the session ends. */
    private fun tickLater(mine: Int) {
        main.postDelayed({
            if (mine != session) return@postDelayed
            apply(mine, transcript.onTick(now()))
            tickLater(mine)
        }, TICK_MS)
    }

    /** Swaps in a fresh recogniser after a pause, so the speech service can finish closing the old one. */
    private fun reopen(mine: Int, wanted: Mode) {
        recognizer?.let { it.cancel(); it.destroy() }
        recognizer = null
        main.postDelayed({ if (mine == session) open(mine, wanted) }, REOPEN_DELAY_MS)
    }

    private fun restart(mine: Int, step: ContinuousTranscript.Step.Restart) {
        val how = (if (step.cancelFirst) " (cancelling the open window)" else "") + (if (step.recreate) " (new recogniser)" else "")
        SpeechLog.event(id, "restart in ${step.delayMs} ms$how")
        if (step.cancelFirst) runCatching { recognizer?.cancel() }
        if (step.recreate) {
            recognizer?.let { runCatching { it.cancel() }; runCatching { it.destroy() } }
            recognizer = null
            main.postDelayed({ if (mine == session) open(mine, mode) }, maxOf(step.delayMs, REOPEN_DELAY_MS))
            return
        }
        main.postDelayed({
            if (mine != session) return@postDelayed
            val i = intent ?: return@postDelayed
            try {
                recognizer?.startListening(i)
                transcript.onWindowOpened(now())
            } catch (t: Throwable) {
                fail(mine, t.message ?: "Could not restart listening")
            }
        }, step.delayMs)
    }

    private fun fail(mine: Int, message: String) {
        if (mine != session) return
        SpeechLog.error(id, language, "mode=${mode.label} $message")
        listener?.onError(message)
    }

    private fun firstResult(bundle: Bundle?): String? =
        bundle?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull()

    /** The recogniser's whole N-best list, best first; see [Hypotheses] for what is dropped and why. */
    private fun hypothesesOf(bundle: Bundle?): List<Hypothesis> = Hypotheses.build(
        bundle?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION).orEmpty(),
        bundle?.getFloatArray(SpeechRecognizer.CONFIDENCE_SCORES),
    )

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
        /** Asked of the recogniser to tolerate longer pauses. Many phones ignore it; the restart policy is the real fix. */
        private const val PAUSE_MS = 3500L
        private const val TICK_MS = 500L
        private const val MAX_HYPOTHESES = 5
        private const val REOPEN_DELAY_MS = 600L
    }
}
