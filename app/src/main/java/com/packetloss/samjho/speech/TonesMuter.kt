package com.packetloss.samjho.speech

import android.content.Context
import android.media.AudioManager
import android.util.Log

/**
 * Silences the recogniser's start and end tones for the length of a recording.
 *
 * Google's recogniser plays a short tone every time it opens or closes a window (an AudioTrack with usage
 * NOTIFICATION_EVENT). Because a recording is a chain of windows, that is a beep every few seconds through a whole
 * consultation. There is no switch for it in the recogniser, so the notification stream is muted while recording.
 *
 * It changes a setting on the patient's phone, so it is careful about it: it only mutes a stream that is not already
 * muted, it remembers that it did (on disk, so a crash cannot leave the phone muted), it gives the stream back on
 * stop, and it gives it back at the next app start if the app died mid-recording. If the phone refuses (Do Not
 * Disturb can), the recording carries on with tones rather than failing.
 */
class TonesMuter(context: Context) {

    private val audio = context.applicationContext.getSystemService(Context.AUDIO_SERVICE) as AudioManager
    private val prefs = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    /** True if this call muted the stream. False when it was already muted, or the phone would not let us. */
    fun mute(): Boolean {
        if (prefs.getBoolean(KEY_MUTED_BY_US, false)) return true // already ours from earlier in this recording
        return try {
            if (audio.isStreamMute(STREAM)) {
                Log.i(TAG, "notification stream was already muted; leaving it alone")
                false
            } else {
                // Recorded first: if the process dies between here and the mute, the worst case is one extra unmute.
                prefs.edit().putBoolean(KEY_MUTED_BY_US, true).commit()
                audio.adjustStreamVolume(STREAM, AudioManager.ADJUST_MUTE, 0)
                val ok = audio.isStreamMute(STREAM)
                if (!ok) prefs.edit().putBoolean(KEY_MUTED_BY_US, false).commit()
                Log.i(TAG, "notification stream muted for the recording: $ok")
                ok
            }
        } catch (t: Throwable) {
            prefs.edit().putBoolean(KEY_MUTED_BY_US, false).commit()
            Log.w(TAG, "could not mute the recogniser tones: ${t.message}")
            false
        }
    }

    /** Gives the stream back, but only if we were the ones who muted it. */
    fun restore() {
        if (!prefs.getBoolean(KEY_MUTED_BY_US, false)) return
        try {
            audio.adjustStreamVolume(STREAM, AudioManager.ADJUST_UNMUTE, 0)
            Log.i(TAG, "notification stream restored: muted=${audio.isStreamMute(STREAM)}")
        } catch (t: Throwable) {
            Log.w(TAG, "could not restore the notification stream: ${t.message}")
        } finally {
            prefs.edit().putBoolean(KEY_MUTED_BY_US, false).commit()
        }
    }

    /** True while a recording of ours is holding the stream muted. */
    val isHolding: Boolean get() = prefs.getBoolean(KEY_MUTED_BY_US, false)

    private companion object {
        const val TAG = "SamjhoSpeech"
        const val STREAM = AudioManager.STREAM_NOTIFICATION
        const val PREFS = "samjho_tones"
        const val KEY_MUTED_BY_US = "muted_by_us"
    }
}
