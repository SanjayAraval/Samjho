package com.packetloss.samjho.llm

import android.content.Context

/** Picks the language model implementation for this build. */
object LlmEngines {

    /**
     * The on-device Gemma model, isolated in its own process. If the model file is missing or anything
     * fails, [LlmEngine.unavailableReason] says so and the rules result stands, which is the whole product.
     */
    fun create(context: Context): LlmEngine = AidlLlmEngine(context)
}
