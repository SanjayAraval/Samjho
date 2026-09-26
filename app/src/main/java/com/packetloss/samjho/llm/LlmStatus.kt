package com.packetloss.samjho.llm

sealed interface LlmStatus {
    data object Loading : LlmStatus
    data class Ready(val backend: String) : LlmStatus
    data class Unavailable(val reason: String) : LlmStatus
}
