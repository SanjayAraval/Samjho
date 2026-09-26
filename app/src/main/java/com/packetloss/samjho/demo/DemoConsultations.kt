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
     * The English consult as the phone actually transcribed it offline, in airplane mode, from a
     * real reading with the real drug names. Unedited: the mishearings ("thromison", "citrus in",
     * "dollar 650", "crossing") are the point, since they show the confirm step at work.
     */
    val ENGLISH_AS_HEARD = DemoConsultation(
        id = "en-as-heard",
        label = "English: as the phone heard it",
        language = Language.ENGLISH,
        lines = listOf(
            "Take the paracetamol tablet three times a day after food for 5 days",
            "and the thromison in the morning on an empty stomach for 3 days",
            "egg 1 tablet of citrus in at night",
            "avoid cold water and fried food",
            "the fever goes above 102 go to the hospital immediately",
            "Come back after 5 days",
            "Also dollar 650",
            "and crossing if the fever comes back",
            "Bluetooth",
        ),
    )

    val ALL = listOf(HINDI, ENGLISH, ENGLISH_AS_HEARD)
}
