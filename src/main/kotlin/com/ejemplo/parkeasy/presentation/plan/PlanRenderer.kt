package com.ejemplo.parkeasy.presentation.plan

import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.unit.sp
import com.ejemplo.parkeasy.domain.model.SpotStatus
import com.ejemplo.parkeasy.domain.model.SpotType
import com.ejemplo.parkeasy.plan.model.Box
import com.ejemplo.parkeasy.plan.model.BoxShape
import com.ejemplo.parkeasy.plan.model.ItemType
import com.ejemplo.parkeasy.plan.model.PathGeometry
import com.ejemplo.parkeasy.plan.model.PathKind
import com.ejemplo.parkeasy.plan.model.PlanDocument
import com.ejemplo.parkeasy.plan.model.PlanItem
import com.ejemplo.parkeasy.plan.model.Pt
import com.ejemplo.parkeasy.plan.model.sampleCurve
import com.ejemplo.parkeasy.plan.ops.Handles
import com.ejemplo.parkeasy.plan.ops.ItemGeometry
import kotlin.math.max
import kotlin.math.min

private val Paper = Color(0xFFFDFDFB)
private val GridMinor = Color(0xFFE6E8EC)
private val GridMajor = Color(0xFFCDD1D8)
private val Accent = Color(0xFF1F5FBF)

private val Free = Color(0xFF2E7D32)
private val Occupied = Color(0xFFC62828)
private val Blocked = Color(0xFF6D4C41)

private fun spotTypeColor(type: SpotType?) = when (type) {
    SpotType.MOTORCYCLE -> Color(0xFF7B1FA2)
    SpotType.DISABLED -> Color(0xFF00838F)
    SpotType.OTHER -> Color(0xFF757575)
    else -> Color(0xFF1F5FBF)
}

/**
 * Dibuja el plano completo en pantalla. Con [operation] los puestos se pintan según su estado
 * en vivo (con símbolo, para no depender solo del color); si no, según su tipo.
 * El plano usa metros; aquí se pasan a píxeles con el [Viewport] del estado.
 */
fun DrawScope.drawPlan(textMeasurer: TextMeasurer, state: PlanUiState, operation: Boolean) {
    val doc = state.doc ?: return
    val vp = state.viewport

    fun px(p: Pt) = Offset(vp.toScreenX(p.x).toFloat(), vp.toScreenY(p.y).toFloat())
    fun len(m: Double) = (m * vp.scale).toFloat()

    drawCanvas(doc, vp, state.showGrid)

    doc.renderOrder().forEach { item -> drawItem(textMeasurer, item, state, operation, ::px, ::len) }

    if (!operation) {
        val selected = state.selectedItems
        selected.forEach { drawSelection(it, ::px) }
        selected.singleOrNull()?.takeIf { !it.locked }?.let { drawHandles(it, state, ::px) }
        if (state.showDimensions) selected.forEach { drawDimensions(textMeasurer, it, ::px) }
        state.preview?.let { drawPreview(it, ::px) }
    }
}

private fun DrawScope.drawCanvas(doc: PlanDocument, vp: Viewport, grid: Boolean) {
    val left = vp.toScreenX(0.0).toFloat()
    val top = vp.toScreenY(0.0).toFloat()
    val w = (doc.width * vp.scale).toFloat()
    val h = (doc.height * vp.scale).toFloat()
    drawRect(Paper, Offset(left, top), Size(w, h))
    if (grid) {
        val step = when {
            vp.scale * 0.5 >= 10 -> 0.5
            vp.scale >= 10 -> 1.0
            else -> 5.0
        }
        var m = 0.0
        while (m <= doc.width + 1e-9) {
            val major = (m / 5.0).let { kotlin.math.abs(it - Math.rint(it)) < 1e-9 }
            val x = vp.toScreenX(m).toFloat()
            drawLine(if (major) GridMajor else GridMinor, Offset(x, top), Offset(x, top + h), if (major) 1.2f else 0.6f)
            m += step
        }
        m = 0.0
        while (m <= doc.height + 1e-9) {
            val major = (m / 5.0).let { kotlin.math.abs(it - Math.rint(it)) < 1e-9 }
            val y = vp.toScreenY(m).toFloat()
            drawLine(if (major) GridMajor else GridMinor, Offset(left, y), Offset(left + w, y), if (major) 1.2f else 0.6f)
            m += step
        }
    }
    drawRect(Color(0xFF8A9099), Offset(left, top), Size(w, h), style = Stroke(1.5f))
}

