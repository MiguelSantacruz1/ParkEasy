package com.ejemplo.parkeasy.presentation.theme

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.shape.RoundedCornerShape

/** Verdadero cuando el tema oscuro está activo: solo ahí se dibujan los brillos de neón. */
val LocalNeon = staticCompositionLocalOf { false }

/** Colores de neón del tema oscuro. */
object Neon {
    val Cyan = Color(0xFF00E5FF)
    val Magenta = Color(0xFFFF2BD6)
    val Violet = Color(0xFF8B5CF6)
    val Green = Color(0xFF00FFA3)
    val Pink = Color(0xFFFF2E93)
    val Amber = Color(0xFFFFB300)
}

/** Colores de estado de un puesto. Se usan siempre junto con un símbolo y un texto, nunca solos. */
object StatusPalette {
    fun free(neon: Boolean) = if (neon) Neon.Green else Color(0xFF2E7D32)
    fun occupied(neon: Boolean) = if (neon) Neon.Pink else Color(0xFFC62828)
    fun blocked(neon: Boolean) = if (neon) Neon.Amber else Color(0xFF6D4C41)
}

/**
 * Resplandor alrededor de un elemento: varias capas translúcidas que se desvanecen hacia afuera.
 * Se dibuja detrás del contenido, así que el elemento debe tener su propio fondo.
 */
fun Modifier.neonGlow(color: Color, radius: Dp = 14.dp, corner: Dp = 16.dp, strength: Float = 1f): Modifier = drawBehind {
    val r = radius.toPx()
    val c = corner.toPx()
    val steps = 8
    for (i in steps downTo 1) {
        val grow = r * i / steps
        drawRoundRect(
            color = color.copy(alpha = (0.05f * strength).coerceIn(0f, 1f)),
            topLeft = Offset(-grow, -grow),
            size = Size(size.width + 2 * grow, size.height + 2 * grow),
            cornerRadius = CornerRadius(c + grow),
        )
    }
}

/** Tarjeta de contenido: en el tema oscuro lleva borde degradado y un resplandor suave. */
@Composable
fun NeonCard(
    modifier: Modifier = Modifier,
    corner: Dp = 16.dp,
    accent: Color = MaterialTheme.colorScheme.primary,
    content: @Composable ColumnScope.() -> Unit,
) {
    val neon = LocalNeon.current
    val shape = RoundedCornerShape(corner)
    val styled = if (neon) {
        modifier
            .neonGlow(accent, radius = 10.dp, corner = corner, strength = 0.7f)
            .border(1.dp, Brush.linearGradient(listOf(accent.copy(alpha = 0.75f), MaterialTheme.colorScheme.secondary.copy(alpha = 0.45f))), shape)
    } else {
        modifier
    }
    Card(
        modifier = styled,
        shape = shape,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
        content = content,
    )
}

/** Fondo de la ventana: [base] liso o, con [neon], con dos resplandores de color en las esquinas. */
fun Modifier.backdrop(base: Color, neon: Boolean): Modifier = background(base).then(
    if (neon) {
        Modifier.drawBehind {
            drawRect(Brush.radialGradient(listOf(Neon.Violet.copy(alpha = 0.24f), Color.Transparent), center = Offset(0f, 0f), radius = size.maxDimension * 0.65f))
            drawRect(Brush.radialGradient(listOf(Neon.Cyan.copy(alpha = 0.15f), Color.Transparent), center = Offset(size.width, size.height), radius = size.maxDimension * 0.6f))
        }
    } else {
        Modifier
    },
)

/** Fondo de la ventana: color liso, o en el tema oscuro con dos resplandores de color en las esquinas. */
@Composable
fun NeonBackground(modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    val neon = LocalNeon.current
    val base = MaterialTheme.colorScheme.background
    val glow = if (neon) {
        Modifier.drawBehind {
            drawRect(
                Brush.radialGradient(listOf(Neon.Violet.copy(alpha = 0.24f), Color.Transparent), center = Offset(0f, 0f), radius = size.maxDimension * 0.65f),
            )
            drawRect(
                Brush.radialGradient(listOf(Neon.Cyan.copy(alpha = 0.15f), Color.Transparent), center = Offset(size.width, size.height), radius = size.maxDimension * 0.6f),
            )
        }
    } else {
        Modifier
    }
    Box(modifier.background(base).then(glow)) { content() }
}
