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

/** Colores del plano. [Light] es el papel de siempre (también para exportar); [Neon] es el plano nocturno de la pantalla. */
class PlanPalette(
    val paper: Color, val gridMinor: Color, val gridMajor: Color, val border: Color, val accent: Color,
    val free: Color, val occupied: Color, val blocked: Color,
    val road: Color, val roadDash: Color, val ramp: Color, val rampEdge: Color, val rampArrow: Color,
    val wall: Color, val column: Color, val stairs: Color, val stairsEdge: Color, val other: Color, val locked: Color,
    val text: Color, val caption: Color,
    val moto: Color, val disabled: Color, val otherSpot: Color, val car: Color,
    val glow: Boolean,
) {
    companion object {
        val Light = PlanPalette(
            paper = Color(0xFFFDFDFB), gridMinor = Color(0xFFE6E8EC), gridMajor = Color(0xFFCDD1D8), border = Color(0xFF8A9099),
            accent = Color(0xFF1F5FBF), free = Color(0xFF2E7D32), occupied = Color(0xFFC62828), blocked = Color(0xFF6D4C41),
            road = Color(0xFFB8BCC4), roadDash = Color.White.copy(alpha = 0.8f), ramp = Color(0xFFF0B060), rampEdge = Color(0xFFB36B00),
            rampArrow = Color(0xFF7A4800), wall = Color(0xFF4F5560), column = Color(0xFF3A3F47), stairs = Color(0xFFD7C4A8),
            stairsEdge = Color(0xFF8D6E43), other = Color(0xFF555555), locked = Color(0xFF8A9099),
            text = Color(0xFF1B1B1F), caption = Color(0xFF222222),
            moto = Color(0xFF7B1FA2), disabled = Color(0xFF00838F), otherSpot = Color(0xFF757575), car = Color(0xFF1F5FBF),
            glow = false,
        )
        val Neon = PlanPalette(
            paper = Color(0xFF080B18), gridMinor = Color(0x14FFFFFF), gridMajor = Color(0x2E00E5FF), border = Color(0x8C00E5FF),
            accent = Color(0xFF00E5FF), free = Color(0xFF00FFA3), occupied = Color(0xFFFF2E93), blocked = Color(0xFFFFB300),
            road = Color(0xFF1B2342), roadDash = Color(0xFF00E5FF).copy(alpha = 0.55f), ramp = Color(0xFF6D4AE0), rampEdge = Color(0xFFB89BFF),
            rampArrow = Color(0xFFF1EAFF), wall = Color(0xFF6C7BB8), column = Color(0xFF3F4B7C), stairs = Color(0xFF25305C),
            stairsEdge = Color(0xFF7C8CD6), other = Color(0xFF8A96C8), locked = Color(0xFF6C7BB8),
            text = Color(0xFFEAF6FF), caption = Color(0xFFEAF6FF),
            moto = Color(0xFFD36CFF), disabled = Color(0xFF3DDCFF), otherSpot = Color(0xFF9AA4C7), car = Color(0xFF4D9BFF),
            glow = true,
        )
    }
}

private val paletteLocal = ThreadLocal.withInitial { PlanPalette.Light }
private val pal: PlanPalette get() = paletteLocal.get()

private fun spotTypeColor(type: SpotType?) = when (type) {
    SpotType.MOTORCYCLE -> pal.moto
    SpotType.DISABLED -> pal.disabled
    SpotType.OTHER -> pal.otherSpot
    else -> pal.car
}
/**
 * Dibuja el plano completo en pantalla. Con [operation] los puestos se pintan según su estado
 * en vivo (con símbolo, para no depender solo del color); si no, según su tipo.
 * El plano usa metros; aquí se pasan a píxeles con el [Viewport] del estado.
 */
fun DrawScope.drawPlan(textMeasurer: TextMeasurer, state: PlanUiState, operation: Boolean, neon: Boolean = false) {
    paletteLocal.set(if (neon) PlanPalette.Neon else PlanPalette.Light)
    try {
        drawPlanWithPalette(textMeasurer, state, operation)
    } finally {
        paletteLocal.set(PlanPalette.Light)
    }
}

