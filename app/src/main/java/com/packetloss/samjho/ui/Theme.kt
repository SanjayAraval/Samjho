package com.packetloss.samjho.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

val Ink = Color(0xFF14181B)
val Muted = Color(0xFF5A6570)
val Line = Color(0xFFDCE1E6)
val AvoidTint = Color(0xFFFFF4E2)
val AvoidInk = Color(0xFF8A5200)
val WarnTint = Color(0xFFFDECEA)
val WarnInk = Color(0xFFB3261E)
val OkTint = Color(0xFFE3F3F0)

private val LightScheme = lightColorScheme(
    primary = Color(0xFF00695C),
    onPrimary = Color.White,
    secondary = Color(0xFF00897B),
    background = Color(0xFFF4F6F8),
    onBackground = Ink,
    surface = Color.White,
    onSurface = Ink,
    error = WarnInk,
)

/** Light only, on purpose: one predictable look on a projector and on a stranger's phone. */
@Composable
fun SamjhoTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = LightScheme, content = content)
}
