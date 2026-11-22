package com.ejemplo.parkeasy.presentation

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.ejemplo.parkeasy.presentation.navigation.Navigator
import com.ejemplo.parkeasy.presentation.navigation.Screen
import com.ejemplo.parkeasy.resources.Res
import com.ejemplo.parkeasy.resources.screen_coming_soon
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.koinInject

/** Barra lateral de navegación y área de contenido. */
@Composable
fun AppShell(navigator: Navigator = koinInject()) {
    val current by navigator.current.collectAsState()

    Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Row(modifier = Modifier.fillMaxSize()) {
            NavigationRail {
                Screen.entries.forEach { screen ->
                    NavigationRailItem(
                        selected = screen == current,
                        onClick = { navigator.navigateTo(screen) },
                        icon = { Text(stringResource(screen.title).take(1)) },
                        label = { Text(stringResource(screen.title)) },
                    )
                }
            }
            Box(
                modifier = Modifier.fillMaxSize().padding(24.dp),
                contentAlignment = Alignment.Center,
            ) {
                PlaceholderScreen(current)
            }
        }
    }
}

@Composable
private fun PlaceholderScreen(screen: Screen) {
    androidx.compose.foundation.layout.Column(
        verticalArrangement = Arrangement.spacedBy(8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(stringResource(screen.title), style = MaterialTheme.typography.headlineMedium)
        Text(stringResource(Res.string.screen_coming_soon))
    }
}
