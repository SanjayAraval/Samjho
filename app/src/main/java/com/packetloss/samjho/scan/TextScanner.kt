package com.packetloss.samjho.scan

import android.graphics.Bitmap
import android.util.Log
import com.google.android.gms.tasks.Tasks
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.Text
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.TextRecognizer
import com.google.mlkit.vision.text.devanagari.DevanagariTextRecognizerOptions
import com.google.mlkit.vision.text.latin.TextRecognizerOptions

/**
 * Reads the text in a photo, on the phone. This uses the BUNDLED ML Kit recognisers (the models are
 * packaged in the APK), never the Play Services variants that download a model, so it works with no
 * network and the app still has no INTERNET permission. The photo is only ever a Bitmap in memory: it
 * is not saved, and nothing here can send it anywhere.
 *
 * Two readers run on the same photo: Latin for English and brand names (the usual case on a prescription),
 * and Devanagari for Hindi and other Devanagari text.
 */
class TextScanner {

    private val latin: TextRecognizer by lazy { TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS) }
    private val devanagari: TextRecognizer by lazy {
        TextRecognition.getClient(DevanagariTextRecognizerOptions.Builder().build())
    }

    /** Calls [done] on the main thread with what was read, or with the failure if both readers failed. */
    fun read(bitmap: Bitmap, done: (Result<OcrRead>) -> Unit) {
        val image = InputImage.fromBitmap(bitmap, 0)
        val start = System.nanoTime()
        val a = latin.process(image)
        val b = devanagari.process(image)
        Tasks.whenAllComplete(a, b).addOnCompleteListener {
            val millis = (System.nanoTime() - start) / 1_000_000
            val result = when {
                !a.isSuccessful && !b.isSuccessful ->
                    Result.failure(a.exception ?: b.exception ?: IllegalStateException("The text reader failed."))
                else -> Result.success(
                    OcrRead(
                        latin = if (a.isSuccessful) linesOf(a.result) else emptyList(),
                        devanagari = if (b.isSuccessful) linesOf(b.result) else emptyList(),
                    ),
                )
            }
            Log.i(TAG, "read in $millis ms: latin=${a.isSuccessful} devanagari=${b.isSuccessful}")
            done(result)
        }
    }

    fun close() {
        runCatching { latin.close() }
        runCatching { devanagari.close() }
    }

    private fun linesOf(text: Text): List<String> = text.textBlocks.flatMap { it.lines }.map { it.text }

    companion object {
        const val TAG = "SamjhoScan"
    }
}
