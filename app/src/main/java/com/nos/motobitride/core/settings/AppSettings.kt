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

    /** Default dell'attesa prima di sospendere l'encoding. */
    const val DEFAULT_ENCODER_IDLE_MINUTES = 5

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

        AppLog.i(TAG, "Comportamento avvio: ${_startup.value.name}")
        AppLog.i(TAG, "Sospensione encoder dopo: ${_encoderIdleMinutes.value} min")
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

    const val MIN_ENCODER_IDLE_MINUTES = 0
    const val MAX_ENCODER_IDLE_MINUTES = 30
}
