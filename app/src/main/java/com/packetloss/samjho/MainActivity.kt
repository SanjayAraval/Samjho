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

    when {
        recording != null -> {
            BackHandler(onBack = viewModel::cancelRecording)
            RecordScreen(
                recording = recording,
                onStop = viewModel::stopRecording,
                onCancel = viewModel::cancelRecording,
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
        )
    }
}
