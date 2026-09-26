package com.packetloss.samjho.ui

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.material3.Checkbox
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import com.packetloss.samjho.model.Language
import com.packetloss.samjho.speech.EngineId
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilterChip
import androidx.compose.material3.OutlinedButton
import androidx.compose.ui.text.style.TextDecoration
import com.packetloss.samjho.AiState
import com.packetloss.samjho.extract.Lexicon
import com.packetloss.samjho.model.Basis
import com.packetloss.samjho.model.Provenance
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.packetloss.samjho.demo.DemoConsultation
import com.packetloss.samjho.demo.DemoConsultations
import com.packetloss.samjho.model.Extraction
import com.packetloss.samjho.model.Medicine
import com.packetloss.samjho.model.Note

// ------------------------------------------------------------------ home

@Composable
fun HomeScreen(
    engine: EngineId,
    unavailable: Map<Language, String>,
    onSelectEngine: (EngineId) -> Unit,
    onRunDemo: (DemoConsultation) -> Unit,
    onRecord: (Language) -> Unit,
) {
    val context = LocalContext.current
    var consent by remember { mutableStateOf(false) }
    var pending by remember { mutableStateOf<Language?>(null) }
    var micDenied by remember { mutableStateOf(false) }
    val askMic = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        val lang = pending
        pending = null
        if (granted && lang != null) onRecord(lang) else micDenied = true
    }
    fun record(lang: Language) {
        micDenied = false
        val has = ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) ==
            PackageManager.PERMISSION_GRANTED
        if (has) onRecord(lang) else {
            pending = lang
            askMic.launch(Manifest.permission.RECORD_AUDIO)
        }
    }

    Surface(color = MaterialTheme.colorScheme.background) {
        Column(
            Modifier
                .fillMaxSize()
                .systemBarsPadding()
                .verticalScroll(rememberScrollState())
                .padding(24.dp),
        ) {
            Spacer(Modifier.height(40.dp))
            Text(
                "Samjho",
                fontSize = 46.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary,
            )
            Spacer(Modifier.height(8.dp))
            Text("डॉक्टर ने जो कहा, आपकी भाषा में।", fontSize = 19.sp, color = Ink)
            Text("What the doctor said, explained back to you.", fontSize = 16.sp, color = Muted)

            Spacer(Modifier.height(18.dp))
            Pill("पूरी तरह ऑफ़लाइन · Fully offline", OkTint, MaterialTheme.colorScheme.primary)

            Spacer(Modifier.height(28.dp))
            Text(
                "बातचीत रिकॉर्ड करें · Record a consultation",
                fontSize = 15.sp,
                fontWeight = FontWeight.Medium,
                color = Muted,
            )
            Spacer(Modifier.height(8.dp))
            Row(
                Modifier
                    .fillMaxWidth()
                    .clickable { consent = !consent },
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Checkbox(checked = consent, onCheckedChange = { consent = it })
                Text(
                    "डॉक्टर ने रिकॉर्डिंग की अनुमति दी है\nThe doctor has agreed to be recorded",
                    fontSize = 14.sp,
                    color = Ink,
                )
            }
            Spacer(Modifier.height(8.dp))
            Text("बोली पहचान · Speech engine", fontSize = 13.sp, color = Muted)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                EngineId.entries.forEach { id ->
                    FilterChip(
                        selected = id == engine,
                        onClick = { onSelectEngine(id) },
                        label = { Text(id.label, fontSize = 14.sp) },
                    )
                }
            }
            Spacer(Modifier.height(10.dp))
            Language.entries.forEach { lang ->
                val reason = unavailable[lang]
                Button(
                    onClick = { record(lang) },
                    enabled = consent && reason == null,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(58.dp),
                    shape = RoundedCornerShape(14.dp),
                ) {
                    Text(
                        if (lang == Language.HINDI) "🎙  हिंदी में रिकॉर्ड करें" else "🎙  Record in English",
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Medium,
                    )
                }
                if (reason != null) {
                    Text(reason, fontSize = 13.sp, color = WarnInk, modifier = Modifier.padding(top = 4.dp))
                }
                Spacer(Modifier.height(10.dp))
            }
            if (micDenied) {
                Text(
                    "माइक की अनुमति चाहिए · Microphone permission is needed to record.",
                    fontSize = 14.sp,
                    color = WarnInk,
                )
            }

            Spacer(Modifier.height(28.dp))
            Text(
                "या नमूना बातचीत देखें · Or try a demo consultation",
                fontSize = 15.sp,
                fontWeight = FontWeight.Medium,
                color = Muted,
            )
            Spacer(Modifier.height(12.dp))
            DemoConsultations.ALL.forEach { demo ->
                Button(
                    onClick = { onRunDemo(demo) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(62.dp),
                    shape = RoundedCornerShape(14.dp),
                ) {
                    Text(demo.label, fontSize = 18.sp, fontWeight = FontWeight.Medium)
                }
                Spacer(Modifier.height(12.dp))
            }

            Spacer(Modifier.height(28.dp))
            Text(
                "Samjho सिर्फ़ वही दोहराता है जो डॉक्टर ने कहा। यह कोई चिकित्सा सलाह नहीं देता।\n" +
                    "Samjho only repeats what the doctor said. It never gives medical advice.",
                fontSize = 13.sp,
                color = Muted,
            )
        }
    }
}

