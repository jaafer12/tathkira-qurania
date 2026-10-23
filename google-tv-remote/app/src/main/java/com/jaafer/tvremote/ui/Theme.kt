package com.jaafer.tvremote.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

object RC {
    val bg = Color(0xFF0B0C10)
    val surface = Color(0xFF1B1D23)
    val surfaceHi = Color(0xFF272A32)
    val outline = Color(0xFF33363F)
    val text = Color(0xFFE8EAED)
    val muted = Color(0xFF9AA0A6)
    val accent = Color(0xFF8AB4F8)
    val accentDeep = Color(0xFF4F7FE0)
    val onAccent = Color(0xFF0B1A33)
    val danger = Color(0xFFF28B82)
    val ok = Color(0xFF81C995)
    val warn = Color(0xFFFDD663)
}

@Composable
fun RemoteTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = darkColorScheme(
            primary = RC.accent,
            onPrimary = RC.onAccent,
            background = RC.bg,
            onBackground = RC.text,
            surface = RC.surface,
            onSurface = RC.text,
            surfaceVariant = RC.surfaceHi,
            onSurfaceVariant = RC.muted,
            surfaceContainer = RC.surface,
            surfaceContainerHigh = RC.surfaceHi,
            surfaceContainerLow = RC.surface,
            outline = RC.outline,
            error = RC.danger,
        ),
        typography = Typography(),
        content = content,
    )
}
