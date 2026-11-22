package com.ejemplo.parkeasy.di

import com.ejemplo.parkeasy.presentation.navigation.Navigator
import org.koin.dsl.module

/** Módulo de Koin de la capa de presentación. */
val presentationModule = module {
    single { Navigator() }
}

val appModules = listOf(presentationModule)