private fun DrawScope.drawPlanWithPalette(textMeasurer: TextMeasurer, state: PlanUiState, operation: Boolean) {
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
    drawRect(pal.paper, Offset(left, top), Size(w, h))
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
            drawLine(if (major) pal.gridMajor else pal.gridMinor, Offset(x, top), Offset(x, top + h), if (major) 1.2f else 0.6f)
            m += step
        }
        m = 0.0
        while (m <= doc.height + 1e-9) {
            val major = (m / 5.0).let { kotlin.math.abs(it - Math.rint(it)) < 1e-9 }
            val y = vp.toScreenY(m).toFloat()
            drawLine(if (major) pal.gridMajor else pal.gridMinor, Offset(left, y), Offset(left + w, y), if (major) 1.2f else 0.6f)
            m += step
        }
    }
    drawRect(pal.border, Offset(left, top), Size(w, h), style = Stroke(1.5f))
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
            fill(pal.accent.copy(alpha = 0.10f))
            outline(pal.accent.copy(alpha = 0.6f), 1.5f, PathEffect.dashPathEffect(floatArrayOf(10f, 6f)))
        }
        ItemType.ROAD -> { fill(pal.road) }
        ItemType.RAMP -> { fill(pal.ramp); outline(pal.rampEdge, 1.5f) }
        ItemType.WALL -> fill(pal.wall)
        ItemType.COLUMN -> fill(pal.column)
        ItemType.STAIRS_ELEVATOR -> { fill(pal.stairs); outline(pal.stairsEdge, 1.5f) }
        ItemType.ENTRANCE -> { fill(pal.free.copy(alpha = 0.85f)) }
        ItemType.EXIT -> { fill(pal.occupied.copy(alpha = 0.85f)) }
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
        drawCentered(tm, caption, topLeft, size, if (onDark) Color.White else pal.caption, 13f)
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
        base = when (status) { SpotStatus.FREE -> pal.free; SpotStatus.OCCUPIED -> pal.occupied; SpotStatus.BLOCKED -> pal.blocked }
        symbol = when (status) { SpotStatus.FREE -> "○"; SpotStatus.OCCUPIED -> "●"; SpotStatus.BLOCKED -> "⊘" }
        fill(base.copy(alpha = if (status == SpotStatus.FREE) 0.18f else 0.32f))
    } else {
        base = spotTypeColor(item.spotType)
        symbol = if (item.covered) "T" else ""
        fill(base.copy(alpha = 0.14f))
    }
    if (pal.glow) {
        val k = if (operation && runtime?.status == SpotStatus.OCCUPIED) 1.3f else 1f
        outline(base.copy(alpha = 0.07f * k), 16f, null)
        outline(base.copy(alpha = 0.12f * k), 10f, null)
        outline(base.copy(alpha = 0.22f * k), 5f, null)
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
    drawCentered(tm, text, topLeft, size, pal.text, (minSide * 0.28f).coerceIn(9f, 15f))
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
            if (g.kind == PathKind.POLYGON) drawPath(path, pal.accent.copy(alpha = 0.10f))
            drawPath(path, pal.accent.copy(alpha = 0.6f), style = Stroke(1.5f, pathEffect = rounding ?: PathEffect.dashPathEffect(floatArrayOf(10f, 6f))))
        }
        ItemType.ROAD -> {
            drawPath(path, pal.road, style = Stroke(width, cap = StrokeCap.Butt, join = StrokeJoin.Round, pathEffect = rounding))
            if (width > 10f) drawPath(path, pal.roadDash, style = Stroke(1.5f, pathEffect = PathEffect.dashPathEffect(floatArrayOf(12f, 10f))))
        }
        ItemType.RAMP -> {
            drawPath(path, pal.ramp, style = Stroke(width, cap = StrokeCap.Butt, join = StrokeJoin.Round, pathEffect = rounding))
            val mid = pts[pts.size / 2]
            val arrow = when (item.rampUp) { true -> "▲"; false -> "▼"; null -> "" }
            if (arrow.isNotEmpty()) drawCentered(tm, arrow, mid - Offset(10f, 10f), Size(20f, 20f), pal.rampArrow, 14f)
        }
        ItemType.WALL -> drawPath(path, pal.wall, style = Stroke(width, cap = StrokeCap.Square, join = StrokeJoin.Miter, pathEffect = rounding))
        else -> drawPath(path, pal.other, style = Stroke(width.coerceAtLeast(2f), cap = StrokeCap.Round, join = StrokeJoin.Round))
    }
}

