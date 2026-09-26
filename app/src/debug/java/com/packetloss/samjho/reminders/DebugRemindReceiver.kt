package com.packetloss.samjho.reminders

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.packetloss.samjho.demo.DemoConsultations
import com.packetloss.samjho.extract.RuleExtractor
import com.packetloss.samjho.model.Language
import java.time.Instant

/**
 * Debug builds only (this file is not in the release build). Sets the reminders of the English viral-fever demo so the
 * first is due in a few minutes, through the same code path as the real thing, so an alarm, a notification and a tap
 * can be checked without waiting for 8am:
 *
 *   adb shell am broadcast -n com.packetloss.samjho/.reminders.DebugRemindReceiver --ei minutes 1
 *
 * The first reminder is due in `minutes`, the second one minute later. Every later day follows the normal hours.
 */
class DebugRemindReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val minutes = intent.getIntExtra("minutes", 1).coerceAtLeast(1)
        val demo = DemoConsultations.ALL.first { it.id == "en-viral-fever" }
        val extraction = RuleExtractor.extract(demo.lines)
        val now = Instant.now()
        val outcome = Reminders(context).set(
            consultationId = "debug-demo",
            extraction = extraction,
            wording = Language.ENGLISH,
            now = now,
            firstAt = { i -> now.plusSeconds((minutes + i) * 60L) },
        )
        Log.i(REMINDERS_TAG, "debug: ${outcome.scheduled.size} reminders set, first due in $minutes min; notifications blocked=${outcome.notificationsBlocked}")
    }
}
