package com.packetloss.samjho.extract

import com.packetloss.samjho.model.Extraction
import com.packetloss.samjho.model.FollowUp
import com.packetloss.samjho.model.FoodRelation
import com.packetloss.samjho.model.Language
import com.packetloss.samjho.model.Medicine
import com.packetloss.samjho.model.Note
import com.packetloss.samjho.model.TimeOfDay
import com.packetloss.samjho.model.TranscriptLine

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

    fun extract(rawLines: List<String>): Extraction {
        val lines = rawLines.mapIndexed { i, t -> TranscriptLine(i, t.trim()) }
            .filter { it.text.isNotEmpty() }
            .mapIndexed { i, l -> TranscriptLine(i, l.text) }

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

            val found = medicinesOn(tokens)
            if (found.isNotEmpty()) {
                val attrs = attributesIn(n)
                for (f in found) {
                    val existing = medicines[f.key]
                    medicines[f.key] = if (existing == null) {
                        Medicine(
                            name = f.name,
                            key = f.key,
                            timesPerDay = attrs.timesPerDay,
                            timesOfDay = attrs.timesOfDay,
                            doseCount = attrs.doseCount,
                            foodRelation = attrs.foodRelation,
                            durationDays = attrs.durationDays,
                            sourceLines = here,
                        )
                    } else {
                        existing.copy(
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

    private data class Found(val key: String, val name: String)

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
                Lexicon.match(t.normalized)?.let { key ->
                    out.putIfAbsent(key, Found(key, t.display))
                }
            }
        }

        tokens.forEachIndexed { u, t ->
            if (t.normalized !in DOSAGE_FORMS) return@forEachIndexed
            val candidate = neighbourBefore(tokens, u) ?: neighbourAfter(tokens, u) ?: return@forEachIndexed
            val key = Lexicon.match(candidate.normalized) ?: candidate.normalized
            out.putIfAbsent(key, Found(key, candidate.display))
        }

        return out.values.toList()
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
        Regex("(?:दिन\\s+में|रोज|रोजाना|प्रतिदिन|हर\\s+दिन)\\s+($N)\\s*बार"),
        Regex("($N)\\s*बार\\s+(?:रोज|रोजाना|प्रतिदिन|दिन\\s+में)"),
        Regex("($N)\\s+times?\\s+(?:a|per|each)\\s+day"),
        Regex("($N)\\s+times?\\s+daily"),
        Regex("\\b(once|twice|thrice)\\s+(?:a\\s+day|daily|per\\s+day|every\\s+day)"),
        Regex("($N)\\s*बार"),
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

    private fun looksLikeWarning(n: String): Boolean =
        WARNING_ACTION.containsMatchIn(n) &&
            (WARNING_CONDITION.containsMatchIn(n) || Regex("come back|वापस आ|दिखाने आ").containsMatchIn(n))

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

    private fun diagnosisIn(n: String): String? {
        for (r in DIAGNOSIS) {
            val raw = r.find(n)?.groupValues?.get(1) ?: continue
            if (raw.contains(',')) continue
            val text = tidy(raw)
            val words = text.split(" ").filter { it.isNotBlank() }
            if (words.isEmpty() || words.size > 6) continue
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
