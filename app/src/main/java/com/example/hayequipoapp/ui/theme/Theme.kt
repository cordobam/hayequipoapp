package com.example.hayequipoapp.ui.theme

import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

// ─── Palette ──────────────────────────────────────────────
val GreenField   = Color(0xFF2D6A4F)   // pasto oscuro – acciones primarias
val GreenLight   = Color(0xFF52B788)   // pasto claro – estados activos
val SandNeutral  = Color(0xFFF4E9D0)   // arena – fondos cálidos
val SlateInk     = Color(0xFF1B1F2A)   // casi-negro – textos
val WhiteLine    = Color(0xFFF8F8F8)   // blanco línea de cancha
val RedCard      = Color(0xFFD62839)   // error / cancelado
val YellowCard   = Color(0xFFF4A261)   // advertencia / pendiente
val SkyBlue      = Color(0xFF90C5F0)   // disponible / info

// ─── Color Scheme ─────────────────────────────────────────
private val LightColorScheme = lightColorScheme(
    primary          = GreenField,
    onPrimary        = WhiteLine,
    primaryContainer = GreenLight,
    onPrimaryContainer = SlateInk,
    secondary        = YellowCard,
    onSecondary      = SlateInk,
    background       = WhiteLine,
    onBackground     = SlateInk,
    surface          = SandNeutral,
    onSurface        = SlateInk,
    error            = RedCard,
    onError          = WhiteLine
)

private val DarkColorScheme = darkColorScheme(
    primary          = GreenLight,
    onPrimary        = SlateInk,
    primaryContainer = GreenField,
    onPrimaryContainer = WhiteLine,
    secondary        = YellowCard,
    onSecondary      = SlateInk,
    background       = SlateInk,
    onBackground     = WhiteLine,
    surface          = Color(0xFF252A36),
    onSurface        = WhiteLine,
    error            = RedCard,
    onError          = WhiteLine
)

// ─── Typography ───────────────────────────────────────────
val HayEquipoTypography = Typography(
    displayLarge = TextStyle(
        fontWeight = FontWeight.Black,
        fontSize   = 36.sp,
        letterSpacing = (-0.5).sp
    ),
    headlineMedium = TextStyle(
        fontWeight = FontWeight.Bold,
        fontSize   = 22.sp
    ),
    titleLarge = TextStyle(
        fontWeight = FontWeight.SemiBold,
        fontSize   = 18.sp
    ),
    bodyLarge = TextStyle(
        fontWeight = FontWeight.Normal,
        fontSize   = 16.sp,
        lineHeight = 24.sp
    ),
    bodyMedium = TextStyle(
        fontWeight = FontWeight.Normal,
        fontSize   = 14.sp,
        lineHeight = 20.sp
    ),
    labelSmall = TextStyle(
        fontWeight = FontWeight.Medium,
        fontSize   = 11.sp,
        letterSpacing = 0.5.sp
    )
)

// ─── Theme ────────────────────────────────────────────────
@Composable
fun HayEquipoTheme(
    darkTheme: Boolean = false,
    content: @Composable () -> Unit
) {
    val colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme

    MaterialTheme(
        colorScheme = colorScheme,
        typography  = HayEquipoTypography,
        content     = content
    )
}
