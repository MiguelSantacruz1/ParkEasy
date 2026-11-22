package com.ejemplo.parkeasy.presentation.settings

import com.ejemplo.parkeasy.data.db.AppDirectories
import com.ejemplo.parkeasy.domain.model.Tariff
import com.ejemplo.parkeasy.domain.repository.TariffRepository
import com.ejemplo.parkeasy.domain.service.TariffService
import com.ejemplo.parkeasy.presentation.common.BaseViewModel
import com.ejemplo.parkeasy.presentation.common.MessageCenter

class SettingsViewModel(
    private val tariffs: TariffRepository,
    private val tariffService: TariffService,
    messages: MessageCenter,
) : BaseViewModel(messages) {

    val databasePath: String = AppDirectories.dataDirectory().resolve("parkeasy.db").toString()

    fun currentTariffs(): List<Tariff> = tariffs.getAll()

    fun save(tariff: Tariff) = launchSafe("Tarifa guardada") { tariffService.save(tariff) }
}
