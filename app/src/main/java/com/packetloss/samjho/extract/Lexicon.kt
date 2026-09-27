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

    /**
     * canonical key to the surface forms that should map onto it.
     *
     * The key is the generic name (or "a + b" for a fixed combination, so a card never shows only one ingredient);
     * the forms are its spellings in Latin and Devanagari, its brand names, and obvious spelling slips.
     * Names come from general knowledge of common Indian prescriptions and should be reviewed by a pharmacist.
     *
     * A form only ships if it does not fire the matcher on everyday words, because a sound-alike match turns an
     * ordinary word next to a dosing phrase into a guess. So some keys have few forms, and some have none: they
     * are still in the list the patient can pick from by hand. Names left out on purpose, and why:
     *  - Zyrtec: its consonants ("srtk") are exactly those of the Hindi phrase "से रात को" (at night).
     *  - Pan-D: one letter from "gonna do", a real Vosk mishearing. The plain form "pan" already existed.
     *  - Digene: one letter from "diagnosis" and "dosing".
     *  - Omez and Liv-52 were removed by decision: their consonants ("ms", "lv") match ordinary words that occur in real
     *    consultation speech ("major", "music", "always", "level"), which would put a false medicine card on screen. The
     *    omeprazole key keeps its other spellings; liv-52 keeps only its key, so it stays in the list to pick by hand.
     * About forty generic names also have no spelling here, for the same reason: warfarin sounds like "vote for" (a real
     * Vosk mishearing), digoxin like "diagnosis", and trimetazidine, erythromycin and tobramycin would out-rank
     * azithromycin for the real "thrombison". Those medicines are still in the list the patient picks from.
     * Adding any of these back needs a decision about the matching, not just the data.
     */
    private val ENTRIES: List<Pair<String, List<String>>> = listOf(
        "paracetamol" to listOf("paracetamol", "पैरासिटामोल", "पेरासिटामोल", "पैरासिटामॉल", "परासिटामोल", "crocin", "क्रोसिन", "dolo", "डोलो", "calpol", "कालपोल", "पारासेटामोल"),
        "azithromycin" to listOf("azithromycin", "एजिथ्रोमाइसिन", "एज़िथ्रोमाइसिन", "अजिथ्रोमाइसिन", "अझिथ्रोमाइसिन", "azithral", "एजिथ्रल", "azee", "azax"),
        "cetirizine" to listOf("cetirizine", "सेटिरिजिन", "सिटिरिजिन", "सेट्रीजीन", "सेटीरिजीन", "cetzine", "सेटजीन", "cetrizine"),
        "levocetirizine" to listOf("levocetirizine", "लेवोसेटिरिजिन", "लिवोसेटिरिजिन"),
        "amoxicillin" to listOf("amoxicillin", "एमोक्सिसिलिन", "अमोक्सिसिलिन", "amoxyclav", "एमोक्सीक्लेव", "एमोक्साइसिलिन", "amoxil", "एमोक्सिल", "अमोक्सिल", "नोवामोक्स", "mox"),
        "ibuprofen" to listOf("ibuprofen", "आइबुप्रोफेन", "इबुप्रोफेन", "brufen", "ब्रूफेन"),
        "pantoprazole" to listOf("pantoprazole", "पैंटोप्राजोल", "पैन्टोप्राजोल", "पंटोप्राजोल", "pan", "पैन", "pantoprazol", "pantaprazole", "पैंटाप्राज़ोल", "pantop", "पैंटोप", "पेंटोप"),
        "omeprazole" to listOf("omeprazole", "ओमेप्राजोल", "ओमीप्राजोल", "omeprazol"),
        "metformin" to listOf("metformin", "मेटफॉर्मिन", "मेटफार्मिन", "मेट्फोर्मिन", "metformine", "glycomet", "ग्लाइकोमेट", "ग्लायकोमेट", "glucophage", "ग्लुकोफाग", "riomet", "रियोमेट"),
        "amlodipine" to listOf("amlodipine", "एम्लोडिपिन", "अम्लोडिपिन", "amlodipin", "amlodac", "एम्लोडाक", "norvasc", "नोर्वास्क", "amlokind", "एम्लोकिन्ड"),
        "cefixime" to listOf("cefixime", "सेफिक्सिम", "सेफिक्साइम", "cefixim", "टैक्सिम-ओ", "टैक्सिमओ"),
        "ciprofloxacin" to listOf("ciprofloxacin", "सिप्रोफ्लोक्सासिन", "सिप्रोफ्लॉक्सासिन", "ciprofloxacine"),
        "ofloxacin" to listOf("ofloxacin", "ओफ्लोक्सासिन", "ज़ेन्फ्लोक्स"),
        "metronidazole" to listOf("metronidazole", "मेट्रोनिडाजोल", "flagyl", "फ्लैजिल", "metronidazol"),
        "domperidone" to listOf("domperidone", "डोम्पेरिडोन", "डोंपेरिडोन", "domperidon"),
        "ondansetron" to listOf("ondansetron", "ओंडानसेट्रॉन", "ओंडानसेट्रान", "ओन्डैंसेट्रोन"),
        "ranitidine" to listOf("ranitidine", "रैनिटिडिन", "रेनिटिडिन", "रानिटिडिन", "ranitidin", "rantac", "रैंटाक", "रैंटैक", "रेंटेक", "aciloc", "एसिलोक"),
        "diclofenac" to listOf("diclofenac", "डाइक्लोफेनाक", "डिक्लोफेनाक"),
        "montelukast" to listOf("montelukast", "मोंटेलुकास्ट", "मॉन्टेलुकास्ट", "मोन्टेलुकास्ट", "montair", "मोन्टैर", "मोंटेयर", "मोंटेर", "montek", "मोन्टेक", "मोंटेक", "मॉन्टेक", "romilast", "रोमिलास्ट"),
        "salbutamol" to listOf("salbutamol", "साल्बुटामोल", "asthalin", "अस्थालिन"),
        "ors" to listOf("ors", "ओआरएस", "ओ.आर.एस", "ओर्स"),
        "vitamin d" to listOf("विटामिन", "vitamin", "cholecalciferol", "चोलेकाल्सिफेरोल", "arachitol", "एराचिटोल"),
        "calcium" to listOf("calcium", "कैल्शियम", "कैल्सियम", "काल्सिउम", "shelcal", "शेल्काल", "शेलकल", "शेल्कैल", "काल्सिमाक्स", "gemcal"),
        "iron" to listOf("iron", "आयरन", "फेरस", "इरोन", "orofer", "ओरोफेर", "डेक्सोरैंग", "livogen", "लिवोगेन", "tonoferron", "टोनोफेरोन"),
        "zincovit" to listOf("zincovit", "जिंकोविट", "zinc", "जिंक", "ज़िन्कोविट"),
        "ibuprofen + paracetamol" to listOf(
            "combiflam", "कोम्बिफ्लाम", "कॉम्बिफ्लेम", "कंबिफ्लाम", "कॉम्बीफ्लाम", "ibugesic-plus", "ibugesicplus",
            "इबुगेसिक-प्लुस", "इबुगेसिकप्लुस"
        ),
        "aceclofenac" to listOf("aceclofenac", "एसेक्लोफेनाक", "hifenac", "हिफेनाक"),
        "aceclofenac + paracetamol" to listOf(
            "zerodol-p", "zerodolp", "ज़ेरोडोल-पी", "ज़ेरोडोलपी", "hifenac-p", "hifenacp", "हाइफेनैक-पी", "हाइफेनैकपी"
        ),
        "aceclofenac + paracetamol + serratiopeptidase" to listOf(
            "zerodol-sp", "zerodolsp", "ज़ेरोडोल-एसपी", "ज़ेरोडोलएसपी", "hifenac-sp", "hifenacsp", "हाइफेनैक-एसपी",
            "हाइफेनैकएसपी"
        ),
        "nimesulide" to listOf("nimesulide", "निमेसुलिड"),
        "etoricoxib" to listOf("नुकोक्सिया"),
        "ketorolac" to listOf("ketorolac", "केटोरोलाक"),
        "naproxen" to listOf("naproxen", "नाप्रोक्सेन"),
        "mefenamic acid" to listOf("mefenamic", "मेफेनामिक"),
        "mefenamic acid + dicyclomine" to listOf("meftal-spas", "meftalspas", "मेफ्टल-स्पास", "मेफ्टलस्पास"),
        "tramadol" to listOf(),
        "tramadol + paracetamol" to listOf(),
        "aspirin" to listOf(
            "aspirin", "एस्पिरिन", "ecosprin", "एकोस्प्रिन", "इकोस्प्रिन", "loprin", "लोप्रिन"
        ),
        "serratiopeptidase" to listOf("serratiopeptidase", "सेराटियोपेप्टिडास"),
        "amoxicillin + clavulanic acid" to listOf(
            "augmentin", "ऑग्मेन्टिन", "ऑगमेंटिन", "ऑगमेन्टिन", "औगमेंटिन", "clavam", "क्लावाम", "novaclav", "नोवाक्लाव",
            "moxikind-cv", "moxikindcv", "मोक्सिकाइंड-सीवी", "मोक्सिकाइंडसीवी", "mega-cv", "megacv"
        ),
        "clarithromycin" to listOf("clarithromycin", "क्लारिथ्रोमाइसिन", "क्रिक्सान"),
        "erythromycin" to listOf(),
        "cefpodoxime" to listOf("cefpodoxime", "सेफ्पोडोक्सिम", "doxcef"),
        "cefuroxime" to listOf("cefuroxime", "सेफुरोक्सिम", "ceftum", "सेफ्टुम"),
        "ceftriaxone" to listOf("सेफ्ट्रियाक्सोन"),
        "cefadroxil" to listOf("cefadroxil", "सेफाड्रोक्सिल"),
        "cephalexin" to listOf("cephalexin", "सेफालेक्सिन", "keflex", "केफ्लेक्स"),
        "ciprofloxacin + tinidazole" to listOf("cifran-ct", "cifranct", "सिप्लॉक्स-टीज़ेड", "सिप्लॉक्सटीज़ेड"),
        "ofloxacin + ornidazole" to listOf("zenflox-oz", "zenfloxoz", "ज़ेनफ्लॉक्स-ओज़ेड", "ज़ेनफ्लॉक्सओज़ेड"),
        "levofloxacin" to listOf("levofloxacin", "लेवोफ्लोक्सासिन", "levoflox", "लेवोफ्लोक्स"),
        "norfloxacin" to listOf("norfloxacin", "नोर्फ्लोक्सासिन", "नोर्फ्लोक्स"),
        "norfloxacin + tinidazole" to listOf("norflox-tz", "norfloxtz", "नॉरफ्लॉक्स-टीज़ेड", "नॉरफ्लॉक्सटीज़ेड"),
        "moxifloxacin" to listOf(
            "moxifloxacin", "मोक्सिफ्लोक्सासिन", "moxicip", "मोक्सिसिप", "vigamox", "विगामोक्स"
        ),
        "ornidazole" to listOf(
            "ornidazole", "ओर्निडाज़ोल", "dazolic", "डाज़ोलिक", "ornof", "ओर्नोफ"
        ),
        "tinidazole" to listOf("tiniba", "टिनिबा"),
        "doxycycline" to listOf("doxycycline", "डोक्साइसाइक्लिन", "vibramycin", "विब्रामाइसिन"),
        "cotrimoxazole" to listOf("cotrimoxazole", "कोट्रिमोक्साज़ोल", "co-trimoxazole", "को-ट्रिमोक्साज़ोल"),
        "nitrofurantoin" to listOf("nitrofurantoin", "निट्रोफुरैंटोइन", "furadantin", "फुराडैंटिन"),
        "linezolid" to listOf("linezolid", "लिनेज़ोलिड", "linospan", "लिनोस्पान"),
        "clindamycin" to listOf("clindamycin", "क्लिन्डामाइसिन"),
        "amikacin" to listOf(),
        "gentamicin" to listOf("gentamicin", "gentamycin"),
        "ampicillin" to listOf("ampicillin", "एम्पिसिलिन"),
        "ampicillin + cloxacillin" to listOf(),
        "cloxacillin" to listOf("cloxacillin", "क्लोक्सासिलिन"),
        "rifampicin" to listOf("rifampicin", "रिफाम्पिसिन", "rifampin", "रिफाम्पिन"),
        "isoniazid" to listOf("isoniazid", "इसोनियाज़िड"),
        "pyrazinamide" to listOf("pyrazinamide", "पाइराज़िनामिड"),
        "ethambutol" to listOf(),
        "rifampicin + isoniazid" to listOf(),
        "fexofenadine" to listOf(
            "fexofenadine", "फेक्सोफेनाडिन", "allegra", "एलेग्रा", "अलेग्रा", "फेक्सोफास्ट", "telfast", "टेल्फास्ट"
        ),
        "montelukast + levocetirizine" to listOf(
            "montek-lc", "monteklc", "मोंटेक-एलसी", "मोंटेकएलसी", "levocet-m", "levocetm", "लेवोसेट-एम", "लेवोसेटएम"
        ),
        "chlorpheniramine" to listOf("chlorpheniramine", "च्लोर्फेनिरामिन", "chlorpheniramin"),
        "diphenhydramine" to listOf("diphenhydramine", "डिफेन्हाइड्रामिन"),
        "cyproheptadine" to listOf("cyproheptadine", "साइप्रोहेप्टाडिन"),
        "hydroxyzine" to listOf("हाइड्रोक्साइज़िन"),
        "desloratadine" to listOf("desloratadine", "डेस्लोराटाडिन"),
        "bilastine" to listOf("बिलाक्स्टेन"),
        "pheniramine" to listOf("pheniramine", "फेनिरामिन"),
        "levosalbutamol" to listOf(
            "levosalbutamol", "लेवोसाल्बुटामोल", "levalbuterol", "लेवाल्बुटेरोल", "क्सोपेनेक्स"
        ),
        "levosalbutamol + ipratropium" to listOf(),
        "ipratropium" to listOf(),
        "budesonide" to listOf(
            "budesonide", "बुडेसोनिड", "budecort", "बुडेकोर्ट", "pulmicort", "पुल्मिकोर्ट"
        ),
        "budesonide + formoterol" to listOf("symbicort", "साइम्बिकोर्ट"),
        "salmeterol + fluticasone" to listOf(),
        "fluticasone" to listOf("fluticasone", "फ्लुटिकासोन"),
        "etofylline + theophylline" to listOf("deriphyllin", "डेरिफाइलिन"),
        "doxofylline" to listOf("doxofylline", "डोक्सोफाइलिन"),
        "ambroxol" to listOf(
            "ambroxol", "एम्ब्रोक्सोल", "ambrodil", "एम्ब्रोडिल", "mucolite", "मुकोलिट", "mucosolvan", "मुकोसोल्वान"
        ),
        "bromhexine" to listOf("bromhexine"),
        "guaifenesin" to listOf("guaifenesin", "गुऐफेनेसिन"),
        "dextromethorphan" to listOf("dextromethorphan", "डेक्स्ट्रोमेथोर्फान"),
        "terbutaline" to listOf(),
        "acetylcysteine" to listOf("acetylcysteine", "एसेटाइल्साइस्टेइन"),
        "ambroxol + levosalbutamol + guaifenesin" to listOf(),
        "paracetamol + phenylephrine + chlorpheniramine" to listOf(),
        "oxymetazoline" to listOf("oxymetazoline", "ओक्साइमेटाज़ोलिन"),
        "xylometazoline" to listOf("xylometazoline", "क्साइलोमेटाज़ोलिन"),
        "pantoprazole + domperidone" to listOf(
            "pantocid-dsr", "pantociddsr", "पैंटोसिड-डीएसआर", "पैंटोसिडडीएसआर", "pantodac-dsr", "pantodacdsr", "पैंटोडेक-डीएसआर",
            "पैंटोडेकडीएसआर"
        ),
        "omeprazole + domperidone" to listOf(),
        "esomeprazole" to listOf("esomeprazole", "एसोमेप्राज़ोल"),
        "rabeprazole" to listOf("rabeprazole", "राबेप्राज़ोल"),
        "rabeprazole + domperidone" to listOf("rabeloc-d", "rabelocd", "रैबेलॉक-डी", "रैबेलॉकडी"),
        "lansoprazole" to listOf("lansoprazole", "लैंसोप्राज़ोल", "lanzol", "लैंज़ोल"),
        "famotidine" to listOf("famotidine", "फामोटिडिन"),
        "metoclopramide" to listOf("metoclopramide", "मेटोक्लोप्रामिड"),
        "levosulpiride" to listOf("levosulpiride", "लेवोसुल्पिरिड"),
        "antacid" to listOf(
            "antacid", "ऐंटासिड", "डाइजीन", "डिजीन", "eno", "ईनो"
        ),
        "sucralfate" to listOf("sucralfate", "सुक्राल्फाट"),
        "dicyclomine" to listOf("dicyclomine", "डिसाइक्लोमिन"),
        "dicyclomine + paracetamol" to listOf("cyclopam", "साइक्लोपाम"),
        "drotaverine" to listOf("no-spa", "nospa", "नो-स्पा", "नोस्पा"),
        "hyoscine" to listOf("buscopan", "बुस्कोपान"),
        "loperamide" to listOf("loperamide", "लोपेरामिड"),
        "racecadotril" to listOf("racecadotril", "रासेकाडोट्रिल"),
        "probiotic" to listOf(
            "econorm", "एकोनोर्म", "enterogermina", "एन्टेरोगेर्मिना", "vizylac", "विज़ाइलाक", "bifilac", "बिफिलाक"
        ),
        "lactulose" to listOf("lactulose", "लाक्टुलोस", "duphalac", "डुफालाक"),
        "bisacodyl" to listOf("bisacodyl", "बिसाकोडाइल", "dulcolax", "डुल्कोलाक्स"),
        "isabgol" to listOf("isabgol", "इसाब्गोल"),
        "milk of magnesia + liquid paraffin" to listOf("cremaffin", "क्रेमाफिन"),
        "ursodeoxycholic acid" to listOf(),
        "liv-52" to listOf(),
        "silymarin" to listOf("silymarin", "सिलाइमारिन"),
        "rifaximin" to listOf(
            "rifaximin", "रिफाक्सिमिन", "rifagut", "रिफागुट", "क्सिफाक्सान"
        ),
        "pancreatin" to listOf("pancreatin", "पैंक्रीटिन"),
        "vitamin b complex" to listOf(
            "becosules", "बेकोसुलेस", "बेकोसूल्स", "बीकोसूल्स", "बेकोसुल्स", "neurobion", "नेउरोबियोन", "न्यूरोबायोन",
            "नयूरोबियोन", "beplex"
        ),
        "multivitamin" to listOf("multivitamin", "मुल्टिविटामिन", "revital", "रेविटाल"),
        "vitamin c" to listOf(),
        "folic acid" to listOf(),
        "methylcobalamin" to listOf(
            "methylcobalamin", "मेथाइल्कोबालामिन", "mecobalamin", "मेकोबालामिन", "methycobal", "मेथाइकोबाल"
        ),
        "vitamin e" to listOf("tocopherol", "टोकोफेरोल"),
        "telmisartan" to listOf(
            "telmisartan", "टेल्मिसार्टान", "telma", "टेल्मा", "तेल्मा", "telmikind", "टेल्मिकिन्ड", "telsartan", "टेल्सार्टान"
        ),
        "telmisartan + amlodipine" to listOf(
            "telma-am", "telmaam", "टेल्मा-एएम", "टेल्माएएम", "telmikind-am", "telmikindam", "टेल्मीकाइंड-एएम", "टेल्मीकाइंडएएम",
            "telsartan-am", "telsartanam", "टेलसार्टन-एएम", "टेलसार्टनएएम"
        ),
        "telmisartan + hydrochlorothiazide" to listOf(
            "telma-h", "telmah", "टेल्मा-एच", "टेल्माएच", "telmikind-h", "telmikindh", "टेल्मीकाइंड-एच", "टेल्मीकाइंडएच",
            "telsartan-h", "telsartanh", "टेलसार्टन-एच", "टेलसार्टनएच"
        ),
        "losartan" to listOf("losartan", "लोसार्टान", "रेपास"),
        "losartan + hydrochlorothiazide" to listOf(
            "लोसार-एच", "लोसारएच", "repace-h", "रेपेस-एच", "रेपेसएच"
        ),
        "olmesartan" to listOf("olmesartan", "ओल्मेसार्टान"),
        "atenolol" to listOf(
            "atenolol", "एटेनोलोल", "tenormin", "टेनोर्मिन", "betacard", "बेटाकार्ड"
        ),
        "metoprolol" to listOf(
            "metoprolol", "मेटोप्रोलोल", "betaloc", "बेटालोक", "lopressor", "लोप्रेसोर"
        ),
        "bisoprolol" to listOf(),
        "carvedilol" to listOf("carvedilol", "कार्वेडिलोल"),
        "propranolol" to listOf(),
        "nebivolol" to listOf("nebivolol", "नेबिवोलोल", "bystolic", "बाइस्टोलिक"),
        "ramipril" to listOf("ramipril", "रामिप्रिल"),
        "enalapril" to listOf("enalapril", "एनालाप्रिल"),
        "lisinopril" to listOf("lisinopril", "लिसिनोप्रिल"),
        "hydrochlorothiazide" to listOf("hydrochlorothiazide", "हाइड्रोच्लोरोथियाज़िड"),
        "chlorthalidone" to listOf("chlorthalidone", "च्लोर्थालिडोन"),
        "furosemide" to listOf(),
        "furosemide + spironolactone" to listOf(),
        "spironolactone" to listOf("spironolactone", "स्पिरोनोलाक्टोन"),
        "torsemide" to listOf(),
        "clopidogrel" to listOf(
            "clopidogrel", "क्लोपिडोग्रेल", "deplatt", "डेप्लाट", "clopitab", "क्लोपिटाब"
        ),
        "aspirin + clopidogrel" to listOf("deplatt-a", "deplatta", "डेप्लैट-ए", "डेप्लैटए"),
        "aspirin + atorvastatin" to listOf("ecosprin-av", "ecosprinav", "इकोस्प्रिन-एवी", "इकोस्प्रिनएवी"),
        "atorvastatin" to listOf("atorvastatin", "एटोर्वास्टाटिन"),
        "rosuvastatin" to listOf("rosuvastatin", "रोसुवास्टाटिन", "razel", "राज़ेल"),
        "fenofibrate" to listOf("fenofibrate", "फेनोफिब्राट", "lipicard", "लिपिकार्ड"),
        "isosorbide mononitrate" to listOf("imdur", "इम्डुर"),
        "isosorbide dinitrate" to listOf("sorbitrate", "सोर्बिट्राट"),
        "nitroglycerin" to listOf("nitroglycerin", "निट्रोग्लाइसेरिन", "angispan", "ऐंगिस्पान"),
        "ranolazine" to listOf("ranolazine", "रानोलाज़िन", "रानोज़ेक्स"),
        "trimetazidine" to listOf(),
        "digoxin" to listOf("डिगोक्सिन"),
        "warfarin" to listOf("uniwarfin", "उनिवार्फिन"),
        "amiodarone" to listOf("cordarone", "कोर्डारोन"),
        "diltiazem" to listOf("diltiazem", "डिल्टियाज़ेम", "dilzem", "डिल्ज़ेम"),
        "nifedipine" to listOf("nifedipine", "निफेडिपिन", "depin", "डेपिन"),
        "clonidine" to listOf(),
        "methyldopa" to listOf("methyldopa", "मेथाइल्डोपा"),
        "prazosin" to listOf(),
        "glimepiride" to listOf(
            "glimepiride", "ग्लिमेपिरिड", "amaryl", "एमाराइल", "glimestar", "ग्लिमेस्टार", "glimy", "ग्लिमाइ"
        ),
        "glimepiride + metformin" to listOf(
            "glycomet-gp", "glycometgp", "ग्लाइकोमेट-जीपी", "ग्लाइकोमेटजीपी", "zoryl-m", "zorylm", "ज़ोरिल-एम", "ज़ोरिलएम",
            "glimestar-m", "glimestarm", "ग्लाइमस्टार-एम", "ग्लाइमस्टारएम"
        ),
        "gliclazide" to listOf("gliclazide", "ग्लिक्लाज़िड", "glizid", "ग्लिज़िड"),
        "glipizide" to listOf("glipizide", "ग्लिपिज़िड", "glucotrol", "ग्लुकोट्रोल"),
        "sitagliptin" to listOf("sitagliptin", "सिटाग्लिप्टिन"),
        "sitagliptin + metformin" to listOf(),
        "vildagliptin" to listOf("vildagliptin", "विल्डाग्लिप्टिन", "galvus", "गाल्वुस"),
        "vildagliptin + metformin" to listOf(
            "galvus-met", "galvusmet", "गैल्वस-मेट", "गैल्वसमेट", "zomelis-met", "zomelismet", "ज़ोमेलिस-मेट", "ज़ोमेलिसमेट"
        ),
        "teneligliptin" to listOf("teneligliptin", "टेनेलिग्लिप्टिन"),
        "linagliptin" to listOf("linagliptin", "लिनाग्लिप्टिन"),
        "pioglitazone" to listOf("pioglitazone", "पियोग्लिटाज़ोन"),
        "dapagliflozin" to listOf(
            "dapagliflozin", "डापाग्लिफ्लोज़िन", "forxiga", "फोर्क्सिगा", "oxra"
        ),
        "empagliflozin" to listOf("empagliflozin", "एम्पाग्लिफ्लोज़िन"),
        "voglibose" to listOf("voglibose", "वोग्लिबोस"),
        "acarbose" to listOf("acarbose", "एकार्बोस", "glucobay", "ग्लुकोबे"),
        "insulin" to listOf(
            "insulin", "इन्सुलिन", "मिक्स्टार्ड", "huminsulin", "हुमिन्सुलिन", "नोवोमिक्स"
        ),
        "insulin glargine" to listOf(),
        "insulin degludec" to listOf(),
        "levothyroxine" to listOf("लेवोथाइरोक्सिन", "thyronorm", "थाइरोनोर्म", "एल्ट्रोक्सिन"),
        "carbimazole" to listOf("carbimazole", "कार्बिमाज़ोल", "neomercazole", "नियोमेर्काज़ोल"),
        "prednisolone" to listOf(
            "prednisolone", "प्रेड्निसोलोन", "wysolone", "वाइसोलोन", "omnacortil", "ओम्नाकोर्टिल"
        ),
        "methylprednisolone" to listOf("methylprednisolone", "मेथाइल्प्रेड्निसोलोन"),
        "dexamethasone" to listOf("dexamethasone", "डेक्सामेथासोन"),
        "deflazacort" to listOf("deflazacort", "डेफ्लाज़ाकोर्ट"),
        "hydrocortisone" to listOf("hydrocortisone", "हाइड्रोकोर्टिसोन", "efcorlin", "एफ्कोर्लिन"),
        "betamethasone" to listOf("betamethasone", "बेटामेथासोन", "betnesol", "बेट्नेसोल"),
        "clobetasol" to listOf("clobetasol", "क्लोबेटासोल", "dermovate", "डेर्मोवाट"),
        "mometasone" to listOf(),
        "minoxidil" to listOf("minoxidil", "मिनोक्सिडिल"),
        "finasteride" to listOf("finasteride", "फिनास्टेरिड"),
        "tamsulosin" to listOf("tamsulosin", "टाम्सुलोसिन"),
        "dutasteride" to listOf("dutasteride", "डुटास्टेरिड", "avodart", "एवोडार्ट"),
        "silodosin" to listOf("silodosin", "सिलोडोसिन"),
        "alendronate" to listOf("alendronate", "एलेन्ड्रोनाट", "fosamax", "फोसामाक्स"),
        "calcitriol" to listOf("calcitriol", "काल्सिट्रियोल"),
        "progesterone" to listOf(),
        "dydrogesterone" to listOf(),
        "norethisterone" to listOf("norethisterone", "नोरेथिस्टेरोन"),
        "medroxyprogesterone" to listOf("medroxyprogesterone", "मेड्रोक्साइप्रोगेस्टेरोन"),
        "tranexamic acid" to listOf("ट्रानेक्सामिक", "cyklokapron", "साइक्लोकाप्रोन"),
        "clomiphene" to listOf("clomiphene", "क्लोमिफेन", "siphene", "सिफेन"),
        "letrozole" to listOf("letrozole", "लेट्रोज़ोल"),
        "gabapentin" to listOf("gabapentin", "गाबापेन्टिन", "gabantin", "गाबैंटिन"),
        "pregabalin" to listOf(
            "pregabalin", "प्रेगाबालिन", "lyrica", "लाइरिका", "pregalin", "प्रेगालिन"
        ),
        "amitriptyline" to listOf("amitriptyline", "एमिट्रिप्टाइलिन"),
        "nortriptyline" to listOf("nortriptyline", "नोर्ट्रिप्टाइलिन"),
        "escitalopram" to listOf(
            "escitalopram", "एस्सिटालोप्राम", "सिप्रालेक्स", "lexapro", "लेक्साप्रो"
        ),
        "sertraline" to listOf("sertraline", "सेर्ट्रालिन"),
        "fluoxetine" to listOf("fluoxetine", "फ्लुओक्सेटिन"),
        "paroxetine" to listOf("paroxetine", "पाक्सिल", "pexep", "पेक्सेप"),
        "clonazepam" to listOf("clonazepam", "क्लोनाज़ेपाम", "lonazep", "लोनाज़ेप"),
        "alprazolam" to listOf(
            "alprazolam", "एल्प्राज़ोलाम", "alprax", "एल्प्राक्स", "xanax", "क्सानाक्स"
        ),
        "lorazepam" to listOf("lorazepam", "लोराज़ेपाम"),
        "diazepam" to listOf("diazepam", "डियाज़ेपाम"),
        "zolpidem" to listOf("zolpidem", "ज़ोल्पिडेम", "ambien", "एम्बीन"),
        "melatonin" to listOf("melatonin", "मेलाटोनिन"),
        "levetiracetam" to listOf("levetiracetam", "लेवेटिरासेटाम", "levipil", "लेविपिल"),
        "sodium valproate" to listOf("valproate", "वाल्प्रोएट", "valparin", "वाल्पारिन"),
        "carbamazepine" to listOf("carbamazepine", "कार्बामाज़ेपिन"),
        "phenytoin" to listOf("phenytoin", "फेनाइटोइन"),
        "oxcarbazepine" to listOf("oxcarbazepine", "ओक्स्कार्बाज़ेपिन"),
        "lamotrigine" to listOf(),
        "topiramate" to listOf("topiramate", "टोपिरामाट", "topamax"),
        "sumatriptan" to listOf("sumatriptan", "सुमाट्रिप्टान"),
        "flunarizine" to listOf(
            "flunarizine", "फ्लुनारिज़िन", "sibelium", "सिबेलिउम", "flunarin", "फ्लुनारिन"
        ),
        "betahistine" to listOf("betahistine", "बेटाहिस्टिन", "serc", "सेर्क"),
        "cinnarizine" to listOf("cinnarizine", "सिनारिज़िन"),
        "prochlorperazine" to listOf("prochlorperazine", "प्रोच्लोर्पेराज़िन"),
        "promethazine" to listOf(),
        "haloperidol" to listOf("haloperidol", "हालोपेरिडोल"),
        "olanzapine" to listOf("olanzapine", "ओलैंज़ापिन"),
        "risperidone" to listOf("risperidone", "रिस्पेरिडोन", "risperdal", "रिस्पेर्डाल"),
        "quetiapine" to listOf("quetiapine", "कुएटियापिन"),
        "lithium" to listOf("lithium", "लिथिउम"),
        "donepezil" to listOf("donepezil", "डोनेपेज़िल"),
        "memantine" to listOf(),
        "levodopa + carbidopa" to listOf("tidomet", "टिडोमेट"),
        "baclofen" to listOf("baclofen", "बाक्लोफेन"),
        "tizanidine" to listOf("tizanidine", "टिज़ानिडिन"),
        "thiocolchicoside" to listOf("thiocolchicoside", "थियोकोल्चिकोसिड", "myoril", "म्योरिल"),
        "allopurinol" to listOf("allopurinol", "एलोपुरिनोल"),
        "febuxostat" to listOf(
            "febuxostat", "फेबुक्सोस्टाट", "zurig", "ज़ुरिग", "uloric", "उलोरिक"
        ),
        "colchicine" to listOf("colchicine", "कोल्चिसिन", "zycolchin", "ज़ाइकोल्चिन"),
        "hydroxychloroquine" to listOf("hydroxychloroquine", "हाइड्रोक्साइच्लोरोकुइन", "plaquenil", "प्लाकुएनिल"),
        "methotrexate" to listOf("मेथोट्रेक्साट", "फोलिट्राक्स"),
        "sulfasalazine" to listOf("sulfasalazine", "सुल्फासालाज़िन", "salazopyrin", "सालाज़ोपाइरिन"),
        "leflunomide" to listOf("leflunomide", "लेफ्लुनोमिड"),
        "albendazole" to listOf("albendazole", "एल्बेन्डाज़ोल"),
        "ivermectin" to listOf(
            "ivermectin", "इवेर्मेक्टिन", "ivecop", "इवेकोप", "ivermectol", "इवेर्मेक्टोल"
        ),
        "mebendazole" to listOf("mebendazole", "मेबेन्डाज़ोल"),
        "fluconazole" to listOf("fluconazole", "फ्लुकोनाज़ोल", "diflucan", "डिफ्लुकान"),
        "itraconazole" to listOf("itraconazole", "इट्राकोनाज़ोल", "स्पोरानोक्स"),
        "terbinafine" to listOf("terbinafine", "टेर्बिनाफिन"),
        "clotrimazole" to listOf("clotrimazole", "क्लोट्रिमाज़ोल"),
        "ketoconazole" to listOf("ketoconazole", "केटोकोनाज़ोल"),
        "acyclovir" to listOf("acyclovir", "एसाइक्लोविर"),
        "valacyclovir" to listOf("valacyclovir", "वालासाइक्लोविर", "वाल्ट्रेक्स"),
        "oseltamivir" to listOf("oseltamivir", "ओसेल्टामिविर", "tamiflu", "टामिफ्लु"),
        "chloroquine" to listOf("chloroquine", "च्लोरोकुइन"),
        "primaquine" to listOf("primaquine", "प्रिमाकुइन"),
        "artemether + lumefantrine" to listOf("lumerax", "लुमेराक्स"),
        "mupirocin" to listOf("bactroban", "बाक्ट्रोबान"),
        "fusidic acid" to listOf("fucidin", "फुसिडिन"),
        "povidone iodine" to listOf(),
        "permethrin" to listOf("scabper", "स्काब्पेर"),
        "calamine" to listOf("calamine", "कालामिन"),
        "carboxymethylcellulose" to listOf("carboxymethylcellulose", "कार्बोक्साइमेथाइल्सेलुलोस"),
        "tobramycin" to listOf(),
        "olopatadine" to listOf("olopatadine", "ओलोपाटाडिन"),
        "latanoprost" to listOf("latanoprost", "लाटानोप्रोस्ट", "xalatan"),
        "lignocaine" to listOf("lignocaine", "लिग्नोकैन", "क्साइलोकैन"),
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

    /** [form] is the spelling the skeleton came from, so a very short entry can be compared letter by letter. */
    private data class Skeleton(val key: String, val skeleton: String, val form: String)

    private val SKELETONS: List<Skeleton> = BY_FORM.entries
        .map { Skeleton(it.value, Phonetic.skeleton(it.key), it.key) }
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
            val rank = phoneticRank(token, c.skeleton, normalizedToken, c.form) ?: continue
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
        "combiflam" to listOf("combiflam", "कोम्बिफ्लाम", "कॉम्बिफ्लेम", "कंबिफ्लाम", "कॉम्बीफ्लाम"),
        "ibugesic-plus" to listOf("ibugesic-plus", "ibugesicplus", "इबुगेसिक-प्लुस", "इबुगेसिकप्लुस"),
        "hifenac" to listOf("hifenac", "हिफेनाक"),
        "zerodol-p" to listOf("zerodol-p", "zerodolp", "ज़ेरोडोल-पी", "ज़ेरोडोलपी"),
        "hifenac-p" to listOf("hifenac-p", "hifenacp", "हाइफेनैक-पी", "हाइफेनैकपी"),
        "zerodol-sp" to listOf("zerodol-sp", "zerodolsp", "ज़ेरोडोल-एसपी", "ज़ेरोडोलएसपी"),
        "hifenac-sp" to listOf("hifenac-sp", "hifenacsp", "हाइफेनैक-एसपी", "हाइफेनैकएसपी"),
        "nucoxia" to listOf("नुकोक्सिया"),
        "meftal-spas" to listOf("meftal-spas", "meftalspas", "मेफ्टल-स्पास", "मेफ्टलस्पास"),
        "ecosprin" to listOf("ecosprin", "एकोस्प्रिन", "इकोस्प्रिन"),
        "loprin" to listOf("loprin", "लोप्रिन"),
        "amoxil" to listOf("amoxil", "एमोक्सिल", "अमोक्सिल"),
        "novamox" to listOf("नोवामोक्स"),
        "mox" to listOf("mox"),
        "augmentin" to listOf("augmentin", "ऑग्मेन्टिन", "ऑगमेंटिन", "ऑगमेन्टिन", "औगमेंटिन"),
        "clavam" to listOf("clavam", "क्लावाम"),
        "novaclav" to listOf("novaclav", "नोवाक्लाव"),
        "moxikind-cv" to listOf("moxikind-cv", "moxikindcv", "मोक्सिकाइंड-सीवी", "मोक्सिकाइंडसीवी"),
        "mega-cv" to listOf("mega-cv", "megacv"),
        "azee" to listOf("azee", "एज़ी"),
        "azax" to listOf("azax"),
        "crixan" to listOf("क्रिक्सान"),
        "taxim-o" to listOf("टैक्सिम-ओ", "टैक्सिमओ"),
        "doxcef" to listOf("doxcef"),
        "ceftum" to listOf("ceftum", "सेफ्टुम"),
        "zocef" to listOf("ज़ोसेफ"),
        "keflex" to listOf("keflex", "केफ्लेक्स"),
        "cifran-ct" to listOf("cifran-ct", "cifranct"),
        "ciplox-tz" to listOf("सिप्लॉक्स-टीज़ेड", "सिप्लॉक्सटीज़ेड"),
        "zenflox" to listOf("ज़ेन्फ्लोक्स"),
        "zenflox-oz" to listOf("zenflox-oz", "zenfloxoz", "ज़ेनफ्लॉक्स-ओज़ेड", "ज़ेनफ्लॉक्सओज़ेड"),
        "levoflox" to listOf("levoflox", "लेवोफ्लोक्स"),
        "norflox" to listOf("नोर्फ्लोक्स"),
        "norflox-tz" to listOf("norflox-tz", "norfloxtz", "नॉरफ्लॉक्स-टीज़ेड", "नॉरफ्लॉक्सटीज़ेड"),
        "moxicip" to listOf("moxicip", "मोक्सिसिप"),
        "vigamox" to listOf("vigamox", "विगामोक्स"),
        "dazolic" to listOf("dazolic", "डाज़ोलिक"),
        "ornof" to listOf("ornof", "ओर्नोफ"),
        "tiniba" to listOf("tiniba", "टिनिबा"),
        "vibramycin" to listOf("vibramycin", "विब्रामाइसिन"),
        "furadantin" to listOf("furadantin", "फुराडैंटिन"),
        "linospan" to listOf("linospan", "लिनोस्पान"),
        "allegra" to listOf("allegra", "एलेग्रा", "अलेग्रा"),
        "fexofast" to listOf("फेक्सोफास्ट"),
        "telfast" to listOf("telfast", "टेल्फास्ट"),
        "montair" to listOf("montair", "मोन्टैर", "मोंटेयर", "मोंटेर"),
        "montek" to listOf("montek", "मोन्टेक", "मोंटेक", "मॉन्टेक"),
        "romilast" to listOf("romilast", "रोमिलास्ट"),
        "montek-lc" to listOf("montek-lc", "monteklc", "मोंटेक-एलसी", "मोंटेकएलसी"),
        "levocet-m" to listOf("levocet-m", "levocetm", "लेवोसेट-एम", "लेवोसेटएम"),
        "bilaxten" to listOf("बिलाक्स्टेन"),
        "xopenex" to listOf("क्सोपेनेक्स"),
        "budecort" to listOf("budecort", "बुडेकोर्ट"),
        "pulmicort" to listOf("pulmicort", "पुल्मिकोर्ट"),
        "symbicort" to listOf("symbicort", "साइम्बिकोर्ट"),
        "deriphyllin" to listOf("deriphyllin", "डेरिफाइलिन"),
        "ambrodil" to listOf("ambrodil", "एम्ब्रोडिल"),
        "mucolite" to listOf("mucolite", "मुकोलिट"),
        "mucosolvan" to listOf("mucosolvan", "मुकोसोल्वान"),
        "pantop" to listOf("pantop", "पैंटोप", "पेंटोप"),
        "pantocid-dsr" to listOf("pantocid-dsr", "pantociddsr", "पैंटोसिड-डीएसआर", "पैंटोसिडडीएसआर"),
        "pantodac-dsr" to listOf("pantodac-dsr", "pantodacdsr", "पैंटोडेक-डीएसआर", "पैंटोडेकडीएसआर"),
        "rabeloc-d" to listOf("rabeloc-d", "rabelocd", "रैबेलॉक-डी", "रैबेलॉकडी"),
        "lanzol" to listOf("lanzol", "लैंज़ोल"),
        "rantac" to listOf("rantac", "रैंटाक", "रैंटैक", "रेंटेक"),
        "aciloc" to listOf("aciloc", "एसिलोक"),
        "digene" to listOf("डाइजीन", "डिजीन"),
        "eno" to listOf("eno", "ईनो"),
        "cyclopam" to listOf("cyclopam", "साइक्लोपाम"),
        "no-spa" to listOf("no-spa", "nospa", "नो-स्पा", "नोस्पा"),
        "buscopan" to listOf("buscopan", "बुस्कोपान"),
        "econorm" to listOf("econorm", "एकोनोर्म"),
        "enterogermina" to listOf("enterogermina", "एन्टेरोगेर्मिना"),
        "vizylac" to listOf("vizylac", "विज़ाइलाक"),
        "bifilac" to listOf("bifilac", "बिफिलाक"),
        "duphalac" to listOf("duphalac", "डुफालाक"),
        "dulcolax" to listOf("dulcolax", "डुल्कोलाक्स"),
        "cremaffin" to listOf("cremaffin", "क्रेमाफिन"),
        "rifagut" to listOf("rifagut", "रिफागुट"),
        "xifaxan" to listOf("क्सिफाक्सान"),
        "becosules" to listOf("becosules", "बेकोसुलेस", "बेकोसूल्स", "बीकोसूल्स", "बेकोसुल्स"),
        "neurobion" to listOf("neurobion", "नेउरोबियोन", "न्यूरोबायोन", "नयूरोबियोन"),
        "beplex" to listOf("beplex"),
        "revital" to listOf("revital", "रेविटाल"),
        "methycobal" to listOf("methycobal", "मेथाइकोबाल"),
        "amlodac" to listOf("amlodac", "एम्लोडाक"),
        "norvasc" to listOf("norvasc", "नोर्वास्क"),
        "amlokind" to listOf("amlokind", "एम्लोकिन्ड"),
        "telma" to listOf("telma", "टेल्मा", "तेल्मा"),
        "telmikind" to listOf("telmikind", "टेल्मिकिन्ड"),
        "telsartan" to listOf("telsartan", "टेल्सार्टान"),
        "telma-am" to listOf("telma-am", "telmaam", "टेल्मा-एएम", "टेल्माएएम"),
        "telmikind-am" to listOf("telmikind-am", "telmikindam", "टेल्मीकाइंड-एएम", "टेल्मीकाइंडएएम"),
        "telsartan-am" to listOf("telsartan-am", "telsartanam", "टेलसार्टन-एएम", "टेलसार्टनएएम"),
        "telma-h" to listOf("telma-h", "telmah", "टेल्मा-एच", "टेल्माएच"),
        "telmikind-h" to listOf("telmikind-h", "telmikindh", "टेल्मीकाइंड-एच", "टेल्मीकाइंडएच"),
        "telsartan-h" to listOf("telsartan-h", "telsartanh", "टेलसार्टन-एच", "टेलसार्टनएच"),
        "repace" to listOf("रेपास"),
        "losar-h" to listOf("लोसार-एच", "लोसारएच"),
        "repace-h" to listOf("repace-h", "रेपेस-एच", "रेपेसएच"),
        "tenormin" to listOf("tenormin", "टेनोर्मिन"),
        "betacard" to listOf("betacard", "बेटाकार्ड"),
        "betaloc" to listOf("betaloc", "बेटालोक"),
        "lopressor" to listOf("lopressor", "लोप्रेसोर"),
        "bystolic" to listOf("bystolic", "बाइस्टोलिक"),
        "deplatt" to listOf("deplatt", "डेप्लाट"),
        "clopitab" to listOf("clopitab", "क्लोपिटाब"),
        "deplatt-a" to listOf("deplatt-a", "deplatta", "डेप्लैट-ए", "डेप्लैटए"),
        "ecosprin-av" to listOf("ecosprin-av", "ecosprinav", "इकोस्प्रिन-एवी", "इकोस्प्रिनएवी"),
        "razel" to listOf("razel", "राज़ेल"),
        "lipicard" to listOf("lipicard", "लिपिकार्ड"),
        "imdur" to listOf("imdur", "इम्डुर"),
        "sorbitrate" to listOf("sorbitrate", "सोर्बिट्राट"),
        "angispan" to listOf("angispan", "ऐंगिस्पान"),
        "ranozex" to listOf("रानोज़ेक्स"),
        "uniwarfin" to listOf("uniwarfin", "उनिवार्फिन"),
        "cordarone" to listOf("cordarone", "कोर्डारोन"),
        "dilzem" to listOf("dilzem", "डिल्ज़ेम"),
        "depin" to listOf("depin", "डेपिन"),
        "glycomet" to listOf("glycomet", "ग्लाइकोमेट", "ग्लायकोमेट"),
        "glucophage" to listOf("glucophage", "ग्लुकोफाग"),
        "riomet" to listOf("riomet", "रियोमेट"),
        "amaryl" to listOf("amaryl", "एमाराइल"),
        "glimestar" to listOf("glimestar", "ग्लिमेस्टार"),
        "glimy" to listOf("glimy", "ग्लिमाइ"),
        "glycomet-gp" to listOf("glycomet-gp", "glycometgp", "ग्लाइकोमेट-जीपी", "ग्लाइकोमेटजीपी"),
        "zoryl-m" to listOf("zoryl-m", "zorylm", "ज़ोरिल-एम", "ज़ोरिलएम"),
        "glimestar-m" to listOf("glimestar-m", "glimestarm", "ग्लाइमस्टार-एम", "ग्लाइमस्टारएम"),
        "glizid" to listOf("glizid", "ग्लिज़िड"),
        "glucotrol" to listOf("glucotrol", "ग्लुकोट्रोल"),
        "galvus" to listOf("galvus", "गाल्वुस"),
        "galvus-met" to listOf("galvus-met", "galvusmet", "गैल्वस-मेट", "गैल्वसमेट"),
        "zomelis-met" to listOf("zomelis-met", "zomelismet", "ज़ोमेलिस-मेट", "ज़ोमेलिसमेट"),
        "forxiga" to listOf("forxiga", "फोर्क्सिगा"),
        "oxra" to listOf("oxra"),
        "glucobay" to listOf("glucobay", "ग्लुकोबे"),
        "mixtard" to listOf("मिक्स्टार्ड"),
        "huminsulin" to listOf("huminsulin", "हुमिन्सुलिन"),
        "novomix" to listOf("नोवोमिक्स"),
        "thyronorm" to listOf("thyronorm", "थाइरोनोर्म"),
        "eltroxin" to listOf("एल्ट्रोक्सिन"),
        "neomercazole" to listOf("neomercazole", "नियोमेर्काज़ोल"),
        "wysolone" to listOf("wysolone", "वाइसोलोन"),
        "omnacortil" to listOf("omnacortil", "ओम्नाकोर्टिल"),
        "efcorlin" to listOf("efcorlin", "एफ्कोर्लिन"),
        "betnesol" to listOf("betnesol", "बेट्नेसोल"),
        "dermovate" to listOf("dermovate", "डेर्मोवाट"),
        "avodart" to listOf("avodart", "एवोडार्ट"),
        "fosamax" to listOf("fosamax", "फोसामाक्स"),
        "cyklokapron" to listOf("cyklokapron", "साइक्लोकाप्रोन"),
        "siphene" to listOf("siphene", "सिफेन"),
        "gabantin" to listOf("gabantin", "गाबैंटिन"),
        "lyrica" to listOf("lyrica", "लाइरिका"),
        "pregalin" to listOf("pregalin", "प्रेगालिन"),
        "cipralex" to listOf("सिप्रालेक्स"),
        "lexapro" to listOf("lexapro", "लेक्साप्रो"),
        "paxil" to listOf("पाक्सिल"),
        "pexep" to listOf("pexep", "पेक्सेप"),
        "lonazep" to listOf("lonazep", "लोनाज़ेप"),
        "alprax" to listOf("alprax", "एल्प्राक्स"),
        "xanax" to listOf("xanax", "क्सानाक्स"),
        "ambien" to listOf("ambien", "एम्बीन"),
        "levipil" to listOf("levipil", "लेविपिल"),
        "valparin" to listOf("valparin", "वाल्पारिन"),
        "topamax" to listOf("topamax"),
        "sibelium" to listOf("sibelium", "सिबेलिउम"),
        "flunarin" to listOf("flunarin", "फ्लुनारिन"),
        "serc" to listOf("serc", "सेर्क"),
        "risperdal" to listOf("risperdal", "रिस्पेर्डाल"),
        "tidomet" to listOf("tidomet", "टिडोमेट"),
        "myoril" to listOf("myoril", "म्योरिल"),
        "zurig" to listOf("zurig", "ज़ुरिग"),
        "uloric" to listOf("uloric", "उलोरिक"),
        "zycolchin" to listOf("zycolchin", "ज़ाइकोल्चिन"),
        "plaquenil" to listOf("plaquenil", "प्लाकुएनिल"),
        "folitrax" to listOf("फोलिट्राक्स"),
        "salazopyrin" to listOf("salazopyrin", "सालाज़ोपाइरिन"),
        "ivecop" to listOf("ivecop", "इवेकोप"),
        "ivermectol" to listOf("ivermectol", "इवेर्मेक्टोल"),
        "diflucan" to listOf("diflucan", "डिफ्लुकान"),
        "sporanox" to listOf("स्पोरानोक्स"),
        "candid" to listOf("candid"),
        "valtrex" to listOf("वाल्ट्रेक्स"),
        "tamiflu" to listOf("tamiflu", "टामिफ्लु"),
        "lumerax" to listOf("lumerax", "लुमेराक्स"),
        "bactroban" to listOf("bactroban", "बाक्ट्रोबान"),
        "fucidin" to listOf("fucidin", "फुसिडिन"),
        "scabper" to listOf("scabper", "स्काब्पेर"),
        "refresh" to listOf("refresh"),
        "xalatan" to listOf("xalatan"),
        "xylocaine" to listOf("क्साइलोकैन"),
        "shelcal" to listOf("shelcal", "शेल्काल", "शेलकल", "शेल्कैल"),
        "calcimax" to listOf("काल्सिमाक्स"),
        "gemcal" to listOf("gemcal"),
        "arachitol" to listOf("arachitol", "एराचिटोल"),
        "orofer" to listOf("orofer", "ओरोफेर"),
        "dexorange" to listOf("डेक्सोरैंग"),
        "livogen" to listOf("livogen", "लिवोगेन"),
        "tonoferron" to listOf("tonoferron", "टोनोफेरोन"),
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

    /** Of a very short entry's letters, the share that may differ from the heard word. Not the shortlist's MAX_DISTANCE. */
    private const val SHORT_ENTRY_MAX_LETTER_DISTANCE = 0.5

    /** 0 = same skeleton, 1 = clipped prefix, 2 = one slip. Lower is a tighter match. */
    private fun phoneticRank(token: String, lex: String, word: String, form: String): Int? {
        val tokenLength = word.length
        // Speech models often drop the first syllable ("azithromycin" heard as "thromison", so
        // "strmsn" arrives as "trmsn"). At five or more consonants a single slip anywhere is far
        // too specific to be a coincidence, so only the one-slip tier may ignore the first letter.
        if (token[0] != lex[0]) {
            return if (token.length >= 5 && lex.length >= 5 && Normalize.editDistance(token, lex) <= 1) 2 else null
        }

        // A two-consonant skeleton (dolo = "dl", ors = "rs") is too short to trust on its own, so it only
        // accepts a longer, clearly word-like token that begins with it ("dollar" = "dlr"). Two shared consonants
        // are also all that "resin", "reason" and "russian" have with ORS, or "panel" and "paint" with Pan, so the
        // WORD itself must be close to the entry, letter by letter: at most half its letters may differ. "dollar" for
        // dolo is exactly half; "resin" for ORS is four fifths.
        if (lex.length == 2) {
            if (token.length != 3 || !token.startsWith(lex) || tokenLength < 5) return null
            val distance = Normalize.editDistance(word, form).toDouble() / maxOf(word.length, form.length)
            return if (distance <= SHORT_ENTRY_MAX_LETTER_DISTANCE) 2 else null
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
