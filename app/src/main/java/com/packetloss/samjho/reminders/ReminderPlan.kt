package com.packetloss.samjho.reminders

import com.packetloss.samjho.extract.Lexicon
import com.packetloss.samjho.model.Basis
import com.packetloss.samjho.model.Extraction
import com.packetloss.samjho.model.FoodRelation
import com.packetloss.samjho.model.Medicine
import com.packetloss.samjho.model.TimeOfDay
import java.time.Instant
import java.time.ZoneId
import java.time.ZonedDateTime

/**
 * One daily reminder for one medicine at one time of day.
 *
 * It only ever restates what the doctor said: the name, and the dose and the food instruction if they were given. The
 * wording is built when the notification is shown, so it follows the language toggle at that moment.
 */
data class Reminder(
    val id: String,
    /** The summary a tap on the notification opens. */
    val consultationId: String,
    val name: String,
    val doseCount: Int?,
    val food: FoodRelation?,
    val slot: TimeOfDay,
    /** Hour of day, 0-23, the reminder repeats at. */
    val hour: Int,
    /** Occurrences still to come, the next one included. Null when the doctor gave no duration: until cancelled. */
    val remaining: Int?,
    /** When the next occurrence is due, epoch millis. */
    val nextAt: Long,
)

/** Why a medicine got no reminder. Said to the patient, never hidden. */
enum class SkipReason {
    /** The doctor did not say a time of day, and a time is never made up. */
    NO_TIME_OF_DAY,

    /** The name is still a guess the patient has not confirmed, and a guess is never put in a notification as a fact. */
    NOT_CONFIRMED,
}

data class Skipped(val name: String, val reason: SkipReason)

data class ReminderPlanResult(val reminders: List<Reminder>, val skipped: List<Skipped>)

/** What the patient was told after tapping "Set reminders". */
data class ReminderOutcome(
    val scheduled: List<Reminder>,
    val skipped: List<Skipped>,
    /** True when notifications are switched off for the app, so the reminders will be silent until they are allowed. */
    val notificationsBlocked: Boolean,
)

/** The pure part of reminders: which medicines get one, at what hour, for how long. No Android in here. */
object ReminderPlan {

    /** Morning 8am, afternoon 2pm, evening 6pm, night 9pm. The doctor picks the time of day; the app only picks the clock. */
    fun hour(slot: TimeOfDay): Int = when (slot) {
        TimeOfDay.MORNING -> 8
        TimeOfDay.AFTERNOON -> 14
        TimeOfDay.EVENING -> 18
        TimeOfDay.NIGHT -> 21
    }

    /** The name a notification carries: the doctor's own word, or the medicine the patient confirmed it to be. */
    fun displayName(m: Medicine): String =
        if (m.basis == Basis.HEARD && m.paper == null) m.name else Lexicon.display(m.key)

    fun plan(consultationId: String, e: Extraction, now: Instant, zone: ZoneId): ReminderPlanResult {
        val reminders = mutableListOf<Reminder>()
        val skipped = mutableListOf<Skipped>()
        e.medicines.filterNot { it.isRejected }.forEach { m ->
            val name = displayName(m)
            when {
                // A guess is named by the word that was heard, as on the card, never by what it might be.
                m.isUnconfirmed -> skipped += Skipped(m.name, SkipReason.NOT_CONFIRMED)
                m.timesOfDay.isEmpty() -> skipped += Skipped(name, SkipReason.NO_TIME_OF_DAY)
                else -> m.timesOfDay.distinct().forEach { slot ->
                    val hour = hour(slot)
                    reminders += Reminder(
                        id = "$consultationId|${m.key}|$slot",
                        consultationId = consultationId,
                        name = name,
                        doseCount = m.doseCount,
                        food = m.foodRelation,
                        slot = slot,
                        hour = hour,
                        remaining = m.durationDays,
                        nextAt = firstOccurrence(now, hour, zone).toInstant().toEpochMilli(),
                    )
                }
            }
        }
        return ReminderPlanResult(reminders, skipped)
    }

    /** Today at [hour] if that is still ahead, otherwise tomorrow. */
    fun firstOccurrence(now: Instant, hour: Int, zone: ZoneId): ZonedDateTime {
        val at = ZonedDateTime.ofInstant(now, zone).withHour(hour).withMinute(0).withSecond(0).withNano(0)
        return if (at.toInstant().isAfter(now)) at else at.plusDays(1)
    }

    /**
     * The reminder after its due occurrence has been dealt with (shown, or found already past on reboot). Returns null
     * when that was the last one. Occurrences already gone by [now] count as done, so a phone that was off for two
     * days does not fire two stale notifications, and a doctor's "for 5 days" is not stretched by the days missed.
     */
    fun advance(r: Reminder, now: Instant, zone: ZoneId): Reminder? {
        var remaining = r.remaining
        var next = ZonedDateTime.ofInstant(Instant.ofEpochMilli(r.nextAt), zone)
        do {
            if (remaining != null) {
                remaining -= 1
                if (remaining <= 0) return null
            }
            next = next.plusDays(1).withHour(r.hour).withMinute(0).withSecond(0).withNano(0)
        } while (!next.toInstant().isAfter(now))
        return r.copy(remaining = remaining, nextAt = next.toInstant().toEpochMilli())
    }

    /** What to do with a stored reminder when the alarms are rebuilt (reboot, update, app start). */
    fun onRebuild(r: Reminder, now: Instant, zone: ZoneId): Reminder? =
        if (Instant.ofEpochMilli(r.nextAt).isAfter(now)) r else advance(r, now, zone)
}
