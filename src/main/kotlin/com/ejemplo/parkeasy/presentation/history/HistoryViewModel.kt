package com.ejemplo.parkeasy.presentation.history

import com.ejemplo.parkeasy.domain.model.ChangeLogEntry
import com.ejemplo.parkeasy.domain.model.ParkingRecord
import com.ejemplo.parkeasy.domain.repository.ChangeLogRepository
import com.ejemplo.parkeasy.domain.repository.ParkingRecordRepository
import com.ejemplo.parkeasy.domain.rules.CsvExport
import com.ejemplo.parkeasy.domain.rules.HistoryFilter
import com.ejemplo.parkeasy.domain.rules.HistoryTotals
import com.ejemplo.parkeasy.presentation.common.BaseViewModel
import com.ejemplo.parkeasy.presentation.common.MessageCenter
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.nio.file.Files
import java.nio.file.Path
import java.time.Clock

enum class HistoryTab { RECORDS, CHANGES }

data class HistoryUiState(
    val loading: Boolean = true,
    val tab: HistoryTab = HistoryTab.RECORDS,
    val filter: HistoryFilter = HistoryFilter(),
    val rows: List<ParkingRecord> = emptyList(),
    val totals: HistoryTotals = HistoryTotals(0, 0, 0),
    val floorNames: List<String> = emptyList(),
    val changes: List<ChangeLogEntry> = emptyList(),
    val totalRecords: Int = 0,
)

/** Historial de registros con filtros y bitácora de cambios. Ambos se pueden exportar a CSV. */
class HistoryViewModel(
    private val records: ParkingRecordRepository,
    private val changeLog: ChangeLogRepository,
    private val clock: Clock,
    private val messages: MessageCenter,
) : BaseViewModel(messages) {

    private val _state = MutableStateFlow(HistoryUiState())
    val state: StateFlow<HistoryUiState> = _state.asStateFlow()

    init {
        scope.launch {
            // Cada vez que entra o sale un vehículo, el historial se actualiza solo.
            records.observeOpen().collect { reload() }
        }
    }

    fun reload() {
        val all = records.getAll()
        val changes = changeLog.getRecent(CHANGE_LIMIT)
        _state.update { s ->
            val rows = s.filter().apply(all, clock.zone)
            s.copy(
                loading = false, rows = rows, totals = HistoryTotals.of(rows), totalRecords = all.size,
                floorNames = all.map { it.floorName }.distinct().sorted(), changes = changes,
            )
        }
    }

    private fun HistoryUiState.filter() = filter

    fun setTab(tab: HistoryTab) = _state.update { it.copy(tab = tab) }

    fun setFilter(change: (HistoryFilter) -> HistoryFilter) {
        _state.update { it.copy(filter = change(it.filter)) }
        reload()
    }

    fun clearFilters() = setFilter { HistoryFilter() }

    /** Escribe el contenido visible (registros filtrados o bitácora) en [target]. */
    fun export(target: Path) = launchSafe("Exportado a ${target.fileName}") {
        val s = _state.value
        val text = when (s.tab) {
            HistoryTab.RECORDS -> CsvExport.records(s.rows, clock.zone)
            HistoryTab.CHANGES -> CsvExport.changeLog(s.changes, clock.zone)
        }
        // BOM para que Excel reconozca las tildes.
        Files.write(target, ("﻿" + text).toByteArray(Charsets.UTF_8))
    }

    companion object {
        const val CHANGE_LIMIT = 1000
    }
}
