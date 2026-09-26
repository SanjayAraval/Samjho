package com.packetloss.samjho.extract

import com.packetloss.samjho.model.Basis
import com.packetloss.samjho.model.Confirmation
import com.packetloss.samjho.model.Extraction
import com.packetloss.samjho.model.FollowUp
import com.packetloss.samjho.model.FoodRelation
import com.packetloss.samjho.model.Language
import com.packetloss.samjho.model.Medicine
import com.packetloss.samjho.model.Note
import com.packetloss.samjho.model.Provenance
import com.packetloss.samjho.model.TimeOfDay
import com.packetloss.samjho.model.TranscriptLine
import com.packetloss.samjho.model.Utterance

/**
 * Deterministic, offline, no model: turns a transcript into instructions by pattern alone.
 *
 * Three properties this file exists to guarantee:
 *  1. It is fast. Pure regex over a handful of lines, so the result screen never waits.
 *  2. It only restates. Every item carries the transcript line indices it came from, and a
 *     medicine is shown under the doctor's own word, not a canonical drug name.
 *  3. It does not guess. A detail the doctor never said stays null and is surfaced as
 *     "not mentioned" rather than filled in with something plausible.
 *
 * Both the Hindi and the English rule sets run on every line regardless of the detected
 * language, because real consultations mix the two inside a single sentence.
 */
object RuleExtractor {

    private val N = "(?:${Numbers.GROUP})"

    // Java's \b is ASCII-only, so word edges in Devanagari are spelled out: no letter or vowel sign
    // may touch the match on that side.
    private const val WORD_START = "(?<![\\p{L}\\p{M}])"
    private const val WORD_END = "(?![\\p{L}\\p{M}])"

    fun extract(rawLines: List<String>): Extraction = extractUtterances(rawLines.map { Utterance(it) })

    /**
     * [utterances] carry each sentence's N-best hypotheses. The top one is the displayed line;
     * lower ones are only ever consulted to find a medicine the top one missed.
     */
    fun extractUtterances(utterances: List<Utterance>): Extraction {
        val entries = utterances.map { it.copy(text = it.text.trim()) }.filter { it.text.isNotEmpty() }
        val lines = entries.mapIndexed { i, u -> TranscriptLine(i, u.text) }

        val medicines = LinkedHashMap<String, Medicine>()
        val diagnosis = mutableListOf<Note>()
        val avoid = mutableListOf<Note>()
        val warnings = mutableListOf<Note>()
        var followUp: FollowUp? = null

        for (line in lines) {
            val n = Normalize.text(line.text)
            val tokens = Normalize.tokens(line.text)
            val here = listOf(line.index)

            val isWarning = looksLikeWarning(n)
            if (isWarning) warnings += Note(line.text, here)

            val avoidItems = if (isWarning) emptyList() else avoidItems(n)
            avoidItems.forEach { avoid += Note(it, here) }

            if (!isWarning && avoidItems.isEmpty()) {
                diagnosisIn(n)?.let { diagnosis += Note(it, here) }
            }

            if (followUp == null) followUp = followUpIn(n, line)

            val top = medicinesOn(tokens)
            val found = if (top.any { Lexicon.isKey(it.key) }) top else top + alternateHearings(entries[line.index], top)
            if (found.isNotEmpty()) {
                val attrs = attributesIn(n)
                for (f in found) {
                    val slot = mergeKey(f)
                    val existing = medicines[slot]
                    medicines[slot] = if (existing == null) {
                        newMedicine(f, attrs, here)
                    } else {
                        val clearer = existing.basis != Basis.HEARD && f.basis == Basis.HEARD
                        existing.copy(
                            name = if (clearer) f.name else existing.name,
                            basis = if (clearer) Basis.HEARD else existing.basis,
                            hypothesis = if (clearer) null else existing.hypothesis,
                            confirmation = if (clearer) Confirmation.NOT_NEEDED else existing.confirmation,
                            timesPerDay = existing.timesPerDay ?: attrs.timesPerDay,
                            timesOfDay = existing.timesOfDay.ifEmpty { attrs.timesOfDay },
                            doseCount = existing.doseCount ?: attrs.doseCount,
                            foodRelation = existing.foodRelation ?: attrs.foodRelation,
                            durationDays = existing.durationDays ?: attrs.durationDays,
                            sourceLines = (existing.sourceLines + here).distinct(),
                        )
                    }
                }
            }
        }

        return Extraction(
            language = detectLanguage(lines.map { it.text }),
            lines = lines,
            medicines = medicines.values.toList(),
            diagnosis = diagnosis,
            avoid = avoid,
            warnings = warnings,
            followUp = followUp,
            dosingLines = lines.filter { hasDosing(it.text) }.map { it.index }.toSet(),
        )
    }

