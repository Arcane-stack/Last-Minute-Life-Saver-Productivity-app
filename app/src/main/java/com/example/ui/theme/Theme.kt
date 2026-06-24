package com.example.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext

import androidx.compose.ui.graphics.Color

private val SleekColorScheme =
  lightColorScheme(
    primary = SleekPrimary,
    secondary = SleekSecondary,
    tertiary = SuccessGreen,
    background = SleekBackground,
    surface = SleekCardBg,
    onPrimary = Color.White,
    onSecondary = Color.White,
    onTertiary = Color.White,
    onBackground = SleekText,
    onSurface = SleekText,
    surfaceVariant = SleekLighterSlate,
    onSurfaceVariant = SleekText,
    error = SafetyRed,
    outline = SleekCardBorder
  )

@Composable
fun MyApplicationTheme(
  darkTheme: Boolean = false, // Use our light sleek theme by default
  dynamicColor: Boolean = false,
  content: @Composable () -> Unit,
) {
  val colorScheme = SleekColorScheme

  MaterialTheme(colorScheme = colorScheme, typography = Typography, content = content)
}
