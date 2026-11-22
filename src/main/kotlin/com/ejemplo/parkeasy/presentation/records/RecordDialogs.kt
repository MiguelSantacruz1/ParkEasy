package com.ejemplo.parkeasy.presentation.records

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.width
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import com.ejemplo.parkeasy.domain.model.Spot
import com.ejemplo.parkeasy.domain.model.VehicleType
import com.ejemplo.parkeasy.domain.rules.PlateValidator
import com.ejemplo.parkeasy.domain.service.CheckInRequest
import com.ejemplo.parkeasy.domain.service.CorrectionRequest
import com.ejemplo.parkeasy.presentation.common.Dropdown
import com.ejemplo.parkeasy.presentation.common.Formatters
import java.time.Instant

private val commonColors = listOf("Blanco", "Negro", "Gris", "Plata", "Rojo", "Azul", "Verde", "Amarillo", "Naranja", "Café")
private const val OTHER_COLOR = "Otro…"

/** Elige un color común o escribe uno. */
@Composable
private fun ColorPicker(color: String, onChange: (String) -> Unit) {
    var custom by remember { mutableStateOf(color.isNotBlank() && color !in commonColors) }
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Dropdown(
            label = "Color",
            options = commonColors + OTHER_COLOR,
            selected = if (custom) OTHER_COLOR else color.ifBlank { null },
            optionText = { it },
            onSelected = {
                if (it == OTHER_COLOR) { custom = true; onChange("") } else { custom = false; onChange(it ?: "") }
            },
        )
        if (custom) {
            OutlinedTextField(
                value = color, onValueChange = onChange, label = { Text("Escribe el color") },
                singleLine = true, modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

@Composable
fun EntryDialog(
    preselectedSpotId: Long?,
    freeSpots: List<Spot>,
    now: Instant = Instant.now(),
    onDismiss: () -> Unit,
    onConfirm: (CheckInRequest) -> Unit,
) {
    var spotId by remember { mutableStateOf(preselectedSpotId) }
    var vehicle by remember { mutableStateOf(VehicleType.CAR) }
    var plate by remember { mutableStateOf("") }
    var color by remember { mutableStateOf("") }
    var time by remember { mutableStateOf(Formatters.dateTime(now)) }
    var notes by remember { mutableStateOf("") }
    var acceptOddPlate by remember { mutableStateOf(false) }

    val spot = freeSpots.firstOrNull { it.id == spotId }
    val compatible = freeSpots.filter { it.type.accepts(vehicle) }
    val entry = Formatters.parseDateTime(time)
    val plateOk = plate.isNotBlank() && PlateValidator.isValid(plate, vehicle)
    val oddPlate = plate.isNotBlank() && !plateOk
    val canSubmit = spot != null && spot.type.accepts(vehicle) && plate.isNotBlank() && entry != null &&
        color.isNotBlank() && (!oddPlate || acceptOddPlate)

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Registrar entrada") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.width(420.dp)) {
                Dropdown(
                    label = "Tipo de vehículo", options = VehicleType.entries, selected = vehicle,
                    optionText = Formatters::vehicle,
                    onSelected = { vehicle = it ?: vehicle; if (spot != null && !spot.type.accepts(vehicle)) spotId = null },
                )
                Dropdown(
                    label = "Puesto", options = compatible, selected = spot,
                    optionText = { "${it.label} · ${Formatters.spotType(it.type)}" },
                    onSelected = { spotId = it?.id },
                )
                if (compatible.isEmpty()) Text("No hay puestos libres para este tipo de vehículo.", color = MaterialTheme.colorScheme.error)
                OutlinedTextField(
                    value = plate, onValueChange = { plate = it.uppercase() }, label = { Text("Placa (ABC123 o ABC12D)") },
                    singleLine = true, modifier = Modifier.fillMaxWidth(),
                )
                if (oddPlate) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(checked = acceptOddPlate, onCheckedChange = { acceptOddPlate = it })
                        Text("El formato no coincide. Continuar de todos modos (placa extranjera o especial).", style = MaterialTheme.typography.bodySmall)
                    }
                }
                ColorPicker(color) { color = it }
                OutlinedTextField(
                    value = time, onValueChange = { time = it }, label = { Text("Hora de llegada (dd/MM/aaaa HH:mm)") },
                    singleLine = true, isError = entry == null, modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = notes, onValueChange = { notes = it }, label = { Text("Observaciones (opcional)") },
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        },
        confirmButton = {
            TextButton(
                enabled = canSubmit,
                onClick = { onConfirm(CheckInRequest(spot!!.id, plate, color, vehicle, entry!!, notes)) },
            ) { Text("Registrar entrada") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancelar") } },
    )
}

@Composable
fun ExitDialog(
    vm: RecordsViewModel,
    recordId: Long,
    now: Instant = Instant.now(),
    onDismiss: () -> Unit,
) {
    val record = remember(recordId) { vm.record(recordId) }
    if (record == null) { onDismiss(); return }
    var time by remember { mutableStateOf(Formatters.dateTime(now)) }
    val exit = Formatters.parseDateTime(time)
    val price = exit?.let { vm.quote(recordId, it) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Registrar salida de ${record.plate}") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.width(420.dp)) {
                Text("${record.floorName} · puesto ${record.spotLabel} · ${Formatters.vehicle(record.vehicleType)} ${record.color}")
                Text("Llegada: ${Formatters.dateTime(record.entryTime)}")
                OutlinedTextField(
                    value = time, onValueChange = { time = it }, label = { Text("Hora de salida (dd/MM/aaaa HH:mm)") },
                    singleLine = true, isError = price == null, modifier = Modifier.fillMaxWidth(),
                )
                if (price == null) {
                    Text("La hora no es válida o es anterior a la llegada.", color = MaterialTheme.colorScheme.error)
                } else {
                    Text("Tiempo: ${Formatters.duration(price.duration)}")
                    Text("Primera hora: ${Formatters.cop(price.firstHour)}")
                    if (price.extraHours > 0) {
                        Text("${price.extraHours} horas adicionales × ${Formatters.cop(price.extraHourValue)} = ${Formatters.cop(price.extraHours * price.extraHourValue)}")
                    }
                    Text("Total a cobrar: ${Formatters.cop(price.total)}", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                    Text("PARKEASY muestra el valor; el cobro se hace en caja.", style = MaterialTheme.typography.bodySmall)
                }
            }
        },
        confirmButton = {
            TextButton(enabled = price != null, onClick = { vm.checkOut(recordId, exit!!) }) { Text("Confirmar salida") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancelar") } },
    )
}

@Composable
fun CorrectDialog(vm: RecordsViewModel, recordId: Long, onDismiss: () -> Unit) {
    val record = remember(recordId) { vm.record(recordId) }
    if (record == null) { onDismiss(); return }
    var plate by remember { mutableStateOf(record.plate) }
    var color by remember { mutableStateOf(record.color) }
    var notes by remember { mutableStateOf(record.notes) }
    var entryText by remember { mutableStateOf(Formatters.dateTime(record.entryTime)) }
    val entry = Formatters.parseDateTime(entryText)

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Corregir registro") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.width(420.dp)) {
                OutlinedTextField(
                    value = plate, onValueChange = { plate = it.uppercase() }, label = { Text("Placa") },
                    singleLine = true, modifier = Modifier.fillMaxWidth(),
                )
                ColorPicker(color) { color = it }
                OutlinedTextField(
                    value = entryText, onValueChange = { entryText = it }, label = { Text("Hora de llegada") },
                    singleLine = true, isError = entry == null, modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = notes, onValueChange = { notes = it }, label = { Text("Observaciones") },
                    modifier = Modifier.fillMaxWidth(),
                )
                Text("La corrección queda en la bitácora.", style = MaterialTheme.typography.bodySmall)
            }
        },
        confirmButton = {
            TextButton(
                enabled = plate.isNotBlank() && entry != null,
                onClick = { vm.correct(recordId, CorrectionRequest(plate, color, notes, entry!!, record.exitTime)) },
            ) { Text("Guardar corrección") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancelar") } },
    )
}

@Composable
fun MoveDialog(vm: RecordsViewModel, recordId: Long, freeSpots: List<Spot>, onDismiss: () -> Unit) {
    val record = remember(recordId) { vm.record(recordId) }
    if (record == null) { onDismiss(); return }
    val options = freeSpots.filter { it.type.accepts(record.vehicleType) }
    var target by remember { mutableStateOf<Spot?>(null) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Cambiar ${record.plate} de puesto") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.width(380.dp)) {
                Text("Ahora está en ${record.spotLabel}. La hora de llegada no cambia.")
                Dropdown(
                    label = "Nuevo puesto", options = options, selected = target,
                    optionText = { "${it.label} · ${Formatters.spotType(it.type)}" },
                    onSelected = { target = it },
                )
                if (options.isEmpty()) Text("No hay puestos libres compatibles.", color = MaterialTheme.colorScheme.error)
            }
        },
        confirmButton = {
            TextButton(enabled = target != null, onClick = { vm.move(recordId, target!!.id) }) { Text("Cambiar") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancelar") } },
    )
}
