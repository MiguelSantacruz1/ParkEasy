package com.ejemplo.parkeasy.presentation.spots

import com.ejemplo.parkeasy.domain.model.Floor
import com.ejemplo.parkeasy.domain.model.Spot
import com.ejemplo.parkeasy.domain.model.SpotStatus
import com.ejemplo.parkeasy.domain.model.SpotType
import com.ejemplo.parkeasy.domain.repository.FloorRepository
import com.ejemplo.parkeasy.domain.repository.ParkingRecordRepository
import com.ejemplo.parkeasy.domain.repository.SpotRepository
import com.ejemplo.parkeasy.domain.rules.OccupancySummary
import com.ejemplo.parkeasy.domain.service.SpotService
import com.ejemplo.parkeasy.presentation.common.BaseViewModel
import com.ejemplo.parkeasy.presentation.common.MessageCenter
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn

data class SpotFilters(
    val floorId: Long? = null,
    val type: SpotType? = null,
    val status: SpotStatus? = null,
    val coveredOnly: Boolean = false,
)

data class SpotRow(val spot: Spot, val floorName: String, val plate: String?)

data class SpotsUiState(
    val loading: Boolean = true,
    val floors: List<Floor> = emptyList(),
    val rows: List<SpotRow> = emptyList(),
    val totalSpots: Int = 0,
    val total: OccupancySummary = OccupancySummary.EMPTY,
    val byFloor: Map<Long, OccupancySummary> = emptyMap(),
    val filters: SpotFilters = SpotFilters(),
)

class SpotsViewModel(
    floors: FloorRepository,
    spots: SpotRepository,
    records: ParkingRecordRepository,
    private val spotService: SpotService,
    private val messages: MessageCenter,
) : BaseViewModel(messages) {

    private val filters = MutableStateFlow(SpotFilters())

    val state: StateFlow<SpotsUiState> =
        combine(floors.observeAll(), spots.observeAll(), records.observeOpen(), filters) { floorList, spotList, open, f ->
            val names = floorList.associate { it.id to it.name }
            val plates = open.associate { it.spotId to it.plate }
            val rows = spotList
                .filter { f.floorId == null || it.floorId == f.floorId }
                .filter { f.type == null || it.type == f.type }
                .filter { f.status == null || it.status == f.status }
                .filter { !f.coveredOnly || it.covered }
                .map { SpotRow(it, names[it.floorId].orEmpty(), plates[it.id]) }
            SpotsUiState(
                loading = false, floors = floorList, rows = rows, totalSpots = spotList.size,
                total = OccupancySummary.of(spotList), byFloor = OccupancySummary.byFloor(spotList), filters = f,
            )
        }.stateIn(scope, SharingStarted.Eagerly, SpotsUiState())

    fun setFilters(update: (SpotFilters) -> SpotFilters) { filters.value = update(filters.value) }

    fun addMany(floorId: Long, type: SpotType, start: String, count: Int, covered: Boolean) =
        launchSafe("$count puestos agregados") { spotService.addMany(floorId, type, start, count, covered) }

    fun update(spot: Spot, label: String, type: SpotType, floorId: Long) =
        launchSafe("Puesto actualizado") { spotService.update(spot.id, label, type, floorId) }

    fun block(spotId: Long, reason: String) =
        launchSafe("Puesto bloqueado") { spotService.block(spotId, reason) }

    fun unblock(spotId: Long) = launchSafe("Puesto desbloqueado") { spotService.unblock(spotId) }

    /** Elimina el puesto y ofrece deshacer durante la sesión. */
    fun delete(spot: Spot) {
        launchSafe(block = {
            spotService.delete(spot.id)
            messages.info("Puesto ${spot.label} eliminado", "Deshacer") { undo(spot) }
        })
    }

    private fun undo(spot: Spot) = launchSafe("Puesto ${spot.label} restaurado") { spotService.undoDelete(spot) }
}
