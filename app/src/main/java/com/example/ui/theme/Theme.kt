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

private val DarkColorScheme =
  darkColorScheme(
    primary = PolishPrimaryDark,
    onPrimary = PolishOnPrimaryDark,
    primaryContainer = PolishPrimaryContainerDark,
    onPrimaryContainer = PolishOnPrimaryContainerDark,
    secondary = PolishSecondaryDark,
    onSecondary = PolishOnSecondaryDark,
    secondaryContainer = PolishSecondaryContainerDark,
    onSecondaryContainer = PolishOnSecondaryContainerDark,
    tertiary = PolishTertiaryDark,
    onTertiary = PolishOnTertiaryDark,
    tertiaryContainer = PolishTertiaryContainerDark,
    onTertiaryContainer = PolishOnTertiaryContainerDark,
    background = PolishBackgroundDark,
    onBackground = PolishOnBackgroundDark,
    surface = PolishSurfaceDark,
    onSurface = PolishOnSurfaceDark,
    surfaceVariant = PolishSurfaceVariantDark,
    onSurfaceVariant = PolishOnSurfaceVariantDark,
    outline = PolishOutlineDark,
    outlineVariant = PolishOutlineVariantDark
  )

private val LightColorScheme =
  lightColorScheme(
    primary = PolishPrimaryLight,
    onPrimary = PolishOnPrimaryLight,
    primaryContainer = PolishPrimaryContainerLight,
    onPrimaryContainer = PolishOnPrimaryContainerLight,
    secondary = PolishSecondaryLight,
    onSecondary = PolishOnSecondaryLight,
    secondaryContainer = PolishSecondaryContainerLight,
    onSecondaryContainer = PolishOnSecondaryContainerLight,
    tertiary = PolishTertiaryLight,
    onTertiary = PolishOnTertiaryLight,
    tertiaryContainer = PolishTertiaryContainerLight,
    onTertiaryContainer = PolishOnTertiaryContainerLight,
    background = PolishBackgroundLight,
    onBackground = PolishOnBackgroundLight,
    surface = PolishSurfaceLight,
    onSurface = PolishOnSurfaceLight,
    surfaceVariant = PolishSurfaceVariantLight,
    onSurfaceVariant = PolishOnSurfaceVariantLight,
    outline = PolishOutlineLight,
    outlineVariant = PolishOutlineVariantLight
  )

@Composable
fun MyApplicationTheme(
  darkTheme: Boolean = isSystemInDarkTheme(),
  // Disable dynamic color to enforce our professional polish look
  dynamicColor: Boolean = false,
  content: @Composable () -> Unit,
) {
  val colorScheme =
    when {
      dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
        val context = LocalContext.current
        if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
      }

      darkTheme -> DarkColorScheme
      else -> LightColorScheme
    }

  MaterialTheme(colorScheme = colorScheme, typography = Typography, content = content)
}
