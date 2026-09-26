package com.packetloss.samjho.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.packetloss.samjho.model.Language

/**
 * The language the screens are shown in. One at a time, never both.
 *
 * The choice is kept for the session (until the app is closed): once the person taps Hindi or English, every screen
 * follows it. Until they do, screens use the language of the latest consultation, and English before there is one.
 * Both languages exist in [Strings]; this only decides which one is displayed.
 */
object UiLanguage {
    /** Null until the person taps the toggle. */
    var chosen by mutableStateOf<Language?>(null)

    /** The language of the latest consultation shown; the default for the screens that have no summary of their own. */
    var consultation by mutableStateOf<Language?>(null)

    /** [current] is the language of the summary on screen, when there is one. */
    fun resolve(current: Language? = null): Language = chosen ?: current ?: consultation ?: Language.ENGLISH
}

/** Hindi | English, one tap, at the top of a screen. Switches the whole screen. */
@Composable
fun LanguageToggle(current: Language, modifier: Modifier = Modifier) {
    Surface(
        shape = RoundedCornerShape(50),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, Line),
        modifier = modifier,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Segment("हिंदी", selected = current == Language.HINDI) { UiLanguage.chosen = Language.HINDI }
            Segment("English", selected = current == Language.ENGLISH) { UiLanguage.chosen = Language.ENGLISH }
        }
    }
}

@Composable
private fun Segment(label: String, selected: Boolean, onClick: () -> Unit) {
    Surface(
        shape = RoundedCornerShape(50),
        color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surface,
        modifier = Modifier
            .padding(2.dp)
            .height(40.dp)
            .widthIn(min = 76.dp)
            .clickable(onClick = onClick),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(horizontal = 14.dp)) {
            Text(
                label,
                fontSize = 16.sp,
                fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                color = if (selected) MaterialTheme.colorScheme.onPrimary else Ink,
            )
        }
    }
}
