package com.packetloss.samjho.debug

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.packetloss.samjho.extract.NameRepair
import com.packetloss.samjho.extract.RuleExtractor
import com.packetloss.samjho.llm.LlmEngines
import com.packetloss.samjho.llm.LlmStatus
import com.packetloss.samjho.llm.LlmStatusSource
import com.packetloss.samjho.model.Hypothesis
import com.packetloss.samjho.model.Utterance
import kotlin.concurrent.thread

/**
 * Debug builds only. Replays the live run whose "Once it resin at night before sleeping" line was never put to the
 * model, and asks the on-device model that line's real question so its real reply can be read from the log:
 *
 *   adb shell am broadcast -n com.packetloss.samjho/.debug.DebugAskReceiver
 *   adb logcat -s SamjhoAsk
 *
 * Needs the phone awake with Samjho in front (the model process is frozen otherwise).
 */
class DebugAskReceiver : BroadcastReceiver() {
    private fun u(vararg raw: String) = Utterance(raw[0], raw.map { Hypothesis(it) })

    override fun onReceive(context: Context, intent: Intent) {
        val pending = goAsync()
        thread(name = "debug-ask") {
            try {
                run(context)
            } catch (t: Throwable) {
                Log.e(TAG, "failed: $t")
            } finally {
                pending.finish()
            }
        }
    }

    private fun run(context: Context) {
        val e = RuleExtractor.extractUtterances(
            listOf(
                u("You have a viral fever nothing to worry about"),
                u("Take paracetamol 1 tablet three times a day after food for 5 days", "Take paracetamol one tablet three times a day after food for 5 days"),
                u("One tablet in the morning on an empty stomach for 3 days"),
                u("Take Panther Brazil before breakfast", "Take pantoprazole before breakfast"),
                u("Once it resin at night before sleeping", "Once it resin at night before sleeping/"),
                u("Award cold water and fried food"),
                u("If the fever goes above 102 or you have difficulty breathing go to the hospital immediately"),
                u("Come back after 5 days"),
            ),
        )
        e.medicines.forEach { Log.i(TAG, "rules: ${it.name} -> ${it.key} ${it.basis} ${it.confirmation} lines=${it.sourceLines}") }
        Log.i(TAG, "dosingLines=${e.dosingLines.sorted()} unnamedDosing=${e.unnamedDosing.map { it.index }} covered=${e.coveredLines.sorted()}")
        Log.i(TAG, "NameRepair.targets = ${NameRepair.targets(e).map { it.line.index to it.candidates.map { c -> c.key } }}")

        val llm = LlmEngines.create(context)
        (llm as? LlmStatusSource)?.start()
        val deadline = System.currentTimeMillis() + 90_000
        while (llm.unavailableReason() != null && System.currentTimeMillis() < deadline) Thread.sleep(500)
        Log.i(TAG, "model: ${llm.unavailableReason() ?: "ready"} ${(llm as? LlmStatusSource)?.status.let { (it as? LlmStatus.Ready)?.let { r -> "${r.backend} ${r.decodeTps} tok/s" } }}")
        if (llm.unavailableReason() != null) return

        // What the app would send for the resin line if it were a target: the same shortlist code, the same prompt.
        val line = e.lines.first { it.text.startsWith("Once it resin") }
        val already = e.medicines.map { it.key }.toSet()
        val candidates = NameRepair.candidatesFor(line.text, already)
        Log.i(TAG, "shortlist for line ${line.index}: " + candidates.joinToString { "${it.key}(${"%.2f".format(it.distance)})" })
        val prompt = NameRepair.prompt(line.text, candidates, NameRepair.Source.SPEECH)
        Log.i(TAG, "PROMPT >>>\n$prompt\n<<<")
        val started = System.currentTimeMillis()
        val reply = llm.complete(prompt)
        Log.i(TAG, "RAW REPLY (${System.currentTimeMillis() - started} ms): ${reply?.let { "\"" + it + "\"" }}")
        Log.i(TAG, "PARSED: ${NameRepair.parse(reply, candidates)?.key ?: "null (UNKNOWN or not on the shortlist)"}")

        // And what the real pipeline adds for this transcript today.
        val added = NameRepair.repair(e, llm)
        Log.i(TAG, "NameRepair.repair added: ${added.map { it.key + " for " + it.name }}")
    }

    private companion object {
        const val TAG = "SamjhoAsk"
    }
}
