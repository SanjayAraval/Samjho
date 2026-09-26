package com.packetloss.samjho.ui

import android.Manifest
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Matrix
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.core.content.ContextCompat
import com.packetloss.samjho.AiState
import com.packetloss.samjho.extract.Lexicon
import com.packetloss.samjho.model.Confirmation
import com.packetloss.samjho.scan.ScanBasis
import com.packetloss.samjho.scan.ScanItem
import com.packetloss.samjho.scan.ScanSearch
import com.packetloss.samjho.scan.ScanStage
import com.packetloss.samjho.scan.ScanUi
import kotlin.math.max
import kotlin.math.min

/** What the patient can do on a scanned medicine. Every answer is theirs; nothing is automatic. */
class ScanActions(
    val confirm: (Int) -> Unit,
    val reject: (Int) -> Unit,
    val undo: (Int) -> Unit,
    val choose: (Int, String) -> Unit,
    val pick: (String) -> Unit,
)

private sealed interface Picker {
    /** Picking a medicine by hand, with no card involved. */
    data object Manual : Picker

    /** Choosing another medicine for one card, with its close matches first. */
    data class Another(val id: Int, val first: List<String>) : Picker
}

@Composable
fun ScanScreen(
    ui: ScanUi,
    onPhoto: (Bitmap) -> Unit,
    onCameraError: (String) -> Unit,
    actions: ScanActions,
    onScanAgain: () -> Unit,
    /** Non-null when the scan came from a summary: merges the confirmed names into it. */
    onApply: (() -> Unit)?,
    /** Where the language-model pass over the words the rules missed stands. */
    ai: AiState,
    onBack: () -> Unit,
) {
    var picker by remember { mutableStateOf<Picker?>(null) }
    val lang = UiLanguage.resolve()
    val t = remember(lang) { Strings(lang) }

    Surface(color = MaterialTheme.colorScheme.background) {
        Column(Modifier.fillMaxSize().systemBarsPadding()) {
            Row(Modifier.padding(horizontal = 16.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                TextButton(onClick = onBack, contentPadding = PaddingValues(0.dp)) {
                    Text(t.back, fontSize = 16.sp)
                }
                Spacer(Modifier.weight(1f))
                LanguageToggle(lang)
            }
            when (val stage = ui.stage) {
                ScanStage.Camera -> CameraStage(t, onPhoto, onCameraError, onPickFromList = { picker = Picker.Manual })
                ScanStage.Reading -> ReadingStage(t)
                ScanStage.Results -> ResultsStage(t, ui, ai, actions, onScanAgain, onApply, onOpenPicker = { picker = it })
                is ScanStage.Failed -> FailedStage(t, stage.message, onScanAgain, onPickFromList = { picker = Picker.Manual })
            }
        }
    }

    picker?.let { target ->
        val already = ui.items.filter { !it.isRejected }.map { it.key }.toSet()
        PickerDialog(
            title = if (target is Picker.Another) t.whichMedicine else t.pickTitle,
            t = t,
            first = (target as? Picker.Another)?.first.orEmpty(),
            already = already,
            onPick = { key ->
                picker = null
                if (target is Picker.Another) actions.choose(target.id, key) else actions.pick(key)
            },
            onDismiss = { picker = null },
        )
    }
}

// ------------------------------------------------------------------ camera

@Composable
private fun CameraStage(t: Strings, onPhoto: (Bitmap) -> Unit, onCameraError: (String) -> Unit, onPickFromList: () -> Unit) {
    val context = LocalContext.current
    var granted by remember {
        mutableStateOf(ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED)
    }
    var refused by rememberSaveable { mutableStateOf(false) }
    val askCamera = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { ok ->
        granted = ok
        refused = !ok
    }
    // The camera is asked for here, when the patient opens the scan, and never when the app starts.
    LaunchedEffect(Unit) { if (!granted) askCamera.launch(Manifest.permission.CAMERA) }

    Column(Modifier.fillMaxSize()) {
        if (granted) {
            Box(Modifier.weight(1f).fillMaxWidth()) {
                CameraPreview(t, onPhoto = onPhoto, reportError = onCameraError)
            }
        } else {
            Column(
                Modifier.weight(1f).fillMaxWidth().padding(24.dp),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(t.cameraNeeded, fontSize = 18.sp, fontWeight = FontWeight.Medium, color = Ink)
                Spacer(Modifier.height(8.dp))
                Text(
                    if (refused) t.cameraRefused else t.cameraPrivacy,
                    fontSize = 15.sp,
                    color = Muted,
                )
                Spacer(Modifier.height(16.dp))
                Button(onClick = { askCamera.launch(Manifest.permission.CAMERA) }, shape = RoundedCornerShape(12.dp)) {
                    Text(t.allowCamera, fontSize = 16.sp)
                }
            }
        }
        PickFromListButton(t, onPickFromList, Modifier.padding(horizontal = 16.dp, vertical = 8.dp))
    }
}

@Composable
private fun CameraPreview(t: Strings, onPhoto: (Bitmap) -> Unit, reportError: (String) -> Unit) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val previewView = remember { PreviewView(context).apply { scaleType = PreviewView.ScaleType.FILL_CENTER } }
    val imageCapture = remember { ImageCapture.Builder().setCaptureMode(ImageCapture.CAPTURE_MODE_MAXIMIZE_QUALITY).build() }
    var capturing by remember { mutableStateOf(false) }

    DisposableEffect(lifecycleOwner) {
        val providerFuture = ProcessCameraProvider.getInstance(context)
        providerFuture.addListener({
            try {
                val provider = providerFuture.get()
                val preview = Preview.Builder().build().also { it.surfaceProvider = previewView.surfaceProvider }
                provider.unbindAll()
                provider.bindToLifecycle(lifecycleOwner, CameraSelector.DEFAULT_BACK_CAMERA, preview, imageCapture)
            } catch (t: Throwable) {
                reportError(t.message ?: "The camera could not be started.")
            }
        }, ContextCompat.getMainExecutor(context))
        onDispose { runCatching { providerFuture.get().unbindAll() } }
    }

    Box(Modifier.fillMaxSize()) {
        AndroidView(factory = { previewView }, modifier = Modifier.fillMaxSize())
        Text(
            t.fillFrame,
            color = Color.White,
            fontSize = 14.sp,
            modifier = Modifier
                .align(Alignment.TopCenter)
                .padding(12.dp)
                .background(Color(0x99000000), RoundedCornerShape(50))
                .padding(horizontal = 14.dp, vertical = 6.dp),
        )
        Surface(
            onClick = {
                if (capturing) return@Surface
                capturing = true
                // takePicture with a callback keeps the photo in memory: nothing is written to storage.
                imageCapture.takePicture(
                    ContextCompat.getMainExecutor(context),
                    object : ImageCapture.OnImageCapturedCallback() {
                        override fun onCaptureSuccess(image: ImageProxy) {
                            val bitmap = try {
                                image.toUprightBitmap()
                            } catch (t: Throwable) {
                                capturing = false
                                reportError(t.message ?: "The photo could not be read.")
                                return
                            } finally {
                                image.close()
                            }
                            capturing = false
                            onPhoto(bitmap)
                        }

                        override fun onError(exception: ImageCaptureException) {
                            capturing = false
                            reportError(exception.message ?: "The photo could not be taken.")
                        }
                    },
                )
            },
            shape = CircleShape,
            color = Color.White,
            border = BorderStroke(5.dp, MaterialTheme.colorScheme.primary),
            modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 20.dp).size(76.dp),
        ) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("📷", fontSize = 28.sp)
            }
        }
    }
}

