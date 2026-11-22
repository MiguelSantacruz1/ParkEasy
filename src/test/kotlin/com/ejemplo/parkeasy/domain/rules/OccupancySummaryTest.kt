package com.ejemplo.parkeasy.domain.rules

import com.ejemplo.parkeasy.domain.model.Spot
import com.ejemplo.parkeasy.domain.model.SpotShape
import com.ejemplo.parkeasy.domain.model.SpotType
import kotlin.test.Test
import kotlin.test.assertEquals

class OccupancySummaryTest {
    private fun spot(id: Long, floor: Long, blocked: Boolean = false, record: Long? = null) = Spot(
        id, floor, "S-$id", SpotType.CAR, SpotShape.RECTANGLE, 0.0, 0.0, 2.5, 5.0, 0.0,
        covered = false, blocked = blocked, blockReason = null, currentRecordId = record,
    )

    @Test
    fun `cuenta libres, ocupados y bloqueados`() {
        val s = OccupancySummary.of(listOf(spot(1, 1), spot(2, 1, record = 9), spot(3, 1, blocked = true), spot(4, 1)))

        assertEquals(OccupancySummary(total = 4, free = 2, occupied = 1, blocked = 1), s)
    }

    @Test
    fun `el porcentaje se calcula sobre los puestos no bloqueados`() {
        val s = OccupancySummary.of(listOf(spot(1, 1, record = 1), spot(2, 1, blocked = true), spot(3, 1), spot(4, 1, blocked = true)))

        assertEquals(2, s.usable)
        assertEquals(50.0, s.occupancyPercent)
    }

    @Test
    fun `sin puestos o con todos bloqueados el porcentaje es cero`() {
        assertEquals(0.0, OccupancySummary.of(emptyList()).occupancyPercent)
        assertEquals(0.0, OccupancySummary.of(listOf(spot(1, 1, blocked = true))).occupancyPercent)
    }

    @Test
    fun `resume por piso y en total`() {
        val spots = listOf(spot(1, 1, record = 1), spot(2, 1), spot(3, 2, record = 2), spot(4, 2, record = 3))

        val porPiso = OccupancySummary.byFloor(spots)

        assertEquals(50.0, porPiso.getValue(1).occupancyPercent)
        assertEquals(100.0, porPiso.getValue(2).occupancyPercent)
        assertEquals(75.0, OccupancySummary.of(spots).occupancyPercent)
    }
}
