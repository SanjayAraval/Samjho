package com.packetloss.samjho.ui

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.packetloss.samjho.AiState
import com.packetloss.samjho.demo.DemoConsultation
import com.packetloss.samjho.demo.DemoConsultations
import com.packetloss.samjho.extract.Lexicon
import com.packetloss.samjho.llm.LlmStatus
import com.packetloss.samjho.model.Basis
import com.packetloss.samjho.model.Confirmation
import com.packetloss.samjho.model.Continuation
import com.packetloss.samjho.model.Extraction
import com.packetloss.samjho.model.Language
import com.packetloss.samjho.model.Medicine
import com.packetloss.samjho.model.Provenance
import com.packetloss.samjho.scan.PaperMerge
import com.packetloss.samjho.share.SummaryBuilder
import com.packetloss.samjho.share.SummarySharer
import com.packetloss.samjho.speech.EngineId
import com.packetloss.samjho.voice.Speaker
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

// ------------------------------------------------------------------ home

/**
 * One thing to do, first: record the consultation. Everything else is smaller and below it. The screen is shown in one
 * language, the one the toggle at the top says, and that is also the language the consultation is recorded in.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun HomeScreen(
    engine: EngineId,
    llm: LlmStatus,
    unavailable: Map<Language, String>,
    onSelectEngine: (EngineId) -> Unit,
    onRunDemo: (DemoConsultation) -> Unit,
    onRecord: (Language) -> Unit,
    onScan: () -> Unit,
) {
    val lang = UiLanguage.resolve()
    val t = remember(lang) { Strings(lang) }
    val context = LocalContext.current
    var consent by remember { mutableStateOf(false) }
    var needConsent by remember { mutableStateOf(false) }
    var pending by remember { mutableStateOf<Language?>(null) }
    var micDenied by remember { mutableStateOf(false) }
    var showDemos by remember { mutableStateOf(false) }
    var showSpeech by remember { mutableStateOf(false) }
    val askMic = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        val asked = pending
        pending = null
        if (granted && asked != null) onRecord(asked) else micDenied = true
    }
    fun record(language: Language) {
        micDenied = false
        val has = ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) ==
            PackageManager.PERMISSION_GRANTED
        if (has) onRecord(language) else {
            pending = language
            askMic.launch(Manifest.permission.RECORD_AUDIO)
        }
    }
    val reason = unavailable[lang]

    Surface(color = MaterialTheme.colorScheme.background) {
        Column(
            Modifier
                .fillMaxSize()
                .systemBarsPadding()
                .verticalScroll(rememberScrollState())
                .padding(24.dp),
        ) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) { LanguageToggle(lang) }

            Spacer(Modifier.height(24.dp))
            Text("Samjho", fontSize = 46.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
            Spacer(Modifier.height(6.dp))
            Text(t.tagline, fontSize = 18.sp, color = Ink)

            Spacer(Modifier.height(36.dp))
            Button(
                onClick = {
                    if (!consent) needConsent = true else record(lang)
                },
                enabled = reason == null,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(84.dp),
                shape = RoundedCornerShape(18.dp),
            ) {
                Text(t.recordConsultation, fontSize = 22.sp, fontWeight = FontWeight.Bold)
            }
            Spacer(Modifier.height(6.dp))
            Row(
                Modifier
                    .fillMaxWidth()
                    .clickable { consent = !consent; needConsent = false },
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Checkbox(checked = consent, onCheckedChange = { consent = it; needConsent = false })
                Text(t.consent, fontSize = 15.sp, color = Ink)
            }
            if (needConsent) Text(t.consentNeeded, fontSize = 14.sp, color = WarnInk)
            if (reason != null) Text(reason, fontSize = 13.sp, color = WarnInk, modifier = Modifier.padding(top = 4.dp))
            if (micDenied) Text(t.micNeeded, fontSize = 14.sp, color = WarnInk)

            Spacer(Modifier.height(24.dp))
            OutlinedButton(
                onClick = onScan,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(54.dp),
                shape = RoundedCornerShape(14.dp),
            ) {
                Text(t.scanShort, fontSize = 17.sp, fontWeight = FontWeight.Medium)
            }

            Spacer(Modifier.height(8.dp))
            TextButton(onClick = { showDemos = !showDemos }, contentPadding = PaddingValues(0.dp)) {
                Text((if (showDemos) "▾  " else "▸  ") + t.demoHeading, fontSize = 15.sp)
            }
            if (showDemos) {
                DemoConsultations.ALL.forEach { demo ->
                    OutlinedButton(
                        onClick = { onRunDemo(demo) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(50.dp),
                        shape = RoundedCornerShape(12.dp),
                    ) {
                        Text(demo.label, fontSize = 16.sp)
                    }
                    Spacer(Modifier.height(8.dp))
                }
            }

            // Which speech recogniser to use is rarely needed, so it sits behind one small control.
            TextButton(onClick = { showSpeech = !showSpeech }, contentPadding = PaddingValues(0.dp)) {
                Text((if (showSpeech) "▾  " else "▸  ") + t.speechOptions, fontSize = 14.sp, color = Muted)
            }
            if (showSpeech) {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    EngineId.entries.forEach { id ->
                        FilterChip(
                            selected = id == engine,
                            onClick = { onSelectEngine(id) },
                            label = { Text(id.label, fontSize = 14.sp) },
                        )
                    }
                }
            }

            Spacer(Modifier.height(28.dp))
            Text(t.readiness(reason == null, llm), fontSize = 13.sp, color = Muted)
            Spacer(Modifier.height(10.dp))
            Text(t.disclaimer, fontSize = 13.sp, color = Muted)
        }
    }
}

// ------------------------------------------------------------------ result

/**
 * What to do, in the order a patient needs it: the medicines, what to avoid, the warning signs, the next visit. The
 * doctor's own words (with the transcript) are behind one control at the bottom, closed until asked for.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun ResultScreen(
    extraction: Extraction,
    ruleMillis: Long,
    sourceLabel: String,
    ai: AiState,
    llm: LlmStatus,
    reading: Speaker.State,
    onRead: (Language) -> Unit,
    onStopReading: () -> Unit,
    /** Opens the prescription scan, whose confirmed names are then merged into this summary. */
    onScanPrescription: () -> Unit,
    /** How many names the last applied prescription confirmed and how many medicines it added, if one was. */
    paperApplied: Pair<Int, Int>?,
    actions: MedicineActions,
    onBack: () -> Unit,
) {
    val lang = UiLanguage.resolve(extraction.language)
    val t = remember(lang) { Strings(lang) }
    // Screens opened from here (the prescription scan) follow this summary until the person chooses otherwise.
    LaunchedEffect(extraction.language) { UiLanguage.consultation = extraction.language }

    var wordsOpen by remember { mutableStateOf(false) }
    var showWordsRequest by remember { mutableIntStateOf(0) }
    val wordsPosition = remember { BringIntoViewRequester() }
    LaunchedEffect(showWordsRequest) {
        if (showWordsRequest > 0) wordsPosition.bringIntoView()
    }

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
                    Text(t.back, fontSize = 16.sp)
                }
                Spacer(Modifier.weight(1f))
                LanguageToggle(lang)
            }

            val context = LocalContext.current
            var choosingShare by remember { mutableStateOf(false) }
            var shareError by remember { mutableStateOf<String?>(null) }

            Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                if (reading is Speaker.State.Speaking) {
                    OutlinedButton(onClick = onStopReading, shape = RoundedCornerShape(12.dp)) { Text(t.stopReading, fontSize = 16.sp) }
                } else {
                    Button(onClick = { onRead(lang) }, shape = RoundedCornerShape(12.dp)) { Text(t.readAloud, fontSize = 16.sp) }
                }
                OutlinedButton(onClick = { shareError = null; choosingShare = true }, shape = RoundedCornerShape(12.dp)) {
                    Text(t.share, fontSize = 16.sp)
                }
            }
            if (reading is Speaker.State.Unavailable) {
                Text(reading.reason, fontSize = 14.sp, color = WarnInk)
            }
            shareError?.let { Text("${t.shareFailed} $it", fontSize = 14.sp, color = WarnInk) }
            if (ai is AiState.Checking) Text(t.stillChecking, fontSize = 13.sp, color = Muted)

            if (choosingShare) {
                val pending = extraction.medicines.count { it.isUnconfirmed }
                val send: (SummarySharer.Format) -> Unit = { format ->
                    choosingShare = false
                    val stamp = SimpleDateFormat(
                        "d MMM yyyy, HH:mm",
                        if (lang == Language.HINDI) Locale("hi", "IN") else Locale.ENGLISH,
                    ).format(Date())
                    // The shared image and PDF are written in the language the screen is showing.
                    SummarySharer.share(
                        context = context,
                        doc = SummaryBuilder.build(extraction, t, stamp),
                        format = format,
                        caption = t.shareCaption,
                        chooserTitle = t.shareTitle,
                        onError = { shareError = it },
                    )
                }
                AlertDialog(
                    onDismissRequest = { choosingShare = false },
                    title = { Text(t.shareTitle) },
                    text = {
                        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            if (pending > 0) Text(t.shareUnconfirmed(pending), fontSize = 14.sp, color = AvoidInk)
                            TextButton(onClick = { send(SummarySharer.Format.IMAGE) }) { Text(t.shareAsImage, fontSize = 16.sp) }
                            TextButton(onClick = { send(SummarySharer.Format.PDF) }) { Text(t.shareAsPdf, fontSize = 16.sp) }
                        }
                    },
                    confirmButton = {},
                    dismissButton = { TextButton(onClick = { choosingShare = false }) { Text(t.cancel) } },
                )
            }

            // 1. Medicines.
            val toConfirm = extraction.medicines.count { it.isUnconfirmed }
            if (toConfirm > 0) {
                Panel(bg = AvoidTint) {
                    Text(t.needConfirming(toConfirm), fontSize = 16.sp, fontWeight = FontWeight.Bold, color = AvoidInk)
                    Text(t.checkName, fontSize = 14.sp, color = AvoidInk)
                    Text(
                        t.showWords,
                        color = MaterialTheme.colorScheme.primary,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Medium,
                        modifier = Modifier
                            .clickable { wordsOpen = true; showWordsRequest++ }
                            .padding(vertical = 6.dp),
                    )
                }
            }

            if (extraction.medicines.isNotEmpty()) {
                SectionTitle(t.medicines)
                extraction.medicines.forEachIndexed { i, m ->
                    key(i) { MedicineCard(i, m, extraction, t, actions) }
                }
            }

            val unnamed = extraction.unnamedDosing
            if (unnamed.isNotEmpty()) {
                SectionTitle(t.unnamedDosing)
                Panel(bg = AvoidTint) {
                    unnamed.forEach { line ->
                        Text("•  ${line.text}", fontSize = 17.sp, color = AvoidInk)
                    }
                    Text(t.unnamedDosingHint, fontSize = 14.sp, color = AvoidInk)
                }
            }

            // 2. Things to avoid.
            if (extraction.avoid.isNotEmpty()) {
                SectionTitle(t.avoid)
                Panel(bg = AvoidTint) {
                    extraction.avoid.forEach { note -> Text("•  ${note.text}", fontSize = 18.sp, color = AvoidInk) }
                }
            }

            // 3. Warning signs: a red block that cannot be mistaken for the rest.
            if (extraction.warnings.isNotEmpty()) {
                Surface(
                    color = WarnTint,
                    shape = RoundedCornerShape(14.dp),
                    border = BorderStroke(2.dp, WarnInk),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text("⚠  ${t.warnings}", fontSize = 20.sp, fontWeight = FontWeight.Bold, color = WarnInk)
                        extraction.warnings.forEach { note ->
                            Text(note.text, fontSize = 19.sp, fontWeight = FontWeight.Medium, color = WarnInk)
                        }
                    }
                }
            }

            // 4. Next visit.
            extraction.followUp?.let { f ->
                SectionTitle(t.followUp)
                Panel {
                    f.inDays?.let { Pill(t.followUpIn(it), OkTint, MaterialTheme.colorScheme.primary) }
                    Text(f.text, fontSize = 18.sp)
                }
            }

            // What the doctor did not say stays visible, and quiet.
            if (extraction.missingSections.isNotEmpty()) {
                SectionTitle(t.notMentioned)
                Panel {
                    extraction.missingSections.forEach { s ->
                        Text("•  ${t.missingSection(s)}", fontSize = 15.sp, color = Muted)
                    }
                    Text(t.askDoctor, fontSize = 13.sp, color = Muted)
                }
            }

            // The doctor's voice gave the instructions, the paper gives the names.
            Panel(bg = OkTint) {
                Text(t.scanPrescriptionHint, fontSize = 14.sp, color = Ink)
                paperApplied?.let { (named, added) ->
                    Text("✓ ${t.paperApplied(named, added)}", fontSize = 14.sp, fontWeight = FontWeight.Medium, color = MaterialTheme.colorScheme.primary)
                }
                OutlinedButton(onClick = onScanPrescription, shape = RoundedCornerShape(12.dp)) {
                    Text("📷  ${t.scanPrescription}", fontSize = 16.sp)
                }
            }

            // 5. Everything else, closed until asked for.
            DoctorsWords(
                extraction = extraction,
                t = t,
                open = wordsOpen,
                onToggle = { wordsOpen = !wordsOpen },
                modifier = Modifier.bringIntoViewRequester(wordsPosition),
            )

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
    val paperOnly = m.basis == Basis.FROM_PRESCRIPTION
    val fromPaper = m.paper != null && !paperOnly
    val known = Lexicon.isKey(m.key)
    // A word next to "tablet" that is not a known medicine is unconfirmed too, without "Possibly: X".
    val showsStatus = inferred || m.confirmation != Confirmation.NOT_NEEDED
    val primary = MaterialTheme.colorScheme.primary

    Panel(bg = if (m.isRejected) Color(0xFFEFF2F4) else if (m.isUnconfirmed) AvoidTint else Color.White) {
        if (m.isRejected) {
            Text(
                "${t.dismissed}: ${t.heardAs} “${m.name}”",
                fontSize = 16.sp,
                color = Muted,
                textDecoration = TextDecoration.LineThrough,
            )
            UndoLink(t) { actions.undo(index) }
            return@Panel
        }

        if (showsStatus) {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                if (m.isUnconfirmed) Pill(t.unconfirmed, WarnTint, WarnInk) else Pill(t.confirmed, OkTint, primary)
                when {
                    paperOnly -> Pill(t.fromPrescriptionNotSpoken, OkTint, primary)
                    fromPaper -> Pill(t.confirmedFromPrescription, OkTint, primary)
                }
                // An AI match is always marked as one; the other ways a name was guessed are told by "heard as".
                if (m.basis == Basis.AI_MATCHED || m.provenance == Provenance.AI) Pill(t.aiMatched, Color(0xFFEDEFF2), Muted)
            }
        }

        when {
            // The paper settled the name, so the card shows it, and still shows what was heard.
            paperOnly -> {
                Text(Lexicon.display(m.key), fontSize = 25.sp, fontWeight = FontWeight.Bold)
                m.paper?.readAs?.let { Text("${t.cameraRead} “$it”", fontSize = 14.sp, color = Muted) }
            }
            fromPaper -> {
                Text(Lexicon.display(m.key), fontSize = 25.sp, fontWeight = FontWeight.Bold)
                // The pill above already says "confirmed from prescription"; this line adds what was heard.
                PaperMerge.heardAs(m)?.let { Text("${t.heardAs} “$it”", fontSize = 14.sp, color = Muted) }
            }
            m.isUnconfirmed -> {
                Text("${t.heardAs} “${m.name}”", fontSize = 22.sp, fontWeight = FontWeight.Bold)
                if (known) Text("${t.possibly}: ${Lexicon.display(m.key)}", fontSize = 17.sp, color = AvoidInk)
                else Text(t.isThisAMedicine, fontSize = 17.sp, color = AvoidInk)
            }
            inferred -> {
                Text(Lexicon.display(m.key), fontSize = 25.sp, fontWeight = FontWeight.Bold)
                Text("${t.heardAs} “${m.name}”", fontSize = 14.sp, color = Muted)
            }
            else -> Text(m.name, fontSize = 25.sp, fontWeight = FontWeight.Bold)
        }

        // "1 tablet, 3 times a day, after food, for 5 days": one plain sentence, only what the doctor said.
        t.sentence(m)?.let { Text(it, fontSize = 19.sp, color = Ink) }

        // A detail from a later, separate sentence is still said to be one; the words themselves are under
        // "Show the doctor's words".
        val later = m.continuations.flatMap { it.details }.distinct()
        if (later.isNotEmpty()) {
            Text("${t.saidLaterShort}: " + later.joinToString(", ") { t.detailName(it) }, fontSize = 13.sp, color = Muted)
        }

        if (m.missing.isNotEmpty()) {
            Text(
                "${t.notMentioned}: " + m.missing.joinToString(", ") { t.missing(it) },
                fontSize = 13.sp,
                color = Muted,
            )
            Text(t.askDoctor, fontSize = 13.sp, color = Muted)
        }

        if (m.isUnconfirmed) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                Button(onClick = { actions.confirm(index) }, shape = RoundedCornerShape(12.dp)) { Text(t.yes) }
                OutlinedButton(onClick = { actions.reject(index) }, shape = RoundedCornerShape(12.dp)) { Text(t.no) }
                TextButton(onClick = { choosing = true }) { Text(t.chooseAnother) }
            }
        } else if (showsStatus) {
            UndoLink(t) { actions.undo(index) }
        }
    }

    if (choosing) {
        // A medicine already listed elsewhere is not offered, so choosing can't create a duplicate card.
        val taken = extraction.medicines.filterIndexed { j, other -> j != index && !other.isRejected }.map { it.key }.toSet()
        val options = m.candidates.filter { it != m.key && it !in taken }
        AlertDialog(
            onDismissRequest = { choosing = false },
            title = { Text(t.chooseTitle) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text("${t.heardAs} “${m.name}”", fontSize = 14.sp, color = Muted)
                    if (options.isEmpty()) Text(t.noOtherMatch, fontSize = 15.sp, color = Muted)
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

/** A plain text link, left-aligned with the other links; a TextButton would centre a short label. */
@Composable
private fun UndoLink(t: Strings, onClick: () -> Unit) {
    Text(
        t.undo,
        color = MaterialTheme.colorScheme.primary,
        fontSize = 15.sp,
        fontWeight = FontWeight.Medium,
        modifier = Modifier
            .clickable(onClick = onClick)
            .padding(vertical = 12.dp),
    )
}

/** What the patient can do with an unconfirmed medicine. Every answer is theirs; nothing is automatic. */
class MedicineActions(
    val confirm: (Int) -> Unit,
    val reject: (Int) -> Unit,
    val undo: (Int) -> Unit,
    val choose: (Int, String) -> Unit,
)

// ------------------------------------------------------------------ the doctor's words

/**
 * The one place the evidence lives: what the doctor said about each item, and the full transcript. Closed by default,
 * one tap to open. Nothing is removed from the app; it is just not in the way.
 */
@Composable
private fun DoctorsWords(
    extraction: Extraction,
    t: Strings,
    open: Boolean,
    onToggle: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        color = Color.White,
        shape = RoundedCornerShape(14.dp),
        border = BorderStroke(1.dp, Line),
        modifier = modifier.fillMaxWidth(),
    ) {
        Column {
            Row(
                Modifier
                    .fillMaxWidth()
                    .clickable(onClick = onToggle)
                    .padding(16.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    if (open) t.hideDoctorWords else t.showWords,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Medium,
                    color = Ink,
                    modifier = Modifier.weight(1f),
                )
                Text(if (open) "▾" else "▸", fontSize = 18.sp, color = Muted)
            }
            if (open) {
                Column(Modifier.padding(start = 16.dp, end = 16.dp, bottom = 16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                    if (extraction.diagnosis.isNotEmpty()) {
                        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Text(t.diagnosisSaid, fontSize = 14.sp, fontWeight = FontWeight.Bold, color = Muted)
                            extraction.diagnosis.forEach { Text("•  ${it.text}", fontSize = 17.sp, color = Ink) }
                        }
                    }

                    extraction.medicines.forEach { m ->
                        val title = when {
                            m.basis == Basis.FROM_PRESCRIPTION || m.paper != null -> Lexicon.display(m.key)
                            m.isUnconfirmed || m.isRejected -> "${t.heardAs} “${m.name}”"
                            m.basis != Basis.HEARD -> Lexicon.display(m.key)
                            else -> m.name
                        }
                        Quotes(extraction, title, m.sourceLines, t, m.continuations)
                    }
                    extraction.diagnosis.forEach { Quotes(extraction, it.text, it.sourceLines, t) }
                    extraction.avoid.forEach { Quotes(extraction, it.text, it.sourceLines, t) }
                    extraction.warnings.forEach { Quotes(extraction, it.text, it.sourceLines, t) }
                    extraction.followUp?.let { Quotes(extraction, it.text, it.sourceLines, t) }

                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text(t.transcript, fontSize = 14.sp, fontWeight = FontWeight.Bold, color = Muted)
                        extraction.lines.forEach { line ->
                            Text("${line.index + 1}.  ${line.text}", fontSize = 16.sp, color = Ink)
                        }
                    }
                }
            }
        }
    }
}

/** One item and the line or lines it came from. Nothing is shown for an item with no line (a name from the paper). */
@Composable
private fun Quotes(
    extraction: Extraction,
    title: String,
    lines: List<Int>,
    t: Strings,
    continuations: List<Continuation> = emptyList(),
) {
    if (lines.isEmpty() && continuations.isEmpty()) return
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(title, fontSize = 14.sp, fontWeight = FontWeight.Bold, color = Muted)
        extraction.quotes(lines).forEach { line ->
            Surface(color = Color(0xFFF1F4F6), shape = RoundedCornerShape(10.dp), modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.padding(12.dp)) {
                    Text(t.line(line.index), fontSize = 12.sp, color = Muted)
                    Text(line.text, fontSize = 16.sp, color = Ink)
                }
            }
        }
        // The details the doctor added in a sentence of their own, each with its own line and what it added.
        continuations.forEach { c ->
            val line = extraction.lines.getOrNull(c.line) ?: return@forEach
            Surface(color = Color(0xFFEAF2F0), shape = RoundedCornerShape(10.dp), modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.padding(12.dp)) {
                    Text("${t.line(line.index)} · ${t.saidLater}", fontSize = 12.sp, color = Muted)
                    Text(line.text, fontSize = 16.sp, color = Ink)
                    Text(c.details.joinToString(", ") { t.detailName(it) }, fontSize = 13.sp, color = Muted)
                }
            }
        }
    }
}

// ------------------------------------------------------------------ pieces

@Composable
private fun SectionTitle(text: String) {
    Text(
        text,
        fontSize = 15.sp,
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
