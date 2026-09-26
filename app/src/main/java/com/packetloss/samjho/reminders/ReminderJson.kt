package com.packetloss.samjho.reminders

import com.packetloss.samjho.model.Basis
import com.packetloss.samjho.model.Confirmation
import com.packetloss.samjho.model.Continuation
import com.packetloss.samjho.model.Detail
import com.packetloss.samjho.model.Extraction
import com.packetloss.samjho.model.FollowUp
import com.packetloss.samjho.model.FoodRelation
import com.packetloss.samjho.model.Language
import com.packetloss.samjho.model.Medicine
import com.packetloss.samjho.model.Note
import com.packetloss.samjho.model.PaperNote
import com.packetloss.samjho.model.Provenance
import com.packetloss.samjho.model.TimeOfDay
import com.packetloss.samjho.model.TranscriptLine
import org.json.JSONArray
import org.json.JSONObject

/** Reminders and the summaries they open, as JSON text kept in the app's private storage. */
object ReminderJson {

    // ------------------------------------------------------------------ reminders

    fun reminders(list: List<Reminder>): String = JSONArray().apply { list.forEach { put(reminder(it)) } }.toString()

    fun remindersFrom(text: String?): List<Reminder> {
        if (text.isNullOrBlank()) return emptyList()
        return runCatching {
            val array = JSONArray(text)
            (0 until array.length()).map { reminder(array.getJSONObject(it)) }
        }.getOrDefault(emptyList())
    }

    private fun reminder(r: Reminder) = JSONObject().apply {
        put("id", r.id)
        put("consultation", r.consultationId)
        put("name", r.name)
        put("dose", r.doseCount ?: JSONObject.NULL)
        put("food", r.food?.name ?: JSONObject.NULL)
        put("slot", r.slot.name)
        put("hour", r.hour)
        put("remaining", r.remaining ?: JSONObject.NULL)
        put("next", r.nextAt)
    }

    private fun reminder(o: JSONObject) = Reminder(
        id = o.getString("id"),
        consultationId = o.getString("consultation"),
        name = o.getString("name"),
        doseCount = o.intOrNull("dose"),
        food = o.stringOrNull("food")?.let { FoodRelation.valueOf(it) },
        slot = TimeOfDay.valueOf(o.getString("slot")),
        hour = o.getInt("hour"),
        remaining = o.intOrNull("remaining"),
        nextAt = o.getLong("next"),
    )

    // ------------------------------------------------------------------ the summary a reminder opens

    fun extraction(e: Extraction): String = JSONObject().apply {
        put("language", e.language.name)
        put("lines", JSONArray().apply { e.lines.forEach { put(JSONObject().put("i", it.index).put("t", it.text)) } })
        put("medicines", JSONArray().apply { e.medicines.forEach { put(medicine(it)) } })
        put("diagnosis", notes(e.diagnosis))
        put("avoid", notes(e.avoid))
        put("warnings", notes(e.warnings))
        e.followUp?.let { f ->
            put(
                "followUp",
                JSONObject()
                    .put("days", f.inDays ?: JSONObject.NULL)
                    .put("text", f.text)
                    .put("lines", ints(f.sourceLines))
                    .put("provenance", f.provenance.name),
            )
        }
        put("dosingLines", ints(e.dosingLines.sorted()))
    }.toString()

    fun extractionFrom(text: String?): Extraction? {
        if (text.isNullOrBlank()) return null
        return runCatching {
            val o = JSONObject(text)
            Extraction(
                language = Language.valueOf(o.getString("language")),
                lines = o.getJSONArray("lines").objects().map { TranscriptLine(it.getInt("i"), it.getString("t")) },
                medicines = o.getJSONArray("medicines").objects().map { medicine(it) },
                diagnosis = notes(o.getJSONArray("diagnosis")),
                avoid = notes(o.getJSONArray("avoid")),
                warnings = notes(o.getJSONArray("warnings")),
                followUp = o.optJSONObject("followUp")?.let { f ->
                    FollowUp(
                        inDays = f.intOrNull("days"),
                        text = f.getString("text"),
                        sourceLines = f.getJSONArray("lines").ints(),
                        provenance = Provenance.valueOf(f.getString("provenance")),
                    )
                },
                dosingLines = o.getJSONArray("dosingLines").ints().toSet(),
            )
        }.getOrNull()
    }