    fun detectLanguage(rawLines: List<String>): Language {
        var devanagari = 0
        var latin = 0
        rawLines.forEach { l ->
            l.forEach { ch ->
                when {
                    ch in 'ऀ'..'ॿ' -> devanagari++
                    ch.isLetter() && ch.code < 128 -> latin++
                }
            }
        }
        return if (devanagari >= latin) Language.HINDI else Language.ENGLISH
    }

    // ---------------------------------------------------------------- medicines

    private data class Found(val key: String, val name: String, val basis: Basis, val hypothesis: Int? = null)

    /**
     * How sure a spoken word's identity is: only a word spelled exactly like a known form counts as
     * heard outright. A word that is merely a slip or a sound away from one is [Basis.SOUNDS_LIKE]
     * and must be confirmed by the patient.
     */
    private fun identify(normalized: String): Pair<String, Basis>? {
        Lexicon.exact(normalized)?.let { return it to Basis.HEARD }
        Lexicon.match(normalized)?.let { return it to Basis.SOUNDS_LIKE }
        return null
    }

    /**
     * Repeat mentions of one medicine share a card, but a brand and its generic do not: "paracetamol
     * three times a day" and "Dolo if the fever comes back" are separate instructions, and merging
     * them would attach one's schedule to the other. Every generic spelling, in any script, and every
     * inferred (sounds-like) mention count as the generic.
     */
    private fun mergeKey(f: Found): String {
        val brand = if (f.basis == Basis.HEARD) Lexicon.brand(Normalize.text(f.name)) else null
        return f.key + "/" + (brand ?: "generic")
    }

    private fun newMedicine(f: Found, attrs: Attributes, lines: List<Int>) = Medicine(
        name = f.name,
        key = f.key,
        timesPerDay = attrs.timesPerDay,
        timesOfDay = attrs.timesOfDay,
        doseCount = attrs.doseCount,
        foodRelation = attrs.foodRelation,
        durationDays = attrs.durationDays,
        sourceLines = lines,
        basis = f.basis,
        hypothesis = f.hypothesis,
        // Only a word spelled like a known medicine is taken as heard. An unknown word that merely sits
        // next to "tablet" ("throw medicine", "egg 1 tablet") is not evidence of a drug name, so it waits
        // for the patient like any other guess.
        confirmation = if (f.basis == Basis.HEARD && Lexicon.isKey(f.key)) Confirmation.NOT_NEEDED else Confirmation.UNCONFIRMED,
        candidates = if (f.basis == Basis.HEARD && Lexicon.isKey(f.key)) emptyList() else candidatesFor(f),
    )

    /**
     * For "choose another": the matched lexicon medicine first (when there is one), then the nearest
     * sound-alikes to the word that was heard.
     */
    private fun candidatesFor(f: Found): List<String> {
        val own = if (Lexicon.isKey(f.key)) listOf(f.key) else emptyList()
        return (own + Lexicon.closest(Normalize.text(f.name), 5).map { it.key }).distinct().take(5)
    }

