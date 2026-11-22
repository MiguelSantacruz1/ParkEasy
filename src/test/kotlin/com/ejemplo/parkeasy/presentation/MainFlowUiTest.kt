package com.ejemplo.parkeasy.presentation

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onLast
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.runComposeUiTest
import app.cash.sqldelight.db.SqlDriver
import com.ejemplo.parkeasy.data.db.DatabaseFactory
import com.ejemplo.parkeasy.data.db.ParkEasyDatabase
import com.ejemplo.parkeasy.di.domainModule
import com.ejemplo.parkeasy.di.presentationModule
import com.ejemplo.parkeasy.di.repositoryModule
import com.ejemplo.parkeasy.presentation.theme.ParkEasyTheme
import org.koin.compose.KoinApplication
import org.koin.dsl.module
import kotlin.test.Test

/** Recorre en la interfaz real el flujo: piso → puestos → entrada → salida con cobro → bloqueo. */
@OptIn(ExperimentalTestApi::class)
class MainFlowUiTest {

    private fun ComposeUiTest.waitForText(text: String) =
        waitUntil(timeoutMillis = 10_000) { onAllNodesWithText(text).fetchSemanticsNodes().isNotEmpty() }

    private fun ComposeUiTest.waitForNoText(text: String) =
        waitUntil(timeoutMillis = 10_000) { onAllNodesWithText(text).fetchSemanticsNodes().isEmpty() }

    @Test
    fun `flujo principal desde un piso nuevo hasta el cobro y el bloqueo`() = runComposeUiTest {
        val driver = DatabaseFactory.createInMemoryDriver()
        val testData = module {
            single<SqlDriver> { driver }
            single<ParkEasyDatabase> { DatabaseFactory.create(get()) }
        }
        setContent {
            KoinApplication(application = { modules(presentationModule, repositoryModule, domainModule, testData) }) {
                ParkEasyTheme { AppShell() }
            }
        }

        // 1. Crear un piso
        onAllNodesWithText("Pisos").onFirst().performClick()
        waitForText("Todavía no hay pisos. Agrega el primero para empezar.")
        onNodeWithText("Agregar piso").performClick()
        onNodeWithText("Nombre (por ejemplo Sótano 1)").performTextInput("Piso 1")
        onNodeWithText("Agregar").performClick()
        waitForText("0 puestos · 0 libres · 0 ocupados · 0 bloqueados · 0 % de ocupación")

        // 2. Agregar 10 puestos de carro desde A-01
        onAllNodesWithText("Puestos").onFirst().performClick()
        waitForText("Todavía no hay puestos. Usa «Agregar puestos» para crear varios de una vez.")
        onNodeWithText("Agregar puestos").performClick()
        onNodeWithText("Agregar").performClick()
        // La lista es perezosa: solo se dibujan las filas que caben en la ventana de prueba.
        waitForText("A-01")

        // 3. Marcar ocupado el primer puesto
        onAllNodesWithText("Marcar ocupado").onFirst().performClick()
        onNodeWithText("Placa (ABC123 o ABC12D)").performTextInput("abc123")
        onNodeWithText("Color: —").performClick()
        onNodeWithText("Rojo").performClick()
        onAllNodesWithText("Registrar entrada").onLast().performClick()
        waitForText("ABC123")
        waitForText("Marcar libre")

        // 4. Registrar la salida: el total mínimo de un carro es la primera hora
        onNodeWithText("Marcar libre").performClick()
        waitForText("Total a cobrar: \$ 4.000")
        onNodeWithText("Confirmar salida").performClick()
        waitForNoText("Marcar libre")

        // 5. Bloquear un puesto libre
        onAllNodesWithText("Bloquear").onFirst().performClick()
        onNodeWithText("Motivo (mantenimiento, obstruido, reservado…)").performTextInput("Mantenimiento")
        onAllNodesWithText("Bloquear").onLast().performClick()
        waitForText("Desbloquear")
    }
}
