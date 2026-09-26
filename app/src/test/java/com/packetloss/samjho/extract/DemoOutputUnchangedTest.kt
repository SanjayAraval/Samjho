package com.packetloss.samjho.extract

import com.packetloss.samjho.demo.DemoConsultations
import com.packetloss.samjho.model.Extraction
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Continuation attachment must not change what the four demo consultations produce. The expected text below was
 * captured from the extractor BEFORE the change; every demo line that has dosing words also has a medicine name, so
 * nothing in them is a continuation.
 */
class DemoOutputUnchangedTest {

    private fun snapshot(e: Extraction): String = buildString {
        e.medicines.forEach { m ->
            appendLine("MED ${m.name}|${m.key}|tpd=${m.timesPerDay}|tod=${m.timesOfDay}|dose=${m.doseCount}|food=${m.foodRelation}|days=${m.durationDays}|lines=${m.sourceLines}|${m.basis}|${m.confirmation}|${m.provenance}|cand=${m.candidates}")
        }
        e.diagnosis.forEach { appendLine("DX ${it.text}|${it.sourceLines}") }
        e.avoid.forEach { appendLine("AVOID ${it.text}|${it.sourceLines}") }
        e.warnings.forEach { appendLine("WARN ${it.text}|${it.sourceLines}") }
        appendLine("FOLLOWUP ${e.followUp?.inDays}|${e.followUp?.text}|${e.followUp?.sourceLines}")
        appendLine("DOSING ${e.dosingLines.sorted()}")
        appendLine("UNNAMED ${e.unnamedDosing.map { it.index }}")
        appendLine("COVERED ${e.coveredLines.sorted()}")
        appendLine("MISSING ${e.missingSections}")
    }

    private fun check(id: String, expected: String) {
        val demo = DemoConsultations.ALL.first { it.id == id }
        val extraction = RuleExtractor.extract(demo.lines)
        assertEquals(expected.trim().lines().joinToString("\n") { it.trimEnd() }, snapshot(extraction).trim().lines().joinToString("\n") { it.trimEnd() })
        assertEquals("nothing in $id is a continuation", emptyList<Any>(), extraction.medicines.flatMap { it.continuations })
    }

    @Test
    fun demoHiViralFever() = check(
        "hi-viral-fever",
        """
MED पैरासिटामोल|paracetamol|tpd=3|tod=[]|dose=null|food=AFTER_FOOD|days=5|lines=[2]|HEARD|NOT_NEEDED|RULES|cand=[]
MED एजिथ्रोमाइसिन|azithromycin|tpd=null|tod=[MORNING]|dose=null|food=EMPTY_STOMACH|days=3|lines=[3]|HEARD|NOT_NEEDED|RULES|cand=[]
MED सेटिरिजिन|cetirizine|tpd=null|tod=[NIGHT]|dose=1|food=null|days=null|lines=[4]|HEARD|NOT_NEEDED|RULES|cand=[]
DX वायरल बुखार|[1]
AVOID ठंडा पानी|[5]
AVOID तला हुआ खाना|[5]
WARN अगर बुखार एक सौ दो से ऊपर जाए या सांस लेने में तकलीफ हो, तो तुरंत अस्पताल जाना।|[6]
FOLLOWUP 5|पांच दिन बाद दोबारा दिखाने आना।|[7]
DOSING [2, 3, 4]
UNNAMED []
COVERED [1, 2, 3, 4, 5, 6, 7]
MISSING []
""",
    )

    @Test
    fun demoEnViralFever() = check(
        "en-viral-fever",
        """
MED paracetamol|paracetamol|tpd=3|tod=[]|dose=null|food=AFTER_FOOD|days=5|lines=[2]|HEARD|NOT_NEEDED|RULES|cand=[]
MED azithromycin|azithromycin|tpd=null|tod=[MORNING]|dose=null|food=EMPTY_STOMACH|days=3|lines=[3]|HEARD|NOT_NEEDED|RULES|cand=[]
MED cetirizine|cetirizine|tpd=null|tod=[NIGHT]|dose=1|food=null|days=null|lines=[4]|HEARD|NOT_NEEDED|RULES|cand=[]
DX a viral fever|[1]
AVOID cold water|[5]
AVOID fried food|[5]
WARN If the fever goes above 102 or you have trouble breathing, go to the hospital immediately.|[6]
FOLLOWUP 5|Come back again after five days.|[7]
DOSING [2, 3, 4]
UNNAMED []
COVERED [1, 2, 3, 4, 5, 6, 7]
MISSING []
""",
    )

    @Test
    fun demoEnAsHeard() = check(
        "en-as-heard",
        """
MED parasite|paracetamol|tpd=3|tod=[]|dose=null|food=AFTER_FOOD|days=5|lines=[1]|SOUNDS_LIKE|UNCONFIRMED|RULES|cand=[paracetamol, metoprolol, omeprazole, topiramate, latanoprost]
MED citrus|cetirizine|tpd=null|tod=[NIGHT]|dose=null|food=null|days=null|lines=[3]|SOUNDS_LIKE|UNCONFIRMED|RULES|cand=[cetirizine, azithromycin, cinnarizine, letrozole, ceftriaxone]
MED Dolo|paracetamol|tpd=null|tod=[]|dose=null|food=null|days=null|lines=[4]|HEARD|NOT_NEEDED|RULES|cand=[]
DX a viral fever|[0]
AVOID cold water|[5]
AVOID fried food|[5]
WARN If the fever goes above 102 go to the hospital immediately|[6]
FOLLOWUP 5|Come back after 5 days for a checkup|[7]
DOSING [1, 2, 3]
UNNAMED [2]
COVERED [0, 1, 3, 4, 5, 6, 7]
MISSING []
""",
    )

    @Test
    fun demoEnLiveRead() = check(
        "en-live-read",
        """
MED Paris at Mall|paracetamol|tpd=3|tod=[]|dose=null|food=AFTER_FOOD|days=5|lines=[0]|SOUNDS_LIKE|UNCONFIRMED|RULES|cand=[paracetamol, pyrazinamide, alprazolam, azithromycin, calcitriol]
MED throw|throw|tpd=1|tod=[MORNING]|dose=null|food=EMPTY_STOMACH|days=3|lines=[1]|HEARD|UNCONFIRMED|RULES|cand=[atorvastatin, azithromycin, amlodipine, atenolol, carvedilol]
DX a viral fever|[0]
AVOID cold water|[4]
AVOID fried food|[4]
WARN If the fever goes above 102 go to the hospital immediately|[5]
FOLLOWUP 5|Come back after 5 days for a checkup|[6]
DOSING [0, 1, 2]
UNNAMED [2]
COVERED [0, 1, 4, 5, 6]
MISSING []
""",
    )

}
