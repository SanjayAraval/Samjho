package com.packetloss.samjho.speech

/**
 * The rules that turn a recogniser that keeps ending into one continuous recording. Pure, so it is tested with a
 * fake clock; [AndroidSpeechEngine] feeds it callbacks and carries out what it says.
 *
 * Why it exists. A system recogniser works in windows. On the iQOO the window often ends about eight seconds after
 * it opened with NO_MATCH and no result, having never noticed the pauses, and the text of the window was thrown
 * away. So a pause in a consultation lost whatever was said around it. The rules here are:
 *
 *  1. The words heard so far in a window (the latest partial result) are never discarded. If the window ends with
 *     no final result, whether by NO_MATCH, a timeout, an error or a watchdog, they are committed as a line.
 *  2. Words that have not changed for a long time ([stallMs]) are committed as a line and the recogniser is rebuilt.
 *     This is a backstop for a window that neither finishes nor fails; it is deliberately slow, because the
 *     recogniser's partial results lag behind speech and cutting a window early cuts words off.
 *  3. Every end of a window restarts listening, and only [stop] ends the session.
 *  4. A restart loop that spins (windows that end almost at once) backs off instead of hammering the recogniser,
 *     and only gives up when something has been failing for a long time.
 *
 * Lines are only ever appended. A restart never replaces what was already committed.
 */
