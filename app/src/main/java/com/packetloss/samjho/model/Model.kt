package com.packetloss.samjho.model

/**
 * One line of the consultation transcript. [index] is the stable id that every extracted item
 * cites, so the app can always show the patient the exact words an item came from.
 */
data class TranscriptLine(val index: Int, val text: String)

enum class Language { HINDI, ENGLISH }

/** Rules run first. The on-device LLM may only ADD items, tagged [AI]; it never overwrites [RULES]. */
enum class Provenance {
    RULES,
    AI,

    /** The medicine was not spoken at all: the patient confirmed it from the prescription paper. */
    PRESCRIPTION,
}

enum class TimeOfDay { MORNING, AFTERNOON, EVENING, NIGHT }

enum class FoodRelation { BEFORE_FOOD, AFTER_FOOD, EMPTY_STOMACH, WITH_FOOD }

/** A detail the doctor never stated. Shown as "not mentioned" so it is never silently guessed. */
enum class MissingField { TIMES_PER_DAY, TIME_OF_DAY, FOOD_RELATION, DURATION }

/** A dosing detail a medicine can carry. Used to say which of them the doctor gave on a later line. */
enum class Detail { TIMES_PER_DAY, TIME_OF_DAY, DOSE, FOOD, DURATION }

/**
 * Details the doctor said on a LATER line than the medicine's own name ("Take paracetamol for 3 days." ... "After
 * food."). Each keeps its own transcript line, so the card can show that the food instruction was a separate
 * sentence and never passes it off as part of the medicine's own line.
 */
data class Continuation(val line: Int, val details: List<Detail>)

/** One way the speech recogniser thought an utterance might read, best first. */
data class Hypothesis(val text: String, val confidence: Float? = null)

/**
 * One spoken sentence. [text] is the top hypothesis and is what the transcript line displays;
 * [hypotheses] is the recogniser's full N-best list (top included, at index 0), possibly empty.
 */
data class Utterance(val text: String, val hypotheses: List<Hypothesis> = emptyList())

/**
 * How a medicine's identity was established. Only [HEARD] means the recogniser produced the
 * medicine's own name; every other basis is an inference that the patient must confirm.
 */
enum class Basis {
    /** The word the recogniser produced is the medicine, or a name the doctor said outright. */
    HEARD,

    /** The recogniser's word only sounds like a known medicine (or is a slip away from one). */
    SOUNDS_LIKE,

    /** The top hearing had no medicine, but a lower-ranked hearing of the same words did. */
    ALTERNATE_HEARING,

    /** The on-device language model picked it from a short list of sound-alike candidates. */
    AI_MATCHED,

    /** Not spoken at all: it is only on the prescription paper, so it has no dosing and cites no transcript line. */
    FROM_PRESCRIPTION,
}

enum class Confirmation { NOT_NEEDED, UNCONFIRMED, CONFIRMED, REJECTED }

/**
 * Set when the patient confirmed this medicine's name from the prescription paper. The paper only ever
 * supplies the NAME. Timings, food and duration still come from what the doctor said, never from the paper.
 */
data class PaperNote(
    /** The words the camera read, exactly as read; null when the patient picked the name from the list. */
    val readAs: String?,
)

data class Medicine(
    /** Exactly as heard (the doctor's or the recogniser's word), so the app restates rather than renames. */
    val name: String,
    /** The lexicon medicine this is taken to be, or the normalised name itself for an unknown brand. */
    val key: String,
    val timesPerDay: Int? = null,
    val timesOfDay: List<TimeOfDay> = emptyList(),
    val doseCount: Int? = null,
    val foodRelation: FoodRelation? = null,
    val durationDays: Int? = null,
    val sourceLines: List<Int> = emptyList(),
    val provenance: Provenance = Provenance.RULES,
    val basis: Basis = Basis.HEARD,
    /** For [Basis.ALTERNATE_HEARING]: which N-best hypothesis (0 is the top) produced the match. */
    val hypothesis: Int? = null,
    /** Nothing becomes a confirmed medicine by itself: an inferred identity starts [Confirmation.UNCONFIRMED]. */
    val confirmation: Confirmation = Confirmation.NOT_NEEDED,
    /** Lexicon medicines closest in sound, offered when the patient chooses another. */
    val candidates: List<String> = emptyList(),
    /** What the app first took this to be. Undo returns to it even after the patient chose another. */
    val suggested: String = key,
    /** Present when the patient confirmed the name from the prescription. */
    val paper: PaperNote? = null,
    /** Details the doctor gave on later lines. [sourceLines] is only the line that named the medicine. */
    val continuations: List<Continuation> = emptyList(),
) {
    /** The line a detail was said on if it came from a later sentence, or null if it is on the medicine's own line. */
    fun continuedFrom(detail: Detail): Int? = continuations.firstOrNull { detail in it.details }?.line

    /** Every line that speaks for this medicine: its own and the ones that added to it. */
    val allLines: List<Int> get() = (sourceLines + continuations.map { it.line }).distinct()

    val missing: List<MissingField>
        get() = buildList {
            if (timesPerDay == null) add(MissingField.TIMES_PER_DAY)
            if (timesOfDay.isEmpty()) add(MissingField.TIME_OF_DAY)
            if (foodRelation == null) add(MissingField.FOOD_RELATION)
            if (durationDays == null) add(MissingField.DURATION)
        }

    val isUnconfirmed: Boolean get() = confirmation == Confirmation.UNCONFIRMED
    val isRejected: Boolean get() = confirmation == Confirmation.REJECTED

    fun confirmed() = copy(confirmation = Confirmation.CONFIRMED)

    fun rejected() = copy(confirmation = Confirmation.REJECTED)

    /**
     * Undo a Yes, No or Choose: back to the app's original suggestion and to needing a decision (or
     * to not needing one if it was heard outright). The patient's pick is not kept as the guess.
     */
    fun reopened() = copy(
        key = suggested,
        confirmation = if (basis == Basis.HEARD) Confirmation.NOT_NEEDED else Confirmation.UNCONFIRMED,
        paper = null,
    )

    /** The patient picked a different medicine from the candidate list, which is itself a confirmation. */
    fun choosing(newKey: String) = copy(key = newKey, confirmation = Confirmation.CONFIRMED)
}

