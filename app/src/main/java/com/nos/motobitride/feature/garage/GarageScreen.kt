package com.nos.motobitride.feature.garage

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Info
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.nos.motobitride.core.garage.DeadlineKind
import com.nos.motobitride.core.garage.DeadlineReminder
import com.nos.motobitride.core.garage.VehicleDeadline
import com.nos.motobitride.core.garage.VehicleStore
import com.nos.motobitride.core.garage.vehicleDisplayName
import com.nos.motobitride.core.link.SavedHud
import com.nos.motobitride.core.qr.QrContent
import com.nos.motobitride.core.settings.AppSettings
import com.nos.motobitride.ui.components.MotobitTopAppBar
import com.nos.motobitride.ui.i18n.AppLanguage
import com.nos.motobitride.ui.i18n.AppTexts
import java.time.LocalDate
import java.time.Month
import java.time.format.TextStyle
import java.util.Locale
import kotlin.math.roundToInt

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GarageScreen(
    hudQrValue: QrContent? = null,
    onNavigateHome: () -> Unit = {},
    onNavigateGarage: () -> Unit = {},
    onNavigateSettings: () -> Unit = {},
    onScanQr: () -> Unit = {}
) {
    val ctx = LocalContext.current
    val currentLang by AppSettings.language.collectAsState()
    val savedHud by SavedHud.current.collectAsState()
    val profile by VehicleStore.current.collectAsState()
    val notificationsEnabled by AppSettings.notificationsEnabled.collectAsState()
    val daysBefore by AppSettings.daysBefore.collectAsState()

    var showDayInfo by remember { mutableStateOf(false) }

    // QR scansionato dalla pagina Garage: salva solo le credenziali.
    LaunchedEffect(hudQrValue) {
        if (hudQrValue is QrContent.HudWifi) {
            SavedHud.save(hudQrValue)
        }
    }

    val hudName = savedHud?.displayName
    val displayName = vehicleDisplayName(profile, hudName)

    Scaffold(
        topBar = {
            MotobitTopAppBar(
                onNavigateHome = onNavigateHome,
                onNavigateGarage = onNavigateGarage,
                onNavigateSettings = onNavigateSettings
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .padding(innerPadding)
                .fillMaxSize()
                .padding(horizontal = 16.dp)
                .verticalScroll(rememberScrollState())
        ) {
            Spacer(Modifier.height(8.dp))
            Text(
                text = AppTexts.get("garage_title", currentLang),
                style = MaterialTheme.typography.headlineSmall
            )
            Spacer(Modifier.height(4.dp))
            Text(
                text = AppTexts.get("garage_sub", currentLang),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(16.dp))

            // ------------------------------------------------------------
            // Card: Veicolo
            // ------------------------------------------------------------
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text(
                        text = AppTexts.get("garage_vehicle_card", currentLang),
                        style = MaterialTheme.typography.titleSmall
                    )

                    OutlinedTextField(
                        value = profile.name,
                        onValueChange = { VehicleStore.setName(it) },
                        label = { Text(AppTexts.get("garage_name_label", currentLang)) },
                        placeholder = { Text(displayName) },
                        supportingText = {
                            Text(AppTexts.get("garage_name_hint", currentLang))
                        },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )

                    OutlinedTextField(
                        value = profile.plate,
                        onValueChange = { VehicleStore.setPlate(it.uppercase()) },
                        label = { Text(AppTexts.get("garage_plate_label", currentLang)) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )

                    HorizontalDivider()

                    DeadlineEditor(
                        kind = DeadlineKind.REVISION,
                        deadline = profile.revision,
                        lang = currentLang,
                        onInfo = { showDayInfo = true }
                    )
                    DeadlineEditor(
                        kind = DeadlineKind.TAX,
                        deadline = profile.tax,
                        lang = currentLang,
                        onInfo = { showDayInfo = true }
                    )
                    DeadlineEditor(
                        kind = DeadlineKind.INSURANCE,
                        deadline = profile.insurance,
                        lang = currentLang,
                        onInfo = { showDayInfo = true }
                    )
                }
            }

            Spacer(Modifier.height(12.dp))

            // ------------------------------------------------------------
            // Card: Informazioni (i tre box unificati)
            // ------------------------------------------------------------
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Text(
                        text = AppTexts.get("garage_info_card", currentLang),
                        style = MaterialTheme.typography.titleSmall
                    )

                    val hud = savedHud
                    if (hud == null) {
                        Text(
                            text = AppTexts.get("hud_no_saved", currentLang),
                            style = MaterialTheme.typography.bodyMedium
                        )
                    } else {
                        Text(
                            text = hud.displayName,
                            style = MaterialTheme.typography.titleSmall
                        )
                        Text("SSID: ${hud.ssid}", style = MaterialTheme.typography.bodySmall)
                        Text("MAC: ${hud.mac ?: "-"}", style = MaterialTheme.typography.bodySmall)
                        Text(
                            "Modello: ${hud.modelId ?: "-"}   SN: ${hud.sn ?: "-"}",
                            style = MaterialTheme.typography.bodySmall
                        )
                    }

                    Spacer(Modifier.height(4.dp))

                    if (hud != null) {
                        OutlinedButton(onClick = { SavedHud.clear() }) {
                            Text(AppTexts.get("garage_forget", currentLang))
                        }
                    } else {
                        OutlinedButton(onClick = onScanQr) {
                            Text(AppTexts.get("garage_scan_qr", currentLang))
                        }
                    }
                }
            }

            Spacer(Modifier.height(12.dp))

            // ------------------------------------------------------------
            // Card: Avvisi di scadenza
            // ------------------------------------------------------------
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = AppTexts.get("garage_notifications_card", currentLang),
                        style = MaterialTheme.typography.titleSmall
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text(
                                text = AppTexts.get("garage_notifications_switch", currentLang),
                                style = MaterialTheme.typography.bodyLarge
                            )
                            Text(
                                text = AppTexts.get("garage_notifications_desc", currentLang),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Switch(
                            checked = notificationsEnabled,
                            onCheckedChange = { AppSettings.setNotificationsEnabled(it) }
                        )
                    }

                    var daysValue by remember(daysBefore) { mutableStateOf(daysBefore.toFloat()) }
                    Text(
                        text = AppTexts.get(
                            "garage_days_before_label",
                            currentLang,
                            daysValue.roundToInt()
                        ),
                        style = MaterialTheme.typography.bodyLarge
                    )
                    Slider(
                        value = daysValue,
                        onValueChange = { daysValue = it },
                        onValueChangeFinished = {
                            AppSettings.setDaysBefore(daysValue.roundToInt())
                        },
                        valueRange = AppSettings.MIN_DAYS_BEFORE.toFloat()..
                                AppSettings.MAX_DAYS_BEFORE.toFloat(),
                        steps = AppSettings.MAX_DAYS_BEFORE - AppSettings.MIN_DAYS_BEFORE - 1,
                        enabled = notificationsEnabled,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }

            Spacer(Modifier.height(28.dp))
        }
    }

    if (showDayInfo) {
        AlertDialog(
            onDismissRequest = { showDayInfo = false },
            title = { Text(AppTexts.get("garage_deadline_info_title", currentLang)) },
            text = { Text(AppTexts.get("garage_deadline_info_text", currentLang)) },
            confirmButton = {
                TextButton(onClick = { showDayInfo = false }) {
                    Text("OK")
                }
            }
        )
    }
}

