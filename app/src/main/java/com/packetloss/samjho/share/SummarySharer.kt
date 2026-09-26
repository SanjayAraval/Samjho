package com.packetloss.samjho.share

import android.app.Activity
import android.content.ClipData
import android.content.Context
import android.content.Intent
import android.os.Handler
import android.os.Looper
import android.util.Log
import androidx.core.content.FileProvider
import java.io.File
import java.io.FileOutputStream
import java.util.concurrent.Executors

/**
 * Hands a summary to Android's share sheet as an image or a PDF, so it can go to someone who does not
 * have the app (WhatsApp, Messages, email). The file lives in the app's private cache and is exposed
 * through a FileProvider with a one-time read grant, so no storage permission is needed, and nothing is
 * sent by Samjho itself: the person picks the destination in the share sheet.
 */
object SummarySharer {

    enum class Format(val mime: String, val extension: String) {
        IMAGE("image/png", "png"),
        PDF("application/pdf", "pdf"),
    }

    private const val TAG = "SamjhoShare"
    private const val MAX_AGE_MS = 24L * 60 * 60 * 1000
    private val worker = Executors.newSingleThreadExecutor()
    private val main = Handler(Looper.getMainLooper())

    fun share(
        context: Context,
        doc: SummaryDocument,
        format: Format,
        caption: String,
        chooserTitle: String,
        onError: (String) -> Unit,
    ) {
        val app = context.applicationContext
        worker.execute {
            try {
                val dir = File(app.cacheDir, "share").apply { mkdirs() }
                purgeOld(dir)
                val file = File(dir, "samjho-summary-${System.currentTimeMillis()}.${format.extension}")
                val start = System.nanoTime()
                val renderer = SummaryRenderer(doc)
                when (format) {
                    Format.IMAGE -> renderer.renderImage().let { bmp ->
                        FileOutputStream(file).use { bmp.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it) }
                        bmp.recycle()
                    }
                    Format.PDF -> renderer.writePdf(file)
                }
                Log.i(TAG, "created ${file.name} ${format.name} ${file.length()} bytes in ${(System.nanoTime() - start) / 1_000_000} ms")
                val uri = FileProvider.getUriForFile(app, app.packageName + ".share", file)
                main.post {
                    val send = Intent(Intent.ACTION_SEND).apply {
                        type = format.mime
                        putExtra(Intent.EXTRA_STREAM, uri)
                        putExtra(Intent.EXTRA_TEXT, caption)
                        clipData = ClipData.newRawUri("", uri)
                        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    }
                    val chooser = Intent.createChooser(send, chooserTitle).apply {
                        if (context !is Activity) addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    }
                    Log.i(TAG, "opening share sheet for ${file.name} (${format.mime})")
                    context.startActivity(chooser)
                }
            } catch (t: Throwable) {
                Log.w(TAG, "share failed: ${t.message}", t)
                main.post { onError(t.message ?: t.javaClass.simpleName) }
            }
        }
    }

    /** Summaries older than a day are deleted, so patient details do not pile up in the cache. */
    private fun purgeOld(dir: File) {
        val cutoff = System.currentTimeMillis() - MAX_AGE_MS
        dir.listFiles()?.filter { it.lastModified() < cutoff }?.forEach { it.delete() }
    }
}
