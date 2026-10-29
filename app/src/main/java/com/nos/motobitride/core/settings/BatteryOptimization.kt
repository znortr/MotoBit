package com.nos.motobitride.core.settings

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.PowerManager
import android.provider.Settings

/**
 * Esclusione dell'app dal risparmio energetico.
 *
 * Su sessioni lunghe Doze puo' terminare il foreground service di cattura:
 * quando muore, Android **revoca la MediaProjection** e serve un nuovo consenso.
 * Escludere l'app riduce drasticamente questi eventi.
 */
object BatteryOptimization {

    fun isIgnoring(context: Context): Boolean {
        val pm = context.getSystemService(PowerManager::class.java) ?: return false
        return pm.isIgnoringBatteryOptimizations(context.packageName)
    }

    /** Dialog di sistema per concedere l'esenzione (richiede il permesso omonimo). */
    @SuppressLint("BatteryLife")
    fun requestIgnoreIntent(context: Context): Intent =
        Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS)
            .setData(Uri.parse("package:${context.packageName}"))

    /**
     * Non esiste un'API per revocare l'esenzione: si apre la lista di sistema
     * dove l'utente puo' rimuoverla manualmente.
     */
    fun listSettingsIntent(): Intent =
        Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)
}
