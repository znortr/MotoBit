package com.nos.motobitride.feature.settings

import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.nos.motobitride.core.settings.AppSettings
import com.nos.motobitride.core.settings.BatteryOptimization
import com.nos.motobitride.core.settings.PermissionsHelper
import com.nos.motobitride.ui.i18n.AppLanguage
import com.nos.motobitride.ui.i18n.AppTexts
import com.nos.motobitride.ui.shizuku.actionKey
import com.nos.motobitride.ui.shizuku.labelKey
import com.nos.motobitride.ui.shizuku.performAction
import com.nos.motobitride.ui.shizuku.rememberShizukuStatus
import kotlinx.coroutines.delay

/**
 * Pagina "Permessi": riepilogo dei permessi/impostazioni che condizionano la
 * riconnessione automatica in background, con l'azione giusta per ciascuno.
 */
@Composable
fun SettingsPermissionsScreen(
    onBack: () -> Unit = {}
) {
    val ctx = LocalContext.current
    val currentLang by AppSettings.language.collectAsState()

    var batteryExempt by remember { mutableStateOf(BatteryOptimization.isIgnoring(ctx)) }

    val batteryLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) {
        batteryExempt = BatteryOptimization.isIgnoring(ctx)
    }

    SettingsSubPage(
        title = AppTexts.get("settings_cat_permissions_title", currentLang),
        subtitle = AppTexts.get("settings_cat_permissions_sub", currentLang),
        backLabel = AppTexts.get("common_back", currentLang),
        onBack = onBack
    ) {
        PermissionsContent(
            currentLang = currentLang,
            batteryExempt = batteryExempt,
            onRequestBattery = {
                runCatching {
                    batteryLauncher.launch(BatteryOptimization.requestIgnoreIntent(ctx))
                }
            }
        )
    }
}

