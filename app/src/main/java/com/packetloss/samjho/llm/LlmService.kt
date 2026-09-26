package com.packetloss.samjho.llm

import android.app.Service
import android.content.Intent
import android.os.Binder
import android.os.IBinder
import android.os.RemoteException
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout

class LlmService : Service() {
    companion object {
        private const val TAG = "SamjhoLlmService"
        const val ASK_TIMEOUT_MS = 90_000L
    }

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private lateinit var engine: LlmEngine

    private val binder = object : ILlmService.Stub() {
        override fun ask(prompt: String): String = runBlocking(Dispatchers.IO) {
            try {
                withTimeout(ASK_TIMEOUT_MS) { engine.ask(prompt) }
            } catch (t: Throwable) {
                Log.e(TAG, "ask() failed", t)
                throw RemoteException(t.message ?: "LLM request failed")
            }
        }

        override fun getStatus(): String = when (val status = engine.status.value) {
            LlmStatus.Loading -> "Loading"
            is LlmStatus.Ready -> "Ready:${status.backend}"
            is LlmStatus.Unavailable -> "Unavailable:${status.reason}"
        }

        override fun getBackend(): String =
            (engine.status.value as? LlmStatus.Ready)?.backend ?: ""
    }

    override fun onCreate() {
        super.onCreate()
        engine = LlmEngine(applicationContext)
        serviceScope.launch {
            engine.initialize()
        }
    }

    override fun onBind(intent: Intent?): IBinder = binder

    override fun onDestroy() {
        engine.close()
        serviceScope.cancel()
        super.onDestroy()
    }
}
