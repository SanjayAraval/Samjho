package com.packetloss.samjho.scan

import com.packetloss.samjho.extract.NameRepair
import com.packetloss.samjho.extract.Normalize
import com.packetloss.samjho.extract.Phonetic
import com.packetloss.samjho.extract.RuleExtractor
import com.packetloss.samjho.llm.LlmEngine
import com.packetloss.samjho.model.Confirmation

/**
 * Layer 2 for the prescription scan: the same repair the speech path does, on words the camera misread.
 *
 * It does not have its own way of asking. It builds a shortlist with [NameRepair.candidatesFor], asks
 * through [NameRepair.choose] (one word out of the shortlist or UNKNOWN, strictly parsed, add-only), and
 * marks the result AI matched and unconfirmed, exactly as speech does. Only the source of the word differs.
 *
 * A word is only asked about when the rules matched nothing for it, it could be a medicine name at all
 * (the speech path's own filter drops grammar, symptoms, dosage forms and numbers), and it has a look-alike
 * in the lexicon. The model sees just that word and its shortlist, never the rest of the page.
 */
object ScanRepair {

    /** A page can hold dozens of unread words; asking about every one would be slow and mostly noise. */
    const val MAX_TARGETS = 6

    /** A word needs at least this many consonants left, or there is too little sound to compare. */
    private const val MIN_SKELETON = 4

    /**
     * How far a look-alike may be. Tighter than the speech cut-off (0.4): a dosing sentence backs up a loose
     * candidate, a lone word on a page does not, and a brand that is not in the lexicon should not be forced
     * onto the nearest unrelated name ("Metrogyl" is not omeprazole).
     */
    const val MAX_DISTANCE = 0.35

    /** A word (or a name split across a gap) as the camera read it, and the medicines it might be. */
    data class Target(val readAs: String, val candidates: List<NameRepair.Candidate>)

    /**
     * Words worth asking about, closest look-alike first. [existing] are the scan items already listed, whose
     * medicines are not offered again, mirroring how the speech path leaves out medicines it already found.
     *
     * The speech shortlist is loose because a dosing sentence gives it context. A page has none, so a word is
     * only asked about if it has enough consonants and shares the first consonant of a candidate (or is that
     * candidate with its first syllable clipped, as "thrombison" is of azithromycin). Neighbouring words are
     * never joined here: two ordinary words can join into something that looks like a drug ("City Clinic").
     */
    fun targets(lines: List<String>, existing: List<ScanItem>): List<Target> {
        val have = existing.map { it.key }.toSet()
        val seen = mutableSetOf<String>()
        val out = mutableListOf<Target>()

        for (line in lines) {
            for (word in RuleExtractor.nameableTokens(line)) {
                val normalized = word.normalized
                if (normalized in seen || !ScanMatcher.isWordLike(normalized)) continue
                if (ScanMatcher.isRecognised(word.display, normalized)) continue
                val skeleton = Phonetic.skeleton(normalized)
                if (skeleton.length < MIN_SKELETON) continue
                val candidates = NameRepair.candidatesFor(word.display, exclude = have)
                    .filter { it.distance <= MAX_DISTANCE && looksLike(skeleton, Phonetic.skeleton(Normalize.text(it.key))) }
                if (candidates.isEmpty()) continue
                seen += normalized
                out += Target(word.display, candidates)
            }
        }
        return out.sortedBy { it.candidates.first().distance }.take(MAX_TARGETS)
    }

    /** Same first consonant, or the medicine with its first syllable dropped and at most one slip after that. */
    private fun looksLike(word: String, medicine: String): Boolean {
        if (word.isEmpty() || medicine.isEmpty()) return false
        if (word[0] == medicine[0]) return true
        return word.length >= 5 && medicine.length >= 5 && Normalize.editDistance(word, medicine.drop(1)) <= 1
    }

    /**
     * Asks about each target and returns what came back valid, as unconfirmed suggestions marked AI matched.
     * Never modifies what is already on the scan: callers append with [ScanUi.withAdded].
     */
    fun repair(targets: List<Target>, llm: LlmEngine): List<ScanItem> {
        val out = LinkedHashMap<String, ScanItem>()
        for (target in targets) {
            val chosen = NameRepair.choose(llm, target.readAs, target.candidates, NameRepair.Source.OCR) ?: continue
            if (chosen.key in out) continue
            out[chosen.key] = ScanItem(
                id = 0,
                readAs = target.readAs,
                key = chosen.key,
                suggested = chosen.key,
                candidates = target.candidates.map { it.key }.filter { it != chosen.key },
                basis = ScanBasis.AI_MATCHED,
                confirmation = Confirmation.UNCONFIRMED,
            )
        }
        return out.values.toList()
    }

    /** Convenience for callers with a fixed page: work out the targets and ask about them. */
    fun repair(lines: List<String>, existing: List<ScanItem>, llm: LlmEngine): List<ScanItem> =
        repair(targets(lines, existing), llm)
}
