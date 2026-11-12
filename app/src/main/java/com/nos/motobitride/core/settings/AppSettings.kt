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

/**
 * Le **due** modalità del display, mutuamente esclusive per costruzione (un solo
 * valore salvato: sceglierne una disattiva l'altra).
 * Il ripristino è affidato al **tasto accensione** (e ai due tasti volume).
 */
enum class DisplayMode {
    /**
     * **Telefono in tasca**: schermo spento dal sensore di prossimità
     * (`PROXIMITY_SCREEN_OFF_WAKE_LOCK`) + risoluzione applicata.
     */
    POCKET,

    /**
     * **Sempre acceso**: si comanda con la sua **bolla**; la bolla cambia la
     * risoluzione e, se lo switch "la bolla oscura" è attivo, oscura anche.
     */
    ALWAYS_ON;

    companion object {
        fun fromStored(value: String?): DisplayMode =
            entries.firstOrNull { it.name == value } ?: ALWAYS_ON
    }
}

object AppSettings {
    private const val TAG = "AppSettings"
    private const val FILE = "motobit_settings"
    private const val KEY_STARTUP = "startup_behavior"       // legacy (migrazione)
    private const val KEY_AUTO_CONNECT = "auto_connect"
    private const val KEY_SHOW_HOME_DIAGNOSTICS = "show_home_diagnostics"
    private const val KEY_SHIZUKU_ENABLED = "shizuku_enabled"
    private const val KEY_DISPLAY_ORIGINALS = "display_originals"
    private const val KEY_DISPLAY_SCALE = "display_scale_pct"
    private const val KEY_DISPLAY_DENSITY = "display_density_scale"
    private const val KEY_DISPLAY_ROTATION = "display_rotation"
    private const val KEY_DISPLAY_NAVBAR = "display_hide_navbar"
    private const val KEY_DISPLAY_DIM_ON = "display_dim_on"
    private const val KEY_DISPLAY_DIM_PCT = "display_dim_pct"
    private const val KEY_DISPLAY_OVERRIDE = "display_override"
    private const val KEY_DISPLAY_MODE = "display_mode"
    private const val KEY_DISPLAY_ORIG_DP = "display_orig_dp"
    private const val KEY_DISPLAY_BUBBLE_DIM = "display_bubble_dim"
    private const val KEY_DISPLAY_CROP_PX = "display_crop_px"
    private const val KEY_POCKET_DELAY_SEC = "pocket_delay_sec"

    /**
     * Versione delle preferenze del display.
     *
     * Serve a ripulire i valori lasciati dalle versioni precedenti, che erano
     * inaffidabili (originali a 7 campi, modalità inventata, barra nascosta):
     * con quelli il ripristino riscriveva valori sbagliati nelle impostazioni di
     * sistema e il telefono restava con la barra nascosta o lampeggiava.
     */
    private const val KEY_PREFS_VERSION = "prefs_version"
    private const val PREFS_VERSION = 1
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

    /**
     * Interruttore generale di Shizuku (pagina Risoluzione).
     *
     * Default ON. Se OFF l'app **non interroga mai** il ponte Shizuku: la pagina
     * risoluzione mostra "disattivato" e tutte le azioni che ne dipendono sono
     * disabilitate. È il modo per escluderlo senza ricompilare.
     */
    private val _shizukuEnabled = MutableStateFlow(true)
    val shizukuEnabled: StateFlow<Boolean> = _shizukuEnabled.asStateFlow()

    // ---------------- Display (pagina Risoluzione) ----------------

    /** Originali (dimensioni|densità|luminosità|modo|accel|rotazione|barra). */
    private val _displayOriginals = MutableStateFlow("")
    val displayOriginals: StateFlow<String> = _displayOriginals.asStateFlow()

    /** Percentuale del formato TFT (100 = 592x752). */
    private val _displayScale = MutableStateFlow(100)
    val displayScale: StateFlow<Int> = _displayScale.asStateFlow()

    private val _displayDensityScale = MutableStateFlow(true)
    val displayDensityScale: StateFlow<Boolean> = _displayDensityScale.asStateFlow()

    /** auto | portrait | landscape */
    /**
     * Larghezza in dp del telefono **allo stato originale**.
     *
     * Si memorizza UNA volta, quando il display non è ancora modificato. Serve
     * al calcolo della densità: leggere i dp del momento mentre il display è già
     * alterato significherebbe compensare due volte (ed è così che si arriva a
     * un'interfaccia inutilizzabile).
     */
    private val _displayOrigDp = MutableStateFlow(0)
    val displayOrigDp: StateFlow<Int> = _displayOrigDp.asStateFlow()

    /** Solo `portrait` o `landscape`: la rotazione automatica non ha senso qui. */
    private val _displayRotation = MutableStateFlow("portrait")
    val displayRotation: StateFlow<String> = _displayRotation.asStateFlow()

    private val _displayHideNavBar = MutableStateFlow(false)
    val displayHideNavBar: StateFlow<Boolean> = _displayHideNavBar.asStateFlow()

