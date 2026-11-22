package com.ejemplo.parkeasy.presentation.navigation

import com.ejemplo.parkeasy.resources.Res
import com.ejemplo.parkeasy.resources.nav_editor
import com.ejemplo.parkeasy.resources.nav_floors
import com.ejemplo.parkeasy.resources.nav_history
import com.ejemplo.parkeasy.resources.nav_home
import com.ejemplo.parkeasy.resources.nav_records
import com.ejemplo.parkeasy.resources.nav_settings
import com.ejemplo.parkeasy.resources.nav_spots
import org.jetbrains.compose.resources.StringResource

/** Pantallas de primer nivel, en el orden de la barra lateral. */
enum class Screen(val title: StringResource) {
    HOME(Res.string.nav_home),
    EDITOR(Res.string.nav_editor),
    SPOTS(Res.string.nav_spots),
    RECORDS(Res.string.nav_records),
    HISTORY(Res.string.nav_history),
    FLOORS(Res.string.nav_floors),
    SETTINGS(Res.string.nav_settings),
}
