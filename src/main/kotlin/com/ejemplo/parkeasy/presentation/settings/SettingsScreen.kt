package com.ejemplo.parkeasy.presentation.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.ejemplo.parkeasy.domain.model.Tariff
import com.ejemplo.parkeasy.presentation.common.Formatters
import org.koin.compose.koinInject

@Composable
fun SettingsScreen(vm: SettingsViewModel = koinInject()) {
    val tariffs = remember { vm.currentTariffs() }

    Column(Modifier.fillMaxSize().padding(24.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Text("Ajustes", style = MaterialTheme.typography.headlineMedium)

        Text("Tarifas", style = MaterialTheme.typography.titleLarge)
        Text(
            "Valores en pesos colombianos, sin impuestos. Cambiar una tarifa no altera los registros ya cerrados.",
            style = MaterialTheme.typography.bodyMedium,
        )
        tariffs.forEach { TariffCard(it, vm::save) }

        Text("Datos", style = MaterialTheme.typography.titleLarge)
        Text("Base de datos: ${vm.databasePath}")
    }
}

@Composable
private fun TariffCard(tariff: Tariff, onSave: (Tariff) -> Unit) {
    var first by remember { mutableStateOf(tariff.firstHour.toString()) }
    var extra by remember { mutableStateOf(tariff.extraHour.toString()) }
    var grace by remember { mutableStateOf(tariff.graceMinutes.toString()) }
    val parsed = Triple(first.toLongOrNull(), extra.toLongOrNull(), grace.toIntOrNull())
    val valid = parsed.first != null && parsed.second != null && parsed.third != null

    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(Formatters.vehicle(tariff.vehicleType), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                OutlinedTextField(first, { first = it.filter(Char::isDigit) }, label = { Text("Primera hora") }, singleLine = true, modifier = Modifier.weight(1f))
                OutlinedTextField(extra, { extra = it.filter(Char::isDigit) }, label = { Text("Hora adicional") }, singleLine = true, modifier = Modifier.weight(1f))
                OutlinedTextField(grace, { grace = it.filter(Char::isDigit) }, label = { Text("Minutos de gracia") }, singleLine = true, modifier = Modifier.weight(1f))
                Button(
                    enabled = valid,
                    onClick = { onSave(Tariff(tariff.vehicleType, parsed.first!!, parsed.second!!, parsed.third!!)) },
                ) { Text("Guardar") }
            }
        }
    }
}