// ------------------------------------------------------------------ result

@Composable
fun ResultScreen(
    extraction: Extraction,
    ruleMillis: Long,
    sourceLabel: String,
    ai: AiState,
    actions: MedicineActions,
    onBack: () -> Unit,
) {
    val t = remember(extraction.language) { Strings(extraction.language) }

    Surface(color = MaterialTheme.colorScheme.background) {
        Column(
            Modifier
                .fillMaxSize()
                .systemBarsPadding()
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                TextButton(onClick = onBack, contentPadding = PaddingValues(0.dp)) {
                    Text("← वापस / Back", fontSize = 16.sp)
                }
                Spacer(Modifier.weight(1f))
                when {
                    ai is AiState.Checking -> Pill(t.aiChecking, AvoidTint, AvoidInk)
                    ai is AiState.Done && ai.added > 0 -> Pill(t.aiPlusRules, OkTint, MaterialTheme.colorScheme.primary)
                    else -> Pill(t.rulesOnly, OkTint, MaterialTheme.colorScheme.primary)
                }
            }

            Text(sourceLabel, fontSize = 13.sp, color = Muted)
            Text("${ruleMillis} ms", fontSize = 13.sp, color = Muted)

            val toConfirm = extraction.medicines.count { it.isUnconfirmed }
            if (toConfirm > 0) {
                Panel(bg = AvoidTint) {
                    Text(t.needConfirming(toConfirm), fontSize = 16.sp, fontWeight = FontWeight.Bold, color = AvoidInk)
                    Text(t.checkName, fontSize = 14.sp, color = AvoidInk)
                }
            }

            if (extraction.diagnosis.isNotEmpty()) {
                NoteSection(t.diagnosis, extraction.diagnosis, extraction, t, Color.White, Ink)
            }

            if (extraction.medicines.isNotEmpty()) {
                SectionTitle(t.medicines)
                extraction.medicines.forEachIndexed { i, m ->
                    key(i) { MedicineCard(i, m, extraction, t, actions) }
                }
            }

            if (extraction.avoid.isNotEmpty()) {
                NoteSection(t.avoid, extraction.avoid, extraction, t, AvoidTint, AvoidInk)
            }

            if (extraction.warnings.isNotEmpty()) {
                NoteSection(t.warnings, extraction.warnings, extraction, t, WarnTint, WarnInk)
            }

            extraction.followUp?.let { f ->
                SectionTitle(t.followUp)
                Panel {
                    f.inDays?.let { Pill(t.followUpIn(it), OkTint, MaterialTheme.colorScheme.primary) }
                    Text(f.text, fontSize = 17.sp)
                    DoctorWords(extraction, f.sourceLines, t)
                }
            }

            if (extraction.missingSections.isNotEmpty()) {
                SectionTitle(t.notMentioned)
                Panel {
                    extraction.missingSections.forEach { s ->
                        Text("•  ${t.missingSection(s)}", fontSize = 16.sp, color = Muted)
                    }
                    Text(t.askDoctor, fontSize = 14.sp, color = Muted)
                }
            }

            SectionTitle(t.transcript)
            TranscriptPanel(extraction, t)

            Spacer(Modifier.height(4.dp))
            Text(t.disclaimer, fontSize = 13.sp, color = Muted)
            Spacer(Modifier.height(24.dp))
        }
    }
}

