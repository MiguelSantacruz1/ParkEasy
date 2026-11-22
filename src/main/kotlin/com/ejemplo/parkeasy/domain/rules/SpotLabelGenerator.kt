package com.ejemplo.parkeasy.domain.rules

/** Numeración consecutiva de etiquetas: "A-01", "A-02", … */
object SpotLabelGenerator {
    private val trailingNumber = Regex("^(.*?)(\\d+)$")

    /**
     * Genera [count] etiquetas a partir de [start]. Conserva el prefijo y el ancho del número
     * ("A-01" sigue con "A-02"; "P7" con "P8"). Las etiquetas que ya existen en [taken] se saltan.
     * Si [start] no termina en número, se usa como prefijo: "Moto" da "Moto-01".
     */
    fun generate(start: String, count: Int, taken: Set<String> = emptySet()): List<String> {
        require(count >= 0) { "La cantidad no puede ser negativa" }
        val clean = start.trim()
        require(clean.isNotEmpty()) { "La etiqueta inicial no puede estar vacía" }

        val match = trailingNumber.matchEntire(clean)
        val prefix: String
        var number: Long
        val width: Int
        if (match != null) {
            prefix = match.groupValues[1]
            width = match.groupValues[2].length
            number = match.groupValues[2].toLong()
        } else {
            prefix = "$clean-"
            width = 2
            number = 1
        }

        val result = ArrayList<String>(count)
        while (result.size < count) {
            val label = prefix + number.toString().padStart(width, '0')
            if (label !in taken) result += label
            number++
        }
        return result
    }
}
