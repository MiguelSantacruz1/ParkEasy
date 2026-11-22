package com.ejemplo.parkeasy.presentation.plan

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.isCtrlPressed
import androidx.compose.ui.input.key.isShiftPressed
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.ejemplo.parkeasy.domain.model.SpotType
import com.ejemplo.parkeasy.plan.model.Box as PlanBox
import com.ejemplo.parkeasy.plan.model.ItemType
import com.ejemplo.parkeasy.plan.model.PlanItem
import com.ejemplo.parkeasy.presentation.common.CenteredNote
import com.ejemplo.parkeasy.presentation.common.Dropdown
import com.ejemplo.parkeasy.presentation.common.Formatters
import org.koin.compose.koinInject

/** Editor del plano de un piso: herramientas, paleta, lienzo y panel de propiedades. */
@Composable
fun EditorScreen(vm: PlanViewModel = koinInject()) {
    val state by vm.state.collectAsState()
    val focus = remember { FocusRequester() }
    var rowDialog by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) { vm.setMode(PlanMode.EDIT) }

    Column(
        Modifier.fillMaxSize().onKeyEvent { handleKey(vm, it, onFinish = vm::finishDraft) },
    ) {
        Toolbar(vm, state, onRow = { rowDialog = true })
        HorizontalDivider()
        if (state.loading) {
            CenteredNote("Cargando el plano…")
        } else if (state.floorId == null) {
            CenteredNote("Primero crea un piso en la pantalla Pisos.")
        } else {
            Row(Modifier.fillMaxSize()) {
                Palette(vm, state)
                Box(Modifier.weight(1f).fillMaxHeight()) {
                    PlanCanvas(vm, state, operation = false, focus = focus)
                }
                Properties(vm, state, onRow = { rowDialog = true })
            }
        }
    }

    if (rowDialog) {
        RowDialog(
            defaultLabel = state.selectedItems.singleOrNull()?.label.orEmpty(),
            onDismiss = { rowDialog = false },
            onConfirm = { count, spacing, angle, label ->
                vm.createRow(count, spacing, angle, label)
                rowDialog = false
            },
        )
    }
}

private fun handleKey(vm: PlanViewModel, e: androidx.compose.ui.input.key.KeyEvent, onFinish: () -> Unit): Boolean {
    if (e.type != KeyEventType.KeyDown) return false
    val ctrl = e.isCtrlPressed
    val step = if (e.isShiftPressed) 0.1 else 0.5
    when {
        ctrl && e.key == Key.Z && e.isShiftPressed -> vm.redo()
        ctrl && e.key == Key.Z -> vm.undo()
        ctrl && e.key == Key.Y -> vm.redo()
        ctrl && e.key == Key.C -> vm.copy()
        ctrl && e.key == Key.V -> vm.paste()
        ctrl && e.key == Key.D -> vm.duplicate()
        ctrl && e.key == Key.A -> vm.selectAll()
        e.key == Key.Delete || e.key == Key.Backspace -> vm.deleteSelected()
        e.key == Key.Escape -> { vm.cancel(); vm.clearSelection() }
        e.key == Key.Enter -> onFinish()
        e.key == Key.R -> vm.rotateSelected(90.0)
        e.key == Key.DirectionLeft -> vm.nudge(-step, 0.0)
        e.key == Key.DirectionRight -> vm.nudge(step, 0.0)
        e.key == Key.DirectionUp -> vm.nudge(0.0, -step)
        e.key == Key.DirectionDown -> vm.nudge(0.0, step)
        else -> return false
    }
    return true
}