/** The photo, turned upright and no larger than the text reader needs, still only in memory. */
private fun ImageProxy.toUprightBitmap(maxEdge: Int = 2560): Bitmap {
    val raw = toBitmap()
    val rotation = imageInfo.rotationDegrees
    val scale = min(1f, maxEdge / max(raw.width, raw.height).toFloat())
    if (rotation == 0 && scale == 1f) return raw
    val matrix = Matrix().apply {
        postRotate(rotation.toFloat())
        postScale(scale, scale)
    }
    val out = Bitmap.createBitmap(raw, 0, 0, raw.width, raw.height, matrix, true)
    if (out !== raw) raw.recycle()
    return out
}

@Composable
private fun ReadingStage(t: Strings) {
    Column(Modifier.fillMaxSize().padding(24.dp), verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) {
        CircularProgressIndicator()
        Spacer(Modifier.height(16.dp))
        Text(t.readingPrescription, fontSize = 17.sp, color = Ink)
        Text(t.onThisPhoneOnly, fontSize = 14.sp, color = Muted)
    }
}

@Composable
private fun FailedStage(t: Strings, message: String, onTryAgain: () -> Unit, onPickFromList: () -> Unit) {
    Column(Modifier.fillMaxSize().padding(24.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Panel(bg = WarnTint) {
            Text(t.scanFailed, fontSize = 17.sp, fontWeight = FontWeight.SemiBold, color = WarnInk)
            Text(message, fontSize = 14.sp, color = WarnInk)
        }
        Text(t.stillPickFromList, fontSize = 15.sp, color = Muted)
        PickFromListButton(t, onPickFromList)
        OutlinedButton(onClick = onTryAgain, modifier = Modifier.fillMaxWidth().height(52.dp), shape = RoundedCornerShape(14.dp)) {
            Text(t.tryCameraAgain, fontSize = 16.sp)
        }
    }
}

@Composable
private fun PickFromListButton(t: Strings, onClick: () -> Unit, modifier: Modifier = Modifier) {
    OutlinedButton(onClick = onClick, modifier = modifier.fillMaxWidth().height(52.dp), shape = RoundedCornerShape(14.dp)) {
        Text(t.pickFromList, fontSize = 16.sp)
    }
}

// ------------------------------------------------------------------ results

@Composable
private fun ResultsStage(t: Strings, ui: ScanUi, ai: AiState, actions: ScanActions, onScanAgain: () -> Unit, onApply: (() -> Unit)?, onOpenPicker: (Picker) -> Unit) {
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp).padding(bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        val suggested = ui.items.count { it.basis != ScanBasis.PICKED }
        Text(t.scanTitle, fontSize = 22.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
        if (ai is AiState.Checking) Text(t.stillChecking, fontSize = 13.sp, color = Muted)

        if (ui.items.isEmpty()) {
            Panel(bg = AvoidTint) {
                Text(t.nothingMatched, fontSize = 17.sp, fontWeight = FontWeight.SemiBold, color = AvoidInk)
                Text(t.nothingMatchedHint, fontSize = 14.sp, color = AvoidInk)
            }
        } else if (suggested > 0) {
            Text(t.foundPossible(suggested), fontSize = 14.sp, color = Muted)
        }

        ui.items.forEach { item ->
            ScanCard(t, item, actions, onChooseAnother = { onOpenPicker(Picker.Another(item.id, item.candidates)) })
        }

        if (onApply != null) {
            // Only names the patient confirmed go into the summary. The paper never supplies timings.
            val confirmed = ui.items.count { it.confirmation == Confirmation.CONFIRMED }
            Button(
                onClick = onApply,
                enabled = confirmed > 0,
                modifier = Modifier.fillMaxWidth().height(58.dp),
                shape = RoundedCornerShape(14.dp),
            ) {
                Text(
                    if (confirmed > 0) t.useConfirmed(confirmed) else t.confirmToUse,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Medium,
                )
            }
            Text(t.scanPrescriptionHint, fontSize = 13.sp, color = Muted)
        }

        Button(
            onClick = { onOpenPicker(Picker.Manual) },
            modifier = Modifier.fillMaxWidth().height(58.dp),
            shape = RoundedCornerShape(14.dp),
        ) {
            Text(t.didntFind, fontSize = 17.sp, fontWeight = FontWeight.Medium)
        }
        OutlinedButton(onClick = onScanAgain, modifier = Modifier.fillMaxWidth().height(52.dp), shape = RoundedCornerShape(14.dp)) {
            Text(t.scanAgain, fontSize = 16.sp)
        }

        WhatTheCameraRead(t, ui)

        Text(t.scanDisclaimer, fontSize = 13.sp, color = Muted)
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ScanCard(t: Strings, item: ScanItem, actions: ScanActions, onChooseAnother: () -> Unit) {
    val primary = MaterialTheme.colorScheme.primary
    val name = Lexicon.display(item.key)

    Panel(bg = if (item.isRejected) Color(0xFFEFF2F4) else if (item.isUnconfirmed) AvoidTint else Color.White) {
        when {
            item.isRejected -> {
                Text(
                    "${t.dismissed}: $name",
                    fontSize = 16.sp,
                    color = Muted,
                    textDecoration = TextDecoration.LineThrough,
                )
                UndoText(t) { actions.undo(item.id) }
            }
            else -> {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (item.isUnconfirmed) Pill(t.unconfirmed, WarnTint, WarnInk) else Pill(t.confirmed, OkTint, primary)
                    // Only the two labels that say who decided: the AI, or the patient. How close a spelling was is ours to know.
                    when (item.basis) {
                        ScanBasis.AI_MATCHED -> Pill(t.aiMatched, Color(0xFFEDEFF2), Muted)
                        ScanBasis.PICKED -> Pill(t.pickedByYou, Color(0xFFEDEFF2), Muted)
                        else -> Unit
                    }
                }
                if (item.readAs != null) {
                    Text(t.cameraRead, fontSize = 13.sp, color = Muted)
                    Text("“${item.readAs}”", fontSize = 20.sp, fontWeight = FontWeight.Bold, color = Ink, fontFamily = FontFamily.Monospace)
                    Text(if (item.isUnconfirmed) t.possibly else t.confirmedAs, fontSize = 13.sp, color = Muted)
                }
                Text(name, fontSize = 24.sp, fontWeight = FontWeight.Bold, color = if (item.isUnconfirmed) AvoidInk else Ink)

                if (item.isUnconfirmed) {
                    Text(t.checkAgainstPrescription, fontSize = 13.sp, color = AvoidInk)
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Button(onClick = { actions.confirm(item.id) }, shape = RoundedCornerShape(12.dp)) { Text(t.confirmButton, fontSize = 16.sp) }
                        OutlinedButton(onClick = { actions.reject(item.id) }, shape = RoundedCornerShape(12.dp)) { Text(t.rejectButton, fontSize = 16.sp) }
                        TextButton(onClick = onChooseAnother) { Text(t.chooseAnother, fontSize = 16.sp) }
                    }
                } else {
                    UndoText(t) { actions.undo(item.id) }
                }
            }
        }
    }
}

@Composable
private fun UndoText(t: Strings, onClick: () -> Unit) {
    Text(
        t.undo,
        color = MaterialTheme.colorScheme.primary,
        fontSize = 15.sp,
        fontWeight = FontWeight.Medium,
        modifier = Modifier.clickable(onClick = onClick).padding(vertical = 8.dp),
    )
}

/** The raw text, collapsed by default: so it can be shown on stage, and so nothing looks invented. */
@Composable
private fun WhatTheCameraRead(t: Strings, ui: ScanUi) {
    var open by rememberSaveable { mutableStateOf(false) }
    Surface(color = Color.White, shape = RoundedCornerShape(14.dp), border = BorderStroke(1.dp, Line), modifier = Modifier.fillMaxWidth()) {
        Column {
            Row(
                Modifier.fillMaxWidth().clickable { open = !open }.padding(16.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(t.showCameraRead, fontSize = 16.sp, fontWeight = FontWeight.Medium, color = Ink, modifier = Modifier.weight(1f))
                Text(if (open) "▾" else "▸", fontSize = 18.sp, color = Muted)
            }
            if (open) {
                HorizontalDivider(color = Line)
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    val read = ui.read
                    if (read == null || read.isEmpty) {
                        Text(t.nothingRead, fontSize = 14.sp, color = Muted)
                    } else {
                        RawBlock(read.latin)
                        RawBlock(read.devanagari)
                    }
                }
            }
        }
    }
}

@Composable
private fun RawBlock(lines: List<String>) {
    if (lines.isEmpty()) return
    Text(
        lines.joinToString("\n"),
        fontSize = 14.sp,
        fontFamily = FontFamily.Monospace,
        color = Ink,
    )
}

@Composable
private fun Panel(bg: Color = Color.White, content: @Composable ColumnScope.() -> Unit) {
    Surface(color = bg, shape = RoundedCornerShape(14.dp), border = BorderStroke(1.dp, Line), modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp), content = content)
    }
}