    private fun medicine(m: Medicine) = JSONObject().apply {
        put("name", m.name)
        put("key", m.key)
        put("tpd", m.timesPerDay ?: JSONObject.NULL)
        put("tod", JSONArray().apply { m.timesOfDay.forEach { put(it.name) } })
        put("dose", m.doseCount ?: JSONObject.NULL)
        put("food", m.foodRelation?.name ?: JSONObject.NULL)
        put("days", m.durationDays ?: JSONObject.NULL)
        put("lines", ints(m.sourceLines))
        put("provenance", m.provenance.name)
        put("basis", m.basis.name)
        put("hypothesis", m.hypothesis ?: JSONObject.NULL)
        put("confirmation", m.confirmation.name)
        put("candidates", JSONArray().apply { m.candidates.forEach { put(it) } })
        put("suggested", m.suggested)
        m.paper?.let { put("paper", JSONObject().put("readAs", it.readAs ?: JSONObject.NULL)) }
        put(
            "continuations",
            JSONArray().apply {
                m.continuations.forEach { c ->
                    put(JSONObject().put("line", c.line).put("details", JSONArray().apply { c.details.forEach { put(it.name) } }))
                }
            },
        )
    }

    private fun medicine(o: JSONObject) = Medicine(
        name = o.getString("name"),
        key = o.getString("key"),
        timesPerDay = o.intOrNull("tpd"),
        timesOfDay = o.getJSONArray("tod").strings().map { TimeOfDay.valueOf(it) },
        doseCount = o.intOrNull("dose"),
        foodRelation = o.stringOrNull("food")?.let { FoodRelation.valueOf(it) },
        durationDays = o.intOrNull("days"),
        sourceLines = o.getJSONArray("lines").ints(),
        provenance = Provenance.valueOf(o.getString("provenance")),
        basis = Basis.valueOf(o.getString("basis")),
        hypothesis = o.intOrNull("hypothesis"),
        confirmation = Confirmation.valueOf(o.getString("confirmation")),
        candidates = o.getJSONArray("candidates").strings(),
        suggested = o.getString("suggested"),
        paper = o.optJSONObject("paper")?.let { PaperNote(it.stringOrNull("readAs")) },
        continuations = o.getJSONArray("continuations").objects().map { c ->
            Continuation(c.getInt("line"), c.getJSONArray("details").strings().map { Detail.valueOf(it) })
        },
    )

    private fun notes(list: List<Note>) = JSONArray().apply {
        list.forEach { put(JSONObject().put("text", it.text).put("lines", ints(it.sourceLines)).put("provenance", it.provenance.name)) }
    }

    private fun notes(a: JSONArray) = a.objects().map {
        Note(it.getString("text"), it.getJSONArray("lines").ints(), Provenance.valueOf(it.getString("provenance")))
    }

    private fun ints(list: List<Int>) = JSONArray().apply { list.forEach { put(it) } }

    private fun JSONArray.ints(): List<Int> = (0 until length()).map { getInt(it) }

    private fun JSONArray.strings(): List<String> = (0 until length()).map { getString(it) }

    private fun JSONArray.objects(): List<JSONObject> = (0 until length()).map { getJSONObject(it) }

    private fun JSONObject.intOrNull(key: String): Int? = if (isNull(key)) null else getInt(key)

    private fun JSONObject.stringOrNull(key: String): String? = if (isNull(key)) null else getString(key)
}
