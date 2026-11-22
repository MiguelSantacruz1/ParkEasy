package com.ejemplo.parkeasy.presentation.settings

import com.ejemplo.parkeasy.data.backup.BackupService
import com.ejemplo.parkeasy.data.db.AppDirectories
import com.ejemplo.parkeasy.domain.model.Tariff
import com.ejemplo.parkeasy.domain.repository.TariffRepository
import com.ejemplo.parkeasy.domain.service.AppSettings
import com.ejemplo.parkeasy.domain.service.RetentionService
import com.ejemplo.parkeasy.domain.service.TariffService
import com.ejemplo.parkeasy.domain.service.ThemeMode
import com.ejemplo.parkeasy.presentation.common.BaseViewModel
import com.ejemplo.parkeasy.presentation.common.MessageCenter
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.nio.file.Path
import java.time.Clock
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter

class SettingsViewModel(
    private val tariffs: TariffRepository,
    private val tariffService: TariffService,
    private val appSettings: AppSettings,
    private val backup: BackupService,
    private val retention: RetentionService,
    private val clock: Clock,
    private val messages: MessageCenter,
) : BaseViewModel(messages) {

    val databasePath: String = AppDirectories.effectiveDirectory().resolve(com.ejemplo.parkeasy.data.db.AppProfile.current().databaseName).toString()

    val theme: StateFlow<ThemeMode> = appSettings.theme

    private val _hasPin = MutableStateFlow(appSettings.hasPin)
    val hasPin: StateFlow<Boolean> = _hasPin.asStateFlow()

    fun currentTariffs(): List<Tariff> = tariffs.getAll()

    fun save(tariff: Tariff) = launchSafe("Tarifa guardada") { tariffService.save(tariff) }

    fun setTheme(mode: ThemeMode) = appSettings.setTheme(mode)

    fun suggestedBackupName(): String =
        "parkeasy-" + DateTimeFormatter.ofPattern("yyyyMMdd-HHmm").format(ZonedDateTime.now(clock)) + ".db"

    fun createBackup(target: Path) = launchSafe("Copia guardada en $target") { backup.backupTo(target) }

    /** Reemplaza los datos por los de la copia. [onDone] corre al terminar bien. */
    fun restore(source: Path, onDone: () -> Unit = {}) = launchSafe(onSuccess = onDone) {
        val summary = backup.restoreFrom(source)
        messages.info("Copia restaurada. Lo anterior quedó en ${summary.safetyCopy}")
    }

    // ---- Privacidad ----

    /** Cuántos registros cerrados se borrarían; null si la fecha no es válida. */
    fun previewPurge(before: java.time.LocalDate): Int? = runCatching { retention.preview(before) }.getOrNull()

    fun purge(before: java.time.LocalDate, onDone: () -> Unit = {}) = launchSafe(onSuccess = onDone) {
        val deleted = retention.purge(before)
        messages.info("$deleted registros borrados")
    }

    // ---- PIN ----

    fun setPin(pin: String, confirmation: String) {
        when {
            !appSettings.isValidPin(pin) -> messages.error("El PIN debe tener de 4 a 8 dígitos")
            pin != confirmation -> messages.error("Los dos PIN no coinciden")
            else -> {
                appSettings.setPin(pin)
                _hasPin.value = true
                messages.info("PIN activado")
            }
        }
    }

    fun clearPin(current: String) {
        if (appSettings.clearPin(current)) {
            _hasPin.value = false
            messages.info("PIN desactivado")
        } else {
            messages.error("El PIN actual no es correcto")
        }
    }
}
