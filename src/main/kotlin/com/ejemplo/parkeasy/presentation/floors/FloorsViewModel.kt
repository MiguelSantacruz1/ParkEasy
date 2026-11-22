package com.ejemplo.parkeasy.presentation.floors

import com.ejemplo.parkeasy.domain.model.Floor
import com.ejemplo.parkeasy.domain.repository.FloorRepository
import com.ejemplo.parkeasy.domain.repository.SpotRepository
import com.ejemplo.parkeasy.domain.rules.OccupancySummary
import com.ejemplo.parkeasy.domain.service.FloorService
import com.ejemplo.parkeasy.presentation.common.BaseViewModel
import com.ejemplo.parkeasy.presentation.common.MessageCenter
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn

data class FloorRow(val floor: Floor, val summary: OccupancySummary)

data class FloorsUiState(val loading: Boolean = true, val rows: List<FloorRow> = emptyList())

class FloorsViewModel(
    floors: FloorRepository,
    spots: SpotRepository,
    private val floorService: FloorService,
    messages: MessageCenter,
) : BaseViewModel(messages) {

    val state: StateFlow<FloorsUiState> = combine(floors.observeAll(), spots.observeAll()) { floorList, spotList ->
        val byFloor = OccupancySummary.byFloor(spotList)
        FloorsUiState(false, floorList.map { FloorRow(it, byFloor[it.id] ?: OccupancySummary.EMPTY) })
    }.stateIn(scope, SharingStarted.Eagerly, FloorsUiState())

    fun add(name: String) = launchSafe("Piso agregado") { floorService.add(name) }

    fun rename(id: Long, name: String) = launchSafe("Piso renombrado") { floorService.rename(id, name) }

    fun move(id: Long, direction: Int) = launchSafe { floorService.move(id, direction) }

    fun delete(id: Long) = launchSafe("Piso eliminado") { floorService.delete(id) }
}
