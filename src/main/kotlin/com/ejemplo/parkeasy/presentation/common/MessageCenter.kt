package com.ejemplo.parkeasy.presentation.common

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** Aviso breve para el usuario. [action] permite ofrecer "Deshacer". */
data class UiMessage(
    val text: String,
    val isError: Boolean = false,
    val actionLabel: String? = null,
    val action: (() -> Unit)? = null,
    val id: Long = System.nanoTime(),
)

/** Cola de avisos que la barra inferior de la ventana muestra uno a uno. */
class MessageCenter {
    private val _current = MutableStateFlow<UiMessage?>(null)
    val current: StateFlow<UiMessage?> = _current.asStateFlow()

    fun info(text: String, actionLabel: String? = null, action: (() -> Unit)? = null) {
        _current.value = UiMessage(text, false, actionLabel, action)
    }

    fun error(text: String) {
        _current.value = UiMessage(text, isError = true)
    }

    fun dismiss(id: Long) {
        if (_current.value?.id == id) _current.value = null
    }
}