    /** The patient talking about themselves ("I have had fever for two days"), which is never dosing. */
    private val PATIENT_SPEECH = Regex("(?:^|\\s)(?:i|my|me|मुझे|मेरा|मेरी|मैं)(?:\\s|$)")

    /**
     * True when the words read as a dosing instruction: a stated frequency, or at least two kinds of
     * dosing detail (a time, a dose, food, a duration). One kind alone is not enough, because "for
     * two days" or "at night" is just as often a symptom ("I have had fever for two days"), and
     * first-person speech is the patient describing themselves, not the doctor's instruction.
     */
    internal fun hasDosing(text: String): Boolean {
        val n = Normalize.text(text)
        if (PATIENT_SPEECH.containsMatchIn(n)) return false
        val a = attributesIn(n)
        // A dosage form ("tablet", "syrup", "गोली") is itself a dosing signal: "tablet at night" is an
        // instruction even when the drug's name was lost ("Once it is in tablet at night before sleeping").
        val hasForm = Normalize.tokens(text).any { it.normalized in DOSAGE_FORMS }
        val kinds = listOf(
            a.timesOfDay.isNotEmpty(), a.doseCount != null, a.foodRelation != null, a.durationDays != null, hasForm,
        ).count { it }
        return a.timesPerDay != null || kinds >= 2
    }

    /** Words in [text] that could be a medicine name at all: not grammar, symptoms, numbers or forms. */
    internal fun nameableTokens(text: String): List<Normalize.Token> =
        Normalize.tokens(text).filterNot { blocked(it) }

    /** Builds the medicine for an item found on a single line, with its dosing read from that line. */
    internal fun medicineOnLine(
        line: TranscriptLine,
        name: String,
        key: String,
        basis: Basis,
        provenance: Provenance,
        candidates: List<String>,
    ): Medicine {
        val attrs = attributesIn(Normalize.text(line.text))
        return newMedicine(Found(key, name, basis), attrs, listOf(line.index)).copy(
            provenance = provenance,
            candidates = candidates,
        )
    }

    /**
     * Lower-ranked hearings of the same words, searched only when the top hearing named no known
     * medicine. A hit needs a confident lexicon match (an exact or near-exact spelling, or an
     * identical consonant skeleton) with dosing words beside it in that same hearing; nothing
     * else is accepted, so a stray N-best word can't become a medicine.
     */
    private fun alternateHearings(utterance: Utterance, alreadyFound: List<Found>): List<Found> {
        val have = alreadyFound.map { it.key }.toSet()
        // Only the best-ranked hypothesis that yields anything is used, never a mix of several:
        // different hearings of one sentence must not add up to several different medicines.
        for ((k, hypothesis) in utterance.hypotheses.withIndex()) {
            if (k == 0) continue
            val tokens = Normalize.tokens(hypothesis.text)
            val hits = LinkedHashMap<String, Found>()
            tokens.forEachIndexed { i, t ->
                if (blocked(t) || !nearDosing(tokens, i)) return@forEachIndexed
                val key = Lexicon.exact(t.normalized)
                    ?: Lexicon.match(t.normalized)
                    ?: Lexicon.matchPhonetic(t.normalized, maxRank = 0)
                    ?: return@forEachIndexed
                if (key !in have) hits.putIfAbsent(key, Found(key, t.display, Basis.ALTERNATE_HEARING, hypothesis = k))
            }
            if (hits.isNotEmpty()) return hits.values.toList()
        }
        return emptyList()
    }

    private val DOSAGE_FORMS = setOf(
        "गोली", "गोलियां", "गोलिया", "टेबलेट", "टैबलेट", "कैप्सूल", "सिरप", "सीरप",
        "दवा", "दवाई", "इंजेक्शन", "ड्रॉप", "ड्रॉप्स", "ड्रॉप्", "चम्मच",
        "tablet", "tablets", "tab", "tabs", "capsule", "capsules", "cap",
        "syrup", "injection", "drops", "medicine", "pill", "pills",
    )

