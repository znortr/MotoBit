package com.nos.motobitride.core.settings

import android.content.Context
import android.content.SharedPreferences
import com.nos.motobitride.core.log.AppLog
import com.nos.motobitride.ui.i18n.AppLanguage
import com.nos.motobitride.ui.i18n.AppThemeMode
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** Comportamento all'avvio dell'app quando esiste un HUD salvato. */
enum class StartupBehavior(
    val labelKey: String
) {
    AUTO_LINK("settings_startup_autolink"),
    AUTO_LINK_AND_STREAM("settings_startup_autolink_stream"),
    MANUAL("settings_startup_manual");

    companion object {
        fun fromStored(value: String?): StartupBehavior =
            entries.firstOrNull { it.name == value } ?: MANUAL
    }
}

object AppSettings {
    private const val TAG = "AppSettings"
    private const val FILE = "motobit_settings"
    private const val KEY_STARTUP = "startup_behavior"
    private const val KEY_ENCODER_IDLE_MIN = "encoder_idle_timeout_min"
    private const val KEY_CLOCK_PUSH_COUNT = "clock_push_count"
    private const val KEY_DIAG_LOG = "diag_log_enabled"
    private const val KEY_LANGUAGE = "app_language"
    private const val KEY_THEME_MODE = "app_theme_mode"
    private const val KEY_PERIODIC_KEEPALIVE = "periodic_clock_keepalive"

    /** Default dell'attesa prima di sospendere l'encoding. */
    const val DEFAULT_ENCODER_IDLE_MINUTES = 5

    /**
     * Default del numero di push della raffica iniziale dell'ora verso il
     * TFT (uno ogni 250ms): minimo 3 pacchetti (1 e 2 resettano l'ora sul TFT).
     */
    const val DEFAULT_CLOCK_PUSH_COUNT = 3
    const val MIN_CLOCK_PUSH_COUNT = 3
    const val MAX_CLOCK_PUSH_COUNT = 60

    /**
     * Log di diagnostica completo (CHANNEL_DIAG, SESSION_DIAG, protocollo):
     * DEFAULT OFF. Abilitato, viene salvato in automatico in Documenti;
     * disabilitato, non viene prodotto nulla (nessun costo in corso).
     */
    const val DEFAULT_DIAG_LOG_ENABLED = false

    /**
     * Push dell'ora NON richiesto ogni 5 min sulla socket di controllo PXC.
     * DEFAULT OFF: misurato sui log del 05/11 che il TFT non lo legge, resta in
     * coda TCP non confermata e blocca gli ACK 0x10631, facendolo chiudere la
     * socket ~48 s dopo. Riattivabile solo per bisazione.
     */
    const val DEFAULT_PERIODIC_CLOCK_KEEPALIVE = false

    private var prefs: SharedPreferences? = null

    private val _startup = MutableStateFlow(StartupBehavior.MANUAL)
    val startup: StateFlow<StartupBehavior> = _startup.asStateFlow()

    private val _language = MutableStateFlow(AppLanguage.ITALIANO)
    val language: StateFlow<AppLanguage> = _language.asStateFlow()

    private val _themeMode = MutableStateFlow(AppThemeMode.DARK)
    val themeMode: StateFlow<AppThemeMode> = _themeMode.asStateFlow()

    /**
     * Minuti di attesa dell'HU prima di mettere l'encoder in pausa.
     * 0 = non sospendere mai.
     */
    private val _encoderIdleMinutes = MutableStateFlow(DEFAULT_ENCODER_IDLE_MINUTES)
    val encoderIdleMinutes: StateFlow<Int> = _encoderIdleMinutes.asStateFlow()

