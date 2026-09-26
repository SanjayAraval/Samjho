package com.packetloss.samjho.extract

/**
 * Common outpatient medicines in the spellings an offline speech model tends to emit.
 *
 * This list exists only to RECOGNISE a word the doctor actually said. It never adds a medicine
 * that was not spoken, and the name shown to the patient is always the doctor's own word, not
 * the canonical entry. Medicines outside this list are still caught by the phrase rules
 * ("X की गोली", "tablet X"), which is what keeps unknown brand names working.
 */
object Lexicon {

    /** canonical key to the surface forms that should map onto it. */
    private val ENTRIES: List<Pair<String, List<String>>> = listOf(
        "paracetamol" to listOf(
            "paracetamol", "पैरासिटामोल", "पेरासिटामोल", "पैरासिटामॉल", "परासिटामोल",
            "crocin", "क्रोसिन", "dolo", "डोलो", "calpol", "कालपोल",
        ),
        "azithromycin" to listOf(
            "azithromycin", "एजिथ्रोमाइसिन", "एज़िथ्रोमाइसिन", "अजिथ्रोमाइसिन",
            "अझिथ्रोमाइसिन", "azithral", "एजिथ्रल",
        ),
        "cetirizine" to listOf(
            "cetirizine", "सेटिरिजिन", "सिटिरिजिन", "सेट्रीजीन", "सेटीरिजीन", "cetzine", "सेटजीन",
        ),
        "levocetirizine" to listOf("levocetirizine", "लेवोसेटिरिजिन", "लिवोसेटिरिजिन"),
        "amoxicillin" to listOf("amoxicillin", "एमोक्सिसिलिन", "अमोक्सिसिलिन", "amoxyclav", "एमोक्सीक्लेव"),
        "ibuprofen" to listOf("ibuprofen", "आइबुप्रोफेन", "इबुप्रोफेन", "brufen", "ब्रूफेन"),
        "pantoprazole" to listOf("pantoprazole", "पैंटोप्राजोल", "पैन्टोप्राजोल", "पंटोप्राजोल", "pan", "पैन"),
        "omeprazole" to listOf("omeprazole", "ओमेप्राजोल", "ओमीप्राजोल"),
        "metformin" to listOf("metformin", "मेटफॉर्मिन", "मेटफार्मिन"),
        "amlodipine" to listOf("amlodipine", "एम्लोडिपिन", "अम्लोडिपिन"),
        "cefixime" to listOf("cefixime", "सेफिक्सिम", "सेफिक्साइम"),
        "ciprofloxacin" to listOf("ciprofloxacin", "सिप्रोफ्लोक्सासिन", "सिप्रोफ्लॉक्सासिन"),
        "ofloxacin" to listOf("ofloxacin", "ओफ्लोक्सासिन"),
        "metronidazole" to listOf("metronidazole", "मेट्रोनिडाजोल", "flagyl", "फ्लैजिल"),
        "domperidone" to listOf("domperidone", "डोम्पेरिडोन", "डोंपेरिडोन"),
        "ondansetron" to listOf("ondansetron", "ओंडानसेट्रॉन", "ओंडानसेट्रान"),
        "ranitidine" to listOf("ranitidine", "रैनिटिडिन", "रेनिटिडिन"),
        "diclofenac" to listOf("diclofenac", "डाइक्लोफेनाक", "डिक्लोफेनाक"),
        "montelukast" to listOf("montelukast", "मोंटेलुकास्ट", "मॉन्टेलुकास्ट"),
        "salbutamol" to listOf("salbutamol", "साल्बुटामोल", "asthalin", "अस्थालिन"),
        "ors" to listOf("ors", "ओआरएस", "ओ.आर.एस"),
        "vitamin d" to listOf("विटामिन", "vitamin"),
        "calcium" to listOf("calcium", "कैल्शियम", "कैल्सियम"),
        "iron" to listOf("iron", "आयरन", "फेरस"),
        "zincovit" to listOf("zincovit", "जिंकोविट", "zinc", "जिंक"),
    )

    /**
     * Medicine names in every spelling above, for a speech engine that accepts biasing: the words
     * the doctor is likely to say, so the recogniser prefers them over sound-alike everyday words.
     */
    fun speechHints(): List<String> = ENTRIES.flatMap { it.second }.distinct()

    private data class Entry(val key: String, val form: String)

    private val BY_FORM: Map<String, String> = buildMap {
        ENTRIES.forEach { (key, forms) ->
            forms.forEach { form -> put(Normalize.text(form), key) }
        }
    }

    private val FUZZY_CANDIDATES: List<Entry> =
        BY_FORM.entries.map { Entry(it.value, it.key) }.filter { it.form.length >= 5 }

    private data class Skeleton(val key: String, val skeleton: String)

    private val SKELETONS: List<Skeleton> = BY_FORM.entries
        .map { Skeleton(it.value, Phonetic.skeleton(it.key)) }
        .filter { it.skeleton.length >= 2 }
        .distinct()

    /**
     * Matches a token by how it SOUNDS, for brand names the small speech models mangle
     * ("pracite" for paracetamol, "dollar" for dolo). Looser than [match], so callers must only
     * use it where dosing context already sits next to the token.
     *
     * Every accepted match needs the same first consonant, and it returns null when the best
     * tier is ambiguous between two different medicines rather than picking one.
     */
    fun matchPhonetic(normalizedToken: String, maxRank: Int = 2): String? {
        val token = Phonetic.skeleton(normalizedToken)
        if (token.length < 2) return null

        var bestRank = Int.MAX_VALUE
        val keys = mutableSetOf<String>()
        for (c in SKELETONS) {
            val rank = phoneticRank(token, c.skeleton, normalizedToken.length) ?: continue
            if (rank < bestRank) {
                bestRank = rank
                keys.clear()
            }
            if (rank == bestRank) keys += c.key
        }
        return if (bestRank <= maxRank) keys.singleOrNull() else null
    }

