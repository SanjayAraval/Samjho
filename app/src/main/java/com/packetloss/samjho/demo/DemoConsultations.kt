package com.packetloss.samjho.demo

import com.packetloss.samjho.model.Language

/**
 * A consultation baked into the app so the whole pipeline can be shown without a microphone,
 * a speech model, or a quiet room. These are the exact lines the stage demo is read from.
 */
data class DemoConsultation(
    val id: String,
    val label: String,
    val language: Language,
    val lines: List<String>,
)

object DemoConsultations {

    val HINDI = DemoConsultation(
        id = "hi-viral-fever",
        label = "हिंदी: वायरल बुखार",
        language = Language.HINDI,
        lines = listOf(
            "नमस्ते डॉक्टर साहब, दो दिन से बुखार और गले में दर्द है।",
            "आपको वायरल बुखार है, घबराने की बात नहीं है।",
            "पैरासिटामोल की गोली दिन में तीन बार खाने के बाद लेनी है, पांच दिन तक।",
            "और एजिथ्रोमाइसिन सुबह खाली पेट, तीन दिन तक।",
            "रात को सोने से पहले सेटिरिजिन की एक गोली।",
            "ठंडा पानी और तला हुआ खाना मत खाना।",
            "अगर बुखार एक सौ दो से ऊपर जाए या सांस लेने में तकलीफ हो, तो तुरंत अस्पताल जाना।",
            "पांच दिन बाद दोबारा दिखाने आना।",
        ),
    )

    val ENGLISH = DemoConsultation(
        id = "en-viral-fever",
        label = "English: viral fever",
        language = Language.ENGLISH,
        lines = listOf(
            "Namaste doctor, I have had fever and a sore throat for two days.",
            "You have a viral fever, nothing to worry about.",
            "Take the paracetamol tablet three times a day after food, for five days.",
            "And azithromycin in the morning on an empty stomach, for three days.",
            "Take one tablet of cetirizine at night before sleeping.",
            "Avoid cold water and fried food.",
            "If the fever goes above 102 or you have trouble breathing, go to the hospital immediately.",
            "Come back again after five days.",
        ),
    )

    /**
     * The English consult as the phone actually transcribed it offline (airplane mode, Wi-Fi and
     * data off), read aloud with the real drug names. Unedited: "parasite mall", "thrombison" and
     * "citrus in" are the point, since they show the confirm step, the unnamed-dosing line, and
     * Dolo kept apart from paracetamol.
     */
    val ENGLISH_AS_HEARD = DemoConsultation(
        id = "en-as-heard",
        label = "English: as the phone heard it",
        language = Language.ENGLISH,
        lines = listOf(
            "You have a viral fever nothing to worry about",
            "parasite mall three times a day after food for 5 days",
            "Is it thrombison once a day in the morning on an empty stomach for 3 days",
            "and one citrus in tablet at night before sleeping",
            "Also Dolo 650 if the fever comes back",
            "avoid cold water and fried food",
            "If the fever goes above 102 go to the hospital immediately",
            "Come back after 5 days for a checkup",
        ),
    )

    /**
     * A real live reading of the consult on the phone, unedited (release candidate 29d497a, Android
     * system recogniser, en-US). Paracetamol arrives as "Paris at Mall", azithromycin as "throw
     * medicine", and cetirizine's name is lost entirely, so it shows every recovery path at once.
     */
    val ENGLISH_LIVE_READ = DemoConsultation(
        id = "en-live-read",
        label = "English: live read on the phone",
        language = Language.ENGLISH,
        lines = listOf(
            "You have a viral fever nothing to worry about take Paris at Mall three times a day after food for 5 days",
            "Is it throw medicine once a day in the morning on an empty stomach for 3 days",
            "Once it is in tablet at night before sleeping",
            "also dollar 650 if the fever comes back",
            "Avoid cold water and fried food",
            "If the fever goes above 102 go to the hospital immediately",
            "Come back after 5 days for a checkup",
        ),
    )

    val ALL = listOf(HINDI, ENGLISH, ENGLISH_AS_HEARD, ENGLISH_LIVE_READ)
}
