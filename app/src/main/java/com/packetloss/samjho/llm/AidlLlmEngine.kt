package com.packetloss.samjho.llm

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.os.Bundle
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.SystemClock
import android.util.Log
import java.io.File
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.TimeoutException
import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.withLock

/** What the app can show about the model: which backend is running and how fast. */
interface LlmStatusSource {
    val status: LlmStatus

    /** Starts loading the model in the background if that has not happened yet. Safe to call again. */
    fun start()

    /** Called on the main thread whenever [status] changes. */
    fun addListener(listener: (LlmStatus) -> Unit)
}

/**
 * The app-side [LlmEngine]: talks to the model process over AIDL and never lets it hurt the app.
 * Every call has a 90 second limit, a process that dies is restarted (a few times, then given up on),
 * and any failure is a null reply, which callers already treat as "the rules result stands".
 */
class AidlLlmEngine(
    context: Context,
    private val findModel: () -> File? = { ModelLocator.find(context) },
) : LlmEngine, LlmStatusSource {

    private val app = context.applicationContext
    private val lock = ReentrantLock()
    private val changed = lock.newCondition()
    private val main = Handler(Looper.getMainLooper())
    private val calls = Executors.newCachedThreadPool()
    private val listeners = CopyOnWriteArrayList<(LlmStatus) -> Unit>()

    private var service: ILlmService? = null
    private var bound = false
    private var started = false
    private var restarts = 0
    private var stoppedOnPurpose = false

    /** The model process's id, so a call that hangs can be ended from here even if the process cannot answer. */
    @Volatile
    private var modelPid = 0

    @Volatile
    override var status: LlmStatus = LlmStatus.Idle
        private set

    override fun addListener(listener: (LlmStatus) -> Unit) {
        listeners += listener
    }

    override fun unavailableReason(): String? {
        if (findModel() == null) return ModelLocator.MISSING
        return (status as? LlmStatus.Failed)?.reason
    }

    override fun start() {
        if (findModel() == null) {
            set(LlmStatus.Failed(ModelLocator.MISSING))
            return
        }
        lock.withLock {
            if (started) return
            started = true
        }
        set(LlmStatus.Loading("starting…"))
        bind()
    }

    override fun complete(prompt: String): String? {
        val deadline = SystemClock.elapsedRealtime() + TIMEOUT_MS
        if (unavailableReason() != null) return null
        start()

        // Loading can take a while on the first run; that wait counts against the same 90 seconds.
        val svc = awaitReady(deadline) ?: return null

        val call = calls.submit<Bundle?> { svc.complete(prompt, MAX_TOKENS) }
        return try {
            val reply = call.get((deadline - SystemClock.elapsedRealtime()).coerceAtLeast(1), TimeUnit.MILLISECONDS)
            if (reply == null || !reply.getBoolean("ok")) {
                Log.w(TAG, "model error: ${reply?.getString("error")}")
                return null
            }
            lock.withLock { restarts = 0 }
            val backend = runCatching { LlmBackend.valueOf(reply.getString("backend").orEmpty()) }.getOrNull()
            val tps = reply.getDouble("decodeTps")
            val current = status
            if (backend != null && tps > 0 && current is LlmStatus.Ready) set(current.copy(backend = backend, decodeTps = tps))
            val text = reply.getString("text")
            // The reply only, never the prompt: the prompt holds the patient's sentence.
            Log.i(TAG, "reply from $backend in ${reply.getLong("millis")} ms, ${"%.1f".format(tps)} tok/s: \"${text?.take(60)}\"")
            text
        } catch (e: TimeoutException) {
            Log.w(TAG, "the model took longer than ${TIMEOUT_MS / 1000} s; stopping it")
            call.cancel(true)
            stopOnPurpose(svc)
            null
        } catch (t: Throwable) {
            // A dead process surfaces here as a RemoteException; onServiceDisconnected does the recovery.
            Log.w(TAG, "the model process failed during a call: ${t.message}")
            null
        }
    }

    /** The connected service once the model is ready, or null if it failed or the time ran out. */
    private fun awaitReady(deadline: Long): ILlmService? = lock.withLock {
        var result: ILlmService? = null
        var waiting = true
        while (waiting) {
            val now = status
            val svc = service
            when {
                now is LlmStatus.Ready && svc != null -> { result = svc; waiting = false }
                now is LlmStatus.Failed -> waiting = false
                else -> {
                    val left = deadline - SystemClock.elapsedRealtime()
                    if (left <= 0) {
                        Log.w(TAG, "gave up waiting for the model to load")
                        waiting = false
                    } else {
                        changed.await(left, TimeUnit.MILLISECONDS)
                    }
                }
            }
        }
        result
    }

    // ---------------------------------------------------------------- connection

    private fun bind() {
        val ok = try {
            app.bindService(Intent(app, LlmService::class.java), connection, Context.BIND_AUTO_CREATE)
        } catch (t: Throwable) {
            Log.w(TAG, "bindService failed: ${t.message}")
            false
        }
        lock.withLock { bound = ok }
        if (!ok) set(LlmStatus.Failed("could not start the model process"))
    }

    private val connection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName, binder: IBinder) {
            val svc = ILlmService.Stub.asInterface(binder)
            lock.withLock { service = svc }
            val model = findModel()
            if (model == null) {
                set(LlmStatus.Failed(ModelLocator.MISSING))
                return
            }
            try {
                svc.load(model.absolutePath, listener)
            } catch (t: Throwable) {
                Log.w(TAG, "load request failed: ${t.message}")
                processEnded()
            }
        }

        override fun onServiceDisconnected(name: ComponentName) = processEnded()
        override fun onBindingDied(name: ComponentName) = processEnded()
        override fun onNullBinding(name: ComponentName) = processEnded()
    }

    private val listener = object : ILlmListener.Stub() {
        override fun onStatus(status: Bundle) {
            status.getInt("pid").takeIf { it > 0 }?.let { modelPid = it }
            set(
                when (status.getString("state")) {
                    "READY" -> LlmStatus.Ready(
                        backend = runCatching { LlmBackend.valueOf(status.getString("backend").orEmpty()) }.getOrDefault(LlmBackend.CPU),
                        decodeTps = status.getDouble("decodeTps"),
                        summary = status.getString("summary").orEmpty(),
                    )
                    "FAILED" -> LlmStatus.Failed(status.getString("detail") ?: "unknown error")
                    else -> LlmStatus.Loading(status.getString("detail") ?: "loading…")
                },
            )
        }
    }

    /** The model process is gone: crashed, killed for memory, or stopped by us after a timeout. */
    private fun processEnded() {
        var giveUp = false
        lock.withLock {
            if (service == null && !bound) return
            service = null
            if (bound) {
                try {
                    app.unbindService(connection)
                } catch (_: Throwable) {
                }
                bound = false
            }
            if (!stoppedOnPurpose) giveUp = ++restarts > MAX_RESTARTS
            stoppedOnPurpose = false
        }
        if (giveUp) {
            Log.w(TAG, "the model process stopped $restarts times in a row; giving up, rules only")
            set(LlmStatus.Failed("the model process kept stopping"))
            return
        }
        Log.w(TAG, "the model process ended; restarting it (restart $restarts of $MAX_RESTARTS)")
        set(LlmStatus.Loading("restarting after the model process stopped…"))
        main.postDelayed({ bind() }, RESTART_DELAY_MS)
    }

    private fun stopOnPurpose(svc: ILlmService) {
        lock.withLock { stoppedOnPurpose = true }
        try {
            svc.kill()
        } catch (_: Throwable) {
        }
        // A hung or stopped process cannot act on the request above, so end it from here as well. The
        // app may end its own other processes without any permission. The disconnect callback restarts it.
        val pid = modelPid
        if (pid > 0) android.os.Process.killProcess(pid)
    }

    private fun set(next: LlmStatus) {
        lock.withLock {
            status = next
            changed.signalAll()
        }
        Log.i(TAG, LlmStatusText.line(next))
        main.post { listeners.forEach { it(next) } }
    }

    companion object {
        const val TAG = "SamjhoLlm"
        const val TIMEOUT_MS = 90_000L
        private const val MAX_TOKENS = 24
        private const val MAX_RESTARTS = 4
        private const val RESTART_DELAY_MS = 500L
    }
}

/** Finds the Gemma model file on the phone. The app never downloads it (it has no internet permission). */
object ModelLocator {
    const val MISSING = "The Gemma model file is not on this phone (see the README to install it)."
    private const val DEFAULT_NAME = "Gemma3-1B-IT_multi-prefill-seq_q4_ekv2048.task"

    fun find(context: Context): File? {
        val dirs = listOfNotNull(File("/data/local/tmp/llm"), context.getExternalFilesDir(null)?.let { File(it, "llm") })
        for (dir in dirs) {
            // Listing a folder can be refused where reading a known file is allowed, so the usual name is tried too.
            val listed = dir.listFiles { f -> f.isFile && (f.extension == "task" || f.extension == "litertlm") }
                ?.minByOrNull { it.name }
            if (listed != null && listed.canRead()) return listed
            val known = File(dir, DEFAULT_NAME)
            if (known.isFile && known.canRead()) return known
        }
        return null
    }
}