    /** Words that may sit between the medicine name and its dosage form. */
    private val CONNECTORS = setOf("की", "का", "के", "वाली", "वाला", "of", "the", "a", "an")

    /**
     * Words that must never become a medicine name. Symptoms, verbs, timing and grammar all live
     * next to "गोली"/"tablet" in ordinary speech, so without this the phrase rules would happily
     * report "fever" as a drug.
     */
    private val NOT_A_MEDICINE = setOf(
        // Hindi grammar and timing
        "और", "या", "में", "से", "को", "है", "हैं", "हो", "था", "थी", "थे", "यह", "ये", "वह", "वो",
        "आप", "आपको", "आपका", "आपकी", "मैं", "मुझे", "तुम", "इस", "उस", "अब", "फिर", "तो", "भी",
        "ही", "न", "नहीं", "मत", "पर", "लेकिन", "क्योंकि", "अगर", "यदि", "जब", "कोई", "कुछ",
        "दिन", "रात", "सुबह", "शाम", "दोपहर", "बाद", "पहले", "तक", "साथ", "बार", "रोज", "रोजाना",
        "प्रतिदिन", "हर", "सोने", "समय", "वक्त",
        // Hindi verbs and food
        "खाना", "खाने", "खाएं", "खाइए", "खा", "भोजन", "पेट", "खाली", "पानी", "दूध", "चाय",
        "लेना", "लेनी", "लेने", "लीजिए", "दीजिए", "देना", "करना", "करें", "जाना", "आना", "पीना",
        "पिएं", "रखना", "चाहिए", "होगी", "होगा",
        // Hindi symptoms and conditions
        "बुखार", "दर्द", "खांसी", "खासी", "सर्दी", "जुकाम", "उल्टी", "दस्त", "गैस", "एसिडिटी",
        "कमजोरी", "थकान", "सूजन", "घाव", "चोट", "बीपी", "शुगर", "डायबिटीज", "इन्फेक्शन",
        "संक्रमण", "एलर्जी", "वायरल", "बैक्टीरियल", "गले", "गला", "सिर", "पेटदर्द", "सांस",
        // Hindi pleasantries and generic
        "नमस्ते", "धन्यवाद", "शुक्रिया", "ठीक", "अच्छा", "ज्यादा", "कम", "थोड़ा", "बहुत",
        "गर्म", "ठंडा", "तला", "हुआ", "चीज", "चीजें", "डॉक्टर", "साहब", "अस्पताल", "मात्रा", "खुराक",
        // English
        "the", "and", "or", "in", "of", "to", "for", "with", "from", "is", "are", "was", "you",
        "your", "me", "this", "that", "it", "at", "on", "after", "before", "day", "days", "night",
        "morning", "afternoon", "evening", "bedtime", "time", "times", "daily", "week", "weeks",
        "month", "months", "food", "meal", "meals", "stomach", "empty", "water", "milk", "take",
        "taking", "have", "has", "eat", "eating", "drink", "go", "come", "doctor", "hospital",
        "dose", "fever", "pain", "cough", "cold", "throat", "headache", "vomiting", "loose",
        "motions", "gas", "acidity", "weakness", "infection", "allergy", "viral", "bacterial",
        "please", "ok", "okay", "some", "any", "every", "next", "also",
    ) + DOSAGE_FORMS

    private fun blocked(t: Normalize.Token): Boolean =
        t.normalized in NOT_A_MEDICINE || Numbers.parse(t.normalized) != null

    private fun skippable(t: Normalize.Token): Boolean =
        t.normalized in CONNECTORS || Numbers.parse(t.normalized) != null

