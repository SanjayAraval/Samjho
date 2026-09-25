package com.packetloss.samjho.ui

import com.packetloss.samjho.model.FoodRelation
import com.packetloss.samjho.model.Language
import com.packetloss.samjho.model.MissingField
import com.packetloss.samjho.model.MissingSection
import com.packetloss.samjho.model.TimeOfDay

/**
 * The patient reads the result in the language the consultation happened in, so every label
 * is chosen by the detected language rather than by the phone's locale.
 */
class Strings(private val language: Language) {

    private fun pick(hi: String, en: String) = if (language == Language.HINDI) hi else en

    val diagnosis = pick("डॉक्टर ने क्या बताया", "What the doctor said it is")
    val medicines = pick("दवाइयाँ", "Medicines")
    val avoid = pick("इनसे बचें", "Things to avoid")
    val warnings = pick("खतरे के लक्षण", "Warning signs")
    val followUp = pick("अगली मुलाक़ात", "Next visit")
    val transcript = pick("पूरी बातचीत", "Full transcript")

    val notMentioned = pick("नहीं बताया गया", "Not mentioned")
    val askDoctor = pick(
        "डॉक्टर या दवा वाले से पूछें।",
        "Ask your doctor or pharmacist.",
    )
    val showWords = pick("डॉक्टर के शब्द देखें", "Show the doctor's words")
    val hideWords = pick("छिपाएँ", "Hide")
    val nothingHere = pick("डॉक्टर ने इस बारे में कुछ नहीं कहा।", "The doctor did not mention this.")

    val rulesOnly = pick("नियम आधारित", "RULES ONLY")
    val offline = pick("पूरी तरह ऑफ़लाइन", "Fully offline")
    val disclaimer = pick(
        "Samjho सिर्फ़ वही दोहराता है जो डॉक्टर ने कहा। यह कोई चिकित्सा सलाह नहीं देता।",
        "Samjho only repeats what the doctor said. It never gives medical advice.",
    )

    fun line(index: Int) = pick("पंक्ति ${index + 1}", "Line ${index + 1}")

    fun timesPerDay(n: Int) = pick("दिन में $n बार", if (n == 1) "Once a day" else "$n times a day")

    fun dose(n: Int) = pick("$n गोली", if (n == 1) "1 tablet" else "$n tablets")

    fun durationDays(n: Int) = pick("$n दिन तक", if (n == 1) "For 1 day" else "For $n days")

    fun followUpIn(n: Int) = pick("$n दिन बाद", if (n == 1) "In 1 day" else "In $n days")

    fun timeOfDay(t: TimeOfDay) = when (t) {
        TimeOfDay.MORNING -> pick("सुबह", "Morning")
        TimeOfDay.AFTERNOON -> pick("दोपहर", "Afternoon")
        TimeOfDay.EVENING -> pick("शाम", "Evening")
        TimeOfDay.NIGHT -> pick("रात", "Night")
    }

    fun food(f: FoodRelation) = when (f) {
        FoodRelation.BEFORE_FOOD -> pick("खाने से पहले", "Before food")
        FoodRelation.AFTER_FOOD -> pick("खाने के बाद", "After food")
        FoodRelation.EMPTY_STOMACH -> pick("खाली पेट", "Empty stomach")
        FoodRelation.WITH_FOOD -> pick("खाने के साथ", "With food")
    }

    fun missing(f: MissingField) = when (f) {
        MissingField.TIMES_PER_DAY -> pick("दिन में कितनी बार", "how many times a day")
        MissingField.TIME_OF_DAY -> pick("किस समय", "what time of day")
        MissingField.FOOD_RELATION -> pick("खाने से पहले या बाद", "before or after food")
        MissingField.DURATION -> pick("कितने दिन तक", "for how many days")
    }

    fun missingSection(s: MissingSection) = when (s) {
        MissingSection.MEDICINES -> pick("कोई दवा", "any medicine")
        MissingSection.DIAGNOSIS -> pick("बीमारी का नाम", "what the problem is")
        MissingSection.AVOID -> pick("परहेज़", "anything to avoid")
        MissingSection.WARNINGS -> pick("खतरे के लक्षण", "warning signs")
        MissingSection.FOLLOW_UP -> pick("अगली मुलाक़ात", "a next visit")
    }
}
