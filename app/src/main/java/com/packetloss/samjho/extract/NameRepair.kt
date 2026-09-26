package com.packetloss.samjho.extract

import com.packetloss.samjho.llm.LlmEngine
import com.packetloss.samjho.model.Basis
import com.packetloss.samjho.model.Extraction
import com.packetloss.samjho.model.Medicine
import com.packetloss.samjho.model.Provenance
import com.packetloss.samjho.model.TranscriptLine

/**
 * Layer 2: asks the language model to pick a medicine name, and nothing else.
 *
 * It only ever looks at a line that carries dosing words but that no item cites (so the rules
 * found no medicine there), and it is only offered the few lexicon medicines whose sound is
 * close to a word on that line. It must answer with exactly one of them or UNKNOWN. Anything
 * else, including a real medicine that was not on the list, is discarded, so the model cannot
 * introduce a name the sounds did not already point to. The dosing details still come from the
 * rules reading the line, never from the model.
 */
object NameRepair {

    const val UNKNOWN = "UNKNOWN"
    const val MAX_CANDIDATES = 5

    /**
     * Skeleton distance, 0 identical to 1 unrelated, beyond which a lexicon medicine is not a
     * candidate. Without a cut-off the model would always be handed five options and could
     * "match" any word, which is exactly the invention this layer exists to prevent.
     */
    const val MAX_DISTANCE = 0.4

    /** A lexicon medicine offered for a line, and the spoken word it was chosen for. */
    data class Candidate(val key: String, val heard: String, val distance: Double)

    data class Target(val line: TranscriptLine, val candidates: List<Candidate>)

    /** Lines worth asking about, each with its candidates. Lines with no plausible candidate are left out. */
    fun targets(extraction: Extraction): List<Target> {
        val already = extraction.medicines.map { it.key }.toSet()
        val covered = extraction.coveredLines
        return extraction.lines
            .filter { it.index !in covered && RuleExtractor.hasDosing(it.text) }
            .mapNotNull { line ->
                val candidates = candidatesFor(line.text, already)
                if (candidates.isEmpty()) null else Target(line, candidates)
            }
    }

    /** The closest lexicon medicines to any word (or joined pair of words) on the line, closest first. */
    fun candidatesFor(text: String, exclude: Set<String> = emptySet()): List<Candidate> {
        val words = RuleExtractor.nameableTokens(text)
        val all = Normalize.tokens(text)
        val units = buildList {
            words.forEach { add(it.normalized to it.display) }
            // Speech often splits one drug name in two ("citrus" + "in"), so adjacent words are also joined.
            all.zipWithNext().forEach { (a, b) ->
                if (a in words && Numbers.parse(b.normalized) == null) {
                    add((a.normalized + b.normalized) to "${a.display} ${b.display}")
                }
            }
        }

        val best = LinkedHashMap<String, Candidate>()
        for ((normalized, display) in units) {
            for (s in Lexicon.closest(normalized, MAX_CANDIDATES, MAX_DISTANCE)) {
                if (s.key in exclude) continue
                val existing = best[s.key]
                if (existing == null || s.distance < existing.distance) {
                    best[s.key] = Candidate(s.key, display, s.distance)
                }
            }
        }
        return best.values.sortedWith(compareBy({ it.distance }, { it.key })).take(MAX_CANDIDATES)
    }

    fun prompt(target: Target): String = buildString {
        appendLine("Match a spoken medicine name to a fixed list. Do not give medical advice.")
        appendLine("Sentence: \"${target.line.text}\"")
        appendLine("Candidates:")
        target.candidates.forEach { appendLine("- ${it.key} (sounds like \"${it.heard}\")") }
        appendLine("Answer with exactly one candidate name from the list, or $UNKNOWN.")
        append("Output only that one word and nothing else.")
    }

    /**
     * The one candidate the reply names, or null. Strict on purpose: after trimming whitespace, quotes
     * and a trailing full stop, the WHOLE reply must equal a candidate (any case) or it is discarded.
     */
    fun parse(reply: String?, target: Target): Candidate? {
        val cleaned = reply.orEmpty().trim().trim('"', '\'', '`', '*').trimEnd('.').trim()
        if (cleaned.isEmpty() || cleaned.equals(UNKNOWN, ignoreCase = true)) return null
        return target.candidates.firstOrNull { it.key.equals(cleaned, ignoreCase = true) }
    }

    /**
     * Asks about each target line and returns what came back valid, as unconfirmed items marked
     * as AI matched. Never modifies [extraction]: callers merge with [Extraction.withAdded].
     */
    fun repair(extraction: Extraction, llm: LlmEngine): List<Medicine> {
        val out = LinkedHashMap<String, Medicine>()
        for (target in targets(extraction)) {
            val reply = try {
                llm.complete(prompt(target))
            } catch (_: Throwable) {
                null
            }
            val chosen = parse(reply, target) ?: continue
            if (chosen.key in out) continue
            out[chosen.key] = RuleExtractor.medicineOnLine(
                line = target.line,
                name = chosen.heard,
                key = chosen.key,
                basis = Basis.AI_MATCHED,
                provenance = Provenance.AI,
                candidates = target.candidates.map { it.key },
            )
        }
        return out.values.toList()
    }
}
