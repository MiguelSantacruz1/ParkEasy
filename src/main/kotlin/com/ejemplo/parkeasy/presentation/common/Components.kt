package com.ejemplo.parkeasy.presentation.common

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.ejemplo.parkeasy.domain.model.SpotStatus
import com.ejemplo.parkeasy.domain.rules.OccupancySummary

/** Lista desplegable simple. [allLabel] agrega una opción "todos" que devuelve null. */
@Composable
fun <T> Dropdown(
    label: String,
    options: List<T>,
    selected: T?,
    optionText: (T) -> String,
    onSelected: (T?) -> Unit,
    modifier: Modifier = Modifier,
    allLabel: String? = null,
) {
    var open by remember { mutableStateOf(false) }
    Box(modifier) {
        OutlinedButton(onClick = { open = true }, modifier = Modifier.fillMaxWidth()) {
            Text("$label: ${selected?.let(optionText) ?: allLabel ?: "—"}", maxLines = 1)
        }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            if (allLabel != null) {
                DropdownMenuItem(text = { Text(allLabel) }, onClick = { onSelected(null); open = false })
            }
            options.forEach {
                DropdownMenuItem(text = { Text(optionText(it)) }, onClick = { onSelected(it); open = false })
            }
        }
    }
}

@Composable
fun ConfirmDialog(
    title: String,
    text: String,
    confirmLabel: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = { Text(text) },
        confirmButton = { TextButton(onClick = onConfirm) { Text(confirmLabel) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancelar") } },
    )
}

/** Estado con texto y símbolo, para no depender solo del color. */
@Composable
fun StatusBadge(status: SpotStatus) {
    val (symbol, color) = when (status) {
        SpotStatus.FREE -> "○" to Color(0xFF2E7D32)
        SpotStatus.OCCUPIED -> "●" to Color(0xFFC62828)
        SpotStatus.BLOCKED -> "⊘" to Color(0xFF6D4C41)
    }
    Surface(shape = RoundedCornerShape(50), color = color.copy(alpha = 0.12f)) {
        Text(
            "$symbol ${Formatters.status(status)}",
            color = color,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
        )
    }
}

/** Total, libres, ocupados, bloqueados y porcentaje de ocupación. */
@Composable
fun SummaryRow(title: String, summary: OccupancySummary, modifier: Modifier = Modifier) {
    Card(modifier = modifier) {
        Row(
            Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.spacedBy(20.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
            Stat("Total", summary.total.toString())
            Stat("Libres", summary.free.toString())
            Stat("Ocupados", summary.occupied.toString())
            Stat("Bloqueados", summary.blocked.toString())
            Stat("Ocupación", Formatters.percent(summary.occupancyPercent))
        }
    }
}

@Composable
private fun Stat(label: String, value: String) {
    Column {
        Text(value, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        Text(label, style = MaterialTheme.typography.bodySmall)
    }
}

/** Texto centrado para estados vacíos o de carga. */
@Composable
fun CenteredNote(text: String, modifier: Modifier = Modifier) {
    Box(modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) {
        Text(text, style = MaterialTheme.typography.bodyLarge)
    }
}
