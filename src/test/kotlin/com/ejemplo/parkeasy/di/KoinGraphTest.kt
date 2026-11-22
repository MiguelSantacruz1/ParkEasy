package com.ejemplo.parkeasy.di

import app.cash.sqldelight.db.SqlDriver
import com.ejemplo.parkeasy.data.db.DatabaseFactory
import com.ejemplo.parkeasy.data.db.ParkEasyDatabase
import com.ejemplo.parkeasy.domain.service.FloorService
import com.ejemplo.parkeasy.domain.service.ParkingService
import com.ejemplo.parkeasy.domain.service.SpotService
import org.koin.core.context.startKoin
import org.koin.core.context.stopKoin
import org.koin.dsl.module
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

class KoinGraphTest {

    /** Cambia la base de la carpeta del usuario por una en memoria y arma todo el grafo. */
    @Test
    fun `el grafo de dependencias se resuelve completo`() {
        val driver = DatabaseFactory.createInMemoryDriver()
        val testData = module {
            single<SqlDriver> { driver }
            single<ParkEasyDatabase> { DatabaseFactory.create(get()) }
        }
        val koin = startKoin { modules(presentationModule, repositoryModule, domainModule, testData) }.koin
        try {
            assertNotNull(koin.get<FloorService>())
            assertNotNull(koin.get<SpotService>())
            val parking = koin.get<ParkingService>()
            assertNotNull(parking)
            assertEquals(2, koin.get<com.ejemplo.parkeasy.domain.repository.TariffRepository>().getAll().size)
        } finally {
            stopKoin()
            driver.close()
        }
    }
}
