package com.packetloss.samjho.speech

import com.packetloss.samjho.extract.Normalize
import com.packetloss.samjho.model.Hypothesis

/**
 * Turns a recogniser's raw N-best output into a clean list, best first.
 *
 * Measured on the iQOO I2501 (offline, en-US), the list holds 2 to 4 hypotheses that are nearly the
 * same sentence, and every confidence score is 0.00. Two things follow:
 *  - a "score" that is identical for every hypothesis says nothing, so it is dropped rather than
 *    shown as if it were a real confidence;
 *  - a hypothesis that differs only in capitals or punctuation adds no information, so it is dropped.
 */
object Hypotheses {

    fun build(texts: List<String>, scores: FloatArray?): List<Hypothesis> {
        val informative = scores != null && scores.size >= texts.size && scores.distinct().size > 1
        val seen = mutableSetOf<String>()
        val out = mutableListOf<Hypothesis>()
        texts.forEachIndexed { i, raw ->
            val text = raw.trim()
            if (text.isEmpty() || !seen.add(Normalize.text(text))) return@forEachIndexed
            val score = if (informative) scores!![i].takeIf { it >= 0f } else null
            out += Hypothesis(text, score)
        }
        return out
    }
}