    /**
     * Brand names that share a lexicon key with their generic (Dolo and Crocin are paracetamol).
     * A brand is a different thing to say than the generic, and the doctor may give different
     * instructions for each, so the extractor must not fold them into one card.
     */
    private val BRANDS: Map<String, List<String>> = mapOf(
        "crocin" to listOf("crocin", "क्रोसिन"),
        "dolo" to listOf("dolo", "डोलो"),
        "calpol" to listOf("calpol", "कालपोल"),
        "azithral" to listOf("azithral", "एजिथ्रल"),
        "cetzine" to listOf("cetzine", "सेटजीन"),
        "amoxyclav" to listOf("amoxyclav", "एमोक्सीक्लेव"),
        "brufen" to listOf("brufen", "ब्रूफेन"),
        "flagyl" to listOf("flagyl", "फ्लैजिल"),
        "asthalin" to listOf("asthalin", "अस्थालिन"),
        "pan" to listOf("pan", "पैन"),
        "zincovit" to listOf("zincovit", "जिंकोविट"),
    )

    private val BRAND_BY_FORM: Map<String, String> = BRANDS.flatMap { (brand, forms) ->
        forms.map { Normalize.text(it) to brand }
    }.toMap()

    /** The brand a spoken form belongs to, or null for a generic name (in any script). */
    fun brand(normalizedToken: String): String? = BRAND_BY_FORM[normalizedToken]

    private val KEYS: Set<String> = ENTRIES.map { it.first }.toSet()

    /** True when [key] is one of the lexicon's medicines, rather than an unknown brand kept as spoken. */
    fun isKey(key: String): Boolean = key in KEYS

    /** The lexicon medicine for a word spelled exactly like a known form, or null. No fuzziness at all. */
    fun exact(normalizedToken: String): String? = BY_FORM[normalizedToken]

    fun keys(): List<String> = ENTRIES.map { it.first }

    fun display(key: String): String = if (key == "ors") "ORS" else key.replaceFirstChar { it.uppercase() }

    /** A lexicon medicine and how far its sound is from a heard word: 0 identical, 1 unrelated. */
    data class Sound(val key: String, val distance: Double)

    private val KEY_SKELETONS: Map<String, List<String>> = SKELETONS
        .groupBy({ it.key }, { it.skeleton })

    /**
     * Lexicon medicines ordered by how close their consonant skeleton is to [normalizedToken],
     * closest first, keeping only those within [maxDistance]. Words with fewer than three
     * consonants carry too little sound to compare, so they have no neighbours at all.
     */
    fun closest(normalizedToken: String, limit: Int, maxDistance: Double = 1.0): List<Sound> {
        val token = Phonetic.skeleton(normalizedToken)
        if (token.length < 3) return emptyList()
        return KEY_SKELETONS.mapNotNull { (key, skeletons) ->
            val best = skeletons.filter { it.length >= 3 }.minOfOrNull { s ->
                Normalize.editDistance(token, s).toDouble() / maxOf(token.length, s.length)
            } ?: return@mapNotNull null
            if (best <= maxDistance) Sound(key, best) else null
        }.sortedWith(compareBy({ it.distance }, { it.key })).take(limit)
    }

    /** 0 = same skeleton, 1 = clipped prefix, 2 = one slip. Lower is a tighter match. */
    private fun phoneticRank(token: String, lex: String, tokenLength: Int): Int? {
        // Speech models often drop the first syllable ("azithromycin" heard as "thromison", so
        // "strmsn" arrives as "trmsn"). At five or more consonants a single slip anywhere is far
        // too specific to be a coincidence, so only the one-slip tier may ignore the first letter.
        if (token[0] != lex[0]) {
            return if (token.length >= 5 && lex.length >= 5 && Normalize.editDistance(token, lex) <= 1) 2 else null
        }

        // A two-consonant skeleton (dolo = "dl") is too short to trust on its own, so it only
        // accepts a longer, clearly word-like token that begins with it ("dollar" = "dlr").
        if (lex.length == 2) {
            return if (token.length == 3 && token.startsWith(lex) && tokenLength >= 5) 2 else null
        }

        if (token == lex) return 0

        // Below four consonants a one-letter slip stops meaning "misheard" and starts meaning
        // "a different word": "fried" is one slip from ferrous (frs), "pain" from pan (pn).
        if (token.length >= 4 && token.length < lex.length &&
            token.length * 10 >= lex.length * 6 && lex.startsWith(token)
        ) return 1
        if (token.length >= 4 && lex.length >= 4 && Normalize.editDistance(token, lex) <= 1) return 2
        return null
    }

    /**
     * Returns the canonical key for a spoken token, or null. Exact match first; a short edit
     * distance is allowed only for longer words, where a one-character slip is far more likely
     * to be speech recognition than a different drug.
     */
    fun match(normalizedToken: String): String? {
        BY_FORM[normalizedToken]?.let { return it }
        if (normalizedToken.length < 5) return null
        val budget = if (normalizedToken.length >= 8) 2 else 1
        var best: String? = null
        var bestDistance = Int.MAX_VALUE
        for (candidate in FUZZY_CANDIDATES) {
            if (kotlin.math.abs(candidate.form.length - normalizedToken.length) > budget) continue
            val d = Normalize.editDistance(normalizedToken, candidate.form)
            if (d <= budget && d < bestDistance) {
                bestDistance = d
                best = candidate.key
            }
        }
        return best
    }
}
