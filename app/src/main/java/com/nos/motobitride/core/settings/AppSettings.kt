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
    private const val KEY_STARTUP = "startup_behavior"       // legacy (migrazione)
    private const val KEY_AUTO_CONNECT = "auto_connect"
    private const val KEY_SHOW_HOME_DIAGNOSTICS = "show_home_diagnostics"
    // Chiave nuova: "attesa attiva" in MINUTI. Le vecchie chiavi
    // (encoder_idle_timeout_min / _sec) restano ignorate di proposito.
    private const val KEY_ACTIVE_WAIT_MIN = "active_wait_min"
    private const val KEY_VIBRATE_ON_CONNECT = "vibrate_on_connect"
    private const val KEY_NOTIFICATIONS_ENABLED = "notifications_enabled"
    private const val KEY_DAYS_BEFORE = "deadline_days_before"
    private const val KEY_CLOCK_PUSH_COUNT = "clock_push_count"
    private const val KEY_DIAG_LOG = "diag_log_enabled"
    private const val KEY_LANGUAGE = "app_language"
    private const val KEY_THEME_MODE = "app_theme_mode"
    private const val KEY_PERIODIC_KEEPALIVE = "periodic_clock_keepalive"

    /**
     * Default dell'"attesa attiva": per quanti minuti l'app tenta attivamente
     * la riconnessione prima di passare in ascolto passivo.
     *
     * Durante l'attesa attiva: encoder in pausa dopo 5 s (interno) e retry P2P
     * ogni 3-10 s. Oltre la soglia: nessun tentativo, solo il receiver
     * `PEERS_CHANGED` in ascolto (la riconnessione resta automatica).
     */
    const val DEFAULT_ACTIVE_WAIT_MINUTES = 5

    /** Vibrazione breve su connessione/perdita del link (default OFF). */
    const val DEFAULT_VIBRATE_ON_CONNECT = false

    /** Notifiche di scadenza (revisione/tassa/assicurazione): default ON. */
    const val DEFAULT_NOTIFICATIONS_ENABLED = true

    /** Quanti giorni prima avvisare della scadenza. */
    const val DEFAULT_DAYS_BEFORE = 7

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

    /**
     * Connessione automatica (switch in Impostazioni → Connessioni).
     *
     * ON  = all'avvio l'app resta in attesa e si connette da sola appena
     *       intercetta l'HUD.
     * OFF = connessione manuale (default).
     */
    private val _autoConnect = MutableStateFlow(false)
    val autoConnect: StateFlow<Boolean> = _autoConnect.asStateFlow()

    /**
     * Mostra in Home la card diagnostica (HUD, stato, IP, evento link...).
     * Default OFF: la Home resta pulita; si accende da Opzioni sviluppo.
     */
    private val _showHomeDiagnostics = MutableStateFlow(false)
    val showHomeDiagnostics: StateFlow<Boolean> = _showHomeDiagnostics.asStateFlow()

    private val _language = MutableStateFlow(AppLanguage.ITALIANO)
    val language: StateFlow<AppLanguage> = _language.asStateFlow()

    private val _themeMode = MutableStateFlow(AppThemeMode.DARK)
    val themeMode: StateFlow<AppThemeMode> = _themeMode.asStateFlow()

    /**
     * Minuti di attesa attiva prima di passare in ascolto passivo.
     */
    private val _activeWaitMinutes = MutableStateFlow(DEFAULT_ACTIVE_WAIT_MINUTES)
    val activeWaitMinutes: StateFlow<Int> = _activeWaitMinutes.asStateFlow()

    /** Vibrazione breve su connessione/perdita del link. */
    private val _vibrateOnConnect = MutableStateFlow(DEFAULT_VIBRATE_ON_CONNECT)
    val vibrateOnConnect: StateFlow<Boolean> = _vibrateOnConnect.asStateFlow()

    /** Interruttore generale delle notifiche di scadenza. */
    private val _notificationsEnabled = MutableStateFlow(DEFAULT_NOTIFICATIONS_ENABLED)
    val notificationsEnabled: StateFlow<Boolean> = _notificationsEnabled.asStateFlow()

    /** Giorni di preavviso per le scadenze. */
    private val _daysBefore = MutableStateFlow(DEFAULT_DAYS_BEFORE)
    val daysBefore: StateFlow<Int> = _daysBefore.asStateFlow()

    fun init(context: Context) {
        if (prefs != null) return
        val p = context.applicationContext.getSharedPreferences(FILE, Context.MODE_PRIVATE)
        prefs = p
        _startup.value = StartupBehavior.fromStored(p.getString(KEY_STARTUP, null))
        // Migrazione dal vecchio comportamento a 3 voci:
        // AUTO_LINK / AUTO_LINK_AND_STREAM -> automatico, MANUAL -> manuale.
        _showHomeDiagnostics.value = p.getBoolean(KEY_SHOW_HOME_DIAGNOSTICS, false)
        _autoConnect.value = if (p.contains(KEY_AUTO_CONNECT)) {
            p.getBoolean(KEY_AUTO_CONNECT, false)
        } else {
            val old = p.getString(KEY_STARTUP, null)
            old != null && old != StartupBehavior.MANUAL.name
        }
        _language.value = AppLanguage.fromCode(p.getString(KEY_LANGUAGE, "it"))
        _themeMode.value = AppThemeMode.fromCode(p.getString(KEY_THEME_MODE, "dark"))
        _activeWaitMinutes.value = p.getInt(
            KEY_ACTIVE_WAIT_MIN,
            DEFAULT_ACTIVE_WAIT_MINUTES
        ).coerceIn(MIN_ACTIVE_WAIT_MINUTES, MAX_ACTIVE_WAIT_MINUTES)
        _vibrateOnConnect.value = p.getBoolean(
            KEY_VIBRATE_ON_CONNECT,
            DEFAULT_VIBRATE_ON_CONNECT
        )
        _notificationsEnabled.value = p.getBoolean(
            KEY_NOTIFICATIONS_ENABLED,
            DEFAULT_NOTIFICATIONS_ENABLED
        )
        _daysBefore.value = p.getInt(
            KEY_DAYS_BEFORE,
            DEFAULT_DAYS_BEFORE
        ).coerceIn(MIN_DAYS_BEFORE, MAX_DAYS_BEFORE)
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
        AppLog.i(
            TAG,
            "Connessione automatica: ${if (_autoConnect.value) "ON" else "OFF"}"
        )
        AppLog.i(TAG, "Attesa attiva: ${_activeWaitMinutes.value} min")
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

    fun setActiveWaitMinutes(minutes: Int) {
        val clamped = minutes.coerceIn(MIN_ACTIVE_WAIT_MINUTES, MAX_ACTIVE_WAIT_MINUTES)
        _activeWaitMinutes.value = clamped
        prefs?.edit()?.putInt(KEY_ACTIVE_WAIT_MIN, clamped)?.apply()
        AppLog.i(TAG, "Attesa attiva -> $clamped min")
    }

    fun setVibrateOnConnect(enabled: Boolean) {
        _vibrateOnConnect.value = enabled
        prefs?.edit()?.putBoolean(KEY_VIBRATE_ON_CONNECT, enabled)?.apply()
        AppLog.i(TAG, "Vibrazione su connessione -> ${if (enabled) "ON" else "OFF"}")
    }

    fun setNotificationsEnabled(enabled: Boolean) {
        _notificationsEnabled.value = enabled
        prefs?.edit()?.putBoolean(KEY_NOTIFICATIONS_ENABLED, enabled)?.apply()
        AppLog.i(TAG, "Notifiche scadenze -> ${if (enabled) "ON" else "OFF"}")
    }

    fun setDaysBefore(days: Int) {
        val clamped = days.coerceIn(MIN_DAYS_BEFORE, MAX_DAYS_BEFORE)
        _daysBefore.value = clamped
        prefs?.edit()?.putInt(KEY_DAYS_BEFORE, clamped)?.apply()
        AppLog.i(TAG, "Preavviso scadenze -> $clamped giorni")
    }

    fun setStartup(behavior: StartupBehavior) {
        _startup.value = behavior
        prefs?.edit()?.putString(KEY_STARTUP, behavior.name)?.apply()
        AppLog.i(TAG, "Comportamento avvio -> ${behavior.name}")
    }

    fun setAutoConnect(enabled: Boolean) {
        _autoConnect.value = enabled
        prefs?.edit()?.putBoolean(KEY_AUTO_CONNECT, enabled)?.apply()
        AppLog.i(TAG, "Connessione automatica -> ${if (enabled) "ON" else "OFF"}")
    }

    fun setShowHomeDiagnostics(enabled: Boolean) {
        _showHomeDiagnostics.value = enabled
        prefs?.edit()?.putBoolean(KEY_SHOW_HOME_DIAGNOSTICS, enabled)?.apply()
        AppLog.i(TAG, "Card diagnostica in Home -> ${if (enabled) "ON" else "OFF"}")
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

    const val MIN_ACTIVE_WAIT_MINUTES = 1
    const val MAX_ACTIVE_WAIT_MINUTES = 30

    const val MIN_DAYS_BEFORE = 1
    const val MAX_DAYS_BEFORE = 60
}
