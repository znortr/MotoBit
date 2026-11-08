package com.nos.motobitride.core.link

import android.content.Context
import android.content.SharedPreferences
import com.nos.motobitride.core.log.AppLog
import com.nos.motobitride.core.qr.QrContent
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONObject

/**
 * Credenziali dell'HU lette dal QR, memorizzate in modo persistente.
 *
 * Serve a non dover riscansionare il QR a ogni avvio dell'app o dopo uno
 * spegnimento del TFT: il link Wi-Fi Direct viene ricostruito da qui.
 */
data class SavedHudData(
    val ssid: String,
    val password: String?,
    val auth: String?,
    val modelId: String?,
    val sn: String?,
    val mac: String?,
    val name: String?
) {
    /** Nome da mostrare in UI: il `name` del QR, altrimenti l'SSID. */
    val displayName: String
        get() = name?.takeIf { it.isNotBlank() } ?: ssid

    fun toQrContent(): QrContent.HudWifi = QrContent.HudWifi(
        ssid = ssid,
        password = password,
        encryption = auth,
        modelId = modelId,
        sn = sn,
        mac = mac,
        name = name
    )

    fun toJson(): String = JSONObject().apply {
        put("ssid", ssid)
        put("password", password ?: JSONObject.NULL)
        put("auth", auth ?: JSONObject.NULL)
        put("modelId", modelId ?: JSONObject.NULL)
        put("sn", sn ?: JSONObject.NULL)
        put("mac", mac ?: JSONObject.NULL)
        put("name", name ?: JSONObject.NULL)
    }.toString()

    companion object {
        fun fromQr(qr: QrContent.HudWifi): SavedHudData = SavedHudData(
            ssid = qr.ssid,
            password = qr.password,
            auth = qr.encryption,
            modelId = qr.modelId,
            sn = qr.sn,
            mac = qr.mac,
            name = qr.name
        )

        fun fromJson(raw: String): SavedHudData? = try {
            val o = JSONObject(raw)
            val ssid = o.optString("ssid", "")
            if (ssid.isEmpty()) {
                null
            } else {
                SavedHudData(
                    ssid = ssid,
                    password = o.optStringOrNull("password"),
                    auth = o.optStringOrNull("auth"),
                    modelId = o.optStringOrNull("modelId"),
                    sn = o.optStringOrNull("sn"),
                    mac = o.optStringOrNull("mac"),
                    name = o.optStringOrNull("name")
                )
            }
        } catch (t: Throwable) {
            AppLog.e(TAG, "HUD salvato illeggibile", t)
            null
        }

        private fun JSONObject.optStringOrNull(key: String): String? {
            if (!has(key) || isNull(key)) return null
            val v = optString(key, "")
            return v.ifEmpty { null }
        }

        private const val TAG = "SavedHud"
    }
}

object SavedHud {
    private const val TAG = "SavedHud"
    private const val FILE = "motobit_hud"
    private const val KEY_HUD = "saved_hud"

    private var prefs: SharedPreferences? = null

    private val _current = MutableStateFlow<SavedHudData?>(null)
    val current: StateFlow<SavedHudData?> = _current.asStateFlow()

    fun init(context: Context) {
        if (prefs != null) return
        val p = context.applicationContext.getSharedPreferences(FILE, Context.MODE_PRIVATE)
        prefs = p
        val loaded = p.getString(KEY_HUD, null)?.let { SavedHudData.fromJson(it) }
        _current.value = loaded
        if (loaded != null) {
            AppLog.i(TAG, "HUD salvato: ${loaded.displayName} (${loaded.ssid}, mac=${loaded.mac})")
        } else {
            AppLog.i(TAG, "Nessun HUD salvato")
        }
    }

    fun save(qr: QrContent.HudWifi) {
        var data = SavedHudData.fromQr(qr)
        val previous = _current.value

        // Il MAC imparato dalla discovery deve SOPRAVVIVERE se il QR non lo
        // contiene.
        //
        // `save()` viene richiamata con lo STESSO QR a ogni ritorno in Home
        // (LaunchedEffect su hudQrValue) e anche ad ogni "Avvia": senza questo
        // merge ogni chiamata riscriveva `mac = null`, cancellando il MAC
        // appreso e costringendo a rifare la discovery (2-3 s) alla
        // connessione successiva. Sintomo: MAC assente in "Info tecniche".
        if (data.mac.isNullOrBlank() && previous != null && previous.ssid == data.ssid) {
            data = data.copy(mac = previous.mac)
            AppLog.i(TAG, "MAC preservato dal salvataggio precedente: ${data.mac}")
        }

        if (previous != null && previous.ssid == data.ssid && previous.mac == data.mac) {
            AppLog.i(TAG, "HUD invariato: ${data.displayName}")
        } else if (previous != null) {
            AppLog.i(
                TAG,
                "HUD sostituito: ${previous.displayName} -> ${data.displayName}"
            )
        } else {
            AppLog.i(TAG, "HUD memorizzato: ${data.displayName}")
        }

        _current.value = data
        prefs?.edit()?.putString(KEY_HUD, data.toJson())?.apply()
    }

    fun clear() {
        _current.value = null
        prefs?.edit()?.remove(KEY_HUD)?.apply()
        AppLog.w(TAG, "HUD salvato dimenticato")
    }

    /**
     * Rende persistente il MAC scoperto via discoverPeers: le connessioni
     * successive partono con il connect diretto, senza discovery (~2-3s
     * risparmiati per avvio e meno BUSY al primo tentativo).
     */
    fun updateMac(mac: String) {
        val current = _current.value ?: return
        if (mac.isBlank() || current.mac == mac) return
        val updated = current.copy(mac = mac)
        _current.value = updated
        prefs?.edit()?.putString(KEY_HUD, updated.toJson())?.apply()
        AppLog.i(TAG, "MAC memorizzato dall'ultima scansione: ${updated.displayName} -> $mac")
    }
}
