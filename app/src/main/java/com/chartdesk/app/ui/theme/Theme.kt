package com.chartdesk.app.ui.theme

import android.app.Activity
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import androidx.core.view.WindowCompat

/**
 * Single source of truth for every colour in the app.
 * Chart-side values mirror this file in assets/chart/tokens.css.
 */
@Immutable
data class ChartDeskPalette(
    val background: Color,
    val surface: Color,
    val surfaceAlt: Color,
    val border: Color,
    val textPrimary: Color,
    val textSecondary: Color,
    val accent: Color,
    val accentSoft: Color,
    val up: Color,
    val down: Color,
    val warning: Color,
    val isDark: Boolean,
    val colorBlind: Boolean,
)

private val DarkPalette = ChartDeskPalette(
    background = Color(0xFF0B0F14),
    surface = Color(0xFF121821),
    surfaceAlt = Color(0xFF1A2230),
    border = Color(0xFF232D3B),
    textPrimary = Color(0xFFE6EDF5),
    textSecondary = Color(0xFF8A99AD),
    accent = Color(0xFF14C7A5),
    accentSoft = Color(0x3314C7A5),
    up = Color(0xFF2ED47A),
    down = Color(0xFFFF5C7A),
    warning = Color(0xFFF5B942),
    isDark = true,
    colorBlind = false,
)

private val DarkPaletteColorBlind = DarkPalette.copy(
    up = Color(0xFF4C9BFF),
    down = Color(0xFFFF9F1C),
    colorBlind = true,
)

private val LightPalette = ChartDeskPalette(
    background = Color(0xFFF7F9FC),
    surface = Color(0xFFFFFFFF),
    surfaceAlt = Color(0xFFEDF1F7),
    border = Color(0xFFD7DEE8),
    textPrimary = Color(0xFF101720),
    textSecondary = Color(0xFF5B6879),
    accent = Color(0xFF0E9F86),
    accentSoft = Color(0x1F0E9F86),
    up = Color(0xFF14935A),
    down = Color(0xFFD33A5A),
    warning = Color(0xFFB77A10),
    isDark = false,
    colorBlind = false,
)

private val LightPaletteColorBlind = LightPalette.copy(
    up = Color(0xFF2C6FE0),
    down = Color(0xFFC96A00),
    colorBlind = true,
)

val LocalChartDeskPalette = staticCompositionLocalOf { DarkPalette }

object ChartDeskType {
    val PriceFont: FontFamily = FontFamily.Monospace

    val price = TextStyle(
        fontFamily = PriceFont,
        fontWeight = FontWeight.Medium,
        fontSize = 13.sp,
        letterSpacing = 0.sp,
    )

    val priceLarge = price.copy(fontSize = 20.sp, fontWeight = FontWeight.SemiBold)

    val label = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Medium,
        fontSize = 11.sp,
        letterSpacing = 0.4.sp,
    )
}

private val AppTypography = Typography().let { base ->
    base.copy(
        titleLarge = base.titleLarge.copy(fontWeight = FontWeight.SemiBold),
        labelSmall = ChartDeskType.label,
    )
}

@Composable
fun ChartDeskTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    colorBlind: Boolean = false,
    content: @Composable () -> Unit,
) {
    val palette = when {
        darkTheme && colorBlind -> DarkPaletteColorBlind
        darkTheme -> DarkPalette
        colorBlind -> LightPaletteColorBlind
        else -> LightPalette
    }

    val scheme = if (darkTheme) {
        darkColorScheme(
            primary = palette.accent,
            onPrimary = Color(0xFF04140F),
            background = palette.background,
            onBackground = palette.textPrimary,
            surface = palette.surface,
            onSurface = palette.textPrimary,
            surfaceVariant = palette.surfaceAlt,
            onSurfaceVariant = palette.textSecondary,
            outline = palette.border,
            error = palette.down,
            tertiary = palette.warning,
        )
    } else {
        lightColorScheme(
            primary = palette.accent,
            onPrimary = Color.White,
            background = palette.background,
            onBackground = palette.textPrimary,
            surface = palette.surface,
            onSurface = palette.textPrimary,
            surfaceVariant = palette.surfaceAlt,
            onSurfaceVariant = palette.textSecondary,
            outline = palette.border,
            error = palette.down,
            tertiary = palette.warning,
        )
    }

    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as? Activity)?.window ?: return@SideEffect
            WindowCompat.getInsetsController(window, view).apply {
                isAppearanceLightStatusBars = !darkTheme
                isAppearanceLightNavigationBars = !darkTheme
            }
        }
    }

    CompositionLocalProvider(LocalChartDeskPalette provides palette) {
        MaterialTheme(colorScheme = scheme, typography = AppTypography, content = content)
    }
}
