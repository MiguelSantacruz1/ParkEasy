package com.ejemplo.parkeasy.presentation

import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.runComposeUiTest
import app.cash.sqldelight.db.SqlDriver
import com.ejemplo.parkeasy.TestEnv
import com.ejemplo.parkeasy.data.db.ParkEasyDatabase
import com.ejemplo.parkeasy.di.domainModule
import com.ejemplo.parkeasy.di.presentationModule
import com.ejemplo.parkeasy.di.repositoryModule
import org.koin.compose.KoinApplication
import org.koin.core.context.stopKoin
import org.koin.dsl.module
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals

/** Primer uso y pantalla de PIN, con la base real en memoria. */
@OptIn(ExperimentalTestApi::class)
class AppRootUiTest {

    @AfterTest
    fun stopGlobalKoin() = stopKoin()

    private fun ComposeUiTest.waitForText(text: String) =
        waitUntil(timeoutMillis = 40_000) { onAllNodesWithText(text).fetchSemanticsNodes().isNotEmpty() }

    private fun ComposeUiTest.show(env: TestEnv) = setContent {
        val data = module {
            single<SqlDriver> { env.driver }
            single<ParkEasyDatabase> { env.db }
        }
        KoinApplication(application = { modules(presentationModule, repositoryModule, domainModule, data) }) { AppRoot() }
    }

    @Test
    fun `una instalación nueva pasa por el asistente y crea el piso y los puestos`() = runComposeUiTest {
        val env = TestEnv()
        try {
            show(env)
            waitForText("Bienvenido a PARKEASY")
            onNodeWithText("Nombre del parqueadero").performTextInput("Central")
            onNodeWithText("Siguiente").performClick()
            waitForText("Nombre del piso")
            onNodeWithText("Siguiente").performClick()
            waitForText("Todo listo. Las tarifas vienen con valores de ejemplo (carro 4.000 + 1.500 por hora adicional, moto 2.500 + 1.000): revísalas en Ajustes antes de cobrar.")
            onNodeWithText("Empezar").performClick()
            waitForText("Registrar entrada")

            assertEquals(1, env.floors.getAll().size)
            assertEquals(10, env.spots.getAll().size)
        } finally {
            env.close()
        }
    }

    @Test
    fun `una base que ya tiene pisos salta el asistente`() = runComposeUiTest {
        val env = TestEnv()
        try {
            env.floorService.add("Piso 1")
            show(env)
            waitForText("Registrar entrada")
        } finally {
            env.close()
        }
    }

    @Test
    fun `con PIN pide la clave y rechaza una equivocada`() = runComposeUiTest {
        val env = TestEnv()
        try {
            env.floorService.add("Piso 1")
            com.ejemplo.parkeasy.domain.service.AppSettings(com.ejemplo.parkeasy.data.repository.SqlSettingRepository(env.db)).setPin("2468")
            show(env)
            waitForText("Escribe tu PIN para entrar.")

            onNodeWithText("PIN").performTextInput("1111")
            onNodeWithText("Entrar").performClick()
            waitForText("PIN incorrecto")

            onNodeWithText("PIN").performTextInput("2468")
            onNodeWithText("Entrar").performClick()
            waitForText("Registrar entrada")
        } finally {
            env.close()
        }
    }
}
