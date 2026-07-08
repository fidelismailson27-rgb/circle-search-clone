package com.circulesearch.app.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext

private val LightColors =
    lightColorScheme(
        primary = CirclePrimary,
        onPrimary = CircleOnPrimary,
        primaryContainer = CirclePrimaryContainer,
        onPrimaryContainer = CircleOnPrimaryContainer,
        secondary = CircleSecondary,
        onSecondary = CircleOnSecondary,
        error = CircleError,
        onError = CircleOnError,
        background = CircleBackgroundLight,
        onBackground = CircleOnBackgroundLight,
        surface = CircleSurfaceLight,
    )

private val DarkColors =
    darkColorScheme(
        primary = CirclePrimary,
        onPrimary = CircleOnPrimary,
        primaryContainer = CirclePrimaryContainer,
        onPrimaryContainer = CircleOnPrimaryContainer,
        secondary = CircleSecondary,
        onSecondary = CircleOnSecondary,
        error = CircleError,
        onError = CircleOnError,
        background = CircleBackgroundDark,
        onBackground = CircleOnBackgroundDark,
        surface = CircleSurfaceDark,
    )

@Composable
fun CircleSearchTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = true,
    content: @Composable () -> Unit,
) {
    val colorScheme =
        when {
            dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
                val context = LocalContext.current
                if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
            }
            darkTheme -> DarkColors
            else -> LightColors
        }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = CircleSearchTypography,
        content = content,
    )
}
