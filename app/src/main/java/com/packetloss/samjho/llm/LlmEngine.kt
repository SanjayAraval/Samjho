package com.packetloss.samjho.llm

/**
 * The on-device language model, behind a seam so the rules never depend on how it runs.
 *
 * The model runs in its own process and may crash, stall or say anything at all. Callers treat
 * every answer as untrusted text: it is checked against a fixed list before it is used, and a
 * null (unavailable, failed, timed out) is always safe, because the rule-based result stands
 * without it.
 */
interface LlmEngine {

    /** Null when the model can be used right now; otherwise why not. */
    fun unavailableReason(): String?

    /** Blocking. Call off the main thread. Returns the raw reply, or null on any failure or timeout. */
    fun complete(prompt: String): String?
}

/** Stands in when no model is installed, so the rest of the app has nothing special to handle. */
class NoLlmEngine(private val reason: String) : LlmEngine {
    override fun unavailableReason(): String = reason
    override fun complete(prompt: String): String? = null
}