private fun DrawScope.drawItem(
    tm: TextMeasurer,
    item: PlanItem,
    state: PlanUiState,
    operation: Boolean,
    px: (Pt) -> Offset,
    len: (Double) -> Float,
) {
    val center = px(ItemGeometry.center(item))
    withTransform({ rotate(item.rotation.toFloat(), center) }) {
        when (val g = item.geometry) {
            is Box -> drawBoxItem(tm, item, g, state, operation, px, len)
            is PathGeometry -> drawPathItem(tm, item, g, px, len)
        }
    }
}

private fun DrawScope.drawBoxItem(
    tm: TextMeasurer, item: PlanItem, g: Box, state: PlanUiState, operation: Boolean,
    px: (Pt) -> Offset, len: (Double) -> Float,
) {
    val topLeft = px(Pt(g.x, g.y))
    val size = Size(len(g.width), len(g.height))
    val radius = CornerRadius(len(g.cornerRadius))
    val ellipse = g.shape == BoxShape.ELLIPSE

    fun fill(color: Color) =
        if (ellipse) drawOval(color, topLeft, size) else drawRoundRect(color, topLeft, size, radius)
    fun outline(color: Color, width: Float, effect: PathEffect? = null) {
        val style = Stroke(width, pathEffect = effect)
        if (ellipse) drawOval(color, topLeft, size, style = style) else drawRoundRect(color, topLeft, size, radius, style)
    }

    when (item.type) {
        ItemType.COVERED_ZONE -> {
            fill(Accent.copy(alpha = 0.10f))
            outline(Accent.copy(alpha = 0.6f), 1.5f, PathEffect.dashPathEffect(floatArrayOf(10f, 6f)))
        }
        ItemType.ROAD -> { fill(Color(0xFFB8BCC4)) }
        ItemType.RAMP -> { fill(Color(0xFFF0B060)); outline(Color(0xFFB36B00), 1.5f) }
        ItemType.WALL -> fill(Color(0xFF4F5560))
        ItemType.COLUMN -> fill(Color(0xFF3A3F47))
        ItemType.STAIRS_ELEVATOR -> { fill(Color(0xFFD7C4A8)); outline(Color(0xFF8D6E43), 1.5f) }
        ItemType.ENTRANCE -> { fill(Free.copy(alpha = 0.85f)) }
        ItemType.EXIT -> { fill(Occupied.copy(alpha = 0.85f)) }
        ItemType.TEXT -> Unit
        ItemType.SPOT -> drawSpot(tm, item, g, state, operation, topLeft, size, ::fill, ::outline)
    }

    val caption = when (item.type) {
        ItemType.ENTRANCE, ItemType.EXIT, ItemType.TEXT, ItemType.STAIRS_ELEVATOR -> item.label.ifBlank {
            if (item.type == ItemType.STAIRS_ELEVATOR) "Esc./Asc." else ""
        }
        else -> ""
    }
    if (caption.isNotEmpty()) {
        val onDark = item.type == ItemType.ENTRANCE || item.type == ItemType.EXIT
        drawCentered(tm, caption, topLeft, size, if (onDark) Color.White else Color(0xFF222222), 13f)
    }
}

