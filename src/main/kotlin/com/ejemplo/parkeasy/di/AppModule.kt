package com.ejemplo.parkeasy.di

import app.cash.sqldelight.db.SqlDriver
import com.ejemplo.parkeasy.data.db.AppDirectories
import com.ejemplo.parkeasy.data.db.DatabaseFactory
import com.ejemplo.parkeasy.data.db.ParkEasyDatabase
import com.ejemplo.parkeasy.data.repository.SqlChangeLogRepository
import com.ejemplo.parkeasy.data.repository.SqlFloorRepository
import com.ejemplo.parkeasy.data.repository.SqlParkingRecordRepository
import com.ejemplo.parkeasy.data.repository.SqlSpotRepository
import com.ejemplo.parkeasy.data.repository.SqlTariffRepository
import com.ejemplo.parkeasy.data.repository.SqlTransactor
import com.ejemplo.parkeasy.domain.repository.ChangeLogRepository
import com.ejemplo.parkeasy.domain.repository.FloorRepository
import com.ejemplo.parkeasy.domain.repository.ParkingRecordRepository
import com.ejemplo.parkeasy.domain.repository.SpotRepository
import com.ejemplo.parkeasy.domain.repository.TariffRepository
import com.ejemplo.parkeasy.domain.repository.Transactor
import com.ejemplo.parkeasy.domain.service.ChangeLogger
import com.ejemplo.parkeasy.domain.service.FloorService
import com.ejemplo.parkeasy.domain.service.ParkingService
import com.ejemplo.parkeasy.domain.service.SpotService
import com.ejemplo.parkeasy.domain.service.TariffService
import com.ejemplo.parkeasy.presentation.common.MessageCenter
import com.ejemplo.parkeasy.presentation.floors.FloorsViewModel
import com.ejemplo.parkeasy.presentation.navigation.Navigator
import com.ejemplo.parkeasy.presentation.records.RecordsViewModel
import com.ejemplo.parkeasy.presentation.settings.SettingsViewModel
import com.ejemplo.parkeasy.presentation.spots.SpotsViewModel
import org.koin.dsl.module
import java.time.Clock
import java.time.ZoneId

/** Módulo de Koin de la capa de presentación. */
val presentationModule = module {
    single { Navigator() }
    single { MessageCenter() }
    single { FloorsViewModel(get(), get(), get(), get()) }
    single { SpotsViewModel(get(), get(), get(), get(), get()) }
    single { RecordsViewModel(get(), get(), get(), get(), get()) }
    single { SettingsViewModel(get(), get(), get()) }
}

/** Módulo de Koin de la capa de datos: abre la base SQLite en la carpeta del usuario. */
val dataModule = module {
    single<SqlDriver> { DatabaseFactory.createDriver(AppDirectories.databaseFile()) }
    single<ParkEasyDatabase>(createdAtStart = true) { DatabaseFactory.create(get()) }
}

/** Repositorios: cada interfaz del dominio tiene su implementación SQLite. */
val repositoryModule = module {
    single<FloorRepository> { SqlFloorRepository(get()) }
    single<SpotRepository> { SqlSpotRepository(get()) }
    single<ParkingRecordRepository> { SqlParkingRecordRepository(get()) }
    single<ChangeLogRepository> { SqlChangeLogRepository(get()) }
    single<TariffRepository> { SqlTariffRepository(get()) }
    single<Transactor> { SqlTransactor(get()) }
}

/** Servicios del dominio con las reglas de integridad. */
val domainModule = module {
    single<Clock> { Clock.system(ZoneId.of("America/Bogota")) }
    single { ChangeLogger(get(), get()) }
    single { FloorService(get(), get(), get(), get()) }
    single { SpotService(get(), get(), get(), get()) }
    single { ParkingService(get(), get(), get(), get(), get(), get(), get()) }
    single { TariffService(get(), get(), get()) }
}

val appModules = listOf(presentationModule, dataModule, repositoryModule, domainModule)
