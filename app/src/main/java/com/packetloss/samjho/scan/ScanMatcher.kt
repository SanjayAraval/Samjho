package com.packetloss.samjho.scan

import com.packetloss.samjho.extract.Lexicon
import com.packetloss.samjho.extract.Normalize
import com.packetloss.samjho.extract.Phonetic
import com.packetloss.samjho.model.Confirmation

/**
 * Matches the words a camera read off a prescription against the medicine lexicon.
 *
 * It reuses the extractor's own matching (exact forms, the small edit distance the speech matcher
 * allows, and the consonant skeleton) rather than a second matcher. The skeleton suits OCR as well as
 * speech: a character-level slip ("Paracetarnol", "Amox1cillin") barely moves the consonants.
 *
 * Every match is only a suggestion. This never creates a confirmed medicine and never adds one that no
 * word on the page resembles; a page with nothing recognisable simply returns nothing, and the patient
 * picks from the full list instead.
 */
object ScanMatcher {

    /** How far apart two consonant skeletons may be (0 identical, 1 unrelated) for a "sounds like" match. */
    const val MAX_SOUND_DISTANCE = 0.3

    /**
     * Two neighbouring words read as one are only accepted this close. Two ordinary words can join into
     * something that sounds like a medicine by chance ("City Clinic" against a salbutamol brand), so a
     * join has to be nearly exact.
     */
    const val MAX_JOINED_SOUND_DISTANCE = 0.15

    /** A wider net used only for the names offered under Choose another. */
    private const val CANDIDATE_DISTANCE = 0.6
    private const val MAX_CANDIDATES = 5

    /** Short words carry too little to compare, so a fuzzy match needs at least this many consonants. */
    private const val MIN_SKELETON = 5

    private data class Hit(val readAs: String, val key: String, val basis: ScanBasis, val distance: Double, val order: Int)

    /** Lines as read, one string per printed or written line. Returns suggestions, best first. */
    fun match(lines: List<String>): List<ScanItem> {
        val best = LinkedHashMap<String, Hit>()
        var order = 0
        for (line in lines) {
            val tokens = Normalize.tokens(line)
            // OCR often splits one long name across a gap ("Para cetamol"), so joined neighbours are tried too.
            class Unit(val display: String, val normalized: String, val joined: Boolean)
            val units = buildList {
                tokens.forEach { add(Unit(it.display, it.normalized, joined = false)) }
                tokens.zipWithNext().forEach { (a, b) ->
                    if (isWordLike(a.normalized) && isWordLike(b.normalized)) {
                        add(Unit("${a.display} ${b.display}", a.normalized + b.normalized, joined = true))
                    }
                }
            }
            for (unit in units) {
                val hit = classify(unit.display, unit.normalized, order++, unit.joined) ?: continue
                val existing = best[hit.key]
                if (existing == null || rank(hit) < rank(existing)) best[hit.key] = hit
            }
        }
        return best.values
            .sortedWith(compareBy({ it.basis.ordinal }, { it.distance }, { it.order }))
            .mapIndexed { i, h ->
                ScanItem(
                    id = i,
                    readAs = h.readAs,
                    key = h.key,
                    suggested = h.key,
                    candidates = candidatesFor(h.key, h.readAs),
                    basis = h.basis,
                    confirmation = Confirmation.UNCONFIRMED,
                )
            }
    }

    private fun rank(h: Hit) = h.basis.ordinal * 10 + h.distance

    private fun classify(display: String, normalized: String, order: Int, joined: Boolean): Hit? {
        // A known spelling, at any length: "dolo", "iron", "ors" are short but unmistakable.
        Lexicon.exact(normalized)?.let { return Hit(display, it, ScanBasis.EXACT, 0.0, order) }

        if (!isWordLike(normalized)) return null
        if (normalized.length < 5) return null

        // A small character slip, which is what an OCR error usually is.
        Lexicon.match(normalized)?.let { return Hit(display, it, ScanBasis.SPELLING, 0.05, order) }

        // Failing that, the same consonants with different vowels or a swapped look-alike letter.
        if (Phonetic.skeleton(normalized).length < MIN_SKELETON) return null
        val limit = if (joined) MAX_JOINED_SOUND_DISTANCE else MAX_SOUND_DISTANCE
        val near = Lexicon.closest(normalized, 1, limit).firstOrNull() ?: return null
        return Hit(display, near.key, ScanBasis.SOUNDS_LIKE, near.distance, order)
    }

    /** Names offered first under Choose another: the closest-sounding medicines other than the suggestion. */
    private fun candidatesFor(key: String, readAs: String): List<String> {
        val normalized = Normalize.text(readAs).replace(" ", "")
        return Lexicon.closest(normalized, MAX_CANDIDATES + 1, CANDIDATE_DISTANCE)
            .map { it.key }
            .filter { it != key }
            .take(MAX_CANDIDATES)
    }

    /** A token that could be a drug name: it starts with a letter and is mostly letters, not "500mg" or "1x3". */
    private fun isWordLike(normalized: String): Boolean {
        if (normalized.isEmpty() || !normalized.first().isLetter()) return false
        val letters = normalized.count { it.isLetter() || Character.getType(it).let { t -> t == Character.NON_SPACING_MARK.toInt() || t == Character.COMBINING_SPACING_MARK.toInt() } }
        return letters >= 3 && letters * 10 >= normalized.length * 6
    }
}
