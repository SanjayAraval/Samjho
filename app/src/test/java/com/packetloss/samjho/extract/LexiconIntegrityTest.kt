package com.packetloss.samjho.extract

import com.packetloss.samjho.scan.ScanSearch
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** The data side of the lexicon: what is in it, that it is consistent, and that it does not fire on ordinary words. */
class LexiconIntegrityTest {

    @Suppress("UNCHECKED_CAST")
    private val entries: List<Pair<String, List<String>>> =
        Lexicon::class.java.getDeclaredField("ENTRIES").apply { isAccessible = true }.get(Lexicon) as List<Pair<String, List<String>>>

    @Test
    fun theLexiconHoldsTwoHundredToThreeHundredMedicines() {
        assertTrue("${entries.size} keys", entries.size in 200..300)
    }

    @Test
    fun everyKeyIsUniqueAndNamed() {
        assertEquals(entries.size, entries.map { it.first }.toSet().size)
        assertTrue(entries.all { it.first.isNotBlank() && it.first == it.first.trim().lowercase() })
    }

    @Test
    fun noSpellingBelongsToTwoMedicines() {
        val owner = HashMap<String, String>()
        val clashes = mutableListOf<String>()
        for ((key, forms) in entries) for (form in forms) {
            val n = Normalize.text(form)
            val other = owner.putIfAbsent(n, key)
            if (other != null && other != key) clashes += "'$form' is both $other and $key"
        }
        assertEquals(emptyList<String>(), clashes)
    }

    @Test
    fun everyOriginalNameAndBrandStillMeansWhatItAlwaysDid() {
        val original = mapOf(
            "paracetamol" to "paracetamol", "crocin" to "paracetamol", "dolo" to "paracetamol", "calpol" to "paracetamol",
            "पैरासिटामोल" to "paracetamol", "डोलो" to "paracetamol", "azithromycin" to "azithromycin", "azithral" to "azithromycin",
            "एजिथ्रोमाइसिन" to "azithromycin", "cetirizine" to "cetirizine", "cetzine" to "cetirizine", "levocetirizine" to "levocetirizine",
            "amoxicillin" to "amoxicillin", "amoxyclav" to "amoxicillin", "ibuprofen" to "ibuprofen", "brufen" to "ibuprofen",
            "pantoprazole" to "pantoprazole", "pan" to "pantoprazole", "omeprazole" to "omeprazole", "metformin" to "metformin",
            "amlodipine" to "amlodipine", "cefixime" to "cefixime", "ciprofloxacin" to "ciprofloxacin", "ofloxacin" to "ofloxacin",
            "metronidazole" to "metronidazole", "flagyl" to "metronidazole", "domperidone" to "domperidone", "ondansetron" to "ondansetron",
            "ranitidine" to "ranitidine", "diclofenac" to "diclofenac", "montelukast" to "montelukast", "salbutamol" to "salbutamol",
            "asthalin" to "salbutamol", "ors" to "ors", "vitamin" to "vitamin d", "calcium" to "calcium", "iron" to "iron",
            "zinc" to "zincovit", "zincovit" to "zincovit",
        )
        for ((form, key) in original) assertEquals("'$form'", key, Lexicon.exact(Normalize.text(form)))
    }

    @Test
    fun theBrandsThatShareAGenericStillHaveTheirOwnIdentity() {
        for (b in listOf("dolo", "crocin", "pantop", "montek", "augmentin", "glycomet", "shelcal", "becosules")) {
            assertEquals(b, Lexicon.brand(b))
        }
        assertNull(Lexicon.brand("paracetamol"))
        assertNull(Lexicon.brand("pantoprazole"))
    }

    // ---- the names asked for, and where they lead

    @Test
    fun theRequiredBrandNamesResolveToTheirMedicine() {
        val required = mapOf(
            "pantop" to "pantoprazole", "montek" to "montelukast", "montair" to "montelukast", "amoxil" to "amoxicillin",
            "augmentin" to "amoxicillin + clavulanic acid", "shelcal" to "calcium", "zincovit" to "zincovit", "telma" to "telmisartan",
            "ecosprin" to "aspirin", "glycomet" to "metformin", "dolo" to "paracetamol", "combiflam" to "ibuprofen + paracetamol",
            "cetrizine" to "cetirizine", "allegra" to "fexofenadine", "rantac" to "ranitidine",
            "neurobion" to "vitamin b complex", "becosules" to "vitamin b complex",
            "पैंटोप" to "pantoprazole", "मोंटेक" to "montelukast", "एमोक्सिल" to "amoxicillin", "ग्लाइकोमेट" to "metformin",
        )
        for ((form, key) in required) assertEquals("'$form'", key, Lexicon.exact(Normalize.text(form)))
    }

