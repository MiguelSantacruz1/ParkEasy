package com.ejemplo.parkeasy.domain.rules

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class SpotLabelGeneratorTest {

    @Test
    fun `sigue la numeracion conservando prefijo y ancho`() {
        assertEquals(listOf("A-01", "A-02", "A-03"), SpotLabelGenerator.generate("A-01", 3))
        assertEquals(listOf("P7", "P8"), SpotLabelGenerator.generate("P7", 2))
    }

    @Test
    fun `el ancho crece cuando el numero lo necesita`() {
        assertEquals(listOf("A-09", "A-10", "A-11"), SpotLabelGenerator.generate("A-09", 3))
        assertEquals(listOf("B-100", "B-101"), SpotLabelGenerator.generate("B-100", 2))
    }

    @Test
    fun `una etiqueta sin numero se usa como prefijo`() {
        assertEquals(listOf("Moto-01", "Moto-02"), SpotLabelGenerator.generate("Moto", 2))
    }

    @Test
    fun `salta las etiquetas que ya existen`() {
        assertEquals(listOf("A-03", "A-05"), SpotLabelGenerator.generate("A-01", 2, taken = setOf("A-01", "A-02", "A-04")))
    }

    @Test
    fun `cantidad cero da lista vacia y valores invalidos fallan`() {
        assertEquals(emptyList(), SpotLabelGenerator.generate("A-01", 0))
        assertFailsWith<IllegalArgumentException> { SpotLabelGenerator.generate("  ", 1) }
        assertFailsWith<IllegalArgumentException> { SpotLabelGenerator.generate("A-01", -1) }
    }
}
