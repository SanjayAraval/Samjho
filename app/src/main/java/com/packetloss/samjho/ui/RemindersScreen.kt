package com.packetloss.samjho.ui

import android.content.Context
import android.content.Intent
import android.provider.Settings
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.app.NotificationManagerCompat
import com.packetloss.samjho.reminders.Reminder

/** Opens the phone's own notification settings for Samjho, for a patient who refused the permission. */
fun openNotificationSettings(context: Context) {
    context.startActivity(
        Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
            .putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
    )
}

/** Every reminder that is still active, each with its own Cancel, and one to cancel them all. */
@Composable
fun RemindersScreen(
    reminders: List<Reminder>,
    onCancel: (String) -> Unit,
    onCancelAll: () -> Unit,
    onOpenSummary: (String) -> Unit,
    onBack: () -> Unit,
) {
    val lang = UiLanguage.resolve()
    val t = remember(lang) { Strings(lang) }
    val context = LocalContext.current

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
                TextButton(onClick = onBack, contentPadding = PaddingValues(0.dp)) { Text(t.back, fontSize = 16.sp) }
                Spacer(Modifier.weight(1f))
                LanguageToggle(lang)
            }
            Text(t.reminders, fontSize = 26.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)

            if (!NotificationManagerCompat.from(context).areNotificationsEnabled()) {
                Card(AvoidTint) {
                    Text(t.notificationsOff, fontSize = 14.sp, color = AvoidInk)
                    OutlinedButton(onClick = { openNotificationSettings(context) }, shape = RoundedCornerShape(12.dp)) {
                        Text(t.openSettings, fontSize = 15.sp)
                    }
                }
            }

            if (reminders.isEmpty()) {
                Text(t.noReminders, fontSize = 17.sp, color = Muted)
            }

            reminders.forEach { r ->
                Card(Color.White) {
                    Text(r.name, fontSize = 22.sp, fontWeight = FontWeight.Bold, color = Ink)
                    t.howToTake(r.doseCount, r.food)?.let { Text(it, fontSize = 17.sp, color = Ink) }
                    Text(
                        t.reminderTime(r.slot, r.hour) + " · " + (r.remaining?.let { t.daysLeft(it) } ?: t.untilCancelled),
                        fontSize = 15.sp,
                        color = Muted,
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                        OutlinedButton(onClick = { onCancel(r.id) }, shape = RoundedCornerShape(12.dp)) { Text(t.cancel, fontSize = 15.sp) }
                        TextButton(onClick = { onOpenSummary(r.consultationId) }) { Text(t.openSummary, fontSize = 15.sp) }
                    }
                }
            }

            if (reminders.isNotEmpty()) {
                OutlinedButton(
                    onClick = onCancelAll,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(14.dp),
                ) { Text(t.cancelAll, fontSize = 16.sp) }
            }

            Text(t.disclaimer, fontSize = 13.sp, color = Muted)
        }
    }
}

@Composable
private fun Card(bg: Color, content: @Composable androidx.compose.foundation.layout.ColumnScope.() -> Unit) {
    Surface(color = bg, shape = RoundedCornerShape(14.dp), border = BorderStroke(1.dp, Line), modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp), content = content)
    }
}
