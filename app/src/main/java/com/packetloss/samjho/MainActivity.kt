package com.packetloss.samjho

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.runtime.Composable
import androidx.lifecycle.viewmodel.compose.viewModel
import com.packetloss.samjho.ui.HomeScreen
import com.packetloss.samjho.ui.MedicineActions
import com.packetloss.samjho.reminders.Reminders
import com.packetloss.samjho.ui.RecordScreen
import com.packetloss.samjho.ui.RemindersScreen
import com.packetloss.samjho.ui.ResultScreen
import com.packetloss.samjho.ui.ScanActions
import com.packetloss.samjho.ui.ScanScreen
import com.packetloss.samjho.scan.ScanStage
import com.packetloss.samjho.ui.SamjhoTheme

class MainActivity : ComponentActivity() {
    private val viewModel: SamjhoViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { SamjhoTheme { SamjhoApp(viewModel) } }
        openFromNotification(intent)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        openFromNotification(intent)
    }

    /** A tapped reminder brings the summary it was set from. The extra is used up, so a rotation does not reopen it. */
    private fun openFromNotification(intent: Intent?) {
        val id = intent?.getStringExtra(Reminders.EXTRA_CONSULTATION) ?: return
        intent.removeExtra(Reminders.EXTRA_CONSULTATION)
        viewModel.openConsultation(id)
    }
}

@Composable
fun SamjhoApp(viewModel: SamjhoViewModel = viewModel()) {
    val state = viewModel.state
    val extraction = state.extraction
    val recording = state.recording
    val scan = state.scan

    when {
        state.remindersOpen -> {
            BackHandler(onBack = viewModel::closeReminders)
            RemindersScreen(
                reminders = state.reminders,
                onCancel = viewModel::cancelReminder,
                onCancelAll = viewModel::cancelAllReminders,
                onOpenSummary = viewModel::openConsultation,
                onBack = viewModel::closeReminders,
            )
        }
        recording != null -> {
            BackHandler(onBack = viewModel::cancelRecording)
            RecordScreen(
                recording = recording,
                onStop = viewModel::stopRecording,
                onCancel = viewModel::cancelRecording,
            )
        }
        scan != null -> {
            // While a photo is being read, Back is ignored rather than leaving a half-finished read behind.
            BackHandler(onBack = { if (scan.stage != ScanStage.Reading) viewModel.closeScan() })
            ScanScreen(
                ui = scan,
                onPhoto = viewModel::scanPhoto,
                onCameraError = viewModel::scanFailed,
                actions = ScanActions(
                    confirm = viewModel::scanConfirm,
                    reject = viewModel::scanReject,
                    undo = viewModel::scanUndo,
                    choose = viewModel::scanChoose,
                    pick = viewModel::scanPick,
                ),
                onScanAgain = viewModel::scanAgain,
                onApply = if (scan.forSummary) viewModel::applyScanToSummary else null,
                ai = state.scanAi,
                onBack = viewModel::closeScan,
            )
        }
        extraction != null -> {
            BackHandler(onBack = viewModel::back)
            ResultScreen(
                extraction = extraction,
                ruleMillis = state.ruleMillis,
                sourceLabel = state.sourceLabel,
                ai = state.ai,
                llm = state.llm,
                reading = state.reading,
                onRead = viewModel::readAloud,
                onStopReading = viewModel::stopReading,
                onScanPrescription = viewModel::openScan,
                reminderOutcome = state.reminderOutcome,
                activeReminders = state.reminders.size,
                onSetReminders = viewModel::setReminders,
                onOpenReminders = viewModel::openReminders,
                paperApplied = state.paperApplied,
                actions = MedicineActions(
                    confirm = viewModel::confirmMedicine,
                    reject = viewModel::rejectMedicine,
                    undo = viewModel::undoMedicine,
                    choose = viewModel::chooseMedicine,
                ),
                onBack = viewModel::back,
            )
        }
        else -> HomeScreen(
            engine = state.engine,
            llm = state.llm,
            unavailable = state.unavailable,
            onSelectEngine = { viewModel.selectEngine(it) },
            onRunDemo = viewModel::runDemo,
            onRecord = viewModel::startRecording,
            onScan = viewModel::openScan,
            activeReminders = state.reminders.size,
            onOpenReminders = viewModel::openReminders,
        )
    }
}
