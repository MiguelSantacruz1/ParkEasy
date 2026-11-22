package com.ejemplo.parkeasy.presentation.spots

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.width
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
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
import androidx.compose.ui.unit.dp
import com.ejemplo.parkeasy.domain.model.Floor
import com.ejemplo.parkeasy.domain.model.Spot
import com.ejemplo.parkeasy.domain.model.SpotType
import com.ejemplo.parkeasy.domain.service.SpotService
import com.ejemplo.parkeasy.presentation.common.Dropdown
import com.ejemplo.parkeasy.presentation.common.Formatters

@Composable
fun AddSpotsDialog(floors: List<Floor>, defaultFloorId: Long?, onDismiss: () -> Unit, onConfirm: (Long, SpotType, String, Int, Boolean) -> Unit) {
    var floor by remember { mutableStateOf(floors.firstOrNull { it.id == defaultFloorId } ?: floors.firstOrNull()) }
    var type by remember { mutableStateOf(SpotType.CAR) }
    var start by remember { mutableStateOf("A-01") }
    var countText by remember { mutableStateOf("10") }
    var covered by remember { mutableStateOf(false) }
    val count = countText.toIntOrNull()
    val valid = floor != null && start.isNotBlank() && count != null && count in 1..SpotService.MAX_BATCH

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Agregar puestos") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.width(400.dp)) {
                Dropdown("Piso", floors, floor, { it.name }, { floor = it ?: floor })
                Dropdown("Tipo", SpotType.entries, type, Formatters::spotType, { type = it ?: type })
                OutlinedTextField(
                    value = start, onValueChange = { start = it }, label = { Text("Primera etiqueta (A-01)") },
                    singleLine = true, modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = countText, onValueChange = { countText = it.filter(Char::isDigit) },
                    label = { Text("Cantidad (1 a ${SpotService.MAX_BATCH})") },
                    singleLine = true, isError = count == null || count !in 1..SpotService.MAX_BATCH, modifier = Modifier.fillMaxWidth(),
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(checked = covered, onCheckedChange = { covered = it })
                    Text("Techados")
                }
                Text("Quedan libres en el plano, en cuadrícula, listos para acomodarlos en el editor.", style = androidx.compose.material3.MaterialTheme.typography.bodySmall)
            }
        },
        confirmButton = {
            TextButton(enabled = valid, onClick = { onConfirm(floor!!.id, type, start, count!!, covered) }) { Text("Agregar") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancelar") } },
    )
}

@Composable
fun EditSpotDialog(spot: Spot, floors: List<Floor>, onDismiss: () -> Unit, onConfirm: (String, SpotType, Long) -> Unit) {
    var label by remember { mutableStateOf(spot.label) }
    var type by remember { mutableStateOf(spot.type) }
    var floor by remember { mutableStateOf(floors.firstOrNull { it.id == spot.floorId }) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Editar puesto ${spot.label}") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.width(380.dp)) {
                OutlinedTextField(
                    value = label, onValueChange = { label = it }, label = { Text("Etiqueta") },
                    singleLine = true, modifier = Modifier.fillMaxWidth(),
                )
                Dropdown("Tipo", SpotType.entries, type, Formatters::spotType, { type = it ?: type })
                Dropdown("Piso", floors, floor, { it.name }, { floor = it ?: floor })
                if (spot.currentRecordId != null) {
                    Text("El puesto está ocupado: solo se puede cambiar la etiqueta.", style = androidx.compose.material3.MaterialTheme.typography.bodySmall)
                }
            }
        },
        confirmButton = {
            TextButton(enabled = label.isNotBlank() && floor != null, onClick = { onConfirm(label, type, floor!!.id) }) { Text("Guardar") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancelar") } },
    )
}

@Composable
fun BlockSpotDialog(spot: Spot, onDismiss: () -> Unit, onConfirm: (String) -> Unit) {
    var reason by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Bloquear puesto ${spot.label}") },
        text = {
            OutlinedTextField(
                value = reason, onValueChange = { reason = it },
                label = { Text("Motivo (mantenimiento, obstruido, reservado…)") },
                modifier = Modifier.fillMaxWidth(),
            )
        },
        confirmButton = { TextButton(onClick = { onConfirm(reason) }) { Text("Bloquear") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancelar") } },
    )
}
