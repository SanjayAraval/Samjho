package com.packetloss.samjho.share

import com.packetloss.samjho.extract.Lexicon
import com.packetloss.samjho.model.Basis
import com.packetloss.samjho.model.Extraction
import com.packetloss.samjho.model.Medicine
import com.packetloss.samjho.ui.Strings

/** How a section is tinted when drawn. It carries meaning, so an image and a PDF agree on it. */
enum class Tone { NEUTRAL, AVOID, WARNING, NOTICE }

/** One item in a section: a headline, optional detail chips, and an optional small note. */
data class Entry(
    val headline: String,
    val details: List<String> = emptyList(),
    val note: String? = null,
)

data class Section(val title: String, val tone: Tone, val entries: List<Entry>)

/** Everything a shared summary says, independent of how it is drawn. */
data class SummaryDocument(
    val title: String,
    val dateLine: String,
    val sections: List<Section>,
    val footer: String,
)

/**
 * The summary someone without the app will read, so it is stricter than the result screen: they cannot
 * tap Yes or No. Only medicines the recogniser produced outright, or that the patient confirmed, are
 * listed as medicines. An unconfirmed name goes in its own "not confirmed, please check" section under
 * the words that were actually heard; a dismissed one is left out. Anything the doctor did not say
 * stays marked "not mentioned", and nothing is added that the result screen does not show.
 */
object SummaryBuilder {

    fun build(e: Extraction, t: Strings, dateLine: String): SummaryDocument {
        val sections = buildList {
            if (e.diagnosis.isNotEmpty()) {
                add(Section(t.diagnosis, Tone.NEUTRAL, e.diagnosis.map { Entry(it.text) }))
            }

            val active = e.medicines.filterNot { it.isRejected }
            val listed = active.filterNot { it.isUnconfirmed }
            val unconfirmed = active.filter { it.isUnconfirmed }

            if (listed.isNotEmpty()) {
                add(Section(t.medicines, Tone.NEUTRAL, listed.map { medicine(it, t) }))
            }

            val notConfirmed = unconfirmed.map { notConfirmed(it, t) } + e.unnamedDosing.map {
                Entry(headline = it.text, note = t.unnamedDosingHint)
            }
            if (notConfirmed.isNotEmpty()) {
                add(Section(t.notConfirmedTitle, Tone.NOTICE, notConfirmed))
            }

            if (e.avoid.isNotEmpty()) {
                add(Section(t.avoid, Tone.AVOID, e.avoid.map { Entry(it.text) }))
            }
            if (e.warnings.isNotEmpty()) {
                add(Section(t.warnings, Tone.WARNING, e.warnings.map { Entry(it.text) }))
            }
            e.followUp?.let { f ->
                add(Section(t.followUp, Tone.NEUTRAL, listOf(Entry(f.inDays?.let { t.followUpIn(it) } ?: f.text, note = f.text.takeIf { f.inDays != null }))))
            }
            if (e.missingSections.isNotEmpty()) {
                add(
                    Section(
                        t.notMentioned, Tone.NEUTRAL,
                        listOf(Entry(e.missingSections.joinToString(", ") { t.missingSection(it) }, note = t.askDoctor)),
                    ),
                )
            }
        }
        return SummaryDocument(t.summaryTitle, dateLine, sections, t.summaryFooter)
    }

    private fun medicine(m: Medicine, t: Strings): Entry {
        val details = buildList {
            m.timesPerDay?.let { add(t.timesPerDay(it)) }
            m.doseCount?.let { add(t.dose(it)) }
            m.timesOfDay.forEach { add(t.timeOfDay(it)) }
            m.foodRelation?.let { add(t.food(it)) }
            m.durationDays?.let { add(t.durationDays(it)) }
        }
        val name = if (m.basis != Basis.HEARD) Lexicon.display(m.key) else m.name
        val note = if (m.missing.isEmpty()) null
        else t.notMentioned + ": " + m.missing.joinToString(", ") { t.missing(it) }
        return Entry(headline = name, details = details, note = note)
    }

    private fun notConfirmed(m: Medicine, t: Strings): Entry {
        val heard = "${t.heardAs} “${m.name}”"
        val headline = if (Lexicon.isKey(m.key)) "$heard · ${t.possibly}: ${Lexicon.display(m.key)}" else "$heard · ${t.isThisAMedicine}"
        val details = buildList {
            m.timesPerDay?.let { add(t.timesPerDay(it)) }
            m.doseCount?.let { add(t.dose(it)) }
            m.timesOfDay.forEach { add(t.timeOfDay(it)) }
            m.foodRelation?.let { add(t.food(it)) }
            m.durationDays?.let { add(t.durationDays(it)) }
        }
        return Entry(headline = headline, details = details, note = t.notConfirmedAsk)
    }
}
