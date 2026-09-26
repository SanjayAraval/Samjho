package com.packetloss.samjho.speech

import android.content.Context
import java.io.File

/**
 * Vosk loads models from real files, not from the APK, so the model shipped in assets is copied
 * into app storage once. The copy goes to a temp folder and is renamed only when complete, so a
 * crash mid-copy can never leave a half-written model that later loads as garbage.
 */
object ModelInstaller {

    private const val MARKER = ".samjho-installed"

    fun isBundled(context: Context, assetDir: String): Boolean =
        !context.assets.list(assetDir).isNullOrEmpty()

    /** Returns the folder Vosk should load. Blocking: call from a background thread. */
    fun ensureInstalled(context: Context, assetDir: String): File {
        check(isBundled(context, assetDir)) { "Speech model '$assetDir' is not bundled in this build" }

        val target = File(context.filesDir, "models/$assetDir")
        if (!File(target, MARKER).exists()) {
            val staging = File(context.filesDir, "models/$assetDir.tmp")
            staging.deleteRecursively()
            copyAsset(context, assetDir, staging)
            File(staging, MARKER).writeText("ok")
            target.deleteRecursively()
            check(staging.renameTo(target)) { "Could not move the speech model into place" }
        }
        return modelRoot(target)
    }

    /** A model unzipped as a nested folder still works: the root is wherever "am" lives. */
    private fun modelRoot(dir: File): File {
        if (File(dir, "am").isDirectory) return dir
        return dir.listFiles { f -> f.isDirectory }?.firstOrNull { File(it, "am").isDirectory }
            ?: error("No Vosk model found under ${dir.name}")
    }

    private fun copyAsset(context: Context, path: String, dest: File) {
        val children = context.assets.list(path).orEmpty()
        if (children.isEmpty()) {
            dest.parentFile?.mkdirs()
            context.assets.open(path).use { input ->
                dest.outputStream().use { input.copyTo(it) }
            }
        } else {
            dest.mkdirs()
            children.forEach { copyAsset(context, "$path/$it", File(dest, it)) }
        }
    }
}