    private fun medicinesOn(tokens: List<Normalize.Token>): List<Found> {
        val out = LinkedHashMap<String, Found>()

        tokens.forEach { t ->
            if (!blocked(t)) {
                identify(t.normalized)?.let { (key, basis) ->
                    out.putIfAbsent(key, Found(key, t.display, basis))
                }
            }
        }

        tokens.forEachIndexed { u, t ->
            if (t.normalized !in DOSAGE_FORMS) return@forEachIndexed
            // "tablet of X" names X after the form word; "X tablet" names it before. Without this,
            // speech that mishears the word before ("egg 1 tablet of citrus") names the wrong one.
            val namedAfter = tokens.getOrNull(u + 1)?.normalized == "of"
            val candidate = (if (namedAfter) neighbourAfter(tokens, u) else neighbourBefore(tokens, u) ?: neighbourAfter(tokens, u))
                ?: return@forEachIndexed
            val known = identify(candidate.normalized)
                ?: Lexicon.matchPhonetic(candidate.normalized)?.let { it to Basis.SOUNDS_LIKE }
            // An unknown name is kept exactly as spoken and is not a claim about which drug it is.
            val (key, basis) = known ?: (candidate.normalized to Basis.HEARD)
            out.putIfAbsent(key, Found(key, candidate.display, basis))
        }

        // Brand names the speech model mangled, away from any dosage-form word: accepted only
        // when dosing words sit right next to the token, so plain speech can't produce a drug.
        tokens.forEachIndexed { i, t ->
            if (blocked(t) || !nearDosing(tokens, i)) return@forEachIndexed
            val key = Lexicon.matchPhonetic(t.normalized) ?: return@forEachIndexed
            out.putIfAbsent(key, Found(key, t.display, Basis.SOUNDS_LIKE))
        }

        // Speech models often split one drug name into several words ("Paris at Mall" for paracetamol:
        // joined, it has exactly paracetamol's consonant skeleton). Two or three adjacent words are
        // tried together, but only with dosing words beside them and only on an IDENTICAL skeleton,
        // the tightest tier, because joining common words gives far more chances of a coincidence.
        for (i in tokens.indices) {
            if (blocked(tokens[i])) continue
            for (span in 2..3) {
                val j = i + span - 1
                if (j > tokens.lastIndex || Numbers.parse(tokens[j].normalized) != null) break
                if (!nearDosingRange(tokens, i, j)) continue
                val joined = (i..j).joinToString("") { tokens[it].normalized }
                val key = Lexicon.matchPhonetic(joined, maxRank = 0) ?: continue
                val heard = (i..j).joinToString(" ") { tokens[it].display }
                out.putIfAbsent(key, Found(key, heard, Basis.SOUNDS_LIKE))
            }
        }

        return out.values.toList()
    }

    /** Dosing words within three tokens of a run of words, not counting the run itself. */
    private fun nearDosingRange(tokens: List<Normalize.Token>, from: Int, to: Int): Boolean {
        val lo = maxOf(0, from - 3)
        val hi = minOf(tokens.lastIndex, to + 3)
        return (lo..hi).any { k ->
            k !in from..to && (tokens[k].normalized in DOSAGE_FORMS || tokens[k].normalized in DOSING_WORDS)
        }
    }

    private val DOSING_WORDS = setOf(
        "times", "daily", "twice", "once", "thrice", "after", "before", "food", "meal", "meals",
        "morning", "afternoon", "evening", "night", "bedtime", "days", "weeks", "empty",
        "बार", "दिन", "खाने", "भोजन", "सुबह", "दोपहर", "शाम", "रात", "बाद", "पहले", "रोज", "रोजाना",
        "हफ्ते", "खाली",
    )

    private fun nearDosing(tokens: List<Normalize.Token>, i: Int): Boolean {
        val from = maxOf(0, i - 3)
        val to = minOf(tokens.lastIndex, i + 3)
        return (from..to).any { j ->
            j != i && (tokens[j].normalized in DOSAGE_FORMS || tokens[j].normalized in DOSING_WORDS)
        }
    }

