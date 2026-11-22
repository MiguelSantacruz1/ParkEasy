package com.ejemplo.parkeasy.presentation.common

import com.ejemplo.parkeasy.domain.DomainError
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Base de los estados de pantalla. Las operaciones corren fuera del hilo de la interfaz y
 * cualquier error de regla se convierte en un aviso en español.
 */
abstract class BaseViewModel(private val messages: MessageCenter) {
    protected val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    /** Cancela las corrutinas del estado. Las pruebas lo llaman al terminar; la app vive tanto como la ventana. */
    fun dispose() = scope.cancel()

    /** Ejecuta [block]; si termina bien avisa con [success] y llama a [onSuccess]. */
    protected fun launchSafe(
        success: String? = null,
        onSuccess: () -> Unit = {},
        block: () -> Unit,
    ) {
        scope.launch {
            try {
                withContext(Dispatchers.IO) { block() }
                success?.let { messages.info(it) }
                onSuccess()
            } catch (e: DomainError) {
                messages.error(e.message ?: "No se pudo completar la operación")
            } catch (e: Exception) {
                messages.error("Ocurrió un error inesperado: ${e.message}")
            }
        }
    }
}
