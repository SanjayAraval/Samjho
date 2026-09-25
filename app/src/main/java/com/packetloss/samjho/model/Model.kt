package com.packetloss.samjho.model

/**
 * One line of the consultation transcript. [index] is the stable id that every extracted item
 * cites, so the app can always show the patient the exact words an item came from.
 */
data class TranscriptLine(val index: Int, val text: String)

enum class Language { HINDI, ENGLISH }

/** Rules run first. The on-device LLM may only ADD items, tagged [AI]; it never overwrites [RULES]. */
enum class Provenance { RULES, AI }

enum class TimeOfDay { MORNING, AFTERNOON, EVENING, NIGHT }

enum class FoodRelation { BEFORE_FOOD, AFTER_FOOD, EMPTY_STOMACH, WITH_FOOD }

/** A detail the doctor never stated. Shown as "not mentioned" so it is never silently guessed. */
enum class MissingField { TIMES_PER_DAY, TIME_OF_DAY, FOOD_RELATION, DURATION }

data class Medicine(
    /** Exactly as the doctor said it, so the app restates rather than renames. */
    val name: String,
    /** Normalised form used only to merge repeat mentions of the same medicine. */
    val key: String,
    val timesPerDay: Int? = null,
    val timesOfDay: List<TimeOfDay> = emptyList(),
    val doseCount: Int? = null,
    val foodRelation: FoodRelation? = null,
    val durationDays: Int? = null,
    val sourceLines: List<Int> = emptyList(),
    val provenance: Provenance = Provenance.RULES,
) {
    val missing: List<MissingField>
        get() = buildList {
            if (timesPerDay == null) add(MissingField.TIMES_PER_DAY)
            if (timesOfDay.isEmpty()) add(MissingField.TIME_OF_DAY)
            if (foodRelation == null) add(MissingField.FOOD_RELATION)
            if (durationDays == null) add(MissingField.DURATION)
        }
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
) {
    fun quotes(indices: List<Int>): List<TranscriptLine> =
        indices.distinct().sorted().mapNotNull { i -> lines.getOrNull(i) }

    val missingSections: List<MissingSection>
        get() = buildList {
            if (medicines.isEmpty()) add(MissingSection.MEDICINES)
            if (diagnosis.isEmpty()) add(MissingSection.DIAGNOSIS)
            if (avoid.isEmpty()) add(MissingSection.AVOID)
            if (warnings.isEmpty()) add(MissingSection.WARNINGS)
            if (followUp == null) add(MissingSection.FOLLOW_UP)
        }

    /** Every line index cited by at least one item. Used later to pick lines the LLM may look at. */
    val coveredLines: Set<Int>
        get() = buildSet {
            medicines.forEach { addAll(it.sourceLines) }
            diagnosis.forEach { addAll(it.sourceLines) }
            avoid.forEach { addAll(it.sourceLines) }
            warnings.forEach { addAll(it.sourceLines) }
            followUp?.let { addAll(it.sourceLines) }
        }
}
