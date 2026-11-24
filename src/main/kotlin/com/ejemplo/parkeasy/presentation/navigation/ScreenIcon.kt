package com.ejemplo.parkeasy.presentation.navigation

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.material3.LocalContentColor
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp

/**
 * Ícono de cada pantalla, dibujado con trazos simples en una cuadrícula de 24 × 24. Se dibujan a mano para no
 * sumar la librería de íconos de Material, que pesa decenas de MB. Toman el color del contenido que los rodea.
 */
@Composable
fun ScreenIcon(screen: Screen, modifier: Modifier = Modifier) {
    val color = LocalContentColor.current
    Canvas(modifier.size(24.dp)) {
        val u = size.width / 24f
        val stroke = Stroke(width = 2f * u, cap = StrokeCap.Round, join = StrokeJoin.Round)

        fun path(build: Path.(Float) -> Unit) = Path().apply { build(u) }
        fun DrawScope.line(x1: Float, y1: Float, x2: Float, y2: Float) =
            drawLine(color, Offset(x1 * u, y1 * u), Offset(x2 * u, y2 * u), strokeWidth = 2f * u, cap = StrokeCap.Round)
        fun DrawScope.box(x: Float, y: Float, w: Float, h: Float, r: Float) =
            drawRoundRect(color, Offset(x * u, y * u), Size(w * u, h * u), CornerRadius(r * u), style = stroke)

        when (screen) {
            // Casa
            Screen.HOME -> {
                drawPath(path { moveTo(3 * it, 11 * it); lineTo(12 * it, 3 * it); lineTo(21 * it, 11 * it) }, color, style = stroke)
                drawPath(path { moveTo(5 * it, 10 * it); lineTo(5 * it, 20 * it); lineTo(19 * it, 20 * it); lineTo(19 * it, 10 * it) }, color, style = stroke)
                drawPath(path { moveTo(10 * it, 20 * it); lineTo(10 * it, 14 * it); lineTo(14 * it, 14 * it); lineTo(14 * it, 20 * it) }, color, style = stroke)
            }
            // Plano con cuadrícula
            Screen.EDITOR -> {
                box(3f, 3f, 18f, 18f, 3f)
                line(3f, 9f, 21f, 9f); line(3f, 15f, 21f, 15f)
                line(9f, 3f, 9f, 21f); line(15f, 3f, 15f, 21f)
            }
            // Cuadro con una P: un puesto
            Screen.SPOTS -> {
                box(3f, 3f, 18f, 18f, 4f)
                drawPath(
                    path { moveTo(10 * it, 17 * it); lineTo(10 * it, 7 * it); lineTo(13 * it, 7 * it); cubicTo(17 * it, 7 * it, 17 * it, 13 * it, 13 * it, 13 * it); lineTo(10 * it, 13 * it) },
                    color, style = stroke,
                )
            }
            // Hoja con renglones
            Screen.RECORDS -> {
                box(5f, 3f, 14f, 18f, 2.5f)
                line(9f, 8f, 15f, 8f); line(9f, 12f, 15f, 12f); line(9f, 16f, 13f, 16f)
            }
            // Reloj
            Screen.HISTORY -> {
                drawCircle(color, radius = 9f * u, center = Offset(12 * u, 12 * u), style = stroke)
                drawPath(path { moveTo(12 * it, 7 * it); lineTo(12 * it, 12 * it); lineTo(16 * it, 14 * it) }, color, style = stroke)
            }
            // Capas apiladas: los pisos
            Screen.FLOORS -> {
                drawPath(path { moveTo(12 * it, 3 * it); lineTo(21 * it, 8 * it); lineTo(12 * it, 13 * it); lineTo(3 * it, 8 * it); close() }, color, style = stroke)
                drawPath(path { moveTo(3 * it, 12 * it); lineTo(12 * it, 17 * it); lineTo(21 * it, 12 * it) }, color, style = stroke)
                drawPath(path { moveTo(3 * it, 16 * it); lineTo(12 * it, 21 * it); lineTo(21 * it, 16 * it) }, color, style = stroke)
            }
            // Destello de IA
            Screen.ASSISTANT -> {
                drawPath(
                    path {
                        moveTo(11 * it, 4 * it); quadraticTo(11 * it, 12 * it, 19 * it, 12 * it)
                        quadraticTo(11 * it, 12 * it, 11 * it, 20 * it); quadraticTo(11 * it, 12 * it, 3 * it, 12 * it)
                        quadraticTo(11 * it, 12 * it, 11 * it, 4 * it); close()
                    },
                    color, style = stroke,
                )
                line(19f, 3f, 19f, 7f); line(17f, 5f, 21f, 5f)
            }
            // Controles deslizantes
            Screen.SETTINGS -> {
                line(4f, 7f, 20f, 7f); line(4f, 12f, 20f, 12f); line(4f, 17f, 20f, 17f)
                drawCircle(color, radius = 2.8f * u, center = Offset(9 * u, 7 * u))
                drawCircle(color, radius = 2.8f * u, center = Offset(15 * u, 12 * u))
                drawCircle(color, radius = 2.8f * u, center = Offset(8 * u, 17 * u))
            }
        }
    }
}
