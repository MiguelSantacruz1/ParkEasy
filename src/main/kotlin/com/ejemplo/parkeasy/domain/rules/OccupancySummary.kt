package com.ejemplo.parkeasy.domain.rules

import com.ejemplo.parkeasy.domain.model.Spot
import com.ejemplo.parkeasy.domain.model.SpotStatus

/** Conteos de puestos. El porcentaje de ocupación se calcula sobre los puestos no bloqueados. */
data class OccupancySummary(
    val total: Int,
    val free: Int,
    val occupied: Int,
    val blocked: Int,
) {
    val usable: Int get() = total - blocked

    /** Entre 0 y 100. Sin puestos utilizables es 0. */
    val occupancyPercent: Double
        get() = if (usable == 0) 0.0 else occupied * 100.0 / usable

    companion object {
        val EMPTY = OccupancySummary(0, 0, 0, 0)

        fun of(spots: Collection<Spot>): OccupancySummary {
            var free = 0
            var occupied = 0
            var blocked = 0
            spots.forEach {
                when (it.status) {
                    SpotStatus.FREE -> free++
                    SpotStatus.OCCUPIED -> occupied++
                    SpotStatus.BLOCKED -> blocked++
                }
            }
            return OccupancySummary(spots.size, free, occupied, blocked)
        }

        fun byFloor(spots: Collection<Spot>): Map<Long, OccupancySummary> =
            spots.groupBy { it.floorId }.mapValues { of(it.value) }
    }
}