@Composable
private fun PermissionsContent(
    currentLang: AppLanguage,
    batteryExempt: Boolean,
    onRequestBattery: () -> Unit
) {
    val ctx = LocalContext.current

    var locationGranted by remember {
        mutableStateOf(PermissionsHelper.hasLocationPermission(ctx))
    }
    var gpsEnabled by remember {
        mutableStateOf(PermissionsHelper.isLocationEnabled(ctx))
    }
    var notifGranted by remember {
        mutableStateOf(PermissionsHelper.hasNotificationPermission(ctx))
    }
    // Permesso "Visualizza sopra altre app": serve al pulsante flottante AVVIA.
    var overlayGranted by remember { mutableStateOf(Settings.canDrawOverlays(ctx)) }

    // Shizuku (opzionale): stato osservato solo se l'interruttore generale è ON.
    val shizukuEnabled by AppSettings.shizukuEnabled.collectAsState()
    val shizukuStatus = rememberShizukuStatus(shizukuEnabled)

    val runtimeLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) {
        locationGranted = PermissionsHelper.hasLocationPermission(ctx)
        notifGranted = PermissionsHelper.hasNotificationPermission(ctx)
        gpsEnabled = PermissionsHelper.isLocationEnabled(ctx)
    }

    val gpsLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) {
        gpsEnabled = PermissionsHelper.isLocationEnabled(ctx)
    }

    // Rinfresca lo stato quando l'utente torna da una schermata di sistema.
    LaunchedEffect(Unit) {
        while (true) {
            locationGranted = PermissionsHelper.hasLocationPermission(ctx)
            gpsEnabled = PermissionsHelper.isLocationEnabled(ctx)
            notifGranted = PermissionsHelper.hasNotificationPermission(ctx)
            overlayGranted = Settings.canDrawOverlays(ctx)
            delay(1_500)
        }
    }

    val allOk = locationGranted && gpsEnabled && notifGranted && batteryExempt && overlayGranted

    Text(
        text = AppTexts.get("perm_section_desc", currentLang),
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant
    )

    if (allOk) {
        Text(
            text = "\u2705 " + AppTexts.get("perm_all_ok", currentLang),
            style = MaterialTheme.typography.bodyMedium,
            color = Color(0xFF4CAF50)
        )
    }

    PermissionRow(
        title = AppTexts.get("perm_location_title", currentLang),
        desc = AppTexts.get("perm_location_desc", currentLang),
        ok = locationGranted,
        actionLabel = if (locationGranted) null else AppTexts.get("perm_grant", currentLang),
        onAction = {
            runtimeLauncher.launch(PermissionsHelper.requiredRuntimePermissions())
        }
    )

    HorizontalDivider()

    PermissionRow(
        title = AppTexts.get("perm_gps_title", currentLang),
        desc = AppTexts.get("perm_gps_desc", currentLang),
        ok = gpsEnabled,
        actionLabel = if (gpsEnabled) null else AppTexts.get("perm_enable", currentLang),
        onAction = {
            runCatching { gpsLauncher.launch(PermissionsHelper.locationSettingsIntent()) }
        }
    )

    HorizontalDivider()

    PermissionRow(
        title = AppTexts.get("perm_notif_title", currentLang),
        desc = AppTexts.get("perm_notif_desc", currentLang),
        ok = notifGranted,
        actionLabel = if (notifGranted) null else AppTexts.get("perm_grant", currentLang),
        onAction = {
            runtimeLauncher.launch(PermissionsHelper.requiredRuntimePermissions())
        }
    )

    HorizontalDivider()

    PermissionRow(
        title = AppTexts.get("perm_battery_title", currentLang),
        desc = AppTexts.get("perm_battery_desc", currentLang),
        ok = batteryExempt,
        actionLabel = if (batteryExempt) null else AppTexts.get("perm_disable", currentLang),
        onAction = onRequestBattery
    )

    HorizontalDivider()

    // Permesso di sistema (non un dialog): il tocco apre la pagina dedicata.
    PermissionRow(
        title = AppTexts.get("perm_overlay_title", currentLang),
        desc = AppTexts.get("perm_overlay_desc", currentLang),
        ok = overlayGranted,
        actionLabel = if (overlayGranted) null else AppTexts.get("perm_grant", currentLang),
        onAction = {
            // Pagina dell'app (Impostazioni -> App -> MotoBit): da qui si
            // accede ai permessi speciali, che la pagina "standard" spesso non
            // mostra in modo completo sulle ROM cinesi.
            val appDetails = Intent(
                Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                Uri.parse("package:${ctx.packageName}")
            )
            val overlayPage = Intent(
                Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                Uri.parse("package:${ctx.packageName}")
            )
            val opened = runCatching { ctx.startActivity(appDetails) }.isSuccess ||
                    runCatching { ctx.startActivity(overlayPage) }.isSuccess
            if (!opened) {
                runCatching { ctx.startActivity(overlayPage) }
            }
        }
    )

    HorizontalDivider()

    // Shizuku: opzionale. Serve alla sola pagina Risoluzione (fase 2).
    PermissionRow(
        title = AppTexts.get("shizuku_section_title", currentLang),
        desc = AppTexts.get(shizukuStatus.labelKey(), currentLang),
        ok = shizukuStatus.isReady,
        actionLabel = shizukuStatus.actionKey()?.let { AppTexts.get(it, currentLang) },
        onAction = { shizukuStatus.performAction() }
    )
}

@Composable
private fun PermissionRow(
    title: String,
    desc: String,
    ok: Boolean,
    actionLabel: String?,
    onAction: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(Modifier.weight(1f)) {
            Text(text = title, style = MaterialTheme.typography.bodyLarge)
            Text(
                text = desc,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        if (ok) {
            Text(
                text = "\u2713",
                color = Color(0xFF4CAF50),
                style = MaterialTheme.typography.titleMedium
            )
        } else if (actionLabel != null) {
            OutlinedButton(onClick = onAction) {
                Text(actionLabel)
            }
        }
    }
}