@Composable
private fun Toolbar(vm: PlanViewModel, state: PlanUiState, onRow: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
    ) {
        Dropdown(
            label = "Piso", options = state.floors, selected = state.floors.firstOrNull { it.id == state.floorId },
            optionText = { it.name }, onSelected = { f -> f?.let { vm.selectFloor(it.id) } }, modifier = Modifier.width(170.dp),
        )
        Tool.entries.forEach { tool ->
            FilterChip(
                selected = state.tool == tool,
                onClick = { vm.setTool(tool) },
                label = { Text(toolName(tool)) },
            )
        }
        HorizontalSpacer()
        OutlinedButton(onClick = vm::undo, enabled = state.canUndo) { Text("Deshacer") }
        OutlinedButton(onClick = vm::redo, enabled = state.canRedo) { Text("Rehacer") }
        FilterChip(state.snap, vm::toggleSnap, { Text("Imán") })
        FilterChip(state.showGrid, vm::toggleGrid, { Text("Cuadrícula") })
        FilterChip(state.showDimensions, vm::toggleDimensions, { Text("Cotas") })
        OutlinedButton(onClick = vm::fitToView) { Text("Ajustar") }
        OutlinedButton(onClick = onRow) { Text("Hilera…") }
        Text(
            when (state.saveStatus) {
                SaveStatus.SAVED -> "✓ Guardado"
                SaveStatus.PENDING -> "… Cambios sin guardar"
                SaveStatus.SAVING -> "Guardando…"
                SaveStatus.ERROR -> "⚠ Error al guardar"
            },
            color = if (state.saveStatus == SaveStatus.ERROR) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface,
            style = MaterialTheme.typography.bodySmall,
        )
    }
}

@Composable
private fun HorizontalSpacer() = Box(Modifier.width(8.dp))

private fun toolName(t: Tool) = when (t) {
    Tool.SELECT -> "Seleccionar"
    Tool.RECTANGLE -> "Rectángulo"
    Tool.ELLIPSE -> "Elipse"
    Tool.LINE -> "Línea"
    Tool.POLYLINE -> "Polilínea"
    Tool.POLYGON -> "Polígono"
    Tool.CURVE -> "Curva"
}

private fun typeName(t: ItemType) = when (t) {
    ItemType.SPOT -> "Puesto"
    ItemType.COVERED_ZONE -> "Zona techada"
    ItemType.ROAD -> "Vía"
    ItemType.RAMP -> "Rampa"
    ItemType.WALL -> "Pared"
    ItemType.COLUMN -> "Columna"
    ItemType.STAIRS_ELEVATOR -> "Escalera / ascensor"
    ItemType.ENTRANCE -> "Entrada"
    ItemType.EXIT -> "Salida"
    ItemType.TEXT -> "Texto"
}

@Composable
private fun Palette(vm: PlanViewModel, state: PlanUiState) {
    Column(
        Modifier.width(190.dp).fillMaxHeight().background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f))
            .verticalScroll(rememberScrollState()).padding(10.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Text("Elementos", fontWeight = FontWeight.Bold)
        ItemType.entries.forEach { type ->
            FilterChip(
                selected = state.drawType == type && state.tool != Tool.SELECT,
                onClick = { vm.setDrawType(type) },
                label = { Text(typeName(type)) },
                modifier = Modifier.fillMaxWidth(),
            )
        }
        if (state.drawType == ItemType.SPOT) {
            HorizontalDivider(Modifier.padding(vertical = 4.dp))
            Dropdown(
                label = "Tipo", options = SpotType.entries, selected = state.drawSpotType,
                optionText = Formatters::spotType, onSelected = { t -> t?.let(vm::setDrawSpotType) },
            )
        }
        HorizontalDivider(Modifier.padding(vertical = 4.dp))
        Text(
            "Clic: coloca con el tamaño por defecto. Arrastra: dibuja. Polilínea, polígono y curva: un clic por punto y doble clic para terminar. Esc cancela.",
            style = MaterialTheme.typography.bodySmall,
        )
    }
}