private fun DrawScope.drawSelection(item: PlanItem, px: (Pt) -> Offset) {
    val pts = ItemGeometry.worldPoints(item).map(px)
    if (pts.size < 2) return
    val outline = if (item.geometry is Box) pts + pts.first() else {
        val bounds = ItemGeometry.worldBounds(item)
        listOf(Pt(bounds.left, bounds.top), Pt(bounds.right, bounds.top), Pt(bounds.right, bounds.bottom), Pt(bounds.left, bounds.bottom), Pt(bounds.left, bounds.top)).map(px)
    }
    val color = if (item.locked) pal.locked else pal.accent
    outline.zipWithNext().forEach { (a, b) -> drawLine(color, a, b, 1.5f, pathEffect = PathEffect.dashPathEffect(floatArrayOf(6f, 4f))) }
}

private fun DrawScope.drawHandles(item: PlanItem, state: PlanUiState, px: (Pt) -> Offset) {
    val half = 4.5f
    Handles.resizePositions(item).values.forEach {
        val o = px(it)
        drawRect(Color.White, Offset(o.x - half, o.y - half), Size(half * 2, half * 2))
        drawRect(pal.accent, Offset(o.x - half, o.y - half), Size(half * 2, half * 2), style = Stroke(1.5f))
    }
    Handles.pathPoints(item).forEach {
        val o = px(it)
        drawCircle(Color.White, half + 1, o)
        drawCircle(pal.accent, half + 1, o, style = Stroke(1.5f))
    }
    val rotate = Handles.rotatePosition(item, 26.0 / state.viewport.scale)
    val top = Handles.rotatePosition(item, 0.0)
    drawLine(pal.accent, px(top), px(rotate), 1.2f)
    drawCircle(Color.White, 6f, px(rotate))
    drawCircle(pal.accent, 6f, px(rotate), style = Stroke(1.5f))
}

private fun DrawScope.drawDimensions(tm: TextMeasurer, item: PlanItem, px: (Pt) -> Offset) {
    val b = ItemGeometry.worldBounds(item)
    val text = tm.measure(ItemGeometry.dimensionText(item), TextStyle(color = pal.accent, fontSize = 11.sp))
    val at = px(Pt((b.left + b.right) / 2, b.bottom))
    drawText(text, topLeft = Offset(at.x - text.size.width / 2f, at.y + 6f))
}

private fun DrawScope.drawPreview(preview: Preview, px: (Pt) -> Offset) {
    val dash = PathEffect.dashPathEffect(floatArrayOf(8f, 5f))
    when (preview) {
        is Preview.BoxPreview -> {
            val tl = px(Pt(preview.rect.left, preview.rect.top))
            val size = Size(px(Pt(preview.rect.right, 0.0)).x - tl.x, px(Pt(0.0, preview.rect.bottom)).y - tl.y)
            if (preview.ellipse) drawOval(pal.accent.copy(alpha = 0.12f), tl, size) else drawRect(pal.accent.copy(alpha = 0.12f), tl, size)
            val style = Stroke(1.5f, pathEffect = dash)
            if (preview.ellipse) drawOval(pal.accent, tl, size, style = style) else drawRect(pal.accent, tl, size, style = style)
        }
        is Preview.PathPreview -> {
            val pts = preview.points.map(px)
            pts.zipWithNext().forEach { (a, b) -> drawLine(pal.accent, a, b, 2f, pathEffect = dash) }
            pts.forEach { drawCircle(pal.accent, 3.5f, it) }
        }
        is Preview.Marquee -> {
            val tl = px(Pt(preview.rect.left, preview.rect.top))
            val size = Size(px(Pt(preview.rect.right, 0.0)).x - tl.x, px(Pt(0.0, preview.rect.bottom)).y - tl.y)
            drawRect(pal.accent.copy(alpha = 0.10f), tl, size)
            drawRect(pal.accent, tl, size, style = Stroke(1f, pathEffect = dash))
        }
    }
}