    private fun neighbourBefore(tokens: List<Normalize.Token>, u: Int): Normalize.Token? {
        var j = u - 1
        while (j >= 0 && skippable(tokens[j])) j--
        return tokens.getOrNull(j)?.takeUnless { blocked(it) }
    }

    private fun neighbourAfter(tokens: List<Normalize.Token>, u: Int): Normalize.Token? {
        var j = u + 1
        while (j < tokens.size && skippable(tokens[j])) j++
        return tokens.getOrNull(j)?.takeUnless { blocked(it) }
    }

    // ---------------------------------------------------------------- attributes

    private data class Attributes(
        val timesPerDay: Int? = null,
        val timesOfDay: List<TimeOfDay> = emptyList(),
        val doseCount: Int? = null,
        val foodRelation: FoodRelation? = null,
        val durationDays: Int? = null,
    )

    private val FREQUENCY = listOf(
        // "बार" must be a whole word: without the guards "दोबारा" (again) reads as "दो बार" = twice.
        Regex("(?:दिन\\s+में|रोज|रोजाना|प्रतिदिन|हर\\s+दिन)\\s+($N)\\s*बार$WORD_END"),
        Regex("$WORD_START($N)\\s*बार$WORD_END\\s+(?:रोज|रोजाना|प्रतिदिन|दिन\\s+में)"),
        Regex("($N)\\s+times?\\s+(?:a|per|each)\\s+day"),
        Regex("($N)\\s+times?\\s+daily"),
        Regex("\\b(once|twice|thrice)\\s+(?:a\\s+day|daily|per\\s+day|every\\s+day)"),
        Regex("$WORD_START($N)\\s*बार$WORD_END"),
    )

    private val DOSE = listOf(
        Regex("($N)\\s+(?:गोली|गोलियां|गोलिया|टेबलेट|टैबलेट|कैप्सूल|चम्मच)"),
        Regex("($N)\\s+(?:tablets?|tabs?|capsules?|spoons?|tsp|pills?)"),
    )

    private val TIME_WORDS = listOf(
        "सुबह" to TimeOfDay.MORNING,
        "दोपहर" to TimeOfDay.AFTERNOON,
        "शाम" to TimeOfDay.EVENING,
        "रात" to TimeOfDay.NIGHT,
        "सोने से पहले" to TimeOfDay.NIGHT,
        "morning" to TimeOfDay.MORNING,
        "afternoon" to TimeOfDay.AFTERNOON,
        "evening" to TimeOfDay.EVENING,
        "night" to TimeOfDay.NIGHT,
        "bedtime" to TimeOfDay.NIGHT,
        "bed time" to TimeOfDay.NIGHT,
    )

    /** Order matters: "खाली पेट" and "सोने से पहले" must win before the generic food phrases. */
    private val FOOD = listOf(
        FoodRelation.EMPTY_STOMACH to listOf("खाली पेट", "empty stomach"),
        FoodRelation.BEFORE_FOOD to listOf(
            "खाने से पहले", "भोजन से पहले", "खाना खाने से पहले",
            "before food", "before meal", "before meals", "before eating", "before lunch",
        ),
        FoodRelation.AFTER_FOOD to listOf(
            "खाने के बाद", "भोजन के बाद", "खाना खाने के बाद",
            "after food", "after meal", "after meals", "after eating", "after lunch", "after dinner",
        ),
        FoodRelation.WITH_FOOD to listOf("खाने के साथ", "भोजन के साथ", "with food", "with meal", "with meals"),
    )

    private val DURATION = listOf(
        Regex("($N)\\s+दिन") to 1,
        Regex("($N)\\s+(?:हफ्ते|हफ्ता|सप्ताह)") to 7,
        Regex("($N)\\s+(?:महीने|महीना|माह)") to 30,
        // English needs the explicit "for"/"next": otherwise "three times a day" reads as one day.
        Regex("(?:for|next)\\s+($N)\\s+days?") to 1,
        Regex("(?:for|next)\\s+($N)\\s+weeks?") to 7,
        Regex("(?:for|next)\\s+($N)\\s+months?") to 30,
    )

