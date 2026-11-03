package com.nos.motobitride.core.settings

import android.content.Context
import android.content.SharedPreferences
import com.nos.motobitride.core.log.AppLog
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** Comportamento all'avvio dell'app quando esiste un HUD salvato. */
enum class StartupBehavior(
    val label: String,
    val description: String
) {
    /** 1 - collega il link, il video parte solo con "Avvia". */
    AUTO_LINK(
        label = "Collega l'HUD",
        description = "Ricostruisce il Wi-Fi Direct in automatico e mostra il nome. " +
                "Il video parte solo premendo Avvia."
    ),

    /** 2 - collega e avvia anche la trasmissione. */
    AUTO_LINK_AND_STREAM(
        label = "Collega e trasmetti",
        description = "Ricostruisce il link e avvia anche il video. " +
                "Android chiede comunque il consenso alla cattura schermo."
    ),

    /** 3 - default: solo nome visibile, tutto parte da "Avvia". */
    MANUAL(
        label = "Solo credenziali salvate",
        description = "Mostra il nome dell'HUD salvato. Link e video partono " +
                "solo premendo Avvia."
    );

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

    /** Default dell'attesa prima di sospendere l'encoding. */
    const val DEFAULT_ENCODER_IDLE_MINUTES = 5

    /**
     * Default del numero di push della raffica iniziale dell'ora verso il
     * TFT (uno ogni 250ms): verificato sul campo che 10 bastano quanto 60
     * (test 1.0.22/1.0.24) e pesano meno sulla socket. Lo slider permette
     * di salire se un firmware TFT diverso fosse piu' esigente.
     */
    const val DEFAULT_CLOCK_PUSH_COUNT = 10
    const val MIN_CLOCK_PUSH_COUNT = 10
    const val MAX_CLOCK_PUSH_COUNT = 60

    /**
     * Log di diagnostica completo (CHANNEL_DIAG, SESSION_DIAG, protocollo):
     * DEFAULT OFF. Abilitato, viene salvato in automatico in Documenti;
     * disabilitato, non viene prodotto nulla (nessun costo in corso).
     */
    const val DEFAULT_DIAG_LOG_ENABLED = false

    private var prefs: SharedPreferences? = null

    private val _startup = MutableStateFlow(StartupBehavior.MANUAL)
    val startup: StateFlow<StartupBehavior> = _startup.asStateFlow()

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
        _encoderIdleMinutes.value = p.getInt(
            KEY_ENCODER_IDLE_MIN,
            DEFAULT_ENCODER_IDLE_MINUTES
        ).coerceIn(MIN_ENCODER_IDLE_MINUTES, MAX_ENCODER_IDLE_MINUTES)
        _clockPushCount.value = p.getInt(
            KEY_CLOCK_PUSH_COUNT,
            DEFAULT_CLOCK_PUSH_COUNT
        ).coerceIn(MIN_CLOCK_PUSH_COUNT, MAX_CLOCK_PUSH_COUNT)
        val diagEnabled = p.getBoolean(KEY_DIAG_LOG, DEFAULT_DIAG_LOG_ENABLED)
        _diagLogEnabled.value = diagEnabled
        // Il gate reale del log vive in AppLog: lo sincronizziamo subito.
        AppLog.setDiagnosticsEnabled(diagEnabled)

        AppLog.i(TAG, "Comportamento avvio: ${_startup.value.name}")
        AppLog.i(TAG, "Sospensione encoder dopo: ${_encoderIdleMinutes.value} min")
        AppLog.i(TAG, "Push orologio all'avvio: ${_clockPushCount.value}")
        AppLog.i(TAG, "Log diagnostica: ${if (diagEnabled) "ON" else "OFF"}")
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