    private val _displayDimOn = MutableStateFlow(false)
    val displayDimOn: StateFlow<Boolean> = _displayDimOn.asStateFlow()

    /** Oscuramento 0..50 % (0 = nessun buio). */
    private val _displayDimPercent = MutableStateFlow(30)
    val displayDimPercent: StateFlow<Int> = _displayDimPercent.asStateFlow()

    /** Vero se il display è attualmente modificato da noi. */
    private val _displayOverride = MutableStateFlow(false)
    val displayOverride: StateFlow<Boolean> = _displayOverride.asStateFlow()

    /**
     * Modalità attiva sul display, **mutuamente esclusive**:
     *  - [DisplayMode.POCKET] telefono in tasca (schermo spento dal sensore);
     *  - [DisplayMode.ALWAYS_ON] sempre acceso: comanda la bolla, che può
     *    oscurare se lo switch "la bolla oscura" è attivo.
     */
    private val _displayMode = MutableStateFlow(DisplayMode.ALWAYS_ON)
    val displayMode: StateFlow<DisplayMode> = _displayMode.asStateFlow()

    /**
     * Se attivo, toccare la bolla oscura **oltre** a cambiare risoluzione.
     * Default ON: è il comportamento richiesto (bolla = risoluzione + scuro).
     */
    private val _displayBubbleDim = MutableStateFlow(true)
    val displayBubbleDim: StateFlow<Boolean> = _displayBubbleDim.asStateFlow()

    fun setDisplayBubbleDim(enabled: Boolean) {
        _displayBubbleDim.value = enabled
        prefs?.edit()?.putBoolean(KEY_DISPLAY_BUBBLE_DIM, enabled)?.apply()
        AppLog.i(TAG, "La bolla oscura -> ${if (enabled) "ON" else "OFF"}")
    }

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
        migrateDisplayPrefs(p)
        _startup.value = StartupBehavior.fromStored(p.getString(KEY_STARTUP, null))
        // Migrazione dal vecchio comportamento a 3 voci:
        // AUTO_LINK / AUTO_LINK_AND_STREAM -> automatico, MANUAL -> manuale.
        _showHomeDiagnostics.value = p.getBoolean(KEY_SHOW_HOME_DIAGNOSTICS, false)
        _shizukuEnabled.value = p.getBoolean(KEY_SHIZUKU_ENABLED, true)
        _displayOriginals.value = p.getString(KEY_DISPLAY_ORIGINALS, "").orEmpty()
        _displayScale.value = p.getInt(KEY_DISPLAY_SCALE, 100)
        _displayDensityScale.value = p.getBoolean(KEY_DISPLAY_DENSITY, true)
        _displayRotation.value = p.getString(KEY_DISPLAY_ROTATION, "portrait").orEmpty()
        _displayHideNavBar.value = p.getBoolean(KEY_DISPLAY_NAVBAR, false)
        _displayDimOn.value = p.getBoolean(KEY_DISPLAY_DIM_ON, false)
        _displayDimPercent.value = p.getInt(KEY_DISPLAY_DIM_PCT, 30)
        _displayOverride.value = p.getBoolean(KEY_DISPLAY_OVERRIDE, false)
        _displayMode.value = DisplayMode.fromStored(p.getString(KEY_DISPLAY_MODE, null))
        _displayOrigDp.value = p.getInt(KEY_DISPLAY_ORIG_DP, 0)
        _displayBubbleDim.value = p.getBoolean(KEY_DISPLAY_BUBBLE_DIM, true)
        _displayCropPx.value = p.getInt(KEY_DISPLAY_CROP_PX, 0)
        _displayPocketDelaySec.value = p.getInt(KEY_POCKET_DELAY_SEC, 1).coerceIn(0, 5)
        // La modalità "auto" non esiste più: solo verticale o orizzontale.
        if (_displayRotation.value == "auto") _displayRotation.value = "portrait"
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

