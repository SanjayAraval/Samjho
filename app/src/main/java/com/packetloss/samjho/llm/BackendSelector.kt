package com.packetloss.samjho.llm

import android.content.Context
import android.util.Log
import com.google.ai.edge.litertlm.Backend
import com.google.ai.edge.litertlm.Engine
import com.google.ai.edge.litertlm.EngineConfig
import java.io.File

internal class BackendSelector(
    private val context: Context,
    private val modelPath: String,
) {
    companion object {
        private const val TAG = "SamjhoLlm"
    }

    data class Result(val backend: String, val engine: Engine)

    fun initialize(): Result {
        val attempts = listOf(
            "NPU" to Backend.NPU(context.applicationInfo.nativeLibraryDir),
            "GPU" to Backend.GPU(),
            "CPU" to Backend.CPU(),
        )
        var lastFailure: Throwable? = null
        for ((name, backend) in attempts) {
            try {
                Log.i(TAG, "Trying backend=$name model=$modelPath")
                val engine = Engine(
                    EngineConfig(
                        modelPath = modelPath,
                        backend = backend,
                        cacheDir = context.cacheDir.absolutePath,
                    )
                )
                engine.initialize()
                Log.i(TAG, "Backend ready: $name")
                return Result(name, engine)
            } catch (t: Throwable) {
                lastFailure = t
                Log.e(TAG, "Backend failed: $name", t)
            }
        }
        throw IllegalStateException(
            "No LiteRT-LM backend available: ${lastFailure?.message ?: "unknown error"}",
            lastFailure,
        )
    }

    fun validateModelFile(): String? {
        val file = File(modelPath)
        return when {
            !file.exists() -> "Model file not found: $modelPath"
            !file.isFile -> "Model path is not a file: $modelPath"
            !file.canRead() -> "Model file is not readable by the app: $modelPath"
            else -> null
        }
    }
}
