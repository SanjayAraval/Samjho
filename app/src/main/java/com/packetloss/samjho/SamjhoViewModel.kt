package com.packetloss.samjho

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import com.packetloss.samjho.demo.DemoConsultation
import com.packetloss.samjho.extract.RuleExtractor
import com.packetloss.samjho.model.Extraction

data class UiState(
    val extraction: Extraction? = null,
    /** How long the deterministic pass took. Shown on screen: the budget is 1-2 seconds. */
    val ruleMillis: Long = 0,
    val sourceLabel: String = "",
)

class SamjhoViewModel : ViewModel() {

    var state by mutableStateOf(UiState())
        private set

    fun runDemo(demo: DemoConsultation) {
        val start = System.nanoTime()
        val extraction = RuleExtractor.extract(demo.lines)
        val millis = (System.nanoTime() - start) / 1_000_000
        state = UiState(extraction = extraction, ruleMillis = millis, sourceLabel = demo.label)
    }

    fun back() {
        state = UiState()
    }
}
