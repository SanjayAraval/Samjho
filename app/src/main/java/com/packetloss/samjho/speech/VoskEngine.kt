package com.packetloss.samjho.speech

import android.content.Context
import android.os.Handler
import android.os.Looper
import com.packetloss.samjho.model.Language
import org.json.JSONObject
import org.vosk.Model
import org.vosk.Recognizer
import org.vosk.android.RecognitionListener
import org.vosk.android.SpeechService
import java.util.concurrent.Executors

/**
 * Offline transcription with bundled Vosk models. Works on any Android version and needs nothing
 * from the OS, but its small models only know their fixed vocabulary, so brand names it was never
 * trained on come out as other words.
 */
class VoskEngine(context: Context) : SpeechEngine {

    override val id = EngineId.VOSK

    private val appContext = context.applicationContext
    private val worker = Executors.newSingleThreadExecutor()
    private val main = Handler(Looper.getMainLooper())

    private var model: Model? = null
    private var loadedAssetDir: String? = null
    private var service: SpeechService? = null

    /** Bumped on every start and stop, so a slow model load can't resurrect a cancelled session. */
    @Volatile private var session = 0
    private var lastPartial = ""
    private var lineCount = 0

    override fun unavailableReason(language: Language): String? =
        if (ModelInstaller.isBundled(appContext, assetDir(language))) null
        else "The Vosk ${if (language == Language.HINDI) "Hindi" else "English"} model is not bundled in this build."

    override fun start(language: Language, listener: SpeechEngine.Listener) {
        val mine = ++session
        lastPartial = ""
        lineCount = 0
        SpeechLog.started(id, language, "model=${assetDir(language)}")
        worker.execute {
            try {
                val m = loadModel(assetDir(language))
                if (mine != session) return@execute
                val recognizer = Recognizer(m, SAMPLE_RATE)
                val s = SpeechService(recognizer, SAMPLE_RATE)
                service = s
                s.startListening(object : RecognitionListener {
                    override fun onPartialResult(hypothesis: String?) {
                        if (mine != session) return
                        val text = JSONObject(hypothesis ?: return).optString("partial")
                        lastPartial = text
                        listener.onPartial(text)
                    }

                    override fun onResult(hypothesis: String?) = emit(hypothesis)
                    override fun onFinalResult(hypothesis: String?) = emit(hypothesis)

                    private fun emit(hypothesis: String?) {
                        if (mine != session) return
                        val text = JSONObject(hypothesis ?: return).optString("text").trim()
                        lastPartial = ""
                        if (text.isEmpty()) return
                        SpeechLog.line(id, language, lineCount++, text)
                        listener.onLine(text)
                    }

                    override fun onError(e: Exception?) {
                        if (mine != session) return
                        val message = e?.message ?: "Speech recognition failed"
                        SpeechLog.error(id, language, message)
                        listener.onError(message)
                    }

                    override fun onTimeout() = Unit
                })
                main.post { if (mine == session) listener.onListening("offline model") }
            } catch (t: Throwable) {
                val message = t.message ?: t.javaClass.simpleName
                SpeechLog.error(id, language, message)
                main.post { if (mine == session) listener.onError(message) }
            }
        }
    }

    override fun stop(): String {
        session++
        val pending = lastPartial.trim()
        lastPartial = ""
        service?.let {
            it.stop()
            it.shutdown()
        }
        service = null
        return pending
    }

    private fun loadModel(assetDir: String): Model {
        model?.takeIf { loadedAssetDir == assetDir }?.let { return it }
        model?.close()
        val dir = ModelInstaller.ensureInstalled(appContext, assetDir)
        return Model(dir.absolutePath).also {
            model = it
            loadedAssetDir = assetDir
        }
    }

    private fun assetDir(language: Language) =
        if (language == Language.HINDI) "model-hi" else "model-en-in"

    companion object {
        private const val SAMPLE_RATE = 16000f
    }
}
