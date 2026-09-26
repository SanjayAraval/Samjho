package com.packetloss.samjho.scan

import com.packetloss.samjho.extract.Lexicon
import com.packetloss.samjho.extract.NameRepair
import com.packetloss.samjho.extract.Normalize
import com.packetloss.samjho.extract.RuleExtractor
import com.packetloss.samjho.model.Basis
import com.packetloss.samjho.model.Confirmation
import com.packetloss.samjho.model.Extraction
import com.packetloss.samjho.model.Medicine
import com.packetloss.samjho.model.PaperNote
import com.packetloss.samjho.model.Provenance

/**
 * Joins a consultation summary (the doctor's voice) with a scanned prescription (the paper).
 *
 * The rule is a division of labour: the paper gives the medicine NAMES, the doctor's words give everything
 * else. So this only ever changes which medicine a card is, or adds a nameless card. Timings, food and
 * duration are never taken from the paper, never guessed, and a medicine the paper adds has none.
 *
 * Only paper items the patient has CONFIRMED are used, so a suggestion nobody answered can never reach the
 * summary. In order, for each confirmed paper medicine:
 *
 * 1. **Agreement.** A spoken card for the same medicine is marked as confirmed from the prescription.
 * 2. **Paper wins.** A spoken card that was only a guess (unconfirmed) whose heard word sounds like the paper
 *    name becomes that medicine, confirmed, and keeps the word that was heard. A card the patient already
 *    answered, or a name the doctor said outright, is never overridden.
 * 3. **A line with dosing and no medicine.** If the doctor's dosing line has a word that sounds like the paper
 *    name (the "thrombison" line), the doctor's dosing is attached to that name.
 * 4. **Paper only.** Otherwise a new card "from prescription, not spoken" is added with no dosing, and the
 *    usual "not mentioned" flags show what the doctor never said.
 *
 * A spoken medicine the paper does not have is left exactly as it was.
 */
object PaperMerge {

    enum class Kind { AGREED, PAPER_WINS, ATTACHED_TO_DOSING_LINE, PAPER_ONLY }

    data class Change(val key: String, val kind: Kind, val heardAs: String?)

    data class Result(val extraction: Extraction, val changes: List<Change>) {
        fun count(kind: Kind) = changes.count { it.kind == kind }
    }

    fun merge(extraction: Extraction, paper: List<ScanItem>): Result {
        val wanted = paper.filter { it.confirmation == Confirmation.CONFIRMED }.distinctBy { it.key }
        if (wanted.isEmpty()) return Result(extraction, emptyList())

        val medicines = extraction.medicines.toMutableList()
        val changes = mutableListOf<Change>()
        val handled = mutableSetOf<String>()

        // 1. Agreement: the same medicine is already there.
        for (p in wanted) {
            var found = false
            medicines.forEachIndexed { i, m ->
                if (m.key == p.key) {
                    medicines[i] = m.copy(confirmation = Confirmation.CONFIRMED, paper = PaperNote(p.readAs))
                    found = true
                }
            }
            if (found) {
                handled += p.key
                changes += Change(p.key, Kind.AGREED, heardAs = null)
            }
        }

        // 2. Paper wins over a guess. Closest sound first, each spoken card and each paper name used once.
        class Pairing(val paper: ScanItem, val index: Int, val distance: Double)
        val pairings = buildList {
            for (p in wanted.filter { it.key !in handled }) {
                medicines.forEachIndexed { i, m ->
                    if (m.confirmation != Confirmation.UNCONFIRMED) return@forEachIndexed
                    val sounds = NameRepair.candidatesFor(m.name)
                    val near = sounds.firstOrNull { it.key == p.key } ?: return@forEachIndexed
                    // The paper only wins if it explains the heard word BETTER than speech's own guess did. A word
                    // that already sounds like its guess ("citrus" for cetirizine) is not taken over by another
                    // medicine that merely sounds a bit like it too.
                    val guess = sounds.firstOrNull { it.key == m.key }?.distance ?: 1.0
                    if (near.distance >= guess) return@forEachIndexed
                    add(Pairing(p, i, near.distance))
                }
            }
        }.sortedBy { it.distance }
        val used = mutableSetOf<Int>()
        for (pair in pairings) {
            if (pair.paper.key in handled || pair.index in used) continue
            val m = medicines[pair.index]
            medicines[pair.index] = m.copy(key = pair.paper.key, confirmation = Confirmation.CONFIRMED, paper = PaperNote(pair.paper.readAs))
            used += pair.index
            handled += pair.paper.key
            changes += Change(pair.paper.key, Kind.PAPER_WINS, heardAs = m.name)
        }

        // 3. The doctor gave dosing but the name was lost. Attach it to a paper name that the line sounds like.
        val stillOpen = wanted.filter { it.key !in handled }
        if (stillOpen.isNotEmpty()) {
            val working = extraction.copy(medicines = medicines.toList())
            class Attach(val paper: ScanItem, val line: com.packetloss.samjho.model.TranscriptLine, val heard: String, val distance: Double, val others: List<String>)
            val options = buildList {
                for (line in working.unnamedDosing) {
                    val onLine = NameRepair.candidatesFor(line.text)
                    for (p in stillOpen) {
                        val near = onLine.firstOrNull { it.key == p.key } ?: continue
                        add(Attach(p, line, near.heard, near.distance, onLine.map { it.key }.filter { it != p.key }))
                    }
                }
            }.sortedBy { it.distance }
            val usedLines = mutableSetOf<Int>()
            for (a in options) {
                if (a.paper.key in handled || a.line.index in usedLines) continue
                // The dosing is read from the doctor's line by the same rules as always. Nothing comes from the paper.
                val fromLine = RuleExtractor.medicineOnLine(
                    line = a.line,
                    name = a.heard,
                    key = a.paper.key,
                    basis = Basis.SOUNDS_LIKE,
                    provenance = Provenance.RULES,
                    candidates = a.others.take(NameRepair.MAX_CANDIDATES),
                )
                medicines += fromLine.copy(confirmation = Confirmation.CONFIRMED, paper = PaperNote(a.paper.readAs))
                usedLines += a.line.index
                handled += a.paper.key
                changes += Change(a.paper.key, Kind.ATTACHED_TO_DOSING_LINE, heardAs = a.heard)
            }
        }

        // 4. On the paper but never spoken: a new card with no dosing and no transcript line.
        for (p in wanted.filter { it.key !in handled }) {
            medicines += Medicine(
                name = Lexicon.display(p.key),
                key = p.key,
                basis = Basis.FROM_PRESCRIPTION,
                provenance = Provenance.PRESCRIPTION,
                confirmation = Confirmation.CONFIRMED,
                paper = PaperNote(p.readAs),
            )
            handled += p.key
            changes += Change(p.key, Kind.PAPER_ONLY, heardAs = null)
        }

        return Result(extraction.copy(medicines = medicines), changes)
    }

    /**
     * What the doctor's side of this card said, for "confirmed from prescription, heard as X". Null when the
     * card was not confirmed from paper, was never spoken, or the heard word is just the name again.
     */
    fun heardAs(m: Medicine): String? {
        if (m.paper == null || m.basis == Basis.FROM_PRESCRIPTION) return null
        val same = Normalize.text(m.name).replace(" ", "") == Normalize.text(Lexicon.display(m.key)).replace(" ", "")
        return if (same) null else m.name
    }
}
