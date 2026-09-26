package com.packetloss.samjho.llm

import android.content.Context

/** Picks the language model implementation for this build. */
object LlmEngines {

    fun create(context: Context): LlmEngine =
        NoLlmEngine("No on-device language model is set up in this build yet.")
}
