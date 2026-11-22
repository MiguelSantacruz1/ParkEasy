package com.ejemplo.parkeasy.domain.model

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class SpotModelTest {
    private fun spot(blocked: Boolean = false, recordId: Long? = null) = Spot(
        id = 1, floorId = 1, label = "A-01", type = SpotType.CAR, shape = SpotShape.RECTANGLE,
        x = 0.0, y = 0.0, width = 2.5, height = 5.0, rotation = 0.0, covered = false,
        blocked = blocked, blockReason = null, currentRecordId = recordId,
    )

    @Test
    fun `sin bloqueo ni registro abierto esta libre`() {
        assertEquals(SpotStatus.FREE, spot().status)
    }

    @Test
    fun `con registro abierto esta ocupado`() {
        assertEquals(SpotStatus.OCCUPIED, spot(recordId = 7).status)
    }

    @Test
    fun `bloqueado cuenta como bloqueado`() {
        assertEquals(SpotStatus.BLOCKED, spot(blocked = true).status)
    }

    @Test
    fun `un carro cabe en puestos de carro, discapacitados y otros`() {
        assertTrue(SpotType.CAR.accepts(VehicleType.CAR))
        assertTrue(SpotType.DISABLED.accepts(VehicleType.CAR))
        assertTrue(SpotType.OTHER.accepts(VehicleType.CAR))
        assertFalse(SpotType.MOTORCYCLE.accepts(VehicleType.CAR))
    }

    @Test
    fun `una moto cabe en puestos de moto y otros`() {
        assertTrue(SpotType.MOTORCYCLE.accepts(VehicleType.MOTORCYCLE))
        assertTrue(SpotType.OTHER.accepts(VehicleType.MOTORCYCLE))
        assertFalse(SpotType.CAR.accepts(VehicleType.MOTORCYCLE))
        assertFalse(SpotType.DISABLED.accepts(VehicleType.MOTORCYCLE))
    }
}
