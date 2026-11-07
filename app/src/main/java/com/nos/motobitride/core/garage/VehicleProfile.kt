package com.nos.motobitride.core.garage

import android.content.Context
import android.content.SharedPreferences
import com.nos.motobitride.core.log.AppLog
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONObject

/** Le tre scadenze annuali del veicolo. */
enum class DeadlineKind { REVISION, TAX, INSURANCE }

/**
 * Una scadenza annuale.
 *
 * @param month 0 = non impostata, 1..12
 * @param day   0 = "entro il mese" (vale l'ultimo giorno del mese), 1..31 = giorno preciso
 * @param snoozedUntil epoch millis fino a cui la notifica e' posposta (0 = nessuno snooze)
 * @param dismissedForYear anno dell'occorrenza per cui l'utente ha premuto "Elimina"
 */
data class VehicleDeadline(
    val month: Int = 0,
    val day: Int = 0,
    val snoozedUntil: Long = 0L,
    val dismissedForYear: Int = 0
) {
    val isSet: Boolean get() = month in 1..12

    fun toJson(): JSONObject = JSONObject().apply {
        put("month", month)
        put("day", day)
        put("snoozedUntil", snoozedUntil)
        put("dismissedForYear", dismissedForYear)
    }

    companion object {
        fun fromJson(o: JSONObject?): VehicleDeadline {
            if (o == null) return VehicleDeadline()
            return VehicleDeadline(
                month = o.optInt("month", 0).coerceIn(0, 12),
                day = o.optInt("day", 0).coerceIn(0, 31),
                snoozedUntil = o.optLong("snoozedUntil", 0L),
                dismissedForYear = o.optInt("dismissedForYear", 0)
            )
        }
    }
}

/**
 * Dati del veicolo scritti dall'utente.
 *
 * `name` vuoto significa "usa il nome dell'HUD" (vedi [vehicleDisplayName]).
 */
data class VehicleProfile(
    val name: String = "",
    val plate: String = "",
    val revision: VehicleDeadline = VehicleDeadline(),
    val tax: VehicleDeadline = VehicleDeadline(),
    val insurance: VehicleDeadline = VehicleDeadline()
) {
    fun deadline(kind: DeadlineKind): VehicleDeadline = when (kind) {
        DeadlineKind.REVISION -> revision
        DeadlineKind.TAX -> tax
        DeadlineKind.INSURANCE -> insurance
    }

    fun withDeadline(kind: DeadlineKind, value: VehicleDeadline): VehicleProfile = when (kind) {
        DeadlineKind.REVISION -> copy(revision = value)
        DeadlineKind.TAX -> copy(tax = value)
        DeadlineKind.INSURANCE -> copy(insurance = value)
    }

    fun toJson(): String = JSONObject().apply {
        put("name", name)
        put("plate", plate)
        put("revision", revision.toJson())
        put("tax", tax.toJson())
        put("insurance", insurance.toJson())
    }.toString()

    companion object {
        fun fromJson(raw: String?): VehicleProfile {
            if (raw.isNullOrBlank()) return VehicleProfile()
            return try {
                val o = JSONObject(raw)
                VehicleProfile(
                    name = o.optString("name", ""),
                    plate = o.optString("plate", ""),
                    revision = VehicleDeadline.fromJson(o.optJSONObject("revision")),
                    tax = VehicleDeadline.fromJson(o.optJSONObject("tax")),
                    insurance = VehicleDeadline.fromJson(o.optJSONObject("insurance"))
                )
            } catch (t: Throwable) {
                AppLog.w("VehicleProfile", "JSON non valido: ${t.message}")
                VehicleProfile()
            }
        }
    }
}

/**
 * Nome da mostrare: quello scritto dall'utente, altrimenti quello dell'HUD
 * (campo `name` del QR o SSID), altrimenti "Nessun veicolo".
 */
fun vehicleDisplayName(profile: VehicleProfile, hudName: String?): String =
    profile.name.takeIf { it.isNotBlank() }
        ?: hudName?.takeIf { it.isNotBlank() }
        ?: "Nessun veicolo"

/**
 * Persistenza del profilo veicolo: JSON in SharedPreferences (stesso schema di
 * `SavedHud`). Scelta D3 = A: niente Room finche' il veicolo e' uno solo.
 */
object VehicleStore {

    private const val TAG = "VehicleStore"
    private const val FILE = "motobit_vehicle"
    private const val KEY_PROFILE = "profile"

    private var prefs: SharedPreferences? = null

    private val _current = MutableStateFlow(VehicleProfile())
    val current: StateFlow<VehicleProfile> = _current.asStateFlow()

    fun init(context: Context) {
        if (prefs != null) return
        val p = context.applicationContext.getSharedPreferences(FILE, Context.MODE_PRIVATE)
        prefs = p
        _current.value = VehicleProfile.fromJson(p.getString(KEY_PROFILE, null))
        AppLog.i(TAG, "Profilo veicolo caricato: nome='${_current.value.name}' targa='${_current.value.plate}'")
    }

    fun save(profile: VehicleProfile) {
        _current.value = profile
        prefs?.edit()?.putString(KEY_PROFILE, profile.toJson())?.apply()
    }

    fun setName(name: String) = save(_current.value.copy(name = name))

    fun setPlate(plate: String) = save(_current.value.copy(plate = plate))

    fun setDeadline(kind: DeadlineKind, month: Int, day: Int) {
        val old = _current.value.deadline(kind)
        val updated = old.copy(
            month = month.coerceIn(0, 12),
            day = day.coerceIn(0, 31),
            // Cambiare la data riattiva l'avviso soppresso.
            dismissedForYear = 0,
            snoozedUntil = 0L
        )
        save(_current.value.withDeadline(kind, updated))
        AppLog.i(TAG, "Scadenza $kind -> mese=${updated.month} giorno=${updated.day}")
    }

    /** Posponi: la notifica torna dopo [untilMillis]. */
    fun snooze(kind: DeadlineKind, untilMillis: Long) {
        val old = _current.value.deadline(kind)
        save(_current.value.withDeadline(kind, old.copy(snoozedUntil = untilMillis)))
        AppLog.i(TAG, "Scadenza $kind posposta fino a $untilMillis")
    }

    /** Elimina: sopprime la notifica per l'occorrenza di [year]. */
    fun dismiss(kind: DeadlineKind, year: Int) {
        val old = _current.value.deadline(kind)
        save(_current.value.withDeadline(kind, old.copy(dismissedForYear = year)))
        AppLog.i(TAG, "Scadenza $kind soppressa per il $year")
    }

    /** Riattiva l'avviso soppresso (pulsante "Riattiva" nel Garage). */
    fun rearm(kind: DeadlineKind) {
        val old = _current.value.deadline(kind)
        save(_current.value.withDeadline(kind, old.copy(dismissedForYear = 0, snoozedUntil = 0L)))
        AppLog.i(TAG, "Scadenza $kind riattivata")
    }

    fun clear() {
        save(VehicleProfile())
        AppLog.i(TAG, "Profilo veicolo azzerato")
    }
}
