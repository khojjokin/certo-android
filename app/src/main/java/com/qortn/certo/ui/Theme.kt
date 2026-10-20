package com.qortn.certo.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.qortn.certo.data.Appearance

@Immutable
data class Palette(
    val bg: Color,
    val card: Color,
    val cardRaised: Color,
    val stroke: Color,
    val ink: Color,
    val inkSoft: Color,
    val inkFaint: Color,
    val dark: Boolean,
)

val Brand = Color(0xFF8B5CF6)
val BrandDeep = Color(0xFF6D28D9)
val Good = Color(0xFF22C55E)
val Bad = Color(0xFFEF4444)
val Warn = Color(0xFFF59E0B)

private val DarkPalette = Palette(
    bg = Color.Black, card = Color(0xFF111113), cardRaised = Color(0xFF1A1A1E), stroke = Color(0xFF232328),
    ink = Color.White, inkSoft = Color(0xFF9A9AA5), inkFaint = Color(0xFF55555E), dark = true,
)
private val LightPalette = Palette(
    bg = Color.White, card = Color(0xFFF4F4F7), cardRaised = Color.White, stroke = Color(0xFFE5E5EB),
    ink = Color(0xFF0A0A0B), inkSoft = Color(0xFF6B6B76), inkFaint = Color(0xFFA1A1AA), dark = false,
)

val LocalPalette = staticCompositionLocalOf { DarkPalette }
val P: Palette @Composable get() = LocalPalette.current

@Composable
fun CertoTheme(appearance: Appearance, content: @Composable () -> Unit) {
    val dark = when (appearance) {
        Appearance.System -> isSystemInDarkTheme()
        Appearance.Black -> true
        Appearance.White -> false
    }
    val palette = if (dark) DarkPalette else LightPalette
    val scheme = if (dark) darkColorScheme(primary = Brand, background = Color.Black, surface = Color(0xFF111113))
    else lightColorScheme(primary = Brand, background = Color.White, surface = Color.White)
    CompositionLocalProvider(LocalPalette provides palette) {
        MaterialTheme(colorScheme = scheme, content = content)
    }
}

// MARK: Building blocks

@Composable
fun Card(modifier: Modifier = Modifier, padding: Dp = 16.dp, onClick: (() -> Unit)? = null, content: @Composable () -> Unit) {
    val shape = RoundedCornerShape(20.dp)
    Box(
        modifier
            .clip(shape)
            .background(P.card)
            .border(BorderStroke(1.dp, P.stroke), shape)
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(padding)
    ) { content() }
}

@Composable
fun PrimaryButton(
    text: String,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    busy: Boolean = false,
    tint: Color? = null,
    onClick: () -> Unit,
) {
    val bg = if (!enabled) P.inkFaint.copy(alpha = 0.35f) else (tint ?: P.ink)
    val fg = if (tint != null) Color.White else P.bg
    Box(
        modifier
            .fillMaxWidth()
            .height(54.dp)
            .clip(CircleShape)
            .background(bg)
            .clickable(enabled = enabled && !busy, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        if (busy) CircularProgressIndicator(Modifier.size(22.dp), color = fg, strokeWidth = 2.dp)
        else Text(text, color = if (enabled) fg else P.inkSoft, fontWeight = FontWeight.Bold, fontSize = 17.sp)
    }
}

@Composable
fun SecondaryButton(text: String, modifier: Modifier = Modifier, icon: (@Composable () -> Unit)? = null, onClick: () -> Unit) {
    Row(
        modifier
            .fillMaxWidth()
            .height(54.dp)
            .clip(CircleShape)
            .background(P.card)
            .border(1.dp, P.stroke, CircleShape)
            .clickable(onClick = onClick),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (icon != null) { icon(); Spacer(Modifier.size(10.dp)) }
        Text(text, color = P.ink, fontWeight = FontWeight.SemiBold, fontSize = 16.sp)
    }
}

@Composable
fun IconTile(icon: ImageVector, tint: Color, size: Dp = 44.dp) {
    Box(
        Modifier.size(size).clip(RoundedCornerShape(size * 0.28f)).background(tint.copy(alpha = 0.16f)),
        contentAlignment = Alignment.Center,
    ) { Icon(icon, null, tint = tint, modifier = Modifier.size(size * 0.5f)) }
}

@Composable
fun SectionTitle(text: String, modifier: Modifier = Modifier) {
    Text(text, modifier = modifier.padding(top = 8.dp, bottom = 8.dp), color = P.ink, fontWeight = FontWeight.Bold, fontSize = 20.sp)
}

@Composable
fun ProgressBar(value: Float, color: Color, modifier: Modifier = Modifier, height: Dp = 8.dp) {
    Box(modifier.fillMaxWidth().height(height).clip(CircleShape).background(P.stroke)) {
        Box(Modifier.fillMaxWidth(value.coerceIn(0f, 1f)).height(height).clip(CircleShape).background(color))
    }
}

val BrandGradient: Brush get() = Brush.linearGradient(listOf(Color(0xFFA78BFA), Brand, BrandDeep))

@Composable
fun ErrorBanner(text: String) {
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(Bad.copy(alpha = 0.12f)).padding(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(text, color = Bad, fontSize = 14.sp)
    }
}

@Composable
fun ColumnScopeSpacer(h: Dp) = Spacer(Modifier.height(h))
