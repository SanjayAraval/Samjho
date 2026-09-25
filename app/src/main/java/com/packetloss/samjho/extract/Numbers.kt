package com.packetloss.samjho.extract

/**
 * Number words the doctor might actually say, in Hindi or English, plus plain digits.
 * Keys are stored already normalised, because every rule matches against normalised text.
 */
object Numbers {

    private val WORDS: Map<String, Int> = buildMap {
        // Hindi
        put("एक", 1); put("दो", 2); put("तीन", 3); put("चार", 4); put("पांच", 5)
        put("पाँच", 5); put("छह", 6); put("छः", 6); put("छे", 6); put("सात", 7)
        put("आठ", 8); put("नौ", 9); put("दस", 10); put("ग्यारह", 11); put("बारह", 12)
        put("तेरह", 13); put("चौदह", 14); put("पंद्रह", 15); put("पन्द्रह", 15)
        put("बीस", 20); put("इक्कीस", 21); put("तीस", 30); put("डेढ़", 1); put("ढाई", 2)
        // English
        put("one", 1); put("two", 2); put("three", 3); put("four", 4); put("five", 5)
        put("six", 6); put("seven", 7); put("eight", 8); put("nine", 9); put("ten", 10)
        put("eleven", 11); put("twelve", 12); put("fourteen", 14); put("fifteen", 15)
        put("twenty", 20); put("thirty", 30)
        put("a", 1); put("an", 1); put("once", 1); put("twice", 2); put("thrice", 3)
    }.mapKeys { (k, _) -> Normalize.text(k) }

    /** A regex fragment that matches any number we understand. Longest alternatives first. */
    val GROUP: String = buildString {
        append("\\d{1,3}")
        WORDS.keys.sortedByDescending { it.length }.forEach { append("|").append(Regex.escape(it)) }
    }

    fun parse(token: String): Int? {
        val t = Normalize.text(token)
        t.toIntOrNull()?.let { return it }
        return WORDS[t]
    }
}
