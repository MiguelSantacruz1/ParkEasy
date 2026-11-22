package com.ejemplo.parkeasy.presentation.common

import com.ejemplo.parkeasy.domain.model.SpotStatus
import com.ejemplo.parkeasy.domain.model.SpotType
import com.ejemplo.parkeasy.domain.model.VehicleType
import java.text.NumberFormat
import java.time.Duration
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeParseException
import java.util.Locale

/** Formatos de pantalla: pesos colombianos, hora de Bogotá y nombres en español. */
object Formatters {
    val zone: ZoneId = ZoneId.of("America/Bogota")
    private val dateTime = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm")
    private val money = NumberFormat.getIntegerInstance(Locale.forLanguageTag("es-CO"))

    fun cop(value: Long): String = "$ " + money.format(value)

    fun dateTime(instant: Instant): String = dateTime.format(instant.atZone(zone))

    /** Interpreta "dd/MM/yyyy HH:mm" como hora de Bogotá. Devuelve null si no se entiende. */
    fun parseDateTime(text: String): Instant? = try {
        LocalDateTime.parse(text.trim(), dateTime).atZone(zone).toInstant()
    } catch (_: DateTimeParseException) {
        null
    }

    fun duration(d: Duration): String {
        val days = d.toDays()
        val hours = d.toHours() % 24
        val minutes = d.toMinutes() % 60
        return buildString {
            if (days > 0) append("$days d ")
            if (days > 0 || hours > 0) append("$hours h ")
            append("%02d min".format(minutes))
        }.trim()
    }

    fun spotType(type: SpotType): String = when (type) {
        SpotType.CAR -> "Carro"
        SpotType.MOTORCYCLE -> "Moto"
        SpotType.DISABLED -> "Discapacitados"
        SpotType.OTHER -> "Otro"
    }

    fun vehicle(type: VehicleType): String = when (type) {
        VehicleType.CAR -> "Carro"
        VehicleType.MOTORCYCLE -> "Moto"
    }

    fun status(status: SpotStatus): String = when (status) {
        SpotStatus.FREE -> "Libre"
        SpotStatus.OCCUPIED -> "Ocupado"
        SpotStatus.BLOCKED -> "Bloqueado"
    }

    fun percent(value: Double): String = "%.0f %%".format(value)
}
