package com.ejemplo.parkeasy.presentation

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.ejemplo.parkeasy.domain.DomainError
import com.ejemplo.parkeasy.domain.model.SpotType
import com.ejemplo.parkeasy.domain.repository.FloorRepository
import com.ejemplo.parkeasy.domain.service.AppSettings
import com.ejemplo.parkeasy.domain.service.FloorService
import com.ejemplo.parkeasy.domain.service.SpotService
import com.ejemplo.parkeasy.domain.service.ThemeMode
import com.ejemplo.parkeasy.presentation.theme.ParkEasyTheme
import com.ejemplo.parkeasy.presentation.theme.backdrop
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.koin.compose.koinInject

/** Raíz de la interfaz: tema elegido, pantalla de PIN, asistente del primer uso y la app. */
@Composable
fun AppRoot(settings: AppSettings = koinInject()) {
    val theme by settings.theme.collectAsState()
    val dark = when (theme) {
        ThemeMode.SYSTEM -> isSystemInDarkTheme()
        ThemeMode.LIGHT -> false
        ThemeMode.DARK -> true
    }
    ParkEasyTheme(darkTheme = dark) {
        Surface(
            Modifier.fillMaxSize().backdrop(MaterialTheme.colorScheme.background, com.ejemplo.parkeasy.presentation.theme.LocalNeon.current),
            color = androidx.compose.ui.graphics.Color.Transparent,
            contentColor = MaterialTheme.colorScheme.onBackground,
        ) {
            var unlocked by remember { mutableStateOf(!settings.hasPin) }
            val profile = remember { com.ejemplo.parkeasy.data.db.AppProfile.current() }
            var onboarded by remember { mutableStateOf(settings.onboardingDone) }
            when {
                !unlocked -> LockScreen(settings) { unlocked = true }
                profile.isTestData -> DemoHost(profile)
                !onboarded -> OnboardingScreen(settings, onDone = { onboarded = true })
                else -> AppShell()
            }
        }
    }
}