/**
 * Riga di una scadenza: etichetta + icona info, poi mese e giorno affiancati,
 * sotto il conto alla rovescia.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DeadlineEditor(
    kind: DeadlineKind,
    deadline: VehicleDeadline,
    lang: AppLanguage,
    onInfo: () -> Unit
) {
    val label = when (kind) {
        DeadlineKind.REVISION -> AppTexts.get("garage_deadline_revision", lang)
        DeadlineKind.TAX -> AppTexts.get("garage_deadline_tax", lang)
        DeadlineKind.INSURANCE -> AppTexts.get("garage_deadline_insurance", lang)
    }

    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = label,
                style = MaterialTheme.typography.bodyLarge,
                modifier = Modifier.weight(1f)
            )
            IconButton(onClick = onInfo) {
                Icon(
                    imageVector = Icons.Default.Info,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary
                )
            }
        }

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            // Mese
            var monthExpanded by remember { mutableStateOf(false) }
            ExposedDropdownMenuBox(
                expanded = monthExpanded,
                onExpandedChange = { monthExpanded = !monthExpanded },
                modifier = Modifier.weight(1f)
            ) {
                OutlinedTextField(
                    value = if (deadline.month in 1..12)
                        monthName(deadline.month, lang) else "—",
                    onValueChange = {},
                    readOnly = true,
                    label = { Text(AppTexts.get("garage_month_label", lang)) },
                    trailingIcon = {
                        ExposedDropdownMenuDefaults.TrailingIcon(expanded = monthExpanded)
                    },
                    colors = ExposedDropdownMenuDefaults.outlinedTextFieldColors(),
                    modifier = Modifier
                        .menuAnchor()
                        .fillMaxWidth()
                )
                ExposedDropdownMenu(
                    expanded = monthExpanded,
                    onDismissRequest = { monthExpanded = false }
                ) {
                    DropdownMenuItem(
                        text = { Text("—") },
                        onClick = {
                            VehicleStore.setDeadline(kind, 0, 0)
                            monthExpanded = false
                        }
                    )
                    (1..12).forEach { m ->
                        DropdownMenuItem(
                            text = { Text(monthName(m, lang)) },
                            onClick = {
                                VehicleStore.setDeadline(kind, m, deadline.day)
                                monthExpanded = false
                            }
                        )
                    }
                }
            }

            // Giorno
            var dayExpanded by remember { mutableStateOf(false) }
            ExposedDropdownMenuBox(
                expanded = dayExpanded,
                onExpandedChange = { dayExpanded = !dayExpanded },
                modifier = Modifier.weight(1f)
            ) {
                OutlinedTextField(
                    value = if (deadline.day in 1..31) "${deadline.day}" else "0",
                    onValueChange = {},
                    readOnly = true,
                    label = { Text(AppTexts.get("garage_day_label", lang)) },
                    trailingIcon = {
                        ExposedDropdownMenuDefaults.TrailingIcon(expanded = dayExpanded)
                    },
                    colors = ExposedDropdownMenuDefaults.outlinedTextFieldColors(),
                    modifier = Modifier
                        .menuAnchor()
                        .fillMaxWidth()
                )
                ExposedDropdownMenu(
                    expanded = dayExpanded,
                    onDismissRequest = { dayExpanded = false }
                ) {
                    DropdownMenuItem(
                        text = { Text("0 — " + AppTexts.get("garage_day_whole_month", lang)) },
                        onClick = {
                            VehicleStore.setDeadline(kind, deadline.month, 0)
                            dayExpanded = false
                        }
                    )
                    (1..31).forEach { d ->
                        DropdownMenuItem(
                            text = { Text("$d") },
                            onClick = {
                                VehicleStore.setDeadline(kind, deadline.month, d)
                                dayExpanded = false
                            }
                        )
                    }
                }
            }
        }

        // Conto alla rovescia + eventuale riattivazione
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = countdownText(deadline, lang),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.weight(1f)
            )
            if (deadline.dismissedForYear != 0) {
                TextButton(onClick = { VehicleStore.rearm(kind) }) {
                    Text(
                        text = AppTexts.get("garage_rearm", lang),
                        style = MaterialTheme.typography.labelSmall
                    )
                }
            }
        }
    }
}

private fun monthName(month: Int, lang: AppLanguage): String {
    val locale = if (lang == AppLanguage.ENGLISH) Locale.ENGLISH else Locale.ITALIAN
    val name = Month.of(month).getDisplayName(TextStyle.FULL, locale)
    return name.replaceFirstChar { it.uppercase(locale) }
}

private fun countdownText(deadline: VehicleDeadline, lang: AppLanguage): String {
    val days = DeadlineReminder.daysUntil(deadline, LocalDate.now())
        ?: return AppTexts.get("garage_countdown_none", lang)
    return when {
        days == 0L -> AppTexts.get("garage_countdown_today", lang)
        days < 60L -> AppTexts.get("garage_countdown_days", lang, days.toInt())
        else -> AppTexts.get("garage_countdown_months", lang, (days / 30).toInt())
    }
}
