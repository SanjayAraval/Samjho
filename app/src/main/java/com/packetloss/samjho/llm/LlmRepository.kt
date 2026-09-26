package com.packetloss.samjho.llm

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.os.IBinder
import android.os.RemoteException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/** Main-process facade. UI/business code never talks to AIDL directly. */
class LlmRepository(context: Context) {
    companion object {
        private const val BIND_TIMEOUT_MS = 90_000L
    }

    private val appContext = context.applicationContext
    private val _status = MutableStateFlow<LlmStatus>(LlmStatus.Loading)
    val status: StateFlow<LlmStatus> = _status.asStateFlow()

    @Volatile
    private var remote: ILlmService? = null

    @Volatile
    private var bound = false

    private val connection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
            remote = ILlmService.Stub.asInterface(service)
            bound = true
            kotlinx.coroutines.CoroutineScope(Dispatchers.IO).launch { refreshStatus() }
        }

        override fun onServiceDisconnected(name: ComponentName?) {
            remote = null
            bound = false
            _status.value = LlmStatus.Unavailable("LLM service disconnected")
        }

        override fun onBindingDied(name: ComponentName?) {
            remote = null
            bound = false
            _status.value = LlmStatus.Unavailable("LLM service binding died")
        }

        override fun onNullBinding(name: ComponentName?) {
            remote = null
            bound = false
            _status.value = LlmStatus.Unavailable("LLM service returned a null binding")
        }
    }

    suspend fun connect() = withContext(Dispatchers.IO) {
        if (bound && remote != null) return@withContext
        suspendCancellableCoroutine<Unit> { continuation ->
            val intent = Intent(appContext, LlmService::class.java)
            val connected = appContext.bindService(intent, connection, Context.BIND_AUTO_CREATE)
            if (!connected) {
                _status.value = LlmStatus.Unavailable("Unable to bind LLM service")
                continuation.resume(Unit)
                return@suspendCancellableCoroutine
            }
            continuation.invokeOnCancellation { runCatching { appContext.unbindService(connection) } }
            continuation.resume(Unit)
        }
        withTimeout(BIND_TIMEOUT_MS) {
            while (remote == null) kotlinx.coroutines.delay(50)
            while (true) {
                refreshStatus()
                if (_status.value !is LlmStatus.Loading) break
                kotlinx.coroutines.delay(100)
            }
        }
    }

    suspend fun ask(prompt: String): String {
        connect()
        val service = remote ?: throw IllegalStateException("LLM unavailable: ${_status.value}")
        return withContext(Dispatchers.IO) {
            try {
                service.ask(prompt)
            } catch (e: RemoteException) {
                _status.value = LlmStatus.Unavailable("LLM service failed: ${e.message}")
                throw e
            }
        }
    }

    suspend fun refreshStatus() = withContext(Dispatchers.IO) {
        val service = remote ?: return@withContext
        try {
            val status = service.getStatus()
            _status.value = parseStatus(status)
        } catch (e: RemoteException) {
            _status.value = LlmStatus.Unavailable("LLM service unavailable")
        }
    }

    fun close() {
        if (bound) {
            runCatching { appContext.unbindService(connection) }
        }
        remote = null
        bound = false
    }

    private fun parseStatus(value: String): LlmStatus = when {
        value == "Loading" -> LlmStatus.Loading
        value.startsWith("Ready:") -> LlmStatus.Ready(value.removePrefix("Ready:"))
        value.startsWith("Unavailable:") ->
            LlmStatus.Unavailable(value.removePrefix("Unavailable:"))
        else -> LlmStatus.Unavailable(value.ifBlank { "Unknown LLM status" })
    }
}