    private fun attributesIn(n: String): Attributes {
        val timesOfDay = TIME_WORDS
            .mapNotNull { (word, tod) -> n.indexOf(word).takeIf { it >= 0 }?.let { it to tod } }
            .sortedBy { it.first }
            .map { it.second }
            .distinct()

        val food = FOOD.firstNotNullOfOrNull { (relation, phrases) ->
            relation.takeIf { phrases.any { p -> n.contains(p) } }
        }

        val duration = DURATION.firstNotNullOfOrNull { (regex, multiplier) ->
            regex.find(n)?.groupValues?.get(1)?.let { Numbers.parse(it) }?.times(multiplier)
        }

        return Attributes(
            timesPerDay = FREQUENCY.firstNotNullOfOrNull { r ->
                r.find(n)?.groupValues?.get(1)?.let { Numbers.parse(it) }
            },
            timesOfDay = timesOfDay,
            doseCount = DOSE.firstNotNullOfOrNull { r ->
                r.find(n)?.groupValues?.get(1)?.let { Numbers.parse(it) }
            },
            foodRelation = food,
            durationDays = duration,
        )
    }

    // ---------------------------------------------------------------- warnings

    // No \b around the Devanagari alternatives: Java's word boundary is ASCII-only, so \bअगर\b
    // can never match. Latin alternatives still get \b, where it does work.
    private val WARNING_CONDITION = Regex("अगर|यदि|जब\\s+भी|\\bif\\b")
    private val WARNING_ACTION = Regex(
        "तुरंत|तुरन्त|फौरन|अस्पताल|इमरजेंसी|आपातकाल|भर्ती|खतरे|गंभीर|" +
            "immediately|hospital|emergency|right away|straight away|urgent",
    )

    // Speech recognition often drops the leading "if", so "go to the hospital immediately" must
    // count on its own: an urgent word next to a hospital word is a warning whatever else is said.
    private val URGENT = Regex("immediately|right away|straight away|urgent|तुरंत|तुरन्त|फौरन")
    private val HOSPITAL = Regex("hospital|emergency|ambulance|अस्पताल|इमरजेंसी|एम्बुलेंस|आपातकाल")

    private fun looksLikeWarning(n: String): Boolean =
        (WARNING_ACTION.containsMatchIn(n) &&
            (WARNING_CONDITION.containsMatchIn(n) || Regex("come back|वापस आ|दिखाने आ").containsMatchIn(n))) ||
            (URGENT.containsMatchIn(n) && HOSPITAL.containsMatchIn(n))

    // ---------------------------------------------------------------- avoid

    private val AVOID_SUFFIX = listOf(
        Regex("^(.*?)\\s+(?:मत|नहीं)\\s+(?:खाना|खाएं|खाइए|खाइये|पीना|पिएं|पीजिए|लेना|लीजिए|करना|करें)"),
        Regex("^(.*?)\\s+से\\s+(?:बचें|बचना|बचिए|परहेज)"),
        Regex("^(.*?)\\s+का\\s+परहेज"),
    )
    private val AVOID_PREFIX = listOf(
        Regex("(?:avoid|stay away from|keep away from|cut out)\\s+(.+)"),
        Regex("(?:don't|do not|dont)\\s+(?:eat|have|take|drink|touch)\\s+(.+)"),
        Regex("परहेज\\s+(?:करें|कीजिए)\\s+(.+)"),
    )
    private val LIST_SPLIT = Regex(",|\\s+और\\s+|\\s+and\\s+|\\s+या\\s+|\\s+or\\s+")

    private fun avoidItems(n: String): List<String> {
        val body = AVOID_SUFFIX.firstNotNullOfOrNull { it.find(n)?.groupValues?.get(1) }
            ?: AVOID_PREFIX.firstNotNullOfOrNull { it.find(n)?.groupValues?.get(1) }
            ?: return emptyList()

        return body.split(LIST_SPLIT)
            .map { tidy(it) }
            .filter { it.length >= 2 && it.split(" ").size <= 6 }
    }

