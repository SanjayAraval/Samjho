package com.packetloss.samjho.reminders

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log

/** A reminder's alarm went off. */
class ReminderReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val id = intent.getStringExtra(Reminders.EXTRA_REMINDER) ?: return
        Log.i(REMINDERS_TAG, "alarm fired: $id")
        Reminders(context).fired(id)
    }
}

/** The phone restarted, or Samjho was updated: both clear alarms, so they are set again from what is stored. */
class ReminderBootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            Intent.ACTION_BOOT_COMPLETED, Intent.ACTION_MY_PACKAGE_REPLACED -> {
                Log.i(REMINDERS_TAG, "rebuilding alarms after ${intent.action}")
                Reminders(context).rebuild()
            }
        }
    }
}
