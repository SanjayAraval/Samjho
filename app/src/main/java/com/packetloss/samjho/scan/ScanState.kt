package com.packetloss.samjho.scan

import com.packetloss.samjho.model.Confirmation

sealed interface ScanStage {
    /** Showing the camera, or the permission prompt, with the manual list one tap away. */
    data object Camera : ScanStage

    /** A photo is being read on the phone. */
    data object Reading : ScanStage

    /** The suggestions, the manual list and the raw text. */
    data object Results : ScanStage

    /** The camera or the reader failed. The manual list still works. */
    data class Failed(val message: String) : ScanStage
}

/**
 * Everything on the scan screen, as plain data. Every change is a function of the patient's own tap, so
 * this is where "nothing is auto-accepted" is enforced and tested.
 */
data class ScanUi(
    /** True when the scan was opened from a consultation summary, so its confirmed names can be merged into it. */
    val forSummary: Boolean = false,
    val stage: ScanStage = ScanStage.Camera,
    val items: List<ScanItem> = emptyList(),
    /** What the camera read, kept so the patient (and we, on stage) can see nothing was invented. */
    val read: OcrRead? = null,
    val millis: Long = 0,
    private val nextId: Int = 0,
) {
    fun reading() = copy(stage = ScanStage.Reading)

    fun failed(message: String) = copy(stage = ScanStage.Failed(message))

    /**
     * A photo was read. Every decision the patient already made (confirmed, rejected or picked by hand)
     * is kept, and a medicine they rejected is not suggested again. Only suggestions they never answered
     * are replaced by the new reading, so scanning again never throws away something they decided.
     */
    fun withRead(read: OcrRead, suggestions: List<ScanItem>, millis: Long): ScanUi {
        val kept = items.filter { it.confirmation != Confirmation.UNCONFIRMED }
        val known = kept.map { it.key }.toSet()
        var id = nextId
        val fresh = suggestions.filter { it.key !in known }.map { it.copy(id = id++) }
        return copy(stage = ScanStage.Results, items = kept + fresh, read = read, millis = millis, nextId = id)
    }

    /**
     * The only way the language model adds to the scan: append, never overwrite. A name that is already listed
     * (suggested, confirmed, rejected or picked by hand) is dropped rather than merged into it, and everything
     * added starts unconfirmed like any other suggestion.
     */
    fun withAdded(additions: List<ScanItem>): ScanUi {
        val have = items.map { it.key }.toMutableSet()
        var id = nextId
        val fresh = additions.filter { have.add(it.key) }.map { it.copy(id = id++) }
        return if (fresh.isEmpty()) this else copy(items = items + fresh, nextId = id)
    }

    fun confirm(id: Int) = update(id) { it.confirmed() }

    fun reject(id: Int) = update(id) { it.rejected() }

    fun choose(id: Int, key: String) = update(id) { it.choosing(key) }

    /** Undo puts a suggestion back to needing an answer; a medicine picked by hand is simply removed. */
    fun undo(id: Int): ScanUi {
        val item = items.firstOrNull { it.id == id } ?: return this
        return if (item.basis == ScanBasis.PICKED) copy(items = items.filter { it.id != id }) else update(id) { it.reopened() }
    }

    /**
     * The patient picked [key] from the full list. That is their own decision, so it is confirmed. If the
     * camera had already suggested it, that card is confirmed instead of adding a second one.
     */
    fun pick(key: String): ScanUi {
        val existing = items.firstOrNull { it.key == key }
        val next = if (existing != null) {
            items.map { if (it.id == existing.id) it.copy(confirmation = Confirmation.CONFIRMED) else it }
        } else {
            items + ScanItem.picked(nextId, key)
        }
        return copy(
            stage = if (stage == ScanStage.Camera || stage is ScanStage.Failed) ScanStage.Results else stage,
            items = next,
            nextId = if (existing != null) nextId else nextId + 1,
        )
    }

    private fun update(id: Int, change: (ScanItem) -> ScanItem) = copy(items = items.map { if (it.id == id) change(it) else it })
}
