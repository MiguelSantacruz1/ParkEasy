package com.ejemplo.parkeasy.domain.model

/**
 * Puesto de parqueo. Las medidas del plano están en metros y la rotación en grados.
 * [currentRecordId] es el registro abierto (sin hora de salida), si lo hay.
 */
data class Spot(
    val id: Long,
    val floorId: Long,
    val label: String,
    val type: SpotType,
    val shape: SpotShape,
    val x: Double,
    val y: Double,
    val width: Double,
    val height: Double,
    val rotation: Double,
    val covered: Boolean,
    val blocked: Boolean,
    val blockReason: String?,
    val currentRecordId: Long?,
) {
    /** Un puesto bloqueado nunca está ocupado: las reglas lo impiden. */
    val status: SpotStatus
        get() = when {
            blocked -> SpotStatus.BLOCKED
            currentRecordId != null -> SpotStatus.OCCUPIED
            else -> SpotStatus.FREE
        }
}

data class NewSpot(
    val floorId: Long,
    val label: String,
    val type: SpotType,
    val shape: SpotShape = SpotShape.RECTANGLE,
    val x: Double = 0.0,
    val y: Double = 0.0,
    val width: Double = DEFAULT_WIDTH,
    val height: Double = DEFAULT_HEIGHT,
    val rotation: Double = 0.0,
    val covered: Boolean = false,
) {
    companion object {
        /** Un puesto de carro mide unos 2,5 × 5 m. */
        const val DEFAULT_WIDTH = 2.5
        const val DEFAULT_HEIGHT = 5.0
    }
}
