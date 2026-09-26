package com.packetloss.samjho.llm

import android.content.Context
import android.util.Log
import java.io.File

/** Resolves the model from the ADB staging path or app-private storage. */
internal class ModelPathResolver(
    private val context: Context,
    private val stagedPath: String,
) {
    companion object {
        private const val TAG = "SamjhoLlm"
        private const val MODEL_FILE_NAME =
            "Gemma3-1B-IT_multi-prefill-seq_q4_ekv2048.task"
    }

    fun resolve(): String? {
        val staged = File(stagedPath)
        if (staged.isFile && staged.canRead()) {
            Log.i(TAG, "Model readable at staged path: ${staged.absolutePath}")
            return staged.absolutePath
        }

        Log.w(TAG, "Staged model is not readable: ${staged.absolutePath}; trying app storage")
        val destination = File(context.filesDir, MODEL_FILE_NAME)
        if (destination.isFile && destination.canRead()) {
            Log.i(TAG, "Using existing app-private model: ${destination.absolutePath}")
            return destination.absolutePath
        }

        return try {
            staged.inputStream().use { input ->
                destination.outputStream().use { output ->
                    input.copyTo(output)
                }
            }
            if (destination.isFile && destination.canRead()) {
                Log.i(TAG, "Copied model to app-private storage: ${destination.absolutePath}")
                destination.absolutePath
            } else {
                Log.e(TAG, "Model copy completed but destination is not readable")
                null
            }
        } catch (t: Throwable) {
            destination.delete()
            Log.e(TAG, "Unable to copy model from ${staged.absolutePath} to ${destination.absolutePath}", t)
            null
        }
    }
}