    fun init(context: Context) {
        if (prefs != null) return
        val p = context.applicationContext.getSharedPreferences(FILE, Context.MODE_PRIVATE)
        prefs = p
        _startup.value = StartupBehavior.fromStored(p.getString(KEY_STARTUP, null))
        _language.value = AppLanguage.fromCode(p.getString(KEY_LANGUAGE, "it"))
        _themeMode.value = AppThemeMode.fromCode(p.getString(KEY_THEME_MODE, "dark"))
        _encoderIdleMinutes.value = p.getInt(
            KEY_ENCODER_IDLE_MIN,
            DEFAULT_ENCODER_IDLE_MINUTES
        ).coerceIn(MIN_ENCODER_IDLE_MINUTES, MAX_ENCODER_IDLE_MINUTES)
        _clockPushCount.value = p.getInt(
            KEY_CLOCK_PUSH_COUNT,
            DEFAULT_CLOCK_PUSH_COUNT
        ).coerceIn(MIN_CLOCK_PUSH_COUNT, MAX_CLOCK_PUSH_COUNT)
        _periodicClockKeepalive.value = p.getBoolean(
            KEY_PERIODIC_KEEPALIVE,
            DEFAULT_PERIODIC_CLOCK_KEEPALIVE
        )
        val diagEnabled = p.getBoolean(KEY_DIAG_LOG, DEFAULT_DIAG_LOG_ENABLED)
        _diagLogEnabled.value = diagEnabled
        // Il gate reale del log vive in AppLog: lo sincronizziamo subito.
        AppLog.setDiagnosticsEnabled(diagEnabled)

        AppLog.i(TAG, "Lingua app: ${_language.value.code} (${_language.value.nameNative})")
        AppLog.i(TAG, "Tema app: ${_themeMode.value.code}")
        AppLog.i(TAG, "Comportamento avvio: ${_startup.value.name}")
        AppLog.i(TAG, "Sospensione encoder dopo: ${_encoderIdleMinutes.value} min")
        AppLog.i(TAG, "Push orologio all'avvio: ${_clockPushCount.value}")
        AppLog.i(TAG, "Keepalive orologio periodico (5 min): ${if (_periodicClockKeepalive.value) "ON" else "OFF"}")
        AppLog.i(TAG, "Log diagnostica: ${if (diagEnabled) "ON" else "OFF"}")
    }

    fun setLanguage(lang: AppLanguage) {
        _language.value = lang
        prefs?.edit()?.putString(KEY_LANGUAGE, lang.code)?.apply()
        AppLog.i(TAG, "Lingua impostata -> ${lang.code}")
    }

    fun setThemeMode(mode: AppThemeMode) {
        _themeMode.value = mode
        prefs?.edit()?.putString(KEY_THEME_MODE, mode.code)?.apply()
        AppLog.i(TAG, "Tema impostato -> ${mode.code}")
    }

    fun setEncoderIdleMinutes(minutes: Int) {
        val clamped = minutes.coerceIn(MIN_ENCODER_IDLE_MINUTES, MAX_ENCODER_IDLE_MINUTES)
        _encoderIdleMinutes.value = clamped
        prefs?.edit()?.putInt(KEY_ENCODER_IDLE_MIN, clamped)?.apply()
        AppLog.i(TAG, "Sospensione encoder -> $clamped min")
    }

    fun setStartup(behavior: StartupBehavior) {
        _startup.value = behavior
        prefs?.edit()?.putString(KEY_STARTUP, behavior.name)?.apply()
        AppLog.i(TAG, "Comportamento avvio -> ${behavior.name}")
    }

    /** Numero di push (a 250ms) della raffica iniziale dell'ora al TFT. */
    private val _clockPushCount = MutableStateFlow(DEFAULT_CLOCK_PUSH_COUNT)
    val clockPushCount: StateFlow<Int> = _clockPushCount.asStateFlow()

    fun setClockPushCount(count: Int) {
        val clamped = count.coerceIn(MIN_CLOCK_PUSH_COUNT, MAX_CLOCK_PUSH_COUNT)
        _clockPushCount.value = clamped
        prefs?.edit()?.putInt(KEY_CLOCK_PUSH_COUNT, clamped)?.apply()
        AppLog.i(TAG, "Push orologio -> $clamped")
    }

    /**
     * Push dell'ora non richiesto ogni 5 min sulla socket di controllo PXC.
     * Default OFF (causa misurata dell'intasamento). Solo per bisazione.
     */
    private val _periodicClockKeepalive = MutableStateFlow(DEFAULT_PERIODIC_CLOCK_KEEPALIVE)
    val periodicClockKeepalive: StateFlow<Boolean> = _periodicClockKeepalive.asStateFlow()

    fun setPeriodicClockKeepalive(enabled: Boolean) {
        _periodicClockKeepalive.value = enabled
        prefs?.edit()?.putBoolean(KEY_PERIODIC_KEEPALIVE, enabled)?.apply()
        AppLog.i(TAG, "Keepalive orologio periodico -> ${if (enabled) "ON" else "OFF"}")
    }

    /**
     * Log di diagnostica completo: ON = salvato in automatico in Documenti,
     * OFF = non prodotto (nessuna scrittura, nessun costo in corso).
     */
    private val _diagLogEnabled = MutableStateFlow(DEFAULT_DIAG_LOG_ENABLED)
    val diagLogEnabled: StateFlow<Boolean> = _diagLogEnabled.asStateFlow()

    fun setDiagLogEnabled(enabled: Boolean) {
        _diagLogEnabled.value = enabled
        prefs?.edit()?.putBoolean(KEY_DIAG_LOG, enabled)?.apply()
        AppLog.setDiagnosticsEnabled(enabled)
        AppLog.i(TAG, "Log diagnostica -> ${if (enabled) "ON" else "OFF"}")
    }

    const val MIN_ENCODER_IDLE_MINUTES = 0
    const val MAX_ENCODER_IDLE_MINUTES = 30
}
