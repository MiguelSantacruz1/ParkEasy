package com.ejemplo.parkeasy.presentation.records

import com.ejemplo.parkeasy.domain.model.ParkingRecord
import com.ejemplo.parkeasy.domain.model.Spot
import com.ejemplo.parkeasy.domain.model.SpotStatus
import com.ejemplo.parkeasy.domain.repository.ParkingRecordRepository
import com.ejemplo.parkeasy.domain.repository.SpotRepository
import com.ejemplo.parkeasy.domain.rules.PriceBreakdown
import com.ejemplo.parkeasy.domain.service.CheckInRequest
import com.ejemplo.parkeasy.domain.service.CorrectionRequest
import com.ejemplo.parkeasy.domain.service.ParkingService
import com.ejemplo.parkeasy.presentation.common.BaseViewModel
import com.ejemplo.parkeasy.presentation.common.MessageCenter
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import java.time.Instant

/** Ventanas de diálogo del flujo de registros. Se abren desde cualquier pantalla. */
sealed interface RecordDialog {
    /** [spotId] viene preseleccionado cuando se elige el puesto en la lista o en el plano. */
    data class Entry(val spotId: Long?) : RecordDialog
    data class Exit(val recordId: Long) : RecordDialog
    data class Correct(val recordId: Long) : RecordDialog
    data class Move(val recordId: Long) : RecordDialog
}

data class RecordsUiState(
    val loading: Boolean = true,
    val query: String = "",
    val inside: List<ParkingRecord> = emptyList(),
    val totalInside: Int = 0,
    val freeSpots: List<Spot> = emptyList(),
)

class RecordsViewModel(
    spots: SpotRepository,
    records: ParkingRecordRepository,
    private val parking: ParkingService,
    private val recordRepo: ParkingRecordRepository,
    messages: MessageCenter,
) : BaseViewModel(messages) {

    private val query = MutableStateFlow("")
    private val _dialog = MutableStateFlow<RecordDialog?>(null)
    val dialog: StateFlow<RecordDialog?> = _dialog.asStateFlow()

    val state: StateFlow<RecordsUiState> = combine(records.observeOpen(), spots.observeAll(), query) { open, allSpots, q ->
        val filtered = if (q.isBlank()) open else {
            val needle = com.ejemplo.parkeasy.domain.rules.PlateValidator.normalize(q)
            open.filter { needle in it.plate }
        }
        RecordsUiState(
            loading = false,
            query = q,
            inside = filtered,
            totalInside = open.size,
            freeSpots = allSpots.filter { it.status == SpotStatus.FREE },
        )
    }.stateIn(scope, SharingStarted.Eagerly, RecordsUiState())

    fun setQuery(text: String) { query.value = text }

    fun openEntry(spotId: Long? = null) { _dialog.value = RecordDialog.Entry(spotId) }
    fun openExit(recordId: Long) { _dialog.value = RecordDialog.Exit(recordId) }
    fun openCorrect(recordId: Long) { _dialog.value = RecordDialog.Correct(recordId) }
    fun openMove(recordId: Long) { _dialog.value = RecordDialog.Move(recordId) }
    fun closeDialog() { _dialog.value = null }

    fun record(id: Long): ParkingRecord? = recordRepo.getById(id)

    /** Cotiza sin guardar; devuelve null si la hora de salida no es válida. */
    fun quote(recordId: Long, exit: Instant): PriceBreakdown? =
        runCatching { parking.quote(recordId, exit) }.getOrNull()

    fun checkIn(request: CheckInRequest) =
        launchSafe("Entrada registrada", onSuccess = ::closeDialog) { parking.checkIn(request) }

    fun checkOut(recordId: Long, exit: Instant) =
        launchSafe("Salida registrada", onSuccess = ::closeDialog) { parking.checkOut(recordId, exit) }

    fun correct(recordId: Long, request: CorrectionRequest) =
        launchSafe("Registro corregido", onSuccess = ::closeDialog) { parking.correct(recordId, request) }

    fun move(recordId: Long, newSpotId: Long) =
        launchSafe("Vehículo cambiado de puesto", onSuccess = ::closeDialog) { parking.move(recordId, newSpotId) }
}
