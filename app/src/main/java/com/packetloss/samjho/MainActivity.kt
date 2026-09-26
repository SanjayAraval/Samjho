package com.packetloss.samjho

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.compose.runtime.Composable
import androidx.lifecycle.viewmodel.compose.viewModel
import com.packetloss.samjho.ui.HomeScreen
import com.packetloss.samjho.ui.MedicineActions
import com.packetloss.samjho.ui.RecordScreen
import com.packetloss.samjho.ui.ResultScreen
import com.packetloss.samjho.ui.ScanActions
import com.packetloss.samjho.ui.ScanScreen
import com.packetloss.samjho.scan.ScanStage
import com.packetloss.samjho.ui.SamjhoTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { SamjhoTheme { SamjhoApp() } }
    }
}

@Composable
fun SamjhoApp(viewModel: SamjhoViewModel = viewModel()) {
    val state = viewModel.state
    val extraction = state.extraction
    val recording = state.recording
    val scan = state.scan

    when {
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
        )
    }
}