@Composable
private fun Properties(vm: PlanViewModel, state: PlanUiState, onRow: () -> Unit) {
    val selected = state.selectedItems
    Column(
        Modifier.width(270.dp).fillMaxHeight().background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f))
            .verticalScroll(rememberScrollState()).padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text("Propiedades", fontWeight = FontWeight.Bold)
        when {
            selected.isEmpty() -> Text("Selecciona un elemento para editarlo.", style = MaterialTheme.typography.bodySmall)
            selected.size > 1 -> MultiProperties(vm, selected.size)
            else -> SingleProperties(vm, state, selected.single(), onRow)
        }
    }
}

@Composable
private fun MultiProperties(vm: PlanViewModel, count: Int) {
    Text("$count elementos seleccionados")
    ActionButtons(vm)
}

@Composable
private fun ActionButtons(vm: PlanViewModel) {
    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        OutlinedButton(onClick = vm::duplicate) { Text("Duplicar") }
        OutlinedButton(onClick = { vm.rotateSelected(90.0) }) { Text("Girar 90°") }
    }
    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        OutlinedButton(onClick = vm::bringToFront) { Text("Al frente") }
        OutlinedButton(onClick = vm::sendToBack) { Text("Al fondo") }
    }
    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        OutlinedButton(onClick = { vm.setLocked(true) }) { Text("Bloquear") }
        OutlinedButton(onClick = { vm.setLocked(false) }) { Text("Desbloquear") }
    }
    Button(onClick = vm::deleteSelected) { Text("Eliminar") }
}

@Composable
private fun SingleProperties(vm: PlanViewModel, state: PlanUiState, item: PlanItem, onRow: () -> Unit) {
    Text(typeName(item.type) + if (item.locked) " (bloqueado)" else "", fontWeight = FontWeight.SemiBold)

    if (item.isSpot) {
        var label by remember(item.id, item.label) { mutableStateOf(item.label) }
        var type by remember(item.id, item.spotType) { mutableStateOf(item.spotType ?: SpotType.CAR) }
        OutlinedTextField(label, { label = it }, label = { Text("Etiqueta") }, singleLine = true, modifier = Modifier.fillMaxWidth())
        Dropdown("Tipo", SpotType.entries, type, Formatters::spotType, { t -> t?.let { type = it } })
        Button(onClick = { vm.setSpotProperties(label, type) }, enabled = label != item.label || type != item.spotType) { Text("Aplicar") }
        item.spotId?.let { id ->
            state.runtime[id]?.let { Text("Estado: ${Formatters.status(it.status)}" + (it.plate?.let { p -> " · $p" } ?: ""), style = MaterialTheme.typography.bodySmall) }
        }
        Text(if (item.covered) "Techado (por zona techada)" else "Sin techo", style = MaterialTheme.typography.bodySmall)
    } else if (item.type == ItemType.TEXT || item.type == ItemType.ENTRANCE || item.type == ItemType.EXIT || item.type == ItemType.STAIRS_ELEVATOR) {
        TextCommit("Texto", item.label, item.id) { vm.setText(it) }
    }

    val box = item.geometry as? PlanBox
    if (box != null) {
        NumberCommit("X (m)", box.x, item.id + "x") { vm.setPosition(it, box.y) }
        NumberCommit("Y (m)", box.y, item.id + "y") { vm.setPosition(box.x, it) }
        NumberCommit("Ancho (m)", box.width, item.id + "w") { vm.setSize(it, box.height) }
        NumberCommit("Alto (m)", box.height, item.id + "h") { vm.setSize(box.width, it) }
    }
    NumberCommit("Giro (°)", item.rotation, item.id + "r") { vm.setRotation(it) }
    if (item.type == ItemType.ROAD || item.type == ItemType.WALL || item.type == ItemType.RAMP) {
        if (item.geometry !is PlanBox) NumberCommit("Ancho del trazo (m)", item.strokeWidth, item.id + "s") { vm.setStrokeWidth(it) }
    }
    val corner = when (val g = item.geometry) {
        is PlanBox -> g.cornerRadius
        is com.ejemplo.parkeasy.plan.model.PathGeometry -> g.cornerRadius
    }
    if (item.type != ItemType.TEXT) NumberCommit("Esquinas redondeadas (m)", corner, item.id + "c") { vm.setCornerRadius(it) }

    if (item.type == ItemType.RAMP) {
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            FilterChip(item.rampUp == true, { vm.setRamp(true, item.connectsToFloorId) }, { Text("Sube") })
            FilterChip(item.rampUp == false, { vm.setRamp(false, item.connectsToFloorId) }, { Text("Baja") })
        }
        Dropdown(
            "Conecta con", state.floors.filter { it.id != state.floorId }, state.floors.firstOrNull { it.id == item.connectsToFloorId },
            { it.name }, { f -> vm.setRamp(item.rampUp, f?.id) }, allLabel = "ninguno",
        )
    }
    if (item.type == ItemType.COLUMN) {
        Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
            Checkbox(item.round, { vm.setRound(it) })
            Text("Redonda")
        }
    }

    HorizontalDivider()
    ActionButtons(vm)
    if (item.isSpot) TextButton(onClick = onRow) { Text("Crear hilera desde este puesto…") }
}

