package com.example.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable

private val LightColorScheme =
  lightColorScheme(
    primary = AmberPrimary,
    onPrimary = AmberOnPrimary,
    primaryContainer = AmberContainer,
    onPrimaryContainer = AmberOnContainer,
    secondary = SageSecondary,
    onSecondary = SageOnSecondary,
    secondaryContainer = SageContainer,
    onSecondaryContainer = SageOnContainer,
    tertiary = MutedBlueTertiary,
    onTertiary = MutedBlueOnTertiary,
    tertiaryContainer = MutedBlueContainer,
    onTertiaryContainer = MutedBlueOnContainer,
    background = CreamBg,
    onBackground = DarkSlateText,
    surface = CreamSurface,
    onSurface = DarkSlateText,
    surfaceVariant = CreamSurfaceVariant,
    onSurfaceVariant = DarkSlateText,
    outline = MediumGrayOutline,
    error = SoftError,
    onError = SoftOnError,
    errorContainer = SoftErrorContainer,
    onErrorContainer = SoftOnErrorContainer
  )

@Composable
fun MyApplicationTheme(
  content: @Composable () -> Unit,
) {
  MaterialTheme(colorScheme = LightColorScheme, typography = Typography, content = content)
}
