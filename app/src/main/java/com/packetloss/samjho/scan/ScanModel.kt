package com.packetloss.samjho.scan

import com.packetloss.samjho.model.Confirmation

/** How a medicine on a scanned prescription came to be suggested. */
enum class ScanBasis {
    /** A word the camera read is spelled exactly like a known medicine. */
    EXACT,

    /** A word the camera read is a small character slip away from a known medicine ("Paracetarnol"). */
    SPELLING,

    /** A word the camera read only sounds like a known medicine, by its consonants. */
    SOUNDS_LIKE,

    /** The on-device language model picked it from a short list of look-alike names for a word the rules missed. */
    AI_MATCHED,

    /** The patient chose it from the list themselves. Nothing was read. */
    PICKED,
}

/**
 * One medicine on the scan result. Everything the camera suggests starts [Confirmation.UNCONFIRMED] and
 * only the patient's own tap moves it: Confirm, Reject, or Choose another. A medicine picked from the
 * list is the patient's own decision, so it is confirmed from the start, exactly as choosing another
 * name is on the consultation result.
 */
data class ScanItem(
    val id: Int,
    /** The words the camera read, exactly as read. Null for a medicine the patient picked from the list. */
    val readAs: String?,
    /** The lexicon medicine this is taken to be. */
    val key: String,
    /** What the app first suggested, so Undo can restore it after a Choose another. */
    val suggested: String,
    /** Other lexicon medicines that sound close, offered first when choosing another. */
    val candidates: List<String>,
    val basis: ScanBasis,
    val confirmation: Confirmation,
) {
    val isUnconfirmed get() = confirmation == Confirmation.UNCONFIRMED
    val isRejected get() = confirmation == Confirmation.REJECTED

    fun confirmed() = copy(confirmation = Confirmation.CONFIRMED)

    fun rejected() = copy(confirmation = Confirmation.REJECTED)

    /** The patient picked a different medicine, which is itself a confirmation. */
    fun choosing(newKey: String) = copy(key = newKey, confirmation = Confirmation.CONFIRMED)

    /** Undo: back to the app's own suggestion and to needing an answer. */
    fun reopened() = copy(key = suggested, confirmation = Confirmation.UNCONFIRMED)

    companion object {
        /** A medicine the patient picked from the full list. The only way an item starts confirmed. */
        fun picked(id: Int, key: String) = ScanItem(
            id = id,
            readAs = null,
            key = key,
            suggested = key,
            candidates = emptyList(),
            basis = ScanBasis.PICKED,
            confirmation = Confirmation.CONFIRMED,
        )
    }
}

/** Everything the two on-device text readers returned for one photo. */
data class OcrRead(
    /** Lines from the Latin reader (English and brand names). */
    val latin: List<String>,
    /** Lines from the Devanagari reader (Hindi, Marathi and other Devanagari text). */
    val devanagari: List<String>,
) {
    /** Every distinct line, in the order read, for matching against the medicine list. */
    fun allLines(): List<String> = (latin + devanagari).map { it.trim() }.filter { it.isNotEmpty() }.distinct()

    val isEmpty get() = allLines().isEmpty()
}
