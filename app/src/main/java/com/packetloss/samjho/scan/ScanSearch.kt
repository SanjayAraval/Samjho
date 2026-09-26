package com.packetloss.samjho.scan

import com.packetloss.samjho.extract.Lexicon
import com.packetloss.samjho.extract.Normalize

/**
 * The searchable list of every medicine in the lexicon, for when the camera found nothing useful or found
 * the wrong thing. It has to be quick: with nothing typed it shows everything, and a few letters, even a
 * misspelt or Devanagari few, bring the right name to the top.
 */
object ScanSearch {

    /** A lexicon medicine, as shown in the list. */
    data class Entry(val key: String, val label: String)

    fun all(): List<Entry> = Lexicon.keys().map { Entry(it, Lexicon.display(it)) }.sortedBy { it.label.lowercase() }

    /**
     * Medicines to show for [query]. Empty query: [first] (the card's close matches, if any), then the rest
     * alphabetically. Otherwise names that start with the query, then names that contain it, then the ones
     * that only sound like it. Brand names count: typing "dolo" finds Paracetamol.
     */
    fun filter(query: String, first: List<String> = emptyList()): List<Entry> {
        val everything = all()
        val q = Normalize.text(query).replace(" ", "")
        if (q.isEmpty()) {
            val head = first.distinct().mapNotNull { key -> everything.firstOrNull { it.key == key } }
            return head + everything.filter { e -> head.none { it.key == e.key } }
        }

        val starts = LinkedHashSet<String>()
        val contains = LinkedHashSet<String>()
        for (e in everything) {
            val name = Normalize.text(e.label).replace(" ", "")
            when {
                name.startsWith(q) -> starts += e.key
                name.contains(q) -> contains += e.key
            }
        }
        // A brand or another spelling of a medicine ("dolo", "crocin") leads to its key.
        Lexicon.exact(q)?.let { starts += it }

        val sounds = LinkedHashSet<String>()
        if (q.length >= 3) Lexicon.closest(q, 5, 0.5).forEach { sounds += it.key }

        val order = LinkedHashSet<String>().apply { addAll(starts); addAll(contains); addAll(sounds) }
        return order.mapNotNull { key -> everything.firstOrNull { it.key == key } }
    }
}
