package com.ejemplo.parkeasy.presentation.plan

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.ejemplo.parkeasy.domain.model.SpotStatus
import com.ejemplo.parkeasy.domain.rules.OccupancySummary
import com.ejemplo.parkeasy.plan.model.PlanItem
import com.ejemplo.parkeasy.presentation.common.CenteredNote
import com.ejemplo.parkeasy.presentation.common.ConfirmDialog
import com.ejemplo.parkeasy.presentation.common.SummaryRow
import com.ejemplo.parkeasy.presentation.navigation.Navigator
import com.ejemplo.parkeasy.presentation.navigation.Screen
import com.ejemplo.parkeasy.presentation.records.RecordsViewModel
import com.ejemplo.parkeasy.presentation.spots.SpotsViewModel
import org.koin.compose.koinInject

/**
 * Inicio: el plano del piso con el estado de cada puesto en vivo. Un clic en un puesto libre
 * abre la entrada, en uno ocupado abre la salida y en uno bloqueado ofrece desbloquearlo.
 */
@Composable
fun HomeScreen(
    vm: PlanViewModel = koinInject(),
    records: RecordsViewModel = koinInject(),
    spots: SpotsViewModel = koinInject(),
    navigator: Navigator = koinInject(),
) {
    val state by vm.state.collectAsState()
    var unblocking by remember { mutableStateOf<PlanItem?>(null) }

    LaunchedEffect(Unit) { vm.setMode(PlanMode.OPERATION) }

    val statuses = state.runtime.values.map { it.status }
    val summary = OccupancySummary(
        total = statuses.size,
        free = statuses.count { it == SpotStatus.FREE },
        occupied = statuses.count { it == SpotStatus.OCCUPIED },
        blocked = statuses.count { it == SpotStatus.BLOCKED },
    )

    Column(Modifier.fillMaxSize().padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
            SummaryRow("Parqueadero", summary, Modifier.weight(1f))
            ExportButtons(vm, state)
            Button(onClick = { records.openEntry() }) { Text("Registrar entrada") }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            state.floors.forEach { floor ->
                val match = floor.id in state.matchFloorIds
                FilterChip(
                    selected = floor.id == state.floorId,
                    onClick = { vm.selectFloor(floor.id) },
                    label = { Text(floor.name + if (match) "  ★" else "") },
                )
            }
            Box(Modifier.weight(1f))
            OutlinedTextField(
                value = state.query, onValueChange = vm::setQuery, singleLine = true,
                label = { Text("Buscar placa en el plano") }, modifier = Modifier.width(260.dp),
            )
        }
        Legend()
        Box(Modifier.weight(1f).fillMaxWidth()) {
            val doc = state.doc
            when {
                state.loading -> CenteredNote("Cargando el plano…")
                state.floorId == null -> CenteredNote("Aún no hay pisos. Crea uno en la pantalla Pisos.")
                doc == null || doc.items.none { it.isSpot } -> Column(
                    Modifier.fillMaxSize(), verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Text("Este piso todavía no tiene puestos dibujados.", style = MaterialTheme.typography.bodyLarge)
                    Button(onClick = { navigator.navigateTo(Screen.EDITOR) }, modifier = Modifier.padding(top = 12.dp)) { Text("Abrir el editor") }
                }
                else -> PlanCanvas(vm, state, operation = true, onSpotClick = { item ->
                    val runtime = item.spotId?.let { state.runtime[it] }
                    when (runtime?.status) {
                        SpotStatus.FREE -> item.spotId?.let { records.openEntry(it) }
                        SpotStatus.OCCUPIED -> runtime.recordId?.let { records.openExit(it) }
                        SpotStatus.BLOCKED -> unblocking = item
                        null -> Unit
                    }
                })
            }
        }
    }

    unblocking?.let { item ->
        ConfirmDialog(
            title = "Desbloquear puesto",
            text = "¿Quieres volver a habilitar el puesto ${item.label}?",
            confirmLabel = "Desbloquear",
            onConfirm = { item.spotId?.let(spots::unblock); unblocking = null },
            onDismiss = { unblocking = null },
        )
    }
}

@Composable
private fun Legend() {
    val neon = com.ejemplo.parkeasy.presentation.theme.LocalNeon.current
    Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
        Text("○ Libre", color = com.ejemplo.parkeasy.presentation.theme.StatusPalette.free(neon), style = MaterialTheme.typography.bodySmall)
        Text("● Ocupado", color = com.ejemplo.parkeasy.presentation.theme.StatusPalette.occupied(neon), style = MaterialTheme.typography.bodySmall)
        Text("⊘ Bloqueado", color = com.ejemplo.parkeasy.presentation.theme.StatusPalette.blocked(neon), style = MaterialTheme.typography.bodySmall)
        Text("Clic en un puesto: libre → entrada · ocupado → salida · bloqueado → desbloquear", style = MaterialTheme.typography.bodySmall)
    }
}
