package com.packetloss.samjho

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.Composable
import androidx.lifecycle.viewmodel.compose.viewModel
import com.packetloss.samjho.ui.HomeScreen
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
    if (extraction == null) {
        HomeScreen(onRunDemo = viewModel::runDemo)
    } else {
        ResultScreen(
            extraction = extraction,
            ruleMillis = state.ruleMillis,
            sourceLabel = state.sourceLabel,
            onBack = viewModel::back,
        )
    }
}
