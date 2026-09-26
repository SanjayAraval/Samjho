package com.packetloss.samjho.reminders

import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.packetloss.samjho.MainActivity
import com.packetloss.samjho.model.Extraction
import com.packetloss.samjho.model.Language
import com.packetloss.samjho.ui.Strings
import java.io.File
import java.time.Instant
import java.time.ZoneId

/** Logcat tag for reminders: what was scheduled, what fired, what was skipped and why. */
const val REMINDERS_TAG = "SamjhoReminders"

/**
 * Medicine reminders: which are active, their alarms, and the summary each one opens.
 *
 * Every alarm is one-shot and set with setAndAllowWhileIdle, so no exact-alarm permission is needed; when one fires it
 * sets the next. Everything active is kept in private storage, so a reboot, an app update or a force-stop can rebuild
 * the alarms from it ([rebuild]).
 */
class Reminders(context: Context) {
    private val app = context.applicationContext
    private val alarms = app.getSystemService(Context.ALARM_SERVICE) as AlarmManager
    private val prefs = app.getSharedPreferences("samjho_reminders", Context.MODE_PRIVATE)
    private val summaries = File(app.filesDir, "consultations").apply { mkdirs() }

    // ------------------------------------------------------------------ reading

    @Synchronized
    fun all(): List<Reminder> =
        ReminderJson.remindersFrom(prefs.getString(KEY_REMINDERS, null)).sortedWith(compareBy({ it.nextAt }, { it.name }))

    fun forConsultation(id: String): List<Reminder> = all().filter { it.consultationId == id }

    fun notificationsAllowed(): Boolean = NotificationManagerCompat.from(app).areNotificationsEnabled()

    /** The language reminders are worded in: the one the patient last chose, so they follow the toggle. */
    var language: Language?
        get() = prefs.getString(KEY_LANGUAGE, null)?.let { runCatching { Language.valueOf(it) }.getOrNull() }
        set(value) {
            prefs.edit().putString(KEY_LANGUAGE, value?.name).commit()
        }

    /** The summary a reminder opens, as it was when the reminders were set (the patient's answers included). */
    fun summary(consultationId: String): Extraction? =
        File(summaries, fileName(consultationId)).takeIf { it.exists() }?.let { ReminderJson.extractionFrom(it.readText()) }

    // ------------------------------------------------------------------ setting and cancelling

    /**
     * Sets a daily reminder for every confirmed medicine the doctor gave a time of day for, replacing any set earlier for
     * the same consultation. [firstAt] can pick the first due time for the n-th reminder (used by the debug hook to make
     * one fire in a minute); the normal rule is the next occurrence of the hour.
     */
    @Synchronized
    fun set(
        consultationId: String,
        extraction: Extraction,
        wording: Language,
        now: Instant = Instant.now(),
        zone: ZoneId = ZoneId.systemDefault(),
        firstAt: (Int) -> Instant? = { null },
    ): ReminderOutcome {
        forConsultation(consultationId).forEach { cancelAlarm(it) }
        val planned = ReminderPlan.plan(consultationId, extraction, now, zone)
        val reminders = planned.reminders.mapIndexed { i, r -> firstAt(i)?.let { r.copy(nextAt = it.toEpochMilli()) } ?: r }
        save(all().filterNot { it.consultationId == consultationId } + reminders)
        if (reminders.isNotEmpty()) {
            File(summaries, fileName(consultationId)).writeText(ReminderJson.extraction(extraction))
        }
        language = wording
        reminders.forEach { schedule(it) }
        planned.skipped.forEach { Log.i(REMINDERS_TAG, "skipped ${it.name}: ${it.reason}") }
        reminders.forEach { Log.i(REMINDERS_TAG, "scheduled ${it.name} ${it.slot} at ${Instant.ofEpochMilli(it.nextAt)} remaining=${it.remaining}") }
        return ReminderOutcome(reminders, planned.skipped, notificationsBlocked = !notificationsAllowed())
    }

    @Synchronized
    fun cancel(id: String) {
        val list = all()
        list.filter { it.id == id }.forEach { cancelAlarm(it) }
        save(list.filterNot { it.id == id })
        Log.i(REMINDERS_TAG, "cancelled $id")
        dropUnusedSummaries()
    }

    @Synchronized
    fun cancelAll() {
        all().forEach { cancelAlarm(it) }
        save(emptyList())
        Log.i(REMINDERS_TAG, "cancelled all")
        dropUnusedSummaries()
    }

    // ------------------------------------------------------------------ alarms

    /** An alarm went off: say so, then set the next one (or finish). A reminder cancelled in the meantime does nothing. */
    @Synchronized
    fun fired(id: String, now: Instant = Instant.now(), zone: ZoneId = ZoneId.systemDefault()) {
        val list = all()
        val r = list.firstOrNull { it.id == id } ?: return
        notify(r)
        val next = ReminderPlan.advance(r, now, zone)
        save(list.filterNot { it.id == id } + listOfNotNull(next))
        if (next != null) schedule(next) else Log.i(REMINDERS_TAG, "finished ${r.name} ${r.slot}")
    }

    /**
     * Rebuilds every alarm from what is stored: after a reboot or an app update, and each time the app opens, since a
     * force-stop clears them. Occurrences that passed while the phone was off count as done and are not fired late.
     */
    @Synchronized
    fun rebuild(now: Instant = Instant.now(), zone: ZoneId = ZoneId.systemDefault()) {
        val kept = all().mapNotNull { ReminderPlan.onRebuild(it, now, zone) }
        save(kept)
        kept.forEach { schedule(it) }
        Log.i(REMINDERS_TAG, "rebuilt ${kept.size} alarms")
    }

    private fun schedule(r: Reminder) {
        alarms.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, r.nextAt, alarmIntent(r.id))
    }

    private fun cancelAlarm(r: Reminder) {
        alarms.cancel(alarmIntent(r.id))
    }

    private fun alarmIntent(id: String): PendingIntent = PendingIntent.getBroadcast(
        app,
        id.hashCode(),
        Intent(app, ReminderReceiver::class.java).setAction(ACTION_FIRE).putExtra(EXTRA_REMINDER, id),
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
    )

    // ------------------------------------------------------------------ the notification

    private fun notify(r: Reminder) {
        val t = Strings(language ?: Language.ENGLISH)
        val manager = NotificationManagerCompat.from(app)
        if (!manager.areNotificationsEnabled()) {
            Log.i(REMINDERS_TAG, "due ${r.name} ${r.slot}, but notifications are off")
            return
        }
        (app.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager).createNotificationChannel(
            NotificationChannel(CHANNEL, t.reminderChannel, NotificationManager.IMPORTANCE_HIGH),
        )
        val open = PendingIntent.getActivity(
            app,
            r.id.hashCode(),
            Intent(app, MainActivity::class.java)
                .putExtra(EXTRA_CONSULTATION, r.consultationId)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val line = t.reminderLine(r.name, r.doseCount, r.food)
        val notification = NotificationCompat.Builder(app, CHANNEL)
            .setSmallIcon(android.R.drawable.ic_popup_reminder)
            .setContentTitle(t.reminderTitle)
            .setContentText(line)
            .setStyle(NotificationCompat.BigTextStyle().bigText(line))
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .setContentIntent(open)
            .build()
        try {
            manager.notify(r.id.hashCode(), notification)
            Log.i(REMINDERS_TAG, "notified: $line")
        } catch (e: SecurityException) {
            Log.w(REMINDERS_TAG, "notification refused: ${e.message}")
        }
    }

    // ------------------------------------------------------------------ storage

    private fun save(list: List<Reminder>) {
        prefs.edit().putString(KEY_REMINDERS, ReminderJson.reminders(list)).commit()
    }

    /** A cancelled reminder leaves no copy of the consultation behind, unless another reminder still opens it. */
    private fun dropUnusedSummaries() {
        val used = all().map { fileName(it.consultationId) }.toSet()
        summaries.listFiles()?.filter { it.name !in used }?.forEach { it.delete() }
    }

    private fun fileName(consultationId: String) = consultationId.filter { it.isLetterOrDigit() || it == '-' } + ".json"

    companion object {
        const val ACTION_FIRE = "com.packetloss.samjho.action.REMINDER_FIRE"
        const val EXTRA_REMINDER = "reminder"

        /** Set on the intent a tapped notification launches the app with. */
        const val EXTRA_CONSULTATION = "consultation"
        private const val CHANNEL = "medicine_reminders"
        private const val KEY_REMINDERS = "reminders"
        private const val KEY_LANGUAGE = "language"
    }
}
