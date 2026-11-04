package com.drishyopot.app.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext

private val Coral = Color(0xFFFF6B4A)
private val Navy = Color(0xFF0F1B2D)

private val LightColors = lightColorScheme(
    primary = Color(0xFFC2432A),
    onPrimary = Color.White,
    secondary = Color(0xFF4F5D75),
    background = Color(0xFFFFFBFA),
    surface = Color(0xFFFFFBFA),
)

private val DarkColors = darkColorScheme(
    primary = Coral,
    onPrimary = Navy,
    secondary = Color(0xFFB8C4DB),
    background = Color(0xFF0B1320),
    surface = Color(0xFF0B1320),
)

@Composable
fun DrishyopotTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = true,
    content: @Composable () -> Unit,
) {
    val colors = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val context = LocalContext.current
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }
        darkTheme -> DarkColors
        else -> LightColors
    }
    MaterialTheme(colorScheme = colors, content = content)
}