// ------------------------------------------------------------------ the full list

/** Every medicine in the lexicon, searchable. Typing goes straight to the search box: it is the fast path. */
@Composable
private fun PickerDialog(title: String, t: Strings, first: List<String>, already: Set<String>, onPick: (String) -> Unit, onDismiss: () -> Unit) {
    var query by remember { mutableStateOf("") }
    val entries = remember(query, first) { ScanSearch.filter(query, first) }
    val focus = remember { FocusRequester() }

    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
            Column(Modifier.statusBarsPadding().navigationBarsPadding().imePadding().padding(16.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(title, fontSize = 18.sp, fontWeight = FontWeight.SemiBold, color = Ink, modifier = Modifier.weight(1f))
                    TextButton(onClick = onDismiss) { Text("✕ ${t.close}", fontSize = 15.sp) }
                }
                OutlinedTextField(
                    value = query,
                    onValueChange = { query = it },
                    singleLine = true,
                    placeholder = { Text(t.typeFewLetters) },
                    modifier = Modifier.fillMaxWidth().focusRequester(focus),
                )
                LaunchedEffect(Unit) { focus.requestFocus() }
                Spacer(Modifier.height(8.dp))
                if (entries.isEmpty()) {
                    Text(
                        t.noNameInList,
                        fontSize = 15.sp,
                        color = Muted,
                        modifier = Modifier.padding(vertical = 16.dp),
                    )
                }
                LazyColumn(Modifier.weight(1f)) {
                    items(entries, key = { it.key }) { entry ->
                        Row(
                            Modifier.fillMaxWidth().height(58.dp).clickable { onPick(entry.key) },
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(entry.label, fontSize = 19.sp, color = Ink, modifier = Modifier.weight(1f))
                            if (entry.key in already) Text(t.added, fontSize = 13.sp, color = Muted)
                        }
                        HorizontalDivider(color = Line)
                    }
                }
            }
        }
    }
}
