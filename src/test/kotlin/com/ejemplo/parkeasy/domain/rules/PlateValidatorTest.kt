package com.ejemplo.parkeasy.domain.rules

import com.ejemplo.parkeasy.domain.model.VehicleType
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class PlateValidatorTest {

    @Test
    fun `acepta placas de carro`() {
        assertTrue(PlateValidator.isValid("ABC123", VehicleType.CAR))
        assertTrue(PlateValidator.isValid("abc 123", VehicleType.CAR))
        assertTrue(PlateValidator.isValid("xyz-987", VehicleType.CAR))
    }

    @Test
    fun `acepta placas de moto`() {
        assertTrue(PlateValidator.isValid("ABC12D", VehicleType.MOTORCYCLE))
        assertTrue(PlateValidator.isValid("xyz98a", VehicleType.MOTORCYCLE))
    }

    @Test
    fun `una placa de moto no es de carro y al reves`() {
        assertFalse(PlateValidator.isValid("ABC12D", VehicleType.CAR))
        assertFalse(PlateValidator.isValid("ABC123", VehicleType.MOTORCYCLE))
    }

    @Test
    fun `rechaza formatos invalidos`() {
        listOf("", "ABC", "AB1234", "ABCD12", "123ABC", "ABC1234", "ABC12", "ÁBC123").forEach {
            assertFalse(PlateValidator.isValidForAnyType(it), "no debería ser válida: '$it'")
        }
    }

    @Test
    fun `normaliza mayusculas, espacios y guiones`() {
        assertEquals("ABC123", PlateValidator.normalize(" abc-123 "))
    }
}
