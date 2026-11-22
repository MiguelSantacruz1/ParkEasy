package com.ejemplo.parkeasy

import androidx.compose.material3.Text
import com.ejemplo.parkeasy.presentation.theme.ParkEasyTheme
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState
import java.awt.Dimension

fun main() = application {
    Window(
        onCloseRequest = ::exitApplication,
        title = "PARKEASY",
        state = rememberWindowState(size = DpSize(1280.dp, 800.dp)),
    ) {
        window.minimumSize = Dimension(960, 640)
        ParkEasyTheme {
            Text("PARKEASY")
        }
    }
}