    // ---------------------------------------------------------------- diagnosis

    private val DIAGNOSIS = listOf(
        Regex("(?:^|\\s)आपको\\s+(.+?)\\s+(?:है|हैं|हो\\s+गया|हो\\s+गई)"),
        Regex("(?:^|\\s)(?:यह|ये)\\s+(.+?)\\s+(?:है|हैं)"),
        Regex("you\\s+have\\s+(.+?)(?:,|\\s+(?:and|but|so|nothing)\\b|$)"),
        Regex("(?:this|it)\\s+is\\s+(.+?)(?:,|\\s+(?:and|but|so|nothing)\\b|$)"),
    )

    /** A condition never begins with one of these ("it is IN tablet at night" is not a diagnosis). */
    private val LEADING_PREPOSITIONS = setOf(
        "in", "at", "on", "for", "to", "of", "with", "by", "from", "about", "after", "before", "during",
        "में", "पर", "से", "को", "के", "का", "की",
    )

    private fun diagnosisIn(n: String): String? {
        for (r in DIAGNOSIS) {
            val raw = r.find(n)?.groupValues?.get(1) ?: continue
            if (raw.contains(',')) continue
            val text = tidy(raw)
            val words = text.split(" ").filter { it.isNotBlank() }
            if (words.isEmpty() || words.size > 6) continue
            // Found on a real run: "Once it is in tablet at night before sleeping" matched "it is ..." and
            // produced a diagnosis. A condition does not start with a preposition or mention a dosage form
            // or dosing words; that is a dosing instruction, not a diagnosis.
            if (words.first() in LEADING_PREPOSITIONS) continue
            if (words.any { it in DOSAGE_FORMS || it in DOSING_WORDS }) continue
            return text
        }
        return null
    }

    // ---------------------------------------------------------------- follow-up

    private val FOLLOW_UP_CUE = Regex(
        "दोबारा|फिर\\s+से|वापस|अगली\\s+बार|फॉलो\\s*अप|फालो\\s*अप|" +
            "come back|come again|see me again|follow\\s*-?\\s*up|next visit|review",
    )
    private val FOLLOW_UP_ACTION = Regex(
        "आना|आइए|आएं|आना|दिखाने|दिखाना|दिखा|मिलना|चेकअप|चेक\\s+अप|" +
            "come|see|visit|check|follow",
    )
    private val FOLLOW_UP_WHEN = listOf(
        Regex("($N)\\s+दिन\\s+बाद") to 1,
        Regex("($N)\\s+(?:हफ्ते|हफ्ता|सप्ताह)\\s+बाद") to 7,
        Regex("($N)\\s+(?:महीने|महीना|माह)\\s+बाद") to 30,
        Regex("(?:in|after)\\s+($N)\\s+days?") to 1,
        Regex("(?:in|after)\\s+($N)\\s+weeks?") to 7,
        Regex("(?:in|after)\\s+($N)\\s+months?") to 30,
    )

    private fun followUpIn(n: String, line: TranscriptLine): FollowUp? {
        if (!FOLLOW_UP_CUE.containsMatchIn(n)) return null
        if (!FOLLOW_UP_ACTION.containsMatchIn(n)) return null
        val days = FOLLOW_UP_WHEN.firstNotNullOfOrNull { (regex, multiplier) ->
            regex.find(n)?.groupValues?.get(1)?.let { Numbers.parse(it) }?.times(multiplier)
        }
        return FollowUp(inDays = days, text = line.text, sourceLines = listOf(line.index))
    }

    // ---------------------------------------------------------------- shared

    private val SPACES = Regex("\\s+")

    private fun tidy(s: String): String =
        s.trim().trim(',').trim().replace(SPACES, " ")
}
