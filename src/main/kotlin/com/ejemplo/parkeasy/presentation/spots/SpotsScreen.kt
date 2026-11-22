package com.ejemplo.parkeasy.presentation.spots

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.Checkbox
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.ejemplo.parkeasy.domain.model.SpotStatus
import com.ejemplo.parkeasy.domain.model.SpotType
import com.ejemplo.parkeasy.presentation.common.CenteredNote
import com.ejemplo.parkeasy.presentation.common.ConfirmDialog
import com.ejemplo.parkeasy.presentation.common.Dropdown
import com.ejemplo.parkeasy.presentation.common.Formatters
import com.ejemplo.parkeasy.presentation.common.StatusBadge
import com.ejemplo.parkeasy.presentation.common.SummaryRow
import com.ejemplo.parkeasy.presentation.records.RecordsViewModel
import org.koin.compose.koinInject

@Composable
fun SpotsScreen(vm: SpotsViewModel = koinInject(), records: RecordsViewModel = koinInject()) {
    val state by vm.state.collectAsState()
    var adding by remember { mutableStateOf(false) }
    var editing by remember { mutableStateOf<SpotRow?>(null) }
    var blocking by remember { mutableStateOf<SpotRow?>(null) }
    var deleting by remember { mutableStateOf<SpotRow?>(null) }
    val f = state.filters

    Column(Modifier.fillMaxSize().padding(24.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("Puestos", style = MaterialTheme.typography.headlineMedium, modifier = Modifier.weight(1f))
            Button(onClick = { records.openEntry() }, enabled = state.total.free > 0) { Text("Nueva entrada") }
            Button(onClick = { adding = true }, enabled = state.floors.isNotEmpty()) { Text("Agregar puestos") }
        }

        SummaryRow("Total", state.total, Modifier.fillMaxWidth())
        if (state.floors.size > 1) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                state.floors.forEach { floor ->
                    SummaryRow(floor.name, state.byFloor[floor.id] ?: com.ejemplo.parkeasy.domain.rules.OccupancySummary.EMPTY, Modifier.weight(1f))
                }
            }
        }

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            Dropdown("Piso", state.floors, state.floors.firstOrNull { it.id == f.floorId }, { it.name },
                { s -> vm.setFilters { it.copy(floorId = s?.id) } }, Modifier.weight(1f), allLabel = "Todos")
            Dropdown("Tipo", SpotType.entries, f.type, Formatters::spotType,
                { s -> vm.setFilters { it.copy(type = s) } }, Modifier.weight(1f), allLabel = "Todos")
            Dropdown("Estado", SpotStatus.entries, f.status, Formatters::status,
                { s -> vm.setFilters { it.copy(status = s) } }, Modifier.weight(1f), allLabel = "Todos")
            Checkbox(checked = f.coveredOnly, onCheckedChange = { c -> vm.setFilters { it.copy(coveredOnly = c) } })
            Text("Solo techados")
        }

        when {
            state.loading -> CenteredNote("Cargando…")
            state.floors.isEmpty() -> CenteredNote("Primero crea un piso en la sección Pisos.")
            state.totalSpots == 0 -> CenteredNote("Todavía no hay puestos. Usa «Agregar puestos» para crear varios de una vez.")
            state.rows.isEmpty() -> CenteredNote("Ningún puesto coincide con los filtros.")
            else -> LazyColumn(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                items(state.rows, key = { it.spot.id }) { row ->
                    val s = row.spot
                    Column {
                        Row(
                            Modifier.fillMaxWidth().padding(vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                        ) {
                            Text(s.label, fontWeight = FontWeight.Bold, modifier = Modifier.weight(0.7f))
                            Text(row.floorName, modifier = Modifier.weight(1f))
                            Text(Formatters.spotType(s.type) + if (s.covered) " · techado" else "", modifier = Modifier.weight(1.2f))
                            Column(Modifier.weight(1.6f)) {
                                StatusBadge(s.status)
                                row.plate?.let { Text(it, fontWeight = FontWeight.SemiBold) }
                                if (s.blocked && !s.blockReason.isNullOrBlank()) Text(s.blockReason, style = MaterialTheme.typography.bodySmall)
                            }
                            Row(Modifier.weight(3.2f), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                when (s.status) {
                                    SpotStatus.FREE -> {
                                        Button(onClick = { records.openEntry(s.id) }) { Text("Marcar ocupado") }
                                        OutlinedButton(onClick = { blocking = row }) { Text("Bloquear") }
                                    }
                                    SpotStatus.OCCUPIED -> Button(onClick = { records.openExit(s.currentRecordId!!) }) { Text("Marcar libre") }
                                    SpotStatus.BLOCKED -> OutlinedButton(onClick = { vm.unblock(s.id) }) { Text("Desbloquear") }
                                }
                                OutlinedButton(onClick = { editing = row }) { Text("Editar") }
                                OutlinedButton(onClick = { deleting = row }) { Text("Eliminar") }
                            }
                        }
                        HorizontalDivider()
                    }
                }
            }
        }
    }

    if (adding) {
        AddSpotsDialog(state.floors, f.floorId, { adding = false }) { floorId, type, start, count, covered ->
            vm.addMany(floorId, type, start, count, covered); adding = false
        }
    }
    editing?.let { row ->
        EditSpotDialog(row.spot, state.floors, { editing = null }) { label, type, floorId ->
            vm.update(row.spot, label, type, floorId); editing = null
        }
    }
    blocking?.let { row ->
        BlockSpotDialog(row.spot, { blocking = null }) { reason -> vm.block(row.spot.id, reason); blocking = null }
    }
    deleting?.let { row ->
        ConfirmDialog(
            title = "Eliminar puesto ${row.spot.label}",
            text = if (row.spot.status == SpotStatus.OCCUPIED) {
                "No se puede eliminar: el puesto está ocupado por ${row.plate}. Registra primero su salida."
            } else {
                "El puesto se elimina del plano y de la lista. El historial conserva su número. Podrás deshacerlo en esta sesión."
            },
            confirmLabel = "Eliminar",
            onConfirm = { vm.delete(row.spot); deleting = null },
            onDismiss = { deleting = null },
        )
    }
}
