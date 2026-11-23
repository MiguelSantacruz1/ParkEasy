package com.ejemplo.parkeasy.presentation.assistant

import com.ejemplo.parkeasy.domain.ai.AiException
import com.ejemplo.parkeasy.domain.ai.AiSettings
import com.ejemplo.parkeasy.domain.ai.AssistantService
import com.ejemplo.parkeasy.domain.ai.ChatTurn
import com.ejemplo.parkeasy.presentation.common.BaseViewModel
import com.ejemplo.parkeasy.presentation.common.MessageCenter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class AssistantState(
    val turns: List<ChatTurn> = emptyList(),
    val waiting: Boolean = false,
    val enabled: Boolean = false,
)

class AssistantViewModel(
    private val assistant: AssistantService,
    private val settings: AiSettings,
    messages: MessageCenter,
) : BaseViewModel(messages) {
    private val _state = MutableStateFlow(AssistantState(enabled = settings.enabled))
    val state: StateFlow<AssistantState> = _state.asStateFlow()

    /** Se llama al abrir la pantalla: la clave pudo cambiar en Ajustes. */
    fun refresh() = _state.update { it.copy(enabled = settings.enabled) }

    fun clear() = _state.update { it.copy(turns = emptyList()) }

    fun ask(question: String) {
        val q = question.trim()
        if (q.isEmpty() || _state.value.waiting) return
        _state.update { it.copy(turns = it.turns + ChatTurn(true, q), waiting = true) }
        scope.launch {
            val answer = try {
                withContext(Dispatchers.IO) { assistant.ask(_state.value.turns) }
            } catch (e: AiException) {
                "⚠ ${e.message}"
            } catch (e: Exception) {
                "⚠ No se pudo consultar al asistente: ${e.message}"
            }
            _state.update { it.copy(turns = it.turns + ChatTurn(false, answer), waiting = false) }
        }
    }
}
