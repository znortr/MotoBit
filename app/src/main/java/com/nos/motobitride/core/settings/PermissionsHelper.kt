package com.nos.motobitride.core.settings

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.location.LocationManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.core.content.ContextCompat
import androidx.core.location.LocationManagerCompat

/**
 * Verifiche e intent per i permessi/impostazioni che condizionano la
 * riconnessione automatica in background.
 *
 * Perche' servono:
 *  - `WifiP2pManager` (Wi-Fi Direct) e' considerato un'API "location-gated" da
 *    Android 10: senza permesso di posizione **e** con la posizione di sistema
 *    spenta, `discoverPeers()`/`connect()` falliscono con ERROR anche in
 *    foreground.
 *  - In background le stesse API sono consentite solo se l'app ha un foreground
 *    service di tipo `location` (vedi ScreenCaptureService) oppure il permesso
 *    di posizione in background.
 *  - L'esenzione dal risparmio energetico evita che il sistema congeli i timer
 *    di riconnessione.
 */
object PermissionsHelper {

    /** Permessi runtime da chiedere per far funzionare link + streaming. */
    fun requiredRuntimePermissions(): Array<String> {
        val list = mutableListOf<String>()
        list += Manifest.permission.ACCESS_FINE_LOCATION
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            list += Manifest.permission.NEARBY_WIFI_DEVICES
            list += Manifest.permission.POST_NOTIFICATIONS
        }
        return list.toTypedArray()
    }

    fun hasLocationPermission(context: Context): Boolean =
        ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED

    fun hasNearbyWifiPermission(context: Context): Boolean =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.NEARBY_WIFI_DEVICES
            ) == PackageManager.PERMISSION_GRANTED
        } else {
            true
        }

    fun hasNotificationPermission(context: Context): Boolean =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
        } else {
            true
        }

    /**
     * Posizione di sistema (GPS) accesa. Serve davvero: con la posizione spenta
     * Android rifiuta `discoverPeers()`/`requestPeers()` anche con i permessi
     * concessi. Non e' un permesso, e' un interruttore di sistema: si apre la
     * schermata dedicata, non compare nessun dialog.
     */
    fun isLocationEnabled(context: Context): Boolean {
        val lm = context.getSystemService(Context.LOCATION_SERVICE) as? LocationManager
            ?: return false
        return runCatching { LocationManagerCompat.isLocationEnabled(lm) }.getOrDefault(false)
    }

    /** Schermata di sistema per accendere la posizione. */
    fun locationSettingsIntent(): Intent =
        Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS)

    /** Schermata di sistema con i permessi della nostra app. */
    fun appDetailsIntent(context: Context): Intent =
        Intent(
            Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
            Uri.parse("package:${context.packageName}")
        )

    /** Riepilogo leggibile dello stato (per il log e per la UI). */
    fun describe(context: Context): String = buildString {
        append("pos=").append(if (hasLocationPermission(context)) "OK" else "NO")
        append(" gps=").append(if (isLocationEnabled(context)) "ON" else "OFF")
        append(" nearby=").append(if (hasNearbyWifiPermission(context)) "OK" else "NO")
        append(" notif=").append(if (hasNotificationPermission(context)) "OK" else "NO")
        append(" battery=").append(if (BatteryOptimization.isIgnoring(context)) "ESENTE" else "OTTIMIZZATA")
    }
}
