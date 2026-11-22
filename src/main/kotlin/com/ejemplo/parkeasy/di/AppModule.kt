package com.ejemplo.parkeasy.di

import app.cash.sqldelight.db.SqlDriver
import com.ejemplo.parkeasy.data.db.AppDirectories
import com.ejemplo.parkeasy.data.db.DatabaseFactory
import com.ejemplo.parkeasy.data.db.ParkEasyDatabase
import com.ejemplo.parkeasy.presentation.navigation.Navigator
import org.koin.dsl.module

/** Módulo de Koin de la capa de presentación. */
val presentationModule = module {
    single { Navigator() }
}

/** Módulo de Koin de la capa de datos: abre la base SQLite en la carpeta del usuario. */
val dataModule = module {
    single<SqlDriver> { DatabaseFactory.createDriver(AppDirectories.databaseFile()) }
    single<ParkEasyDatabase> { DatabaseFactory.create(get()) }
}

val appModules = listOf(presentationModule, dataModule)
