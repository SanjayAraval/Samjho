package com.packetloss.samjho.voice

import com.packetloss.samjho.extract.Lexicon
import com.packetloss.samjho.model.Extraction
import com.packetloss.samjho.model.Medicine
import com.packetloss.samjho.ui.Strings

/**
 * What read-aloud says, one sentence per entry. It speaks the result screen and nothing else, so it
 * follows the same rules: it only restates, an unconfirmed medicine is spoken as "not confirmed:
 * heard as X, possibly Y" and never as a fact, and a medicine the patient dismissed is not spoken.
 */
object ReadAloudScript {

    fun build(e: Extraction, t: Strings): List<String> = buildList {
        add(t.readIntro)

        if (e.diagnosis.isNotEmpty()) {
            add(t.diagnosis + ".")
            e.diagnosis.forEach { add(it.text + ".") }
        }

        val medicines = e.medicines.filterNot { it.isRejected }
        if (medicines.isNotEmpty()) {
            add(t.medicines + ".")
            medicines.forEach { add(medicine(it, t)) }
        }

        val unnamed = e.unnamedDosing
        if (unnamed.isNotEmpty()) {
            add(t.unnamedDosing + ".")
            unnamed.forEach { add(it.text) }
        }

        if (e.avoid.isNotEmpty()) {
            add(t.avoid + ".")
            e.avoid.forEach { add(it.text + ".") }
        }

        if (e.warnings.isNotEmpty()) {
            add(t.warnings + ".")
            e.warnings.forEach { add(it.text) }
        }

        e.followUp?.let { f ->
            add(t.followUp + ".")
            add((f.inDays?.let { t.followUpIn(it) } ?: f.text) + ".")
        }

        if (e.missingSections.isNotEmpty()) {
            add(t.notMentioned + ": " + e.missingSections.joinToString(", ") { t.missingSection(it) } + ".")
        }

        add(t.disclaimer)
    }

    private fun medicine(m: Medicine, t: Strings): String {
        val details = buildList {
            m.timesPerDay?.let { add(t.timesPerDay(it)) }
            m.doseCount?.let { add(t.dose(it)) }
            m.timesOfDay.forEach { add(t.timeOfDay(it)) }
            m.foodRelation?.let { add(t.food(it)) }
            m.durationDays?.let { add(t.durationDays(it)) }
        }
        val name = when {
            m.isUnconfirmed -> t.readNotConfirmed(m.name, Lexicon.display(m.key))
            m.basis != com.packetloss.samjho.model.Basis.HEARD -> Lexicon.display(m.key)
            else -> m.name
        }
        val body = details.joinToString(", ")
        // The "not confirmed" phrase already ends in a full stop; a plain name gets a colon or a stop.
        val core = when {
            m.isUnconfirmed -> if (body.isEmpty()) name else "$name $body."
            body.isEmpty() -> "$name."
            else -> "$name: $body."
        }
        val missing = if (m.missing.isEmpty()) "" else " " + t.notMentioned + ": " + m.missing.joinToString(", ") { t.missing(it) } + "."
        return core + missing
    }
}
