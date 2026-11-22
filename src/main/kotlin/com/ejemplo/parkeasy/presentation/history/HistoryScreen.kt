package com.ejemplo.parkeasy.presentation.history

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
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
import com.ejemplo.parkeasy.domain.model.VehicleType
import com.ejemplo.parkeasy.domain.rules.RecordState
import com.ejemplo.parkeasy.presentation.common.CenteredNote
import com.ejemplo.parkeasy.presentation.common.Dropdown
import com.ejemplo.parkeasy.presentation.common.FileDialogs
import com.ejemplo.parkeasy.presentation.common.Formatters
import org.koin.compose.koinInject
import java.time.LocalDate
import java.time.ZoneId

@Composable
fun HistoryScreen(vm: HistoryViewModel = koinInject()) {
    val state by vm.state.collectAsState()
    val zone = remember { ZoneId.of("America/Bogota") }

    Column(Modifier.fillMaxSize().padding(24.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("Historial", style = MaterialTheme.typography.headlineMedium, modifier = Modifier.weight(1f))
            FilterChip(state.tab == HistoryTab.RECORDS, { vm.setTab(HistoryTab.RECORDS) }, { Text("Registros") })
            FilterChip(state.tab == HistoryTab.CHANGES, { vm.setTab(HistoryTab.CHANGES) }, { Text("Bitácora de cambios") })
            Button(onClick = {
                val name = if (state.tab == HistoryTab.RECORDS) "registros.csv" else "bitacora.csv"
                FileDialogs.save("Exportar a CSV", name)?.let(vm::export)
            }) { Text("Exportar CSV") }
        }

        when {
            state.loading -> CenteredNote("Cargando…")
            state.tab == HistoryTab.RECORDS -> RecordsTab(vm, state, zone)
            else -> ChangesTab(state, zone)
        }
    }
}

@Composable
private fun RecordsTab(vm: HistoryViewModel, state: HistoryUiState, zone: ZoneId) {
    val filter = state.filter
    var from by remember { mutableStateOf(filter.from?.toString().orEmpty()) }
    var to by remember { mutableStateOf(filter.to?.toString().orEmpty()) }

    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
        OutlinedTextField(filter.plate, { t -> vm.setFilter { it.copy(plate = t) } }, label = { Text("Placa") }, singleLine = true, modifier = Modifier.width(140.dp))
        OutlinedTextField(
            from, { from = it; parseDate(it).let { d -> if (d != null || it.isBlank()) vm.setFilter { f -> f.copy(from = d) } } },
            label = { Text("Desde (AAAA-MM-DD)") }, singleLine = true, isError = from.isNotBlank() && parseDate(from) == null,
            modifier = Modifier.width(190.dp),
        )
        OutlinedTextField(
            to, { to = it; parseDate(it).let { d -> if (d != null || it.isBlank()) vm.setFilter { f -> f.copy(to = d) } } },
            label = { Text("Hasta (AAAA-MM-DD)") }, singleLine = true, isError = to.isNotBlank() && parseDate(to) == null,
            modifier = Modifier.width(190.dp),
        )
        Dropdown("Piso", state.floorNames, filter.floorName, { it }, { n -> vm.setFilter { it.copy(floorName = n) } }, Modifier.width(160.dp), allLabel = "todos")
        Dropdown("Tipo", VehicleType.entries, filter.vehicleType, Formatters::vehicle, { t -> vm.setFilter { it.copy(vehicleType = t) } }, Modifier.width(160.dp), allLabel = "todos")
        Dropdown(
            "Estado", RecordState.entries, filter.state,
            { when (it) { RecordState.ALL -> "todos"; RecordState.INSIDE -> "dentro"; RecordState.CLOSED -> "cerrados" } },
            { s -> vm.setFilter { it.copy(state = s ?: RecordState.ALL) } }, Modifier.width(160.dp),
        )
        OutlinedButton(onClick = { from = ""; to = ""; vm.clearFilters() }) { Text("Limpiar") }
    }

    Text(
        "${state.totals.count} de ${state.totalRecords} registros · ${state.totals.closed} cerrados · cobrado: ${Formatters.cop(state.totals.charged)}",
        style = MaterialTheme.typography.titleSmall,
    )

    when {
        state.totalRecords == 0 -> CenteredNote("Todavía no hay registros. Aparecerán aquí cuando entre el primer vehículo.")
        state.rows.isEmpty() -> CenteredNote("Ningún registro coincide con los filtros.")
        else -> LazyColumn(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            items(state.rows, key = { it.id }) { r ->
                Card(Modifier.fillMaxWidth()) {
                    Row(Modifier.padding(12.dp), horizontalArrangement = Arrangement.spacedBy(16.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text(r.plate, fontWeight = FontWeight.Bold, modifier = Modifier.width(90.dp))
                        Text("${Formatters.vehicle(r.vehicleType)} ${r.color}", modifier = Modifier.width(150.dp))
                        Text("${r.floorName} · ${r.spotLabel}", modifier = Modifier.width(170.dp))
                        Text("Entrada ${Formatters.dateTime(r.entryTime)}", modifier = Modifier.weight(1f))
                        Text(
                            r.exitTime?.let { "Salida ${Formatters.dateTime(it)}" } ?: "Dentro",
                            modifier = Modifier.weight(1f),
                        )
                        Text(r.charged?.let { Formatters.cop(it) } ?: "—", fontWeight = FontWeight.SemiBold, modifier = Modifier.width(100.dp))
                    }
                }
            }
        }
    }
}

@Composable
private fun ChangesTab(state: HistoryUiState, zone: ZoneId) {
    if (state.changes.isEmpty()) {
        CenteredNote("La bitácora está vacía. Cada cambio importante queda anotado aquí.")
        return
    }
    Text("Últimos ${state.changes.size} cambios", style = MaterialTheme.typography.titleSmall)
    LazyColumn(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        items(state.changes, key = { it.id }) { e ->
            Card(Modifier.fillMaxWidth()) {
                Row(Modifier.padding(12.dp), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    Text(Formatters.dateTime(e.timestamp), modifier = Modifier.width(160.dp))
                    Text(e.action.name, fontWeight = FontWeight.SemiBold, modifier = Modifier.width(170.dp))
                    Text(e.detail, modifier = Modifier.weight(1f))
                }
            }
        }
    }
}

private fun parseDate(text: String): LocalDate? = runCatching { LocalDate.parse(text.trim()) }.getOrNull()
