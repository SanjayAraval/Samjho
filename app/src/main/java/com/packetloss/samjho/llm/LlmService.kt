package com.packetloss.samjho.llm

import android.app.Service
import android.content.Intent
import android.os.Bundle
import android.os.IBinder
import android.os.Process
import android.util.Log

/**
 * The language model's home: a bound service in its own process (`android:process=":llm"`). The app
 * only ever talks to it through [ILlmService], so anything that goes wrong in the model, including a
 * native crash, ends this process and nothing else.
 */
class LlmService : Service() {

    private val runner by lazy { LiteRtRunner(applicationContext) }
    private val lock = Any()
    private var loadStarted = false

    @Volatile
    private var listener: ILlmListener? = null

    @Volatile
    private var last: Bundle? = null

    @Volatile
    private var ready = false

    private val binder = object : ILlmService.Stub() {

        override fun load(modelPath: String, l: ILlmListener) {
            listener = l
            last?.let { send(it) }
            synchronized(lock) {
                if (loadStarted) return
                loadStarted = true
            }
            Thread({
                runner.load(modelPath) { status ->
                    ready = status is LlmStatus.Ready
                    publish(status)
                }
            }, "llm-load").start()
        }

        override fun complete(prompt: String, maxTokens: Int): Bundle {
            if (!ready) return failure("the model is not ready")
            return try {
                val r = runner.complete(prompt, maxTokens)
                Bundle().apply {
                    putBoolean("ok", true)
                    putString("text", r.text)
                    putString("backend", runner.active?.name)
                    putDouble("decodeTps", r.decodeTps)
                    putDouble("prefillTps", r.prefillTps)
                    putLong("millis", r.millis)
                }
            } catch (t: Throwable) {
                Log.w(LiteRtRunner.TAG, "complete failed: ${t.message}", t)
                failure(t.message ?: t.javaClass.simpleName)
            }
        }

        override fun kill() {
            Log.w(LiteRtRunner.TAG, "asked to stop; ending the model process")
            Process.killProcess(Process.myPid())
        }
    }

    private fun failure(why: String) = Bundle().apply {
        putBoolean("ok", false)
        putString("error", why)
    }

    private fun publish(status: LlmStatus) {
        val b = Bundle()
        b.putInt("pid", Process.myPid())
        when (status) {
            LlmStatus.Idle -> b.putString("state", "LOADING")
            is LlmStatus.Loading -> {
                b.putString("state", "LOADING")
                b.putString("detail", status.detail)
            }
            is LlmStatus.Ready -> {
                b.putString("state", "READY")
                b.putString("backend", status.backend.name)
                b.putDouble("decodeTps", status.decodeTps)
                b.putString("summary", status.summary)
            }
            is LlmStatus.Failed -> {
                b.putString("state", "FAILED")
                b.putString("detail", status.reason)
            }
        }
        last = b
        send(b)
    }

    private fun send(b: Bundle) {
        try {
            listener?.onStatus(b)
        } catch (t: Throwable) {
            Log.w(LiteRtRunner.TAG, "the app is no longer listening: ${t.message}")
        }
    }

    override fun onBind(intent: Intent?): IBinder = binder

    /** Nobody is bound any more: free the model completely by ending this process. */
    override fun onDestroy() {
        runner.close()
        super.onDestroy()
        Process.killProcess(Process.myPid())
    }
}
