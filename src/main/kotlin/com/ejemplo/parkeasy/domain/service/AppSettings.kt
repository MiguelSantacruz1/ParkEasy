package com.ejemplo.parkeasy.domain.service

import com.ejemplo.parkeasy.domain.repository.SettingRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.security.MessageDigest
import java.security.SecureRandom

enum class ThemeMode { SYSTEM, LIGHT, DARK }

/**
 * Ajustes de la instalación: tema, nombre del parqueadero, primer uso y PIN opcional.
 *
 * El PIN solo evita que alguien use la app sin permiso mientras está abierta en el equipo; no cifra
 * la base de datos, que cualquiera con acceso a la carpeta del usuario puede abrir.
 */
class AppSettings(private val settings: SettingRepository) {
    private val _theme = MutableStateFlow(readTheme())
    val theme: StateFlow<ThemeMode> = _theme.asStateFlow()

    private fun readTheme(): ThemeMode =
        settings.get(THEME)?.let { runCatching { ThemeMode.valueOf(it) }.getOrNull() } ?: ThemeMode.SYSTEM

    fun setTheme(mode: ThemeMode) {
        settings.set(THEME, mode.name)
        _theme.value = mode
    }

    var businessName: String
        get() = settings.get(NAME).orEmpty()
        set(value) = settings.set(NAME, value.trim())

    val onboardingDone: Boolean get() = settings.get(ONBOARDING) == "1"

    fun markOnboardingDone() = settings.set(ONBOARDING, "1")

    // ---- PIN ----------------------------------------------------------------------------

    val hasPin: Boolean get() = settings.get(PIN_HASH) != null

    /** Un PIN válido son de 4 a 8 dígitos. */
    fun isValidPin(pin: String) = pin.length in 4..8 && pin.all(Char::isDigit)

    fun setPin(pin: String) {
        require(isValidPin(pin)) { "El PIN debe tener de 4 a 8 dígitos" }
        val salt = ByteArray(16).also { SecureRandom().nextBytes(it) }.toHex()
        settings.set(PIN_SALT, salt)
        settings.set(PIN_HASH, hash(salt, pin))
    }

    fun verifyPin(pin: String): Boolean {
        val salt = settings.get(PIN_SALT) ?: return true
        val stored = settings.get(PIN_HASH) ?: return true
        return hash(salt, pin) == stored
    }

    /** Quita el PIN si [current] es el correcto. Devuelve si se quitó. */
    fun clearPin(current: String): Boolean {
        if (!verifyPin(current)) return false
        settings.remove(PIN_HASH)
        settings.remove(PIN_SALT)
        return true
    }

    private fun hash(salt: String, pin: String): String =
        MessageDigest.getInstance("SHA-256").digest((salt + pin).toByteArray()).toHex()

    private fun ByteArray.toHex() = joinToString("") { "%02x".format(it) }

    private companion object {
        const val THEME = "tema"
        const val NAME = "nombre"
        const val ONBOARDING = "primer_uso_hecho"
        const val PIN_HASH = "pin_hash"
        const val PIN_SALT = "pin_sal"
    }
}
