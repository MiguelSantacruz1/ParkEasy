package com.ejemplo.parkeasy.plan.model

import com.ejemplo.parkeasy.domain.model.SpotType
import kotlinx.serialization.Serializable

/** Tipos de elemento del plano. [layer] fija el orden de dibujo: las zonas techadas van debajo de los puestos. */
enum class ItemType(val layer: Int) {
    COVERED_ZONE(0),
    ROAD(1),
    RAMP(2),
    WALL(3),
    COLUMN(3),
    STAIRS_ELEVATOR(3),
    SPOT(4),
    ENTRANCE(5),
    EXIT(5),
    TEXT(5),
}

/**
 * Elemento del plano: un puesto o cualquier otro objeto dibujado.
 * Los puestos se guardan en la tabla Spot (por [spotId]); el resto, como JSON en PlanElement.
 */
@Serializable
data class PlanItem(
    val id: String,
    val type: ItemType,
    val geometry: Geometry,
    val rotation: Double = 0.0,
    val label: String = "",
    val zOrder: Int = 0,
    val locked: Boolean = false,
    /** Ancho en metros de vías, paredes y rampas dibujadas como línea, polilínea o curva. */
    val strokeWidth: Double = 0.0,
    /** Rampas: true sube, false baja, null sin indicar. */
    val rampUp: Boolean? = null,
    /** Rampas: piso al que conecta. */
    val connectsToFloorId: Long? = null,
    /** Columnas: redonda o cuadrada. */
    val round: Boolean = false,
    /** Puestos: id en la base, o null si todavía no se ha guardado. */
    val spotId: Long? = null,
    val spotType: SpotType? = null,
    /** Puestos: se calcula según las zonas techadas. */
    val covered: Boolean = false,
) {
    val isSpot: Boolean get() = type == ItemType.SPOT
}

/** Plano de un piso: lienzo en metros y sus elementos. */
data class PlanDocument(
    val floorId: Long,
    val width: Double,
    val height: Double,
    val items: List<PlanItem> = emptyList(),
) {
    fun item(id: String): PlanItem? = items.firstOrNull { it.id == id }

    /** Orden de dibujo: primero la capa más baja y, dentro de ella, el apilado. */
    fun renderOrder(): List<PlanItem> = items.sortedWith(compareBy({ it.type.layer }, { it.zOrder }))

    fun replace(updated: Collection<PlanItem>): PlanDocument {
        val byId = updated.associateBy { it.id }
        return copy(items = items.map { byId[it.id] ?: it })
    }

    fun map(ids: Set<String>, transform: (PlanItem) -> PlanItem): PlanDocument =
        copy(items = items.map { if (it.id in ids) transform(it) else it })
}
