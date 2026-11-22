package com.ejemplo.parkeasy.presentation.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
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
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.ejemplo.parkeasy.domain.service.ThemeMode
import com.ejemplo.parkeasy.presentation.common.ConfirmDialog
import com.ejemplo.parkeasy.presentation.common.FileDialogs
import com.ejemplo.parkeasy.presentation.plan.PlanViewModel
import org.koin.compose.koinInject
import java.nio.file.Path

@Composable
fun AppearanceSection(vm: SettingsViewModel) {
    val theme by vm.theme.collectAsState()
    Text("Apariencia", style = MaterialTheme.typography.titleLarge)
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        listOf(ThemeMode.SYSTEM to "Como el sistema", ThemeMode.LIGHT to "Claro", ThemeMode.DARK to "Oscuro").forEach { (mode, label) ->
            FilterChip(theme == mode, { vm.setTheme(mode) }, { Text(label) })
        }
    }
}

@Composable
fun SecuritySection(vm: SettingsViewModel) {
    val hasPin by vm.hasPin.collectAsState()
    var pin by remember { mutableStateOf("") }
    var confirm by remember { mutableStateOf("") }

    Text("Seguridad", style = MaterialTheme.typography.titleLarge)
    Text(
        "El PIN pide una clave al abrir la app. Sirve para que nadie use el programa sin permiso, pero no cifra la base de datos: " +
            "quien tenga acceso a la carpeta del usuario puede abrir el archivo.",
        style = MaterialTheme.typography.bodyMedium,
    )
    if (hasPin) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            OutlinedTextField(
                pin, { pin = it.filter(Char::isDigit).take(8) }, label = { Text("PIN actual") }, singleLine = true,
                visualTransformation = PasswordVisualTransformation(),
            )
            OutlinedButton(onClick = { vm.clearPin(pin); pin = "" }) { Text("Quitar PIN") }
        }
    } else {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            OutlinedTextField(
                pin, { pin = it.filter(Char::isDigit).take(8) }, label = { Text("Nuevo PIN (4 a 8 dígitos)") }, singleLine = true,
                visualTransformation = PasswordVisualTransformation(),
            )
            OutlinedTextField(
                confirm, { confirm = it.filter(Char::isDigit).take(8) }, label = { Text("Repetir PIN") }, singleLine = true,
                visualTransformation = PasswordVisualTransformation(),
            )
            Button(onClick = { vm.setPin(pin, confirm); pin = ""; confirm = "" }) { Text("Activar PIN") }
        }
    }
}

@Composable
fun BackupSection(vm: SettingsViewModel, plan: PlanViewModel = koinInject()) {
    var pending by remember { mutableStateOf<Path?>(null) }

    Text("Copias de seguridad", style = MaterialTheme.typography.titleLarge)
    Text(
        "La copia es un archivo con todos los datos: pisos, puestos, planos, registros, bitácora y tarifas. " +
            "Guárdala en otro disco o en una memoria USB.",
        style = MaterialTheme.typography.bodyMedium,
    )
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Button(onClick = { FileDialogs.save("Guardar copia de seguridad", vm.suggestedBackupName())?.let(vm::createBackup) }) {
            Text("Crear copia…")
        }
        OutlinedButton(onClick = { pending = FileDialogs.open("Elegir copia para restaurar") }) { Text("Restaurar copia…") }
    }

    pending?.let { file ->
        ConfirmDialog(
            title = "Restaurar copia",
            text = "Se reemplazarán TODOS los datos actuales por los de ${file.fileName}. " +
                "Antes se guarda una copia de lo que hay ahora en la carpeta de copias de la app, por si te arrepientes.",
            confirmLabel = "Restaurar",
            onConfirm = { vm.restore(file, onDone = plan::reload); pending = null },
            onDismiss = { pending = null },
        )
    }
}

@Composable
fun ShortcutsSection() {
    Text("Atajos de teclado", style = MaterialTheme.typography.titleLarge)
    Column(Modifier.padding(start = 4.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
        listOf(
            "Ctrl+N" to "Registrar entrada",
            "Ctrl+F" to "Buscar placa (abre el historial)",
            "Ctrl+E" to "Alternar entre inicio y editor del plano",
            "Ctrl+1 / Ctrl+2 / Ctrl+3" to "Ir a Inicio / Editor / Historial",
            "Ctrl+," to "Ir a Ajustes",
            "— En el editor —" to "",
            "Ctrl+Z / Ctrl+Y" to "Deshacer / rehacer",
            "Ctrl+C / Ctrl+V / Ctrl+D" to "Copiar, pegar y duplicar",
            "Ctrl+A" to "Seleccionar todo",
            "Supr o Retroceso" to "Eliminar la selección",
            "Esc" to "Cancelar lo que se dibuja o quitar la selección",
            "Enter o doble clic" to "Terminar polilínea, polígono o curva",
            "R" to "Girar 90°",
            "Flechas (Mayús = fino)" to "Mover 0,5 m (0,1 m)",
            "Rueda del ratón" to "Acercar y alejar",
            "Botón central o derecho" to "Mover la vista",
        ).forEach { (keys, what) -> Text("$keys — $what", style = MaterialTheme.typography.bodyMedium) }
    }
}

@Composable
fun PrivacySection(vm: SettingsViewModel) {
    var text by remember { mutableStateOf("") }
    var pending by remember { mutableStateOf<Pair<java.time.LocalDate, Int>?>(null) }
    val date = runCatching { java.time.LocalDate.parse(text.trim()) }.getOrNull()

    Text("Privacidad y registros antiguos", style = MaterialTheme.typography.titleLarge)
    Text(
        "Las placas son datos personales. Borra los registros cerrados que ya no necesites: se eliminan de verdad, no se pueden recuperar " +
            "(salvo desde una copia de seguridad) y los vehículos que siguen dentro no se tocan. Más detalles en docs/PRIVACIDAD.md.",
        style = MaterialTheme.typography.bodyMedium,
    )
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
        OutlinedTextField(
            text, { text = it }, label = { Text("Borrar salidas anteriores a (AAAA-MM-DD)") }, singleLine = true,
            isError = text.isNotBlank() && date == null,
        )
        OutlinedButton(
            enabled = date != null,
            onClick = { date?.let { d -> pending = vm.previewPurge(d)?.let { n -> d to n } ?: run { null } } },
        ) { Text("Revisar") }
    }
    pending?.let { (d, n) ->
        if (n == 0) {
            ConfirmDialog("Nada que borrar", "No hay registros cerrados anteriores al $d.", "Entendido", { pending = null }, { pending = null })
        } else {
            ConfirmDialog(
                title = "Borrar registros antiguos",
                text = "Se borrarán $n registros cerrados anteriores al $d. Esta acción no se puede deshacer.",
                confirmLabel = "Borrar $n registros",
                onConfirm = { vm.purge(d); pending = null; text = "" },
                onDismiss = { pending = null },
            )
        }
    }
}