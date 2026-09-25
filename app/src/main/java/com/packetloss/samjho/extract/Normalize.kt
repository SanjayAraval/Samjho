package com.packetloss.samjho.extract

import java.text.Normalizer

/**
 * Folds the spelling variation that offline speech models produce, so the rules can match on a
 * single stable form. Nothing here changes meaning: it only removes cosmetic differences
 * (chandrabindu vs anusvara, nukta, Devanagari vs ASCII digits, case, spacing).
 */
object Normalize {

    private const val ZWJ = '‍'
    private const val ZWNJ = '‌'
    private const val CHANDRABINDU = 'ँ'
    private const val ANUSVARA = 'ं'
    private const val NUKTA = '़'

    private const val DEVANAGARI_ZERO = '०'
    private const val DEVANAGARI_NINE = '९'

    /** Danda, double danda and the punctuation that surrounds spoken clauses. */
    private val PUNCTUATION = setOf(
        '।', '॥', '.', ',', ';', ':', '!', '?', '"', '\'', '(', ')', '[', ']', '—', '–',
    )

    fun text(raw: String): String {
        val decomposed = Normalizer.normalize(raw, Normalizer.Form.NFC)
        val sb = StringBuilder(decomposed.length)
        for (ch in decomposed) {
            when {
                ch == ZWJ || ch == ZWNJ || ch == NUKTA -> Unit
                ch == CHANDRABINDU -> sb.append(ANUSVARA)
                // Kept: a comma is how a doctor separates a list of things to avoid.
                ch == ',' -> sb.append(',')
                ch in DEVANAGARI_ZERO..DEVANAGARI_NINE ->
                    sb.append(('0' + (ch - DEVANAGARI_ZERO)))
                ch in PUNCTUATION -> sb.append(' ')
                ch.isWhitespace() -> sb.append(' ')
                else -> sb.append(ch.lowercaseChar())
            }
        }
        return sb.toString().trim().replace(WHITESPACE, " ")
    }

    private val WHITESPACE = Regex("\\s+")

    /** Splits a line into display tokens paired with their normalised form. */
    fun tokens(raw: String): List<Token> {
        val out = mutableListOf<Token>()
        var start = -1
        for (i in raw.indices) {
            val ch = raw[i]
            val isBreak = ch.isWhitespace() || ch in PUNCTUATION
            if (isBreak) {
                if (start >= 0) {
                    out.addToken(raw, start, i)
                    start = -1
                }
            } else if (start < 0) {
                start = i
            }
        }
        if (start >= 0) out.addToken(raw, start, raw.length)
        return out
    }

    private fun MutableList<Token>.addToken(raw: String, start: Int, end: Int) {
        val display = raw.substring(start, end)
        val normalized = text(display)
        if (normalized.isNotEmpty()) add(Token(display, normalized, start, end))
    }

    data class Token(val display: String, val normalized: String, val start: Int, val end: Int)

    /**
     * Levenshtein distance, capped for speed. Used only to absorb small speech-recognition slips
     * in medicine names that are already in the lexicon; never to invent a name.
     */
    fun editDistance(a: String, b: String): Int {
        if (a == b) return 0
        if (a.isEmpty()) return b.length
        if (b.isEmpty()) return a.length
        var prev = IntArray(b.length + 1) { it }
        var cur = IntArray(b.length + 1)
        for (i in 1..a.length) {
            cur[0] = i
            for (j in 1..b.length) {
                val cost = if (a[i - 1] == b[j - 1]) 0 else 1
                cur[j] = minOf(cur[j - 1] + 1, prev[j] + 1, prev[j - 1] + cost)
            }
            val swap = prev; prev = cur; cur = swap
        }
        return prev[b.length]
    }
}
