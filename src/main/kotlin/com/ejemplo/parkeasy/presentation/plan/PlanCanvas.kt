package com.ejemplo.parkeasy.presentation.plan

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.isPrimaryPressed
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.text.rememberTextMeasurer
import com.ejemplo.parkeasy.plan.model.PlanItem
import com.ejemplo.parkeasy.plan.model.Pt
import com.ejemplo.parkeasy.plan.ops.PlanOps
import kotlin.math.hypot
import androidx.compose.ui.input.pointer.isShiftPressed as pointerShift

/**
 * Lienzo del plano. Traduce el puntero a metros del plano y se los pasa al [PlanViewModel]
 * (edición) o avisa con [onSpotClick] (operación). La rueda acerca y aleja; el botón del
 * medio o el derecho mueven la vista.
 */
@Composable
fun PlanCanvas(
    vm: PlanViewModel,
    state: PlanUiState,
    operation: Boolean,
    modifier: Modifier = Modifier,
    focus: FocusRequester = FocusRequester(),
    onSpotClick: (PlanItem) -> Unit = {},
) {
    val textMeasurer = rememberTextMeasurer()
    val current = rememberUpdatedState(state)

    Canvas(
        modifier = modifier
            .fillMaxSize()
            .clipToBounds()
            .background(Color(0xFFE9EBEF))
            .onSizeChanged { vm.setViewSize(it.width.toDouble(), it.height.toDouble()) }
            .focusRequester(focus)
            .focusable()
            .pointerInput(operation) {
                awaitPointerEventScope {
                    var lastPressTime = 0L
                    var lastPressPos = androidx.compose.ui.geometry.Offset.Zero
                    var pressPos = androidx.compose.ui.geometry.Offset.Zero
                    var pressed = false
                    var panning = false
                    while (true) {
                        val event = awaitPointerEvent()
                        val change = event.changes.first()
                        val pos = change.position
                        val plan = current.value.viewport.toPlan(pos.x.toDouble(), pos.y.toDouble())
                        when (event.type) {
                            PointerEventType.Scroll -> {
                                val dy = change.scrollDelta.y
                                if (dy != 0f) vm.zoom(if (dy < 0) 1.12 else 1 / 1.12, pos.x.toDouble(), pos.y.toDouble())
                                change.consume()
                            }
                            PointerEventType.Press -> {
                                focus.requestFocus()
                                if (event.buttons.isPrimaryPressed) {
                                    val now = System.currentTimeMillis()
                                    val double = now - lastPressTime < 350 &&
                                        hypot((pos.x - lastPressPos.x).toDouble(), (pos.y - lastPressPos.y).toDouble()) < 6
                                    lastPressTime = if (double) 0L else now
                                    lastPressPos = pos
                                    pressPos = pos
                                    pressed = true
                                    if (!operation) vm.pointerDown(plan, event.keyboardModifiers.pointerShift, if (double) 2 else 1)
                                } else {
                                    panning = true
                                }
                                change.consume()
                            }
                            PointerEventType.Move -> {
                                when {
                                    panning -> vm.pan((pos.x - change.previousPosition.x).toDouble(), (pos.y - change.previousPosition.y).toDouble())
                                    pressed -> if (!operation) vm.pointerDrag(plan)
                                    !operation -> vm.pointerMove(plan)
                                }
                            }
                            PointerEventType.Release -> {
                                if (panning) {
                                    panning = false
                                } else if (pressed) {
                                    pressed = false
                                    if (operation) {
                                        val still = hypot((pos.x - pressPos.x).toDouble(), (pos.y - pressPos.y).toDouble()) < 6
                                        val doc = current.value.doc
                                        if (still && doc != null) {
                                            PlanOps.hitTest(doc, Pt(plan.x, plan.y), 0.0)?.takeIf { it.isSpot }?.let(onSpotClick)
                                        }
                                    } else {
                                        vm.pointerUp(plan)
                                    }
                                }
                                change.consume()
                            }
                            else -> Unit
                        }
                    }
                }
            },
    ) {
        drawPlan(textMeasurer, state, operation)
    }
}
