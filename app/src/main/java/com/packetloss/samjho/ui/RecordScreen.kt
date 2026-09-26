package com.packetloss.samjho.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
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
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.packetloss.samjho.RecordingState

@Composable
fun RecordScreen(
    recording: RecordingState,
    onStop: () -> Unit,
    onCancel: () -> Unit,
) {
    val t = Strings(recording.language)
    Surface(color = MaterialTheme.colorScheme.background) {
        Column(
            Modifier
                .fillMaxSize()
                .systemBarsPadding()
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Text(
                t.listening,
                fontSize = 26.sp,
                fontWeight = FontWeight.Bold,
                color = if (recording.error == null) MaterialTheme.colorScheme.primary else WarnInk,
            )

            when {
                recording.error != null -> Panel(WarnTint) {
                    Text(recording.error, color = WarnInk, fontSize = 16.sp)
                }
                recording.loading -> Text(t.gettingSpeechReady, color = Muted, fontSize = 15.sp)
                else -> {
                    // Which recogniser is running is not the patient's business. Whether it may leave the phone is.
                    val notGuaranteed = recording.detail.startsWith("system")
                    Pill(t.micOn, OkTint, MaterialTheme.colorScheme.primary)
                    if (notGuaranteed) Text(t.notGuaranteedOffline, color = AvoidInk, fontSize = 14.sp)
                }
            }

            Column(
                Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState(), reverseScrolling = true),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                recording.lines.forEachIndexed { i, line ->
                    Panel {
                        Text("${i + 1}", fontSize = 12.sp, color = Muted)
                        Text(line.text, fontSize = 18.sp)
                    }
                }
                if (recording.partial.isNotBlank()) {
                    Text(recording.partial, fontSize = 18.sp, color = Muted)
                }
            }

            Button(
                onClick = onStop,
                enabled = recording.error == null,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(60.dp),
                shape = RoundedCornerShape(14.dp),
            ) {
                Text(t.stopAndExplain, fontSize = 19.sp, fontWeight = FontWeight.Bold)
            }
            OutlinedButton(
                onClick = onCancel,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp),
                shape = RoundedCornerShape(14.dp),
                colors = ButtonDefaults.outlinedButtonColors(contentColor = Muted),
            ) {
                Text(t.cancel, fontSize = 16.sp)
            }
            Spacer(Modifier.height(4.dp))
        }
    }
}

@Composable
private fun Panel(bg: Color = Color.White, content: @Composable androidx.compose.foundation.layout.ColumnScope.() -> Unit) {
    Surface(color = bg, shape = RoundedCornerShape(14.dp), modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(4.dp), content = content)
    }
}
