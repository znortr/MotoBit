package com.nos.motobitride.feature.settings

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
            delay(1_500)
        }
    }

    val allOk = locationGranted && gpsEnabled && notifGranted && batteryExempt

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
