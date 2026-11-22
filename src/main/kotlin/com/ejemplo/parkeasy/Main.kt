package com.ejemplo.parkeasy

import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState
import com.ejemplo.parkeasy.di.appModules
import com.ejemplo.parkeasy.presentation.AppRoot
import org.koin.compose.KoinApplication
import java.awt.Dimension

fun main() = application {
    Window(
        onCloseRequest = ::exitApplication,
        title = "PARKEASY",
        state = rememberWindowState(size = DpSize(1280.dp, 800.dp)),
    ) {
        window.minimumSize = Dimension(960, 640)
        KoinApplication(application = { modules(appModules) }) {
            AppRoot()
        }
    }
}