/**
 * A medicine is only presented as a fact when the recogniser produced its own name. Anything
 * inferred (sounds like, an alternative hearing, an AI match) shows the raw word that was heard,
 * says plainly that it is unconfirmed, and waits for the patient's answer.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun MedicineCard(index: Int, m: Medicine, extraction: Extraction, t: Strings, actions: MedicineActions) {
    var choosing by remember { mutableStateOf(false) }
    val inferred = m.basis != Basis.HEARD
    val primary = MaterialTheme.colorScheme.primary

    Panel(bg = if (m.isRejected) Color(0xFFEFF2F4) else if (m.isUnconfirmed) AvoidTint else Color.White) {
        if (m.isRejected) {
            Text(
                "${t.dismissed}: ${t.heardAs} “${m.name}”",
                fontSize = 16.sp,
                color = Muted,
                textDecoration = TextDecoration.LineThrough,
            )
            TextButton(onClick = { actions.undo(index) }, contentPadding = PaddingValues(0.dp)) { Text(t.undo) }
            DoctorWords(extraction, m.sourceLines, t)
            return@Panel
        }

        if (inferred) {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                if (m.isUnconfirmed) Pill(t.unconfirmed, WarnTint, WarnInk) else Pill(t.confirmed, OkTint, primary)
                Pill(t.basis(m.basis, m.hypothesis), Color(0xFFEDEFF2), Muted)
                if (m.provenance == Provenance.AI) Pill("AI", Color(0xFFEDEFF2), Muted)
            }
        }

        when {
            m.isUnconfirmed -> {
                Text("${t.heardAs} “${m.name}”", fontSize = 22.sp, fontWeight = FontWeight.Bold)
                Text("${t.possibly}: ${Lexicon.display(m.key)}", fontSize = 17.sp, color = AvoidInk)
            }
            inferred -> {
                Text(Lexicon.display(m.key), fontSize = 25.sp, fontWeight = FontWeight.Bold)
                Text("${t.heardAs} “${m.name}”", fontSize = 14.sp, color = Muted)
            }
            else -> Text(m.name, fontSize = 25.sp, fontWeight = FontWeight.Bold)
        }

        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            m.timesPerDay?.let { Pill(t.timesPerDay(it), OkTint, primary) }
            m.doseCount?.let { Pill(t.dose(it), OkTint, primary) }
            m.timesOfDay.forEach { Pill(t.timeOfDay(it), OkTint, primary) }
            m.foodRelation?.let { Pill(t.food(it), OkTint, primary) }
            m.durationDays?.let { Pill(t.durationDays(it), OkTint, primary) }
        }

        if (m.missing.isNotEmpty()) {
            Text(
                "${t.notMentioned}: " + m.missing.joinToString(", ") { t.missing(it) },
                fontSize = 15.sp,
                color = AvoidInk,
            )
            Text(t.askDoctor, fontSize = 14.sp, color = Muted)
        }

        if (m.isUnconfirmed) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                Button(onClick = { actions.confirm(index) }, shape = RoundedCornerShape(12.dp)) { Text(t.yes) }
                OutlinedButton(onClick = { actions.reject(index) }, shape = RoundedCornerShape(12.dp)) { Text(t.no) }
                TextButton(onClick = { choosing = true }) { Text(t.chooseAnother) }
            }
        } else if (inferred) {
            TextButton(onClick = { actions.undo(index) }, contentPadding = PaddingValues(0.dp)) { Text(t.undo) }
        }

        DoctorWords(extraction, m.sourceLines, t)
    }

    if (choosing) {
        val options = m.candidates.filter { it != m.key }
        AlertDialog(
            onDismissRequest = { choosing = false },
            title = { Text(t.chooseTitle) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text("${t.heardAs} “${m.name}”", fontSize = 14.sp, color = Muted)
                    options.forEach { key ->
                        TextButton(onClick = { choosing = false; actions.choose(index, key) }) {
                            Text(Lexicon.display(key), fontSize = 18.sp)
                        }
                    }
                }
            },
            confirmButton = {},
            dismissButton = { TextButton(onClick = { choosing = false }) { Text(t.cancel) } },
        )
    }
}

/** What the patient can do with an unconfirmed medicine. Every answer is theirs; nothing is automatic. */
class MedicineActions(
    val confirm: (Int) -> Unit,
    val reject: (Int) -> Unit,
    val undo: (Int) -> Unit,
    val choose: (Int, String) -> Unit,
)

