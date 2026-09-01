package com.lumabeat.app.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.border
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** Semantic design tokens shared by phone and TV layouts. */
internal object LumaBeatColor {
    val Canvas = Color(0xFF080A0F)
    val CanvasRaised = Color(0xFF10141C)
    val CanvasGlow = Color(0xFF0A1224)
    val Surface = Color(0xFF151A23)
    val SurfaceRaised = Color(0xFF1E2531)
    val SurfaceInteractive = Color(0xFF252D3B)
    val Border = Color(0xFF303949)

    val Accent = Color(0xFF9B78FF)
    val AccentStrong = Color(0xFF7B4DFF)
    val AccentContainer = Color(0xFF302A61)
    val Cyan = Color(0xFF36D9FF)
    val Success = Color(0xFF46E6A4)
    val SuccessContainer = Color(0xFF15372D)
    val Warning = Color(0xFFF3C76B)

    val TextPrimary = Color(0xFFF7F8FB)
    val TextSecondary = Color(0xFFB2BAC8)
    val TextMuted = Color(0xFF7D8798)
    val Focus = Color(0xFFFFFFFF)
}

internal object LumaBeatSpace {
    val Xs = 4.dp
    val Sm = 8.dp
    val Md = 12.dp
    val Lg = 16.dp
    val Xl = 24.dp
    val Xxl = 32.dp
}

internal object LumaBeatRadius {
    val Small = 10.dp
    val Medium = 14.dp
    val Large = 20.dp
    val ExtraLarge = 26.dp
}

internal val LumaBeatPanelBorder = BorderStroke(1.dp, LumaBeatColor.Border.copy(alpha = 0.72f))

private val AppColors = darkColorScheme(
    primary = LumaBeatColor.Accent,
    onPrimary = Color.White,
    primaryContainer = LumaBeatColor.AccentContainer,
    onPrimaryContainer = LumaBeatColor.TextPrimary,
    secondary = LumaBeatColor.Cyan,
    background = LumaBeatColor.Canvas,
    onBackground = LumaBeatColor.TextPrimary,
    surface = LumaBeatColor.Surface,
    onSurface = LumaBeatColor.TextPrimary,
    surfaceVariant = LumaBeatColor.SurfaceRaised,
    onSurfaceVariant = LumaBeatColor.TextSecondary,
    outline = LumaBeatColor.Border,
)

private val AppTypography = Typography(
    headlineLarge = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Bold,
        fontSize = 28.sp,
        lineHeight = 32.sp,
    ),
    headlineSmall = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.SemiBold,
        fontSize = 20.sp,
        lineHeight = 25.sp,
    ),
    titleMedium = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.SemiBold,
        fontSize = 16.sp,
        lineHeight = 21.sp,
    ),
    bodyMedium = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Normal,
        fontSize = 13.sp,
        lineHeight = 18.sp,
    ),
    labelLarge = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.SemiBold,
        fontSize = 14.sp,
        lineHeight = 18.sp,
    ),
)

@Composable
internal fun LumaBeatTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = AppColors,
        typography = AppTypography,
        content = content,
    )
}

/**
 * TV focus is deliberately immediate. D-pad navigation is a frequent keyboard action, so an
 * animated scale would add latency and visual churn. The two-pixel focus ring is unambiguous on
 * every surface and does not depend on color alone.
 */
@Composable
internal fun Modifier.tvFocus(shape: Shape): Modifier {
    var focused by remember { mutableStateOf(false) }
    return this
        .onFocusChanged { focused = it.isFocused }
        .then(
            if (focused) Modifier.border(2.dp, LumaBeatColor.Focus, shape)
            else Modifier,
        )
}