    /**
     * Riporta ai valori sicuri le preferenze display delle versioni precedenti.
     * Una volta sola per installazione (poi `prefs_version` resta aggiornata).
     */
    private fun migrateDisplayPrefs(p: SharedPreferences) {
        val version = p.getInt(KEY_PREFS_VERSION, 0)
        if (version >= PREFS_VERSION) return

        p.edit()
            // Gli originali vecchi avevano 7 campi: il nuovo ripristino li
            // leggeva male (policy_control = "0", rotazione = densità).
            .remove(KEY_DISPLAY_ORIGINALS)
            .remove(KEY_DISPLAY_OVERRIDE)
            .remove(KEY_DISPLAY_ORIG_DP)
            .putString(KEY_DISPLAY_MODE, DisplayMode.ALWAYS_ON.name)
            .putString(KEY_DISPLAY_ROTATION, "portrait")
            .putBoolean(KEY_DISPLAY_NAVBAR, false)
            .putBoolean(KEY_DISPLAY_DENSITY, true)
            .putInt(KEY_DISPLAY_SCALE, 100)
            .putInt(KEY_DISPLAY_DIM_PCT, 30)
            .putInt(KEY_PREFS_VERSION, PREFS_VERSION)
            .apply()

        AppLog.w(
            TAG,
            "Migrazione preferenze display (era v$version): modalità manuale, " +
                    "barra visibile, 100%, densità attiva, originali da rileggere"
        )
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

    fun setShizukuEnabled(enabled: Boolean) {
        _shizukuEnabled.value = enabled
        prefs?.edit()?.putBoolean(KEY_SHIZUKU_ENABLED, enabled)?.apply()
        AppLog.i(TAG, "Shizuku -> ${if (enabled) "ON" else "OFF"}")
    }

    // ---------------- Display ----------------

    fun setDisplayOriginals(value: String) {
        _displayOriginals.value = value
        prefs?.edit()?.putString(KEY_DISPLAY_ORIGINALS, value)?.apply()
    }

    fun setDisplayScale(pct: Int) {
        val clamped = pct.coerceIn(100, 200)
        _displayScale.value = clamped
        prefs?.edit()?.putInt(KEY_DISPLAY_SCALE, clamped)?.apply()
    }

    fun setDisplayOrigDp(dp: Int) {
        if (dp <= 0 || _displayOrigDp.value == dp) return
        _displayOrigDp.value = dp
        prefs?.edit()?.putInt(KEY_DISPLAY_ORIG_DP, dp)?.apply()
        AppLog.i(TAG, "Larghezza originale del telefono: ${dp}dp")
    }

    /**
     * Righe da tagliare in fondo al frame, prima della codifica.
     *
     * `0` = nessun taglio (comportamento identico a prima). Si usa l'altezza
     * **reale** della barra comandi del telefono, calcolata dalla pagina
     * impostazioni: così il taglio combacia con quello che c'è da togliere.
     * Il flusso resta della misura negoziata: si taglia e si ricampiona a
     * righe intere, senza toccare encoder né protocollo.
     */
    private val _displayCropPx = MutableStateFlow(0)
    val displayCropPx: StateFlow<Int> = _displayCropPx.asStateFlow()

    /**
     * Ritardo (0–5 s) prima di applicare oscuramento e risoluzione quando il
     * sensore viene **coperto**.
     *
     * Serve a evitare che un semplice sfioramento faccia scattare tutto.
     * Quando il sensore viene **scoperto** NON c'è attesa: si ripristina subito.
     */
    private val _displayPocketDelaySec = MutableStateFlow(1)
    val displayPocketDelaySec: StateFlow<Int> = _displayPocketDelaySec.asStateFlow()

    fun setDisplayPocketDelaySec(sec: Int) {
        val v = sec.coerceIn(0, 5)
        _displayPocketDelaySec.value = v
        prefs?.edit()?.putInt(KEY_POCKET_DELAY_SEC, v)?.apply()
        AppLog.i(TAG, "Ritardo sensore in tasca: ${v}s")
    }

    fun setDisplayCropPx(px: Int) {
        val v = px.coerceIn(0, 400)
        _displayCropPx.value = v
        prefs?.edit()?.putInt(KEY_DISPLAY_CROP_PX, v)?.apply()
        AppLog.i(TAG, "Taglio del bordo inferiore: ${v}px")
    }

    fun setDisplayDensityScale(enabled: Boolean) {
        _displayDensityScale.value = enabled
        prefs?.edit()?.putBoolean(KEY_DISPLAY_DENSITY, enabled)?.apply()
    }

    fun setDisplayRotation(mode: String) {
        val value = if (mode == "landscape") "landscape" else "portrait"
        _displayRotation.value = value
        prefs?.edit()?.putString(KEY_DISPLAY_ROTATION, value)?.apply()
    }

    fun setDisplayHideNavBar(enabled: Boolean) {
        _displayHideNavBar.value = enabled
        prefs?.edit()?.putBoolean(KEY_DISPLAY_NAVBAR, enabled)?.apply()
    }

    fun setDisplayDimOn(enabled: Boolean) {
        _displayDimOn.value = enabled
        prefs?.edit()?.putBoolean(KEY_DISPLAY_DIM_ON, enabled)?.apply()
    }

    /** Oscuramento: 0 = spento (luminosità originale), 1..50 = percentuale di buio. */
    fun setDisplayDimPercent(pct: Int) {
        val clamped = pct.coerceIn(0, 50)
        _displayDimPercent.value = clamped
        prefs?.edit()?.putInt(KEY_DISPLAY_DIM_PCT, clamped)?.apply()
    }

    fun setDisplayOverride(active: Boolean) {
        _displayOverride.value = active
        prefs?.edit()?.putBoolean(KEY_DISPLAY_OVERRIDE, active)?.apply()
    }

    fun setDisplayMode(mode: DisplayMode) {
        _displayMode.value = mode
        prefs?.edit()?.putString(KEY_DISPLAY_MODE, mode.name)?.apply()
        AppLog.i(TAG, "Modalità display -> ${mode.name}")
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