    @Test
    fun aCombinationProductIsItsOwnMedicineNotJustOneIngredient() {
        assertEquals("ibuprofen + paracetamol", Lexicon.exact("combiflam"))
        assertTrue(Lexicon.isKey("amoxicillin + clavulanic acid"))
        assertEquals("Ibuprofen + paracetamol", Lexicon.display("ibuprofen + paracetamol"))
    }

    @Test
    fun theNamesLeftOutOnPurposeAreStillOut() {
        // Each collides with a real word or phrase (see the note above ENTRIES). Adding one back is a decision, not a data fix.
        for (form in listOf("zyrtec", "pan-d", "pand", "digene", "ज़िर्टेक", "पैन-डी", "डिगेन", "omez", "ओमेज़", "liv-52", "liv52", "लिव-52")) {
            assertNull("'$form' collides with everyday text", Lexicon.exact(Normalize.text(form)))
        }
    }

    @Test
    fun omezAndLiv52WereRemovedButTheirMedicinesCanStillBePickedByHand() {
        assertTrue(Lexicon.isKey("liv-52"))
        assertTrue(Lexicon.isKey("omeprazole"))
        assertTrue("liv-52" in ScanSearch.filter("liv").map { it.key })
        assertEquals("omeprazole", Lexicon.exact("omeprazole"))
    }

    @Test
    fun wordsFromRealConsultationSpeechNeverBecomeAMedicineCard() {
        // "always", "major", "music", "level", "lives" sounded like Liv-52 and Omez; they must not put a card on screen.
        for (w in listOf("always", "major", "music", "level", "lives", "message", "measure", "allowed")) {
            for (sentence in listOf("take it $w twice a day after food", "you must $w take it after food for five days")) {
                val cards = RuleExtractor.extract(listOf(sentence)).medicines.map { it.key }
                assertEquals("'$sentence' produced $cards", emptyList<String>(), cards)
            }
        }
    }

    // ---- ordinary words

    @Test
    fun everydayWordsNeverMatchAMedicineByExactOrNearSpelling() {
        val words = listOf(
            "tablet", "table", "cable", "able", "capsule", "doctor", "please", "practice", "patient", "hospital", "diagnosis", "dosing",
            "morning", "evening", "night", "months", "minutes", "always", "million", "music", "major", "points", "fever", "temperature",
            "immediately", "medication", "medicine", "prescription", "signature", "advice", "follow", "review", "weeks", "before",
            "after", "daily", "twice", "water", "food", "stomach", "cold", "cough", "pain", "headache", "clinic", "address",
            "मौसम", "बुखार", "दर्द", "डॉक्टर", "अस्पताल", "तुरंत", "दवाइयां", "परामर्श", "सुबह", "रात", "शाम", "पांच", "तीन",
        )
        for (w in words) {
            val n = Normalize.text(w)
            assertNull("'$w' is an exact form", Lexicon.exact(n))
            assertNull("'$w' is a near spelling of a medicine", Lexicon.match(n))
        }
    }

    @Test
    fun theOnlyEverydayWordsThatAreExactFormsAreTheOnesThatWereAlreadyThere() {
        // 'iron', 'pan', 'vitamin', 'zinc' and 'calcium' were exact forms before the expansion. No new common word joined them.
        // "candid" and "refresh" (brands that are ordinary English words) and "ferrous" (one letter from "fried") were left out.
        val words = listOf("candid", "refresh", "eno", "ferrous", "aspirin", "insulin", "lithium", "melatonin")
        val exact = words.filter { Lexicon.exact(Normalize.text(it)) != null }
        // these are real medicine names that happen to be dictionary words; anything else showing up here is a new collision
        assertEquals(setOf("eno", "aspirin", "insulin", "lithium", "melatonin"), exact.toSet())
    }

    // ---- reaching every medicine

    @Test
    fun everyMedicineCanBeFoundByTypingItsFirstLettersInTheManualList() {
        val all = ScanSearch.all().map { it.key }.toSet()
        assertEquals(Lexicon.keys().toSet(), all)
        for (key in Lexicon.keys()) {
            val q = key.take(4)
            assertTrue("'$q' should find '$key'", key in ScanSearch.filter(q).map { it.key })
        }
    }

    @Test
    fun theBiasingListHandedToTheRecogniserStaysABoundedSize() {
        val n = Lexicon.speechHints().size
        assertTrue("$n hints", n in 100..1500)
    }
}