/** Perfiles demo y perf: carga los datos de prueba si hace falta y deja un aviso permanente arriba. */
@Composable
private fun DemoHost(
    profile: com.ejemplo.parkeasy.data.db.AppProfile,
    sample: com.ejemplo.parkeasy.demo.SampleData = koinInject(),
    plan: com.ejemplo.parkeasy.presentation.plan.PlanViewModel = koinInject(),
) {
    var ready by remember { mutableStateOf(false) }
    var confirmReset by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    LaunchedEffect(Unit) {
        withContext(Dispatchers.IO) { sample.seedIfEmpty(profile) }
        plan.reload()
        ready = true
    }
    Column(Modifier.fillMaxSize()) {
        Surface(color = MaterialTheme.colorScheme.tertiaryContainer, contentColor = MaterialTheme.colorScheme.onTertiaryContainer) {
            Row(
                Modifier.padding(horizontal = 12.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("DATOS DE DEMOSTRACIÓN (TEST_DATA): nada de esto es real", style = MaterialTheme.typography.labelLarge)
                OutlinedButton(onClick = { confirmReset = true }, enabled = ready) { Text("Restablecer datos de demostración") }
            }
        }
        Box(Modifier.weight(1f)) { if (ready) AppShell() else Text("Cargando datos de prueba…", Modifier.padding(24.dp)) }
    }
    if (confirmReset) {
        com.ejemplo.parkeasy.presentation.common.ConfirmDialog(
            title = "Restablecer datos de demostración",
            text = "Se borran todos los datos de este perfil (pisos, planos, registros y bitácora) y se vuelven a crear los de ejemplo.",
            confirmLabel = "Restablecer",
            onConfirm = {
                confirmReset = false
                ready = false
                scope.launch {
                    withContext(Dispatchers.IO) { sample.reset(profile) }
                    plan.reload()
                    ready = true
                }
            },
            onDismiss = { confirmReset = false },
        )
    }
}

@Composable
private fun LockScreen(settings: AppSettings, onUnlock: () -> Unit) {
    var pin by remember { mutableStateOf("") }
    var wrong by remember { mutableStateOf(false) }
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(Modifier.widthIn(max = 360.dp).padding(24.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("PARKEASY", style = MaterialTheme.typography.headlineMedium)
            Text("Escribe tu PIN para entrar.")
            OutlinedTextField(
                pin, { pin = it.filter(Char::isDigit).take(8); wrong = false },
                label = { Text("PIN") }, singleLine = true, isError = wrong,
                visualTransformation = PasswordVisualTransformation(),
                supportingText = { if (wrong) Text("PIN incorrecto") },
            )
            Button(onClick = { if (settings.verifyPin(pin)) onUnlock() else { wrong = true; pin = "" } }, enabled = pin.length >= 4) {
                Text("Entrar")
            }
        }
    }
}

/**
 * Asistente de primer uso: nombre, primer piso con sus puestos y aviso de tarifas. Si la base ya
 * tiene pisos (una instalación anterior) no se muestra.
 */
@Composable
private fun OnboardingScreen(
    settings: AppSettings,
    onDone: () -> Unit,
    floors: FloorRepository = koinInject(),
    floorService: FloorService = koinInject(),
    spotService: SpotService = koinInject(),
) {
    LaunchedEffect(Unit) {
        if (withContext(Dispatchers.IO) { floors.getAll().isNotEmpty() }) {
            settings.markOnboardingDone()
            onDone()
        }
    }

    var step by remember { mutableStateOf(0) }
    var name by remember { mutableStateOf("") }
    var floorName by remember { mutableStateOf("Piso 1") }
    var count by remember { mutableStateOf("10") }
    var error by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()
    val countValue = count.toIntOrNull()

    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(Modifier.widthIn(max = 520.dp).padding(24.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Text("Bienvenido a PARKEASY", style = MaterialTheme.typography.headlineMedium)
            Text("Paso ${step + 1} de 3", style = MaterialTheme.typography.labelLarge)
            when (step) {
                0 -> {
                    Text("Empecemos por el nombre de tu parqueadero. Es opcional y lo puedes cambiar después.")
                    OutlinedTextField(name, { name = it }, label = { Text("Nombre del parqueadero") }, singleLine = true)
                }
                1 -> {
                    Text("Crea el primer piso y, si quieres, unos puestos de carro para empezar. Luego los acomodas en el editor del plano.")
                    OutlinedTextField(floorName, { floorName = it }, label = { Text("Nombre del piso") }, singleLine = true)
                    OutlinedTextField(
                        count, { count = it.filter(Char::isDigit) }, label = { Text("Puestos de carro (0 a 500)") }, singleLine = true,
                        isError = countValue == null || countValue !in 0..SpotService.MAX_BATCH,
                    )
                }
                else -> {
                    Text("Todo listo. Las tarifas vienen con valores de ejemplo (carro 4.000 + 1.500 por hora adicional, moto 2.500 + 1.000): revísalas en Ajustes antes de cobrar.")
                }
            }
            error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (step > 0 && step < 2) OutlinedButton(onClick = { step-- }) { Text("Atrás") }
                Button(
                    enabled = step != 1 || (floorName.isNotBlank() && countValue != null && countValue in 0..SpotService.MAX_BATCH),
                    onClick = {
                        when (step) {
                            0 -> { settings.businessName = name; step = 1 }
                            1 -> scope.launch {
                                try {
                                    withContext(Dispatchers.IO) {
                                        val id = floorService.add(floorName)
                                        if (countValue != null && countValue > 0) spotService.addMany(id, SpotType.CAR, "A-01", countValue)
                                    }
                                    error = null
                                    step = 2
                                } catch (e: DomainError) {
                                    error = e.message
                                }
                            }
                            else -> { settings.markOnboardingDone(); onDone() }
                        }
                    },
                ) { Text(if (step == 2) "Empezar" else "Siguiente") }
            }
        }
    }
}
