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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Info
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.nos.motobitride.core.garage.DeadlineKind
import com.nos.motobitride.core.garage.DeadlineReminder
import com.nos.motobitride.core.garage.VehicleDeadline
import com.nos.motobitride.core.garage.VehicleStore
import com.nos.motobitride.core.garage.vehicleDisplayName
import com.nos.motobitride.core.hud.HudSession
import com.nos.motobitride.core.link.SavedHud
import com.nos.motobitride.core.qr.QrContent
import com.nos.motobitride.core.settings.AppSettings
import com.nos.motobitride.feature.settings.SettingsCategoryCard
import com.nos.motobitride.ui.components.MotobitTopAppBar
import com.nos.motobitride.ui.i18n.AppLanguage
import com.nos.motobitride.ui.i18n.AppTexts
import java.time.LocalDate
import java.time.Month
import java.time.format.TextStyle
import java.util.Locale
import kotlin.math.roundToInt

/**
 * Garage: una sola card, pulita.
 *
 *  - Nome moto e targa in evidenza (H1/H2), modificabili con la matita → dialog
 *  - Pulsante "Scadenze" (stile Impostazioni) → dialog con le 3 scadenze e lo
 *    switch degli avvisi; una sola icona ⓘ in alto a destra
 *  - Pulsante "Info tecniche" → dialog con i dati dell'HUD
 *  - In fondo alla card: "Dimentica" (o "Scansiona QR" se non c'è nulla)
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GarageScreen(
    hudQrValue: QrContent? = null,
    onNavigateHome: () -> Unit = {},
    onNavigateGarage: () -> Unit = {},
    onNavigateSettings: () -> Unit = {},
    onScanQr: () -> Unit = {}
) {
    val currentLang by AppSettings.language.collectAsState()
    val savedHud by SavedHud.current.collectAsState()
    val profile by VehicleStore.current.collectAsState()

    var showEdit by remember { mutableStateOf(false) }
    var showDeadlines by remember { mutableStateOf(false) }
    var showInfo by remember { mutableStateOf(false) }

    LaunchedEffect(hudQrValue) {
        if (hudQrValue is QrContent.HudWifi) SavedHud.save(hudQrValue)
    }

    val displayName = vehicleDisplayName(profile, savedHud?.displayName)

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

            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.30f)
                ),
                elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(18.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    // ---------------- Intestazione: nome + targa ----------------
                    Row(verticalAlignment = Alignment.Top) {
                        Column(Modifier.weight(1f)) {
                            Text(
                                text = displayName,
                                style = MaterialTheme.typography.headlineSmall,
                                fontWeight = FontWeight.Bold
                            )
                            Spacer(Modifier.height(2.dp))
                            Text(
                                text = profile.plate.ifBlank { "—" },
                                style = MaterialTheme.typography.titleLarge,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        IconButton(onClick = { showEdit = true }) {
                            Icon(
                                imageVector = Icons.Default.Edit,
                                contentDescription = AppTexts.get("garage_edit_title", currentLang),
                                tint = MaterialTheme.colorScheme.primary
                            )
                        }
                    }

                    // ---------------- Pulsante Scadenze ----------------
                    SettingsCategoryCard(
                        title = AppTexts.get("garage_deadlines_button", currentLang),
                        subtitle = deadlinesSummary(profile, currentLang),
                        icon = Icons.Default.DateRange,
                        accent = Color(0xFFFF9F43),
                        onClick = { showDeadlines = true }
                    )

                    // ---------------- Pulsante Info tecniche ----------------
                    SettingsCategoryCard(
                        title = AppTexts.get("garage_info_button", currentLang),
                        subtitle = AppTexts.get("garage_info_sub", currentLang),
                        icon = Icons.Default.Info,
                        accent = Color(0xFF4C8DFF),
                        onClick = { showInfo = true }
                    )

                    HorizontalDivider()

                    // ---------------- In fondo: Dimentica / Scansiona QR -------
                    if (savedHud != null) {
                        OutlinedButton(
                            onClick = { SavedHud.clear() },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(AppTexts.get("garage_forget", currentLang))
                        }
                    } else {
                        OutlinedButton(
                            onClick = onScanQr,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(AppTexts.get("garage_scan_qr", currentLang))
                        }
                    }
                }
            }

            Spacer(Modifier.height(28.dp))
        }
    }

    if (showEdit) {
        EditVehicleDialog(
            initialName = profile.name,
            initialPlate = profile.plate,
            nameHint = displayName,
            lang = currentLang,
            onDismiss = { showEdit = false },
            onSave = { name, plate ->
                VehicleStore.setName(name)
                VehicleStore.setPlate(plate)
                showEdit = false
            }
        )
    }

    if (showDeadlines) {
        DeadlinesDialog(
            lang = currentLang,
            onDismiss = { showDeadlines = false }
        )
    }

    if (showInfo) {
        TechnicalInfoDialog(
            lang = currentLang,
            onDismiss = { showInfo = false }
        )
    }
}

// ---------------------------------------------------------------------------
// Dialog: nome e targa
// ---------------------------------------------------------------------------

@Composable
private fun EditVehicleDialog(
    initialName: String,
    initialPlate: String,
    nameHint: String,
    lang: AppLanguage,
    onDismiss: () -> Unit,
    onSave: (String, String) -> Unit
) {
    var name by remember { mutableStateOf(initialName) }
    var plate by remember { mutableStateOf(initialPlate) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(AppTexts.get("garage_edit_title", lang)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text(AppTexts.get("garage_name_label", lang)) },
                    placeholder = { Text(nameHint) },
                    supportingText = { Text(AppTexts.get("garage_name_hint", lang)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = plate,
                    onValueChange = { plate = it.uppercase() },
                    label = { Text(AppTexts.get("garage_plate_label", lang)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            TextButton(onClick = { onSave(name.trim(), plate.trim()) }) {
                Text(AppTexts.get("common_save", lang))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(AppTexts.get("common_cancel", lang))
            }
        }
    )
}

// ---------------------------------------------------------------------------
// Dialog: scadenze + avvisi
// ---------------------------------------------------------------------------

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DeadlinesDialog(
    lang: AppLanguage,
    onDismiss: () -> Unit
) {
    val profile by VehicleStore.current.collectAsState()
    val notificationsEnabled by AppSettings.notificationsEnabled.collectAsState()
    val daysBefore by AppSettings.daysBefore.collectAsState()

    var showInfoText by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = AppTexts.get("garage_deadlines_button", lang),
                    modifier = Modifier.weight(1f)
                )
                IconButton(onClick = { showInfoText = !showInfoText }) {
                    Icon(
                        imageVector = Icons.Default.Info,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary
                    )
                }
            }
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                if (showInfoText) {
                    Text(
                        text = AppTexts.get("garage_deadline_info_text", lang),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    HorizontalDivider()
                }

                DeadlineEditor(DeadlineKind.REVISION, profile.revision, lang, onInfo = null)
                DeadlineEditor(DeadlineKind.TAX, profile.tax, lang, onInfo = null)
                DeadlineEditor(DeadlineKind.INSURANCE, profile.insurance, lang, onInfo = null)

                HorizontalDivider()

                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(
                            text = AppTexts.get("garage_notifications_switch", lang),
                            style = MaterialTheme.typography.bodyLarge
                        )
                        Text(
                            text = AppTexts.get("garage_notifications_desc", lang),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Switch(
                        checked = notificationsEnabled,
                        onCheckedChange = { AppSettings.setNotificationsEnabled(it) }
                    )
                }

                if (notificationsEnabled) {
                    var daysValue by remember(daysBefore) { mutableStateOf(daysBefore.toFloat()) }
                    Text(
                        text = AppTexts.get(
                            "garage_days_before_label",
                            lang,
                            daysValue.roundToInt()
                        ),
                        style = MaterialTheme.typography.bodyMedium
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
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text(AppTexts.get("common_close", lang))
            }
        }
    )
}

// ---------------------------------------------------------------------------
// Dialog: info tecniche
// ---------------------------------------------------------------------------

@Composable
private fun TechnicalInfoDialog(
    lang: AppLanguage,
    onDismiss: () -> Unit
) {
    val savedHud by SavedHud.current.collectAsState()
    val resolution = HudSession.stream?.let { "${it.negotiatedWidth}×${it.negotiatedHeight}" }
        ?: "—"

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(AppTexts.get("garage_info_button", lang)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                val hud = savedHud
                if (hud == null) {
                    Text(
                        text = AppTexts.get("hud_no_saved", lang),
                        style = MaterialTheme.typography.bodyMedium
                    )
                } else {
                    InfoRow(AppTexts.get("garage_info_hud", lang), hud.displayName)
                    InfoRow("SSID", hud.ssid)
                    InfoRow("MAC", hud.mac ?: "—")
                    InfoRow(AppTexts.get("garage_info_model", lang), hud.modelId ?: "—")
                    InfoRow("S/N", hud.sn ?: "—")
                }
                InfoRow(AppTexts.get("garage_info_resolution", lang), resolution)
                InfoRow(
                    AppTexts.get("garage_info_protocol", lang),
                    "Carbit / EasyConn (PXC + MediaStream)"
                )
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text(AppTexts.get("common_close", lang))
            }
        }
    )
}

@Composable
private fun InfoRow(label: String, value: String) {
    Row(Modifier.fillMaxWidth()) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.width(110.dp)
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium
        )
    }
}

// ---------------------------------------------------------------------------
// Riga di una scadenza (mese + giorno + conto alla rovescia)
// ---------------------------------------------------------------------------

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DeadlineEditor(
    kind: DeadlineKind,
    deadline: VehicleDeadline,
    lang: AppLanguage,
    onInfo: (() -> Unit)?
) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = deadlineLabel(kind, lang),
                style = MaterialTheme.typography.bodyLarge,
                modifier = Modifier.weight(1f)
            )
            if (onInfo != null) {
                IconButton(onClick = onInfo) {
                    Icon(
                        imageVector = Icons.Default.Info,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary
                    )
                }
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
                    value = if (deadline.month in 1..12) monthName(deadline.month, lang) else "—",
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

// ---------------------------------------------------------------------------
// Helper
// ---------------------------------------------------------------------------

private fun deadlineLabel(kind: DeadlineKind, lang: AppLanguage): String = when (kind) {
    DeadlineKind.REVISION -> AppTexts.get("garage_deadline_revision", lang)
    DeadlineKind.TAX -> AppTexts.get("garage_deadline_tax", lang)
    DeadlineKind.INSURANCE -> AppTexts.get("garage_deadline_insurance", lang)
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

/** Sottotitolo del pulsante "Scadenze": la scadenza più vicina. */
private fun deadlinesSummary(
    profile: com.nos.motobitride.core.garage.VehicleProfile,
    lang: AppLanguage
): String {
    val today = LocalDate.now()
    val nearest = DeadlineKind.entries
        .mapNotNull { kind ->
            DeadlineReminder.daysUntil(profile.deadline(kind), today)?.let { kind to it }
        }
        .minByOrNull { it.second }
        ?: return AppTexts.get("garage_deadlines_none", lang)

    return deadlineLabel(nearest.first, lang) + " · " + countdownText(
        profile.deadline(nearest.first),
        lang
    )
}