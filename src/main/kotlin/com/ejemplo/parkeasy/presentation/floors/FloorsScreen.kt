package com.ejemplo.parkeasy.presentation.floors

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import com.ejemplo.parkeasy.presentation.common.CenteredNote
import com.ejemplo.parkeasy.presentation.common.ConfirmDialog
import com.ejemplo.parkeasy.presentation.common.Formatters
import org.koin.compose.koinInject

@Composable
fun FloorsScreen(vm: FloorsViewModel = koinInject()) {
    val state by vm.state.collectAsState()
    var adding by remember { mutableStateOf(false) }
    var renaming by remember { mutableStateOf<FloorRow?>(null) }
    var deleting by remember { mutableStateOf<FloorRow?>(null) }

    Column(Modifier.fillMaxSize().padding(24.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text("Pisos", style = MaterialTheme.typography.headlineMedium, modifier = Modifier.weight(1f))
            Button(onClick = { adding = true }) { Text("Agregar piso") }
        }

        when {
            state.loading -> CenteredNote("Cargando…")
            state.rows.isEmpty() -> CenteredNote("Todavía no hay pisos. Agrega el primero para empezar.")
            else -> LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                items(state.rows, key = { it.floor.id }) { row ->
                    com.ejemplo.parkeasy.presentation.theme.NeonCard(Modifier.fillMaxWidth()) {
                        Row(
                            Modifier.padding(16.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                        ) {
                            Column(Modifier.weight(1f)) {
                                Text(row.floor.name, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                                val s = row.summary
                                Text("${s.total} puestos · ${s.free} libres · ${s.occupied} ocupados · ${s.blocked} bloqueados · ${Formatters.percent(s.occupancyPercent)} de ocupación")
                            }
                            OutlinedButton(onClick = { vm.move(row.floor.id, -1) }) { Text("Subir") }
                            OutlinedButton(onClick = { vm.move(row.floor.id, +1) }) { Text("Bajar") }
                            OutlinedButton(onClick = { renaming = row }) { Text("Renombrar") }
                            OutlinedButton(onClick = { deleting = row }) { Text("Eliminar") }
                        }
                    }
                }
            }
        }
    }

    if (adding) NameDialog("Agregar piso", "", "Agregar", onDismiss = { adding = false }) { vm.add(it); adding = false }
    renaming?.let { row ->
        NameDialog("Renombrar piso", row.floor.name, "Guardar", onDismiss = { renaming = null }) {
            vm.rename(row.floor.id, it); renaming = null
        }
    }
    deleting?.let { row ->
        val s = row.summary
        ConfirmDialog(
            title = "Eliminar ${row.floor.name}",
            text = if (s.occupied > 0) {
                "No se puede eliminar: tiene ${s.occupied} puestos ocupados. Registra sus salidas primero."
            } else {
                "Se eliminarán el piso y sus ${s.total} puestos con su plano. El historial se conserva."
            },
            confirmLabel = "Eliminar",
            onConfirm = { vm.delete(row.floor.id); deleting = null },
            onDismiss = { deleting = null },
        )
    }
}

@Composable
private fun NameDialog(title: String, initial: String, confirm: String, onDismiss: () -> Unit, onConfirm: (String) -> Unit) {
    var name by remember { mutableStateOf(initial) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            OutlinedTextField(
                value = name, onValueChange = { name = it }, label = { Text("Nombre (por ejemplo Sótano 1)") },
                singleLine = true, modifier = Modifier.fillMaxWidth(),
            )
        },
        confirmButton = { TextButton(onClick = { onConfirm(name) }, enabled = name.isNotBlank()) { Text(confirm) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancelar") } },
    )
}
