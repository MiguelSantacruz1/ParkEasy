package com.ejemplo.parkeasy.plan.ops

/**
 * Historial de deshacer y rehacer. Guarda estados completos (el plano es inmutable), con un
 * límite de [limit] pasos; el requisito es de al menos 50.
 */
class History<T>(private val limit: Int = 100) {
    private val past = ArrayDeque<T>()
    private val future = ArrayDeque<T>()

    val canUndo: Boolean get() = past.isNotEmpty()
    val canRedo: Boolean get() = future.isNotEmpty()
    val undoSteps: Int get() = past.size

    /** Guarda [previous] antes de aplicar un cambio. Un cambio nuevo borra lo que se podía rehacer. */
    fun record(previous: T) {
        past.addLast(previous)
        while (past.size > limit) past.removeFirst()
        future.clear()
    }

    /** Devuelve el estado anterior, o null si no hay. [current] pasa a poder rehacerse. */
    fun undo(current: T): T? {
        val previous = past.removeLastOrNull() ?: return null
        future.addLast(current)
        return previous
    }

    fun redo(current: T): T? {
        val next = future.removeLastOrNull() ?: return null
        past.addLast(current)
        return next
    }

    fun clear() {
        past.clear()
        future.clear()
    }
}
