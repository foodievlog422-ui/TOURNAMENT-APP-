package com.example.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val ExaloteColorScheme = darkColorScheme(
  primary = ExaloteOrange,
  onPrimary = Color.White,
  primaryContainer = Color(0xFF3B1A12),
  onPrimaryContainer = ExaloteOrangeLight,
  secondary = ExaloteGold,
  onSecondary = Color(0xFF1A1200),
  secondaryContainer = Color(0xFF332607),
  onSecondaryContainer = ExaloteGold,
  tertiary = ExaloteEmerald,
  onTertiary = Color(0xFF002116),
  tertiaryContainer = Color(0xFF0A3628),
  onTertiaryContainer = ExaloteEmerald,
  error = ExaloteCrimson,
  onError = Color.White,
  background = ObsidianBg,
  onBackground = TextPrimary,
  surface = ObsidianSurface,
  onSurface = TextPrimary,
  surfaceVariant = ObsidianCard,
  onSurfaceVariant = TextSecondary,
  outline = ObsidianBorder
)

@Composable
fun MyApplicationTheme(
  content: @Composable () -> Unit
) {
  MaterialTheme(
    colorScheme = ExaloteColorScheme,
    typography = Typography,
    content = content
  )
}
