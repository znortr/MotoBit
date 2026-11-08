package com.nos.motobitride.core.apps

import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.graphics.drawable.Drawable
import com.nos.motobitride.core.log.AppLog

/** Un'app avviabile con la sua icona originale. */
data class AppInfo(
    val packageName: String,
    val label: String,
    val icon: Drawable
)

/**
 * Elenco e avvio delle app installate.
 *
 * Visibilità: da Android 11 servono le `<queries>` nel manifest
 * (ACTION_MAIN / CATEGORY_LAUNCHER), altrimenti si vedrebbero solo poche app.
 */
object InstalledApps {

    private const val TAG = "InstalledApps"

    /**
     * App di sistema che hanno un launcher ma non servono a nulla sul TFT.
     * Volutamente breve: tutto il resto resta selezionabile.
     */
    private val DENY = setOf(
        "com.android.settings",
        "com.android.permissioncontroller",
        "com.google.android.permissioncontroller",
        "com.android.packageinstaller",
        "com.google.android.packageinstaller",
        "com.android.shell",
        "com.android.systemui",
        "com.google.android.setupwizard",
        "com.android.provision"
    )

    /** Elenco delle app avviabili, ordinate per nome. */
    fun load(context: Context): List<AppInfo> {
        val pm = context.packageManager
        val intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        val self = context.packageName

        val packages = runCatching { pm.queryIntentActivities(intent, 0) }
            .getOrElse {
                AppLog.w(TAG, "queryIntentActivities fallita: ${it.message}")
                return emptyList()
            }
            .asSequence()
            .mapNotNull { it.activityInfo?.packageName }
            .distinct()
            .filter { it != self && it !in DENY }
            .toList()

        return packages
            .mapNotNull { pkg ->
                runCatching {
                    val ai: ApplicationInfo = pm.getApplicationInfo(pkg, 0)
                    AppInfo(
                        packageName = pkg,
                        label = pm.getApplicationLabel(ai).toString(),
                        icon = pm.getApplicationIcon(ai)
                    )
                }.getOrNull()
            }
            .sortedBy { it.label.lowercase() }
    }

    /** Solo i pacchetti (per la potatura di quelli disinstallati). */
    fun installedPackages(apps: List<AppInfo>): Set<String> =
        apps.map { it.packageName }.toSet()

    /** Apre l'app. Restituisce false se non ha un launch intent. */
    fun launch(context: Context, packageName: String): Boolean {
        val pm = context.packageManager
        val intent = pm.getLaunchIntentForPackage(packageName) ?: run {
            AppLog.w(TAG, "Nessun launch intent per $packageName")
            return false
        }
        intent.addFlags(
            Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED
        )
        return runCatching {
            context.startActivity(intent)
            AppLog.i(TAG, "Aperta app $packageName")
            true
        }.getOrElse {
            AppLog.e(TAG, "Avvio app $packageName fallito", it)
            false
        }
    }
}