class ContinuousTranscript(
    private val stallMs: Long = STALL_MS,
    private val minWindowMs: Long = MIN_WINDOW_MS,
    private val endOfSpeechGraceMs: Long = END_OF_SPEECH_GRACE_MS,
    private val silenceRecycleMs: Long = SILENCE_RECYCLE_MS,
    private val maxQuickEnds: Int = MAX_QUICK_ENDS,
) {

    /** What the engine should do. Steps come back in the order they must be carried out. */
    sealed interface Step {
        /** Append this as a finished transcript line. [fromPartial] is true when no final result ever arrived for it. */
        data class Commit(val text: String, val fromPartial: Boolean) : Step

        /**
         * Listen again after [delayMs]. [cancelFirst] when the current window is still open (a stall), so it must be
         * cancelled; [recreate] when the recogniser itself should be torn down and rebuilt (it stopped answering).
         */
        data class Restart(val delayMs: Long, val cancelFirst: Boolean = false, val recreate: Boolean = false) : Step

        /** Something has been failing for too long to keep trying. */
        data class Fail(val reason: String) : Step
    }

    /** Why a window ended with an error, as far as this policy cares. */
    enum class EndKind {
        /** NO_MATCH or SPEECH_TIMEOUT: normal in a quiet room, never a reason to stop. */
        SILENT,

        /** BUSY, CLIENT or a dropped connection: the recogniser was not ready. */
        NOT_READY,
    }

    private var partial = ""
    private var partialChangedAt = 0L
    private var windowStartedAt = 0L
    private var lastCallbackAt = 0L
    private var endOfSpeechAt = -1L
    private var quickEnds = 0
    private var notReadyStreak = 0
    private var stopped = false

    /** The words heard in the open window that have not been committed yet. */
    val pending: String get() = partial.trim()

    // ---------------------------------------------------------------- the recogniser's callbacks

    /** A window was opened (listening was started or restarted). */
    fun onWindowOpened(now: Long) {
        windowStartedAt = now
        lastCallbackAt = now
        endOfSpeechAt = -1L
        partial = ""
        partialChangedAt = now
    }

    fun onReady(now: Long) {
        lastCallbackAt = now
    }

    fun onPartial(text: String, now: Long) {
        lastCallbackAt = now
        val t = text.trim()
        if (t.isEmpty()) return
        if (t != partial) {
            partial = t
            partialChangedAt = now
        }
    }

    fun onEndOfSpeech(now: Long) {
        lastCallbackAt = now
        if (endOfSpeechAt < 0) endOfSpeechAt = now
    }

    /** A final result. Its text is the line; if it is empty the last partial is kept rather than lost. */
    fun onResults(text: String, now: Long): List<Step> {
        if (stopped) return emptyList()
        lastCallbackAt = now
        val line = text.trim().ifEmpty { partial }
        return finishWindow(now, line, fromPartial = text.isBlank(), notReady = false)
    }

    /** A window ended with an error the caller wants to recover from. */
    fun onEnded(kind: EndKind, now: Long): List<Step> {
        if (stopped) return emptyList()
        lastCallbackAt = now
        return finishWindow(now, partial, fromPartial = true, notReady = kind == EndKind.NOT_READY)
    }

    /** Called regularly while recording. Finds windows that have stalled, hung or gone silent. */
    fun onTick(now: Long): List<Step> {
        if (stopped) return emptyList()
        // 1. The words stopped changing: commit them and start a fresh window.
        if (partial.isNotEmpty() && now - partialChangedAt >= stallMs) {
            return stall(now)
        }
        // 2. The recogniser said it heard the end of speech but never followed up with a result or an error.
        if (endOfSpeechAt >= 0 && now - endOfSpeechAt >= endOfSpeechGraceMs) {
            return stall(now)
        }
        // 3. Nothing at all from the recogniser for a long time: it has probably stopped answering.
        if (now - lastCallbackAt >= silenceRecycleMs) {
            lastCallbackAt = now
            return listOf(Step.Restart(delayMs = RESTART_MIN_MS, cancelFirst = true, recreate = true))
        }
        return emptyList()
    }

    /** The user pressed stop. Returns the words still in flight so the last sentence is not lost. */
    fun stop(): String {
        val words = pending
        stopped = true
        partial = ""
        return words
    }

    // ---------------------------------------------------------------- internals

    private fun stall(now: Long): List<Step> {
        val steps = mutableListOf<Step>()
        if (partial.isNotEmpty()) steps += Step.Commit(partial, fromPartial = true)
        partial = ""
        endOfSpeechAt = -1L
        // A stall is not a failing recogniser: it is a person pausing. Reusing a recogniser straight after cancelling it
        // gives ERROR_CLIENT on the iQOO, so the window is reopened on a fresh recogniser.
        quickEnds = 0
        steps += Step.Restart(delayMs = RESTART_MIN_MS, cancelFirst = true, recreate = true)
        return steps
    }

    private fun finishWindow(now: Long, line: String, fromPartial: Boolean, notReady: Boolean): List<Step> {
        val steps = mutableListOf<Step>()
        val text = line.trim()
        if (text.isNotEmpty()) steps += Step.Commit(text, fromPartial)
        partial = ""
        endOfSpeechAt = -1L

        // A window that ended almost at once, with nothing said, is what a spinning loop looks like.
        val quick = now - windowStartedAt < minWindowMs && text.isEmpty()
        quickEnds = if (quick) quickEnds + 1 else 0
        notReadyStreak = if (notReady) notReadyStreak + 1 else 0

        if (quickEnds > maxQuickEnds) {
            steps += Step.Fail("The speech recogniser keeps ending immediately and cannot be restarted.")
            return steps
        }
        steps += Step.Restart(
            // "Not ready" means the previous window is still closing: give it a moment, and if it happens twice in a row
            // stop reusing that recogniser.
            delayMs = if (notReady) maxOf(backoff(), NOT_READY_DELAY_MS) else backoff(),
            recreate = notReady && notReadyStreak >= RECREATE_AFTER,
        )
        return steps
    }

    /** 60 ms normally, then doubling for each window in a row that ended at once, up to two seconds. */
    private fun backoff(): Long {
        if (quickEnds == 0) return RESTART_MIN_MS
        val delay = RESTART_MIN_MS shl (quickEnds.coerceAtMost(MAX_SHIFT))
        return delay.coerceAtMost(BACKOFF_CAP_MS)
    }

    companion object {
        /** Words unchanged for this long, in a window that has not ended, are committed as a line. */
        const val STALL_MS = 6_000L

        /** A window shorter than this, with nothing in it, counts as an instant end. */
        const val MIN_WINDOW_MS = 800L

        /** After end-of-speech the result or error should follow within milliseconds; this much later it will not. */
        const val END_OF_SPEECH_GRACE_MS = 2_500L

        /** No callback at all for this long and the recogniser is rebuilt. */
        const val SILENCE_RECYCLE_MS = 20_000L

        const val MAX_QUICK_ENDS = 25
        const val RESTART_MIN_MS = 60L
        const val BACKOFF_CAP_MS = 2_000L
        private const val MAX_SHIFT = 6
        private const val RECREATE_AFTER = 2
        private const val NOT_READY_DELAY_MS = 200L
    }
}
