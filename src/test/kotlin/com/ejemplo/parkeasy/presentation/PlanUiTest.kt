package com.ejemplo.parkeasy.presentation

import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.runComposeUiTest
import app.cash.sqldelight.db.SqlDriver
import com.ejemplo.parkeasy.TestEnv
import com.ejemplo.parkeasy.data.db.ParkEasyDatabase
import com.ejemplo.parkeasy.di.domainModule
import com.ejemplo.parkeasy.di.presentationModule
import com.ejemplo.parkeasy.di.repositoryModule
import com.ejemplo.parkeasy.domain.model.SpotType
import com.ejemplo.parkeasy.plan.model.Box
import com.ejemplo.parkeasy.plan.model.ItemType
import com.ejemplo.parkeasy.plan.model.PlanItem
import com.ejemplo.parkeasy.presentation.theme.ParkEasyTheme
import org.koin.compose.KoinApplication
import org.koin.core.context.stopKoin
import kotlin.test.AfterTest
import org.koin.dsl.module
import kotlin.test.Test

/** Dibuja el plano operativo y el editor con datos reales para comprobar que el lienzo no falla. */
@OptIn(ExperimentalTestApi::class)
class PlanUiTest {

    /** Cada prueba arranca su propio Koin; si el anterior sigue vivo, la composición se queda esperando. */
    @AfterTest
    fun stopGlobalKoin() = stopKoin()

    private fun ComposeUiTest.waitForText(text: String) =
        waitUntil(timeoutMillis = 40_000) { onAllNodesWithText(text).fetchSemanticsNodes().isNotEmpty() }

    @Test
    fun `inicio muestra el plano del piso y el editor sus paneles`() = runComposeUiTest {
        val env = TestEnv()
        try {
            val floorId = env.floorService.add("Piso 1")
            val doc = env.planPersistence.load(floorId)!!
            val spots = (0 until 6).map {
                PlanItem(
                    id = "n$it", type = ItemType.SPOT, geometry = Box(1.0 + it * 3.0, 2.0, 2.5, 5.0),
                    label = "A-0${it + 1}", spotType = SpotType.CAR,
                )
            }
            env.planPersistence.save(doc.copy(items = spots))
            env.checkIn(env.spots.getAll().first().id, plate = "ABC123")

            val testData = module {
                single<SqlDriver> { env.driver }
                single<ParkEasyDatabase> { env.db }
            }
            setContent {
                KoinApplication(application = { modules(presentationModule, repositoryModule, domainModule, testData) }) {
                    ParkEasyTheme { AppShell() }
                }
            }

            waitForText("Registrar entrada")
            waitForText("Piso 1")
            waitForText("○ Libre")
            waitForText("Parqueadero")

            onAllNodesWithText("Editor del plano").onFirst().performClick()
            waitForText("Elementos")
            waitForText("Propiedades")
            waitForText("✓ Guardado")
        } finally {
            env.close()
        }
    }
}