/** A restated instruction: a diagnosis, something to avoid, or a warning sign. */
data class Note(
    val text: String,
    val sourceLines: List<Int> = emptyList(),
    val provenance: Provenance = Provenance.RULES,
)

data class FollowUp(
    val inDays: Int?,
    val text: String,
    val sourceLines: List<Int> = emptyList(),
    val provenance: Provenance = Provenance.RULES,
)

/** Sections that can be absent from a consultation entirely. */
enum class MissingSection { MEDICINES, DIAGNOSIS, AVOID, WARNINGS, FOLLOW_UP }

data class Extraction(
    val language: Language,
    val lines: List<TranscriptLine>,
    val medicines: List<Medicine> = emptyList(),
    val diagnosis: List<Note> = emptyList(),
    val avoid: List<Note> = emptyList(),
    val warnings: List<Note> = emptyList(),
    val followUp: FollowUp? = null,
    /** Lines whose words state dosing (a frequency, a time, a dose, food or a duration). */
    val dosingLines: Set<Int> = emptySet(),
) {
    /**
     * Dosing the doctor gave that no active MEDICINE cites, so it is not tied to any medicine (its
     * name was misheard past recognition, or the patient dismissed the guess). Shown as the line as
     * heard, so an instruction is never silently dropped and no medicine is guessed for it.
     *
     * Only medicines count as covering a line. A diagnosis, an avoid item or a warning citing the same
     * line says nothing about the medicine: on a real run one sentence held both "you have a viral
     * fever" and "take <mangled name> three times a day", and the diagnosis hid the dosing.
     */
    val unnamedDosing: List<TranscriptLine>
        get() {
            val named = medicines.filterNot { it.isRejected }.flatMap { it.allLines }.toSet()
            return lines.filter { it.index in dosingLines && it.index !in named }
        }

    fun quotes(indices: List<Int>): List<TranscriptLine> =
        indices.distinct().sorted().mapNotNull { i -> lines.getOrNull(i) }

    /** Removes one medicine, used to undo a paper-only card, which has no speech to fall back to. */
    fun withoutMedicine(index: Int): Extraction = copy(medicines = medicines.filterIndexed { i, _ -> i != index })

    /** Replaces one medicine (e.g. after the patient answers Yes/No) without touching the others. */
    fun updateMedicine(index: Int, change: (Medicine) -> Medicine): Extraction =
        copy(medicines = medicines.mapIndexed { i, m -> if (i == index) change(m) else m })

    /**
     * The only way later passes (the language model) add to a result: append, never overwrite. An
     * addition for a medicine that is already listed is dropped rather than merged into it.
     */
    fun withAdded(additions: List<Medicine>): Extraction {
        val have = medicines.map { it.key }.toMutableSet()
        val fresh = additions.filter { have.add(it.key) }
        return if (fresh.isEmpty()) this else copy(medicines = medicines + fresh)
    }

    val missingSections: List<MissingSection>
        get() = buildList {
            if (medicines.all { it.isRejected }) add(MissingSection.MEDICINES)
            if (diagnosis.isEmpty()) add(MissingSection.DIAGNOSIS)
            if (avoid.isEmpty()) add(MissingSection.AVOID)
            if (warnings.isEmpty()) add(MissingSection.WARNINGS)
            if (followUp == null) add(MissingSection.FOLLOW_UP)
        }

    /** Every line index cited by at least one item. Used later to pick lines the LLM may look at. */
    val coveredLines: Set<Int>
        get() = buildSet {
            medicines.forEach { addAll(it.allLines) }
            diagnosis.forEach { addAll(it.sourceLines) }
            avoid.forEach { addAll(it.sourceLines) }
            warnings.forEach { addAll(it.sourceLines) }
            followUp?.let { addAll(it.sourceLines) }
        }
}