@Composable
private fun TextCommit(label: String, value: String, key: Any, onCommit: (String) -> Unit) {
    var text by remember(key, value) { mutableStateOf(value) }
    OutlinedTextField(
        text, { text = it }, label = { Text(label) }, singleLine = true,
        modifier = Modifier.fillMaxWidth().onFocusChanged { if (!it.isFocused && text != value) onCommit(text) },
    )
}

/** Campo numérico: aplica al salir del campo, para que cada edición sea un solo paso de deshacer. */
@Composable
private fun NumberCommit(label: String, value: Double, key: Any, onCommit: (Double) -> Unit) {
    var text by remember(key, value) { mutableStateOf(format(value)) }
    OutlinedTextField(
        text, { text = it }, label = { Text(label) }, singleLine = true,
        isError = parse(text) == null,
        modifier = Modifier.fillMaxWidth().onFocusChanged {
            if (!it.isFocused) parse(text)?.let { v -> if (v != value) onCommit(v) }
        },
    )
}

private fun format(v: Double) = "%.2f".format(v).replace(',', '.').trimEnd('0').trimEnd('.')

private fun parse(text: String) = text.trim().replace(',', '.').toDoubleOrNull()

@Composable
private fun RowDialog(defaultLabel: String, onDismiss: () -> Unit, onConfirm: (Int, Double, Double, String?) -> Unit) {
    var count by remember { mutableStateOf("5") }
    var spacing by remember { mutableStateOf("2.6") }
    var angle by remember { mutableStateOf("0") }
    var label by remember { mutableStateOf(defaultLabel) }
    val countValue = count.trim().toIntOrNull()
    val spacingValue = parse(spacing)
    val angleValue = parse(angle)
    val valid = countValue != null && countValue in 1..200 && spacingValue != null && spacingValue > 0 && angleValue != null

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Crear hilera de puestos") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Copia el elemento seleccionado (o un puesto nuevo) en línea recta y numera las etiquetas.", style = MaterialTheme.typography.bodySmall)
                OutlinedTextField(count, { count = it }, label = { Text("Cantidad (1 a 200)") }, singleLine = true, isError = countValue == null || countValue !in 1..200)
                OutlinedTextField(spacing, { spacing = it }, label = { Text("Separación entre puestos (m)") }, singleLine = true, isError = spacingValue == null || spacingValue <= 0)
                OutlinedTextField(angle, { angle = it }, label = { Text("Dirección (0° derecha, 90° abajo)") }, singleLine = true, isError = angleValue == null)
                OutlinedTextField(label, { label = it }, label = { Text("Primera etiqueta (ej. A-01)") }, singleLine = true)
            }
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(countValue!!, spacingValue!!, angleValue!!, label.ifBlank { null }) }, enabled = valid) { Text("Crear") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancelar") } },
    )
}
