package com.ejemplo.parkeasy.presentation.assistant

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
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
import org.koin.compose.koinInject

private val SUGGESTIONS = listOf(
    "¿Cuánto cobré en los últimos 7 días?",
    "¿A qué horas hay más entradas?",
    "¿Cuántos vehículos llevan más de 8 horas?",
    "Hazme un resumen del día",
)

@Composable
fun AssistantScreen(vm: AssistantViewModel = koinInject()) {
    val state by vm.state.collectAsState()
    var text by remember { mutableStateOf("") }
    val list = rememberLazyListState()
    LaunchedEffect(Unit) { vm.refresh() }
    LaunchedEffect(state.turns.size) { if (state.turns.isNotEmpty()) list.animateScrollToItem(state.turns.size - 1) }

    Column(Modifier.fillMaxSize().padding(24.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("Asistente con IA", style = MaterialTheme.typography.headlineMedium)
        Text(
            "Pregunta sobre ocupación, ingresos y tiempos. Al consultar se envía a Gemini (Google) un resumen sin placas " +
                "ni datos de personas. Las respuestas pueden tener errores: revisa las cifras importantes en el Historial.",
            style = MaterialTheme.typography.bodyMedium,
        )

        if (!state.enabled) {
            Text("El asistente está apagado. Pega tu clave de Gemini en Ajustes → Asistente con IA para activarlo.")
            return@Column
        }

        LazyColumn(Modifier.weight(1f).fillMaxWidth(), state = list, verticalArrangement = Arrangement.spacedBy(8.dp)) {
            if (state.turns.isEmpty()) {
                item {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        SUGGESTIONS.take(3).forEach { s -> AssistChip(onClick = { vm.ask(s) }, label = { Text(s) }) }
                    }
                }
            }
            items(state.turns) { t ->
                Row(Modifier.fillMaxWidth(), horizontalArrangement = if (t.fromUser) Arrangement.End else Arrangement.Start) {
                    Card(
                        Modifier.fillMaxWidth(0.8f),
                        colors = CardDefaults.cardColors(
                            containerColor = if (t.fromUser) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant,
                        ),
                    ) { Text(t.text, Modifier.padding(12.dp)) }
                }
            }
            if (state.waiting) item { Text("Pensando…", style = MaterialTheme.typography.labelLarge) }
        }

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            OutlinedTextField(
                text, { text = it }, Modifier.weight(1f), label = { Text("Tu pregunta") }, singleLine = true,
                enabled = !state.waiting,
            )
            Button(enabled = text.isNotBlank() && !state.waiting, onClick = { vm.ask(text); text = "" }) { Text("Preguntar") }
            OutlinedButton(enabled = state.turns.isNotEmpty(), onClick = vm::clear) { Text("Limpiar") }
        }
    }
}
