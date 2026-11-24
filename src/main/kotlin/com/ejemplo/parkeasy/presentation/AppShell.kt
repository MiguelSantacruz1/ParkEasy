package com.ejemplo.parkeasy.presentation

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.foundation.focusable
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.isCtrlPressed
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.unit.dp
import com.ejemplo.parkeasy.presentation.common.MessageCenter
import com.ejemplo.parkeasy.presentation.floors.FloorsScreen
import com.ejemplo.parkeasy.presentation.navigation.Navigator
import com.ejemplo.parkeasy.presentation.navigation.Screen
import com.ejemplo.parkeasy.presentation.history.HistoryScreen
import com.ejemplo.parkeasy.presentation.plan.EditorScreen
import com.ejemplo.parkeasy.presentation.plan.HomeScreen
import com.ejemplo.parkeasy.presentation.records.CorrectDialog
import com.ejemplo.parkeasy.presentation.records.EntryDialog
import com.ejemplo.parkeasy.presentation.records.ExitDialog
import com.ejemplo.parkeasy.presentation.records.MoveDialog
import com.ejemplo.parkeasy.presentation.records.RecordDialog
import com.ejemplo.parkeasy.presentation.records.RecordsScreen
import com.ejemplo.parkeasy.presentation.records.RecordsViewModel
import com.ejemplo.parkeasy.presentation.settings.SettingsScreen
import com.ejemplo.parkeasy.presentation.spots.SpotsScreen
import com.ejemplo.parkeasy.resources.Res
import com.ejemplo.parkeasy.resources.screen_coming_soon
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.koinInject

/** Barra lateral, área de contenido, avisos y diálogos de registro. */
@Composable
fun AppShell(
    navigator: Navigator = koinInject(),
    messages: MessageCenter = koinInject(),
    records: RecordsViewModel = koinInject(),
) {
    val current by navigator.current.collectAsState()
    val message by messages.current.collectAsState()
    val dialog by records.dialog.collectAsState()
    val recordsState by records.state.collectAsState()
    val snackbar = remember { SnackbarHostState() }
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) { runCatching { focus.requestFocus() } }

    LaunchedEffect(message?.id) {
        val m = message ?: return@LaunchedEffect
        val result = snackbar.showSnackbar(m.text, m.actionLabel, duration = SnackbarDuration.Short)
        if (result == SnackbarResult.ActionPerformed) m.action?.invoke()
        messages.dismiss(m.id)
    }

    Scaffold(snackbarHost = { SnackbarHost(snackbar) }) { padding ->
        Row(
            modifier = Modifier.fillMaxSize().padding(padding).focusRequester(focus).focusable().onPreviewKeyEvent { e ->
                if (e.type != KeyEventType.KeyDown || !e.isCtrlPressed || dialog != null) return@onPreviewKeyEvent false
                when (e.key) {
                    Key.N -> records.openEntry()
                    Key.F -> navigator.navigateTo(Screen.HISTORY)
                    Key.E -> navigator.navigateTo(if (current == Screen.EDITOR) Screen.HOME else Screen.EDITOR)
                    Key.One -> navigator.navigateTo(Screen.HOME)
                    Key.Two -> navigator.navigateTo(Screen.EDITOR)
                    Key.Three -> navigator.navigateTo(Screen.HISTORY)
                    Key.Comma -> navigator.navigateTo(Screen.SETTINGS)
                    else -> return@onPreviewKeyEvent false
                }
                true
            },
        ) {
            NavigationRail {
                Screen.entries.forEach { screen ->
                    NavigationRailItem(
                        selected = screen == current,
                        onClick = { navigator.navigateTo(screen) },
                        icon = { com.ejemplo.parkeasy.presentation.navigation.ScreenIcon(screen) },
                        label = { Text(stringResource(screen.title)) },
                    )
                }
            }
            Box(modifier = Modifier.fillMaxSize()) {
                when (current) {
                    Screen.HOME -> HomeScreen()
                    Screen.EDITOR -> EditorScreen()
                    Screen.HISTORY -> HistoryScreen()
                    Screen.FLOORS -> FloorsScreen()
                    Screen.SPOTS -> SpotsScreen()
                    Screen.RECORDS -> RecordsScreen()
                    Screen.SETTINGS -> SettingsScreen()
                    Screen.ASSISTANT -> com.ejemplo.parkeasy.presentation.assistant.AssistantScreen()
                    else -> PlaceholderScreen(current)
                }
            }
        }
    }

    when (val d = dialog) {
        is RecordDialog.Entry -> EntryDialog(d.spotId, recordsState.freeSpots, onDismiss = records::closeDialog, onConfirm = records::checkIn)
        is RecordDialog.Exit -> ExitDialog(records, d.recordId, onDismiss = records::closeDialog)
        is RecordDialog.Correct -> CorrectDialog(records, d.recordId, onDismiss = records::closeDialog)
        is RecordDialog.Move -> MoveDialog(records, d.recordId, recordsState.freeSpots, onDismiss = records::closeDialog)
        null -> Unit
    }
}

@Composable
private fun PlaceholderScreen(screen: Screen) {
    Box(Modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Text(stringResource(screen.title), style = MaterialTheme.typography.headlineMedium)
            Text(stringResource(Res.string.screen_coming_soon))
        }
    }
}