private fun DrawScope.drawSpot(
    tm: TextMeasurer, item: PlanItem, g: Box, state: PlanUiState, operation: Boolean,
    topLeft: Offset, size: Size,
    fill: (Color) -> Unit, outline: (Color, Float, PathEffect?) -> Unit,
) {
    val runtime = item.spotId?.let { state.runtime[it] }
    val highlighted = item.spotId != null && item.spotId in state.highlighted
    val base: Color
    val symbol: String
    if (operation) {
        val status = runtime?.status ?: SpotStatus.FREE
        base = when (status) { SpotStatus.FREE -> Free; SpotStatus.OCCUPIED -> Occupied; SpotStatus.BLOCKED -> Blocked }
        symbol = when (status) { SpotStatus.FREE -> "○"; SpotStatus.OCCUPIED -> "●"; SpotStatus.BLOCKED -> "⊘" }
        fill(base.copy(alpha = if (status == SpotStatus.FREE) 0.18f else 0.32f))
    } else {
        base = spotTypeColor(item.spotType)
        symbol = if (item.covered) "T" else ""
        fill(base.copy(alpha = 0.14f))
    }
    outline(base, 2f, null)
    if (highlighted) outline(Color(0xFFFFB300), 5f, null)

    val minSide = min(size.width, size.height)
    if (minSide < 18f) return
    val plate = if (operation) runtime?.plate else null
    val text = buildString {
        append(item.label)
        if (symbol.isNotEmpty()) append(" $symbol")
        if (plate != null && size.height > 44f) append("\n$plate")
    }
    drawCentered(tm, text, topLeft, size, Color(0xFF1B1B1F),(minSide * 0.28f).coerceIn(9f, 15f))
}

private fun DrawScope.drawCentered(tm: TextMeasurer, text: String, topLeft: Offset, size: Size, color: Color, fontPx: Float) {
    val layout = tm.measure(text, TextStyle(color = color, fontSize = fontPx.sp))
    val x = topLeft.x + (size.width - layout.size.width) / 2
    val y = topLeft.y + (size.height - layout.size.height) / 2
    drawText(layout, topLeft = Offset(x, y))
}

private fun DrawScope.drawPathItem(tm: TextMeasurer, item: PlanItem, g: PathGeometry, px: (Pt) -> Offset, len: (Double) -> Float) {
    val pts = (if (g.kind == PathKind.CURVE) sampleCurve(g.points) else g.points).map(px)
    if (pts.size < 2) return
    val path = Path().apply {
        moveTo(pts[0].x, pts[0].y)
        pts.drop(1).forEach { lineTo(it.x, it.y) }
        if (g.kind == PathKind.POLYGON) close()
    }
    val width = max(len(item.strokeWidth), 1.5f)
    val rounding = if (g.cornerRadius > 0) PathEffect.cornerPathEffect(len(g.cornerRadius)) else null

    when (item.type) {
        ItemType.COVERED_ZONE -> {
            if (g.kind == PathKind.POLYGON) drawPath(path, Accent.copy(alpha = 0.10f))
            drawPath(path, Accent.copy(alpha = 0.6f), style = Stroke(1.5f, pathEffect = rounding ?: PathEffect.dashPathEffect(floatArrayOf(10f, 6f))))
        }
        ItemType.ROAD -> {
            drawPath(path, Color(0xFFB8BCC4), style = Stroke(width, cap = StrokeCap.Butt, join = StrokeJoin.Round, pathEffect = rounding))
            if (width > 10f) drawPath(path, Color.White.copy(alpha = 0.8f), style = Stroke(1.5f, pathEffect = PathEffect.dashPathEffect(floatArrayOf(12f, 10f))))
        }
        ItemType.RAMP -> {
            drawPath(path, Color(0xFFF0B060), style = Stroke(width, cap = StrokeCap.Butt, join = StrokeJoin.Round, pathEffect = rounding))
            val mid = pts[pts.size / 2]
            val arrow = when (item.rampUp) { true -> "▲"; false -> "▼"; null -> "" }
            if (arrow.isNotEmpty()) drawCentered(tm, arrow, mid - Offset(10f, 10f), Size(20f, 20f), Color(0xFF7A4800), 14f)
        }
        ItemType.WALL -> drawPath(path, Color(0xFF4F5560), style = Stroke(width, cap = StrokeCap.Square, join = StrokeJoin.Miter, pathEffect = rounding))
        else -> drawPath(path, Color(0xFF555555), style = Stroke(width.coerceAtLeast(2f), cap = StrokeCap.Round, join = StrokeJoin.Round))
    }
}

