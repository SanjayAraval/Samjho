package com.packetloss.samjho

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.lifecycle.viewmodel.compose.viewModel
import com.google.firebase.auth.FirebaseAuth
import com.packetloss.samjho.ui.HomeScreen
import com.packetloss.samjho.ui.LoginScreen
import com.packetloss.samjho.ui.MedicineActions
import com.packetloss.samjho.ui.RecordScreen
import com.packetloss.samjho.ui.ResultScreen
import com.packetloss.samjho.ui.SamjhoTheme

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContent {
            SamjhoTheme {

                val auth = remember {
                    FirebaseAuth.getInstance()
                }

                var user by remember {
                    mutableStateOf(auth.currentUser)
                }

                DisposableEffect(auth) {

                    val listener = FirebaseAuth.AuthStateListener {
                        user = it.currentUser
                    }

                    auth.addAuthStateListener(listener)

                    onDispose {
                        auth.removeAuthStateListener(listener)
                    }
                }

                if (user == null) {

                    LoginScreen(
                        onAuthSuccess = {
                            // Firebase AuthStateListener
                            // updates the user automatically.
                        }
                    )

                } else {

                    SamjhoApp(
                        onLogout = {
                            auth.signOut()
                        }
                    )
                }
            }
        }
    }
}

@Composable
fun SamjhoApp(
    viewModel: SamjhoViewModel = viewModel(),
    onLogout: () -> Unit
) {
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

        else -> {
            HomeScreen(
                engine = state.engine,
                llm = state.llm,
                unavailable = state.unavailable,
                onSelectEngine = { viewModel.selectEngine(it) },
                onRunDemo = viewModel::runDemo,
                onRecord = viewModel::startRecording,
                onLogout = onLogout,
            )
        }
    }
}