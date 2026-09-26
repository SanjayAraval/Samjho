package com.packetloss.samjho.speech

import android.content.Context
import android.os.Handler
import android.os.Looper
import org.json.JSONObject
import org.vosk.Model
import org.vosk.Recognizer
import org.vosk.android.RecognitionListener
import org.vosk.android.SpeechService
import java.util.concurrent.Executors

/**
 * Offline microphone transcription. Each pause in speech closes one utterance, and each
 * utterance becomes one transcript line, which is what every extracted item later cites.
 *
 * All callbacks arrive on the main thread.
 */
class Transcriber(context: Context) {

    interface Listener {
        fun onListening()
        fun onPartial(text: String)
        fun onLine(text: String)
        fun onError(message: String)
    }

    private val appContext = context.applicationContext
    private val worker = Executors.newSingleThreadExecutor()
    private val main = Handler(Looper.getMainLooper())

    private var model: Model? = null
    private var loadedAssetDir: String? = null
    private var service: SpeechService? = null

    /** Bumped on every start and stop, so a slow model load can't resurrect a cancelled session. */
    @Volatile private var session = 0
    private var lastPartial = ""

    fun start(assetDir: String, listener: Listener) {
        val mine = ++session
        lastPartial = ""
        worker.execute {
            try {
                val m = loadModel(assetDir)
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

                    override fun onResult(hypothesis: String?) = emit(hypothesis, "text")
                    override fun onFinalResult(hypothesis: String?) = emit(hypothesis, "text")

                    private fun emit(hypothesis: String?, field: String) {
                        if (mine != session) return
                        val text = JSONObject(hypothesis ?: return).optString(field).trim()
                        lastPartial = ""
                        if (text.isNotEmpty()) listener.onLine(text)
                    }

                    override fun onError(e: Exception?) {
                        if (mine == session) listener.onError(e?.message ?: "Speech recognition failed")
                    }

                    override fun onTimeout() = Unit
                })
                main.post { if (mine == session) listener.onListening() }
            } catch (t: Throwable) {
                main.post { if (mine == session) listener.onError(t.message ?: t.javaClass.simpleName) }
            }
        }
    }

    /**
     * Stops listening and returns any words still in flight, so the last sentence the doctor
     * said before the patient pressed stop is not lost.
     */
    fun stop(): String {
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

    companion object {
        private const val SAMPLE_RATE = 16000f
    }
}