private fun DrawScope.drawSelection(item: PlanItem, px: (Pt) -> Offset) {
    val pts = ItemGeometry.worldPoints(item).map(px)
    if (pts.size < 2) return
    val outline = if (item.geometry is Box) pts + pts.first() else {
        val bounds = ItemGeometry.worldBounds(item)
        listOf(Pt(bounds.left, bounds.top), Pt(bounds.right, bounds.top), Pt(bounds.right, bounds.bottom), Pt(bounds.left, bounds.bottom), Pt(bounds.left, bounds.top)).map(px)
    }
    val color = if (item.locked) Color(0xFF8A9099) else Accent
    outline.zipWithNext().forEach { (a, b) -> drawLine(color, a, b, 1.5f, pathEffect = PathEffect.dashPathEffect(floatArrayOf(6f, 4f))) }
}

private fun DrawScope.drawHandles(item: PlanItem, state: PlanUiState, px: (Pt) -> Offset) {
    val half = 4.5f
    Handles.resizePositions(item).values.forEach {
        val o = px(it)
        drawRect(Color.White, Offset(o.x - half, o.y - half), Size(half * 2, half * 2))
        drawRect(Accent, Offset(o.x - half, o.y - half), Size(half * 2, half * 2), style = Stroke(1.5f))
    }
    Handles.pathPoints(item).forEach {
        val o = px(it)
        drawCircle(Color.White, half + 1, o)
        drawCircle(Accent, half + 1, o, style = Stroke(1.5f))
    }
    val rotate = Handles.rotatePosition(item, 26.0 / state.viewport.scale)
    val top = Handles.rotatePosition(item, 0.0)
    drawLine(Accent, px(top), px(rotate), 1.2f)
    drawCircle(Color.White, 6f, px(rotate))
    drawCircle(Accent, 6f, px(rotate), style = Stroke(1.5f))
}

private fun DrawScope.drawDimensions(tm: TextMeasurer, item: PlanItem, px: (Pt) -> Offset) {
    val b = ItemGeometry.worldBounds(item)
    val text = tm.measure(ItemGeometry.dimensionText(item), TextStyle(color = Accent, fontSize = 11.sp))
    val at = px(Pt((b.left + b.right) / 2, b.bottom))
    drawText(text, topLeft = Offset(at.x - text.size.width / 2f, at.y + 6f))
}

private fun DrawScope.drawPreview(preview: Preview, px: (Pt) -> Offset) {
    val dash = PathEffect.dashPathEffect(floatArrayOf(8f, 5f))
    when (preview) {
        is Preview.BoxPreview -> {
            val tl = px(Pt(preview.rect.left, preview.rect.top))
            val size = Size(px(Pt(preview.rect.right, 0.0)).x - tl.x, px(Pt(0.0, preview.rect.bottom)).y - tl.y)
            if (preview.ellipse) drawOval(Accent.copy(alpha = 0.12f), tl, size) else drawRect(Accent.copy(alpha = 0.12f), tl, size)
            val style = Stroke(1.5f, pathEffect = dash)
            if (preview.ellipse) drawOval(Accent, tl, size, style = style) else drawRect(Accent, tl, size, style = style)
        }
        is Preview.PathPreview -> {
            val pts = preview.points.map(px)
            pts.zipWithNext().forEach { (a, b) -> drawLine(Accent, a, b, 2f, pathEffect = dash) }
            pts.forEach { drawCircle(Accent, 3.5f, it) }
        }
        is Preview.Marquee -> {
            val tl = px(Pt(preview.rect.left, preview.rect.top))
            val size = Size(px(Pt(preview.rect.right, 0.0)).x - tl.x, px(Pt(0.0, preview.rect.bottom)).y - tl.y)
            drawRect(Accent.copy(alpha = 0.10f), tl, size)
            drawRect(Accent, tl, size, style = Stroke(1f, pathEffect = dash))
        }
    }
}
