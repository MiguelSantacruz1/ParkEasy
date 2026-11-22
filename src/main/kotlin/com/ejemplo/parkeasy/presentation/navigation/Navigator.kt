package com.ejemplo.parkeasy.presentation.navigation

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Navegación de primer nivel. La app solo tiene siete pantallas sin pila de
 * retroceso, así que un estado observable basta (ver docs/DECISIONES.md).
 */
class Navigator(initial: Screen = Screen.HOME) {
    private val _current = MutableStateFlow(initial)
    val current: StateFlow<Screen> = _current.asStateFlow()

    fun navigateTo(screen: Screen) {
        _current.value = screen
    }
}
