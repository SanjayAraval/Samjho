package com.packetloss.samjho.speech

import android.content.Context

/** The engine the patient picked. Android's on-device recogniser is the default. */
object SpeechPrefs {
    private const val FILE = "samjho_prefs"
    private const val KEY_ENGINE = "speech_engine"

    val DEFAULT = EngineId.ANDROID

    fun engine(context: Context): EngineId {
        val saved = context.getSharedPreferences(FILE, Context.MODE_PRIVATE).getString(KEY_ENGINE, null)
        return EngineId.entries.firstOrNull { it.name == saved } ?: DEFAULT
    }

    fun setEngine(context: Context, id: EngineId) {
        context.getSharedPreferences(FILE, Context.MODE_PRIVATE).edit().putString(KEY_ENGINE, id.name).apply()
    }
}
