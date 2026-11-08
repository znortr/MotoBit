package com.nos.motobitride.core.apps

import android.content.Context
import android.content.SharedPreferences
import com.nos.motobitride.core.log.AppLog
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONArray

/**
 * App scelte per la Home, **in ordine di visualizzazione**.
 *
 * Persistenza: JSON in SharedPreferences (stesso schema di `SavedHud` e
 * `VehicleStore`). L'ordine conta, quindi NON si usa `putStringSet`.
 */
object SavedApps {

    private const val TAG = "SavedApps"
    private const val FILE = "motobit_apps"
    private const val KEY_LIST = "apps"

    private var prefs: SharedPreferences? = null

    private val _packages = MutableStateFlow<List<String>>(emptyList())
    val packages: StateFlow<List<String>> = _packages.asStateFlow()

    fun init(context: Context) {
        if (prefs != null) return
        val p = context.applicationContext.getSharedPreferences(FILE, Context.MODE_PRIVATE)
        prefs = p
        _packages.value = read(p)
        AppLog.i(TAG, "App in Home: ${_packages.value.size}")
    }

    /** Aggiunge (in coda) se non già presente. */
    fun add(packageName: String) {
        if (packageName.isBlank()) return
        val current = _packages.value
        if (current.contains(packageName)) return
        write(current + packageName)
    }

    fun remove(packageName: String) {
        val current = _packages.value
        if (!current.contains(packageName)) return
        write(current - packageName)
    }

    fun toggle(packageName: String) {
        if (_packages.value.contains(packageName)) remove(packageName) else add(packageName)
    }

    /** Rimuove i pacchetti non più installati (chiamata all'avvio della Home). */
    fun pruneMissing(installedPackages: Set<String>) {
        val current = _packages.value
        val valid = current.filter { installedPackages.contains(it) }
        if (valid.size != current.size) {
            AppLog.i(TAG, "Rimosse ${current.size - valid.size} app non più installate")
            write(valid)
        }
    }

    private fun write(list: List<String>) {
        _packages.value = list
        val array = JSONArray().apply { list.forEach { put(it) } }
        prefs?.edit()?.putString(KEY_LIST, array.toString())?.apply()
        AppLog.i(TAG, "App in Home -> ${list.size}")
    }

    private fun read(p: SharedPreferences): List<String> {
        val raw = p.getString(KEY_LIST, null) ?: return emptyList()
        return runCatching {
            val array = JSONArray(raw)
            (0 until array.length()).mapNotNull { i ->
                array.optString(i, "").takeIf { it.isNotBlank() }
            }
        }.getOrElse {
            AppLog.w(TAG, "Lista app illeggibile: ${it.message}")
            emptyList()
        }
    }
}