@Composable
private fun NoteSection(
    title: String,
    notes: List<Note>,
    extraction: Extraction,
    t: Strings,
    tint: Color,
    ink: Color,
) {
    SectionTitle(title)
    Panel(bg = tint) {
        notes.forEach { note ->
            Text("•  ${note.text}", fontSize = 17.sp, color = ink)
        }
        DoctorWords(extraction, notes.flatMap { it.sourceLines }, t)
    }
}

@Composable
private fun TranscriptPanel(extraction: Extraction, t: Strings) {
    var open by remember { mutableStateOf(false) }
    Panel {
        TextButton(onClick = { open = !open }, contentPadding = PaddingValues(0.dp)) {
            Text(if (open) t.hideWords else t.showTranscript, fontSize = 15.sp)
        }
        if (open) {
            extraction.lines.forEach { line ->
                Text("${line.index + 1}.  ${line.text}", fontSize = 16.sp)
            }
        }
    }
}

// ------------------------------------------------------------------ pieces

@Composable
private fun DoctorWords(extraction: Extraction, lines: List<Int>, t: Strings) {
    if (lines.isEmpty()) return
    var open by remember { mutableStateOf(false) }
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        TextButton(onClick = { open = !open }, contentPadding = PaddingValues(0.dp)) {
            Text(if (open) t.hideWords else t.showWords, fontSize = 15.sp)
        }
        if (open) {
            extraction.quotes(lines).forEach { line ->
                Surface(
                    color = Color(0xFFF1F4F6),
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Column(Modifier.padding(12.dp)) {
                        Text(t.line(line.index), fontSize = 12.sp, color = Muted)
                        Text(line.text, fontSize = 16.sp, color = Ink)
                    }
                }
            }
        }
    }
}

@Composable
private fun SectionTitle(text: String) {
    Text(
        text,
        fontSize = 14.sp,
        fontWeight = FontWeight.Bold,
        color = Muted,
    )
}

@Composable
private fun Panel(bg: Color = Color.White, content: @Composable ColumnScope.() -> Unit) {
    Surface(
        color = bg,
        shape = RoundedCornerShape(14.dp),
        border = BorderStroke(1.dp, Line),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(
            Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
            content = content,
        )
    }
}

@Composable
fun Pill(text: String, bg: Color, fg: Color) {
    Surface(color = bg, shape = RoundedCornerShape(50)) {
        Text(
            text,
            Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
            color = fg,
            fontSize = 14.sp,
            fontWeight = FontWeight.Medium,
        )
    }
}
