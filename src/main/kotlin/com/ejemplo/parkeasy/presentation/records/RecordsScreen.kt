package com.ejemplo.parkeasy.presentation.records

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
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.ejemplo.parkeasy.presentation.common.CenteredNote
import com.ejemplo.parkeasy.presentation.common.Formatters
import org.koin.compose.koinInject
import java.time.Duration
import java.time.Instant

@Composable
fun RecordsScreen(vm: RecordsViewModel = koinInject()) {
    val state by vm.state.collectAsState()
    val now = Instant.now()

    Column(Modifier.fillMaxSize().padding(24.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("Registros", style = MaterialTheme.typography.headlineMedium, modifier = Modifier.weight(1f))
            OutlinedTextField(
                value = state.query, onValueChange = vm::setQuery, label = { Text("Buscar placa") },
                singleLine = true, modifier = Modifier.weight(1f),
            )
            Button(onClick = { vm.openEntry() }) { Text("Nueva entrada") }
        }
        Text("Vehículos dentro ahora: ${state.totalInside}", style = MaterialTheme.typography.titleMedium)

        when {
            state.loading -> CenteredNote("Cargando…")
            state.totalInside == 0 -> CenteredNote("No hay vehículos dentro. Usa «Nueva entrada» para registrar el primero.")
            state.inside.isEmpty() -> CenteredNote("Ninguna placa dentro coincide con «${state.query}».")
            else -> LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                items(state.inside, key = { it.id }) { r ->
                    Card(Modifier.fillMaxWidth()) {
                        Row(
                            Modifier.padding(16.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                        ) {
                            Column(Modifier.weight(1f)) {
                                Text(r.plate, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                                Text("${Formatters.vehicle(r.vehicleType)} ${r.color}" + if (r.notes.isNotBlank()) " · ${r.notes}" else "")
                            }
                            Column(Modifier.weight(1f)) {
                                Text("${r.floorName} · puesto ${r.spotLabel}", fontWeight = FontWeight.SemiBold)
                                Text("Llegó ${Formatters.dateTime(r.entryTime)} · ${Formatters.duration(Duration.between(r.entryTime, now))}")
                            }
                            Button(onClick = { vm.openExit(r.id) }) { Text("Registrar salida") }
                            OutlinedButton(onClick = { vm.openMove(r.id) }) { Text("Cambiar puesto") }
                            OutlinedButton(onClick = { vm.openCorrect(r.id) }) { Text("Corregir") }
                        }
                    }
                }
            }
        }
    }
}
