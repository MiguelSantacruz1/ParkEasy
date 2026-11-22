package com.ejemplo.parkeasy.domain.model

/** Piso del parqueadero. El lienzo del plano se mide en metros. */
data class Floor(
    val id: Long,
    val name: String,
    val sortOrder: Int,
    val canvasWidth: Double,
    val canvasHeight: Double,
)

data class NewFloor(
    val name: String,
    val sortOrder: Int,
    val canvasWidth: Double = DEFAULT_CANVAS_WIDTH,
    val canvasHeight: Double = DEFAULT_CANVAS_HEIGHT,
) {
    companion object {
        const val DEFAULT_CANVAS_WIDTH = 60.0
        const val DEFAULT_CANVAS_HEIGHT = 40.0
    }
}
