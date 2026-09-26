package com.packetloss.samjho.extract

/**
 * A consonant skeleton: what is left of a word once vowels and the sounds a small speech model
 * routinely confuses are folded together. It lets "pracite" be compared with "paracetamol" and
 * "dollar" with "dolo", in Latin or Devanagari, because both collapse to the same short string.
 *
 * Folding: vowels and h/y dropped; c/k/q one sound (soft c before e/i/y is s); z/s/j/sh one
 * sound; ph is f; th/dh/kh/gh/bh are t/d/k/g/b; repeated sounds collapse.
 *
 * This is only ever used to RECOGNISE a spoken word against the medicine lexicon, and only where
 * the caller has already seen dosing context next to it. It never invents a medicine.
 */
object Phonetic {

    private val DEVANAGARI: Map<Char, Char> = buildMap {
        "कख".forEach { put(it, 'k') }
        "गघ".forEach { put(it, 'g') }
        "चछ".forEach { put(it, 'c') }
        // ज carries z as well as j in loanwords, and Normalize drops the nukta that told them apart.
        "जझशषस".forEach { put(it, 's') }
        "टठतथ".forEach { put(it, 't') }
        "डढदध".forEach { put(it, 'd') }
        "णनंञङ".forEach { put(it, 'n') }
        put('प', 'p')
        put('फ', 'f')
        "बभ".forEach { put(it, 'b') }
        put('म', 'm')
        put('र', 'r')
        put('ल', 'l')
        put('व', 'v')
    }

    private val DIGRAPHS = mapOf(
        "ph" to 'f', "th" to 't', "kh" to 'k', "gh" to 'g', "dh" to 'd',
        "bh" to 'b', "sh" to 's', "ch" to 'c', "ck" to 'k',
    )

    private const val KEEP = "bdfgklmnprstv"

    /** Expects text already passed through [Normalize.text]. */
    fun skeleton(normalized: String): String {
        val out = StringBuilder()
        fun emit(c: Char) {
            if (out.isEmpty() || out.last() != c) out.append(c)
        }

        var i = 0
        while (i < normalized.length) {
            val ch = normalized[i]
            val pair = if (i + 1 < normalized.length) normalized.substring(i, i + 2) else ""
            val digraph = DIGRAPHS[pair]
            when {
                digraph != null -> {
                    emit(digraph)
                    i += 2
                    continue
                }
                ch in 'ऀ'..'ॿ' -> DEVANAGARI[ch]?.let(::emit)
                ch == 'c' -> {
                    val next = normalized.getOrNull(i + 1)
                    emit(if (next == 'e' || next == 'i' || next == 'y') 's' else 'k')
                }
                ch == 'q' -> emit('k')
                ch == 'z' || ch == 'j' -> emit('s')
                ch == 'w' -> emit('v')
                ch in KEEP -> emit(ch)
            }
            i++
        }
        return out.toString()
    }
}
