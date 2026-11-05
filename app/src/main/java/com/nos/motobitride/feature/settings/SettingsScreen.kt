package com.nos.motobitride.feature.settings

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
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
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.nos.motobitride.core.link.SavedHud
import com.nos.motobitride.core.log.AppLog
import com.nos.motobitride.core.settings.AppSettings
import com.nos.motobitride.core.settings.BatteryOptimization
import com.nos.motobitride.core.settings.StartupBehavior
import com.nos.motobitride.ui.i18n.AppLanguage
import com.nos.motobitride.ui.i18n.AppTexts
import com.nos.motobitride.ui.i18n.AppThemeMode
import kotlinx.coroutines.delay
import kotlin.math.roundToInt

private const val TAG = "SettingsScreen"

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onBack: () -> Unit = {},
    onOpenDiagnostics: () -> Unit = {}
) {
    val ctx = LocalContext.current
    val currentLang by AppSettings.language.collectAsState()
    val currentThemeMode by AppSettings.themeMode.collectAsState()
    val startup by AppSettings.startup.collectAsState()
    val savedHud by SavedHud.current.collectAsState()
    val idleMinutes by AppSettings.encoderIdleMinutes.collectAsState()
    val clockPushCount by AppSettings.clockPushCount.collectAsState()
    val diagLogEnabled by AppSettings.diagLogEnabled.collectAsState()

    var batteryExempt by remember { mutableStateOf(BatteryOptimization.isIgnoring(ctx)) }
    LaunchedEffect(Unit) {
        while (true) {
            batteryExempt = BatteryOptimization.isIgnoring(ctx)
            delay(2_000)
        }
    }

    val batteryLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) {
        batteryExempt = BatteryOptimization.isIgnoring(ctx)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(AppTexts.get("settings_title", currentLang)) },
                actions = {
                    TextButton(onClick = onBack) {
                        Text(AppTexts.get("common_back", currentLang))
                    }
                }
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .padding(innerPadding)
                .fillMaxSize()
                .padding(12.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Sezione Lingua App (Menu a tendina / Dropdown)
            HorizontalDivider()
            Text(AppTexts.get("settings_language_title", currentLang), style = MaterialTheme.typography.titleMedium)
            Text(
                text = AppTexts.get("settings_language_desc", currentLang),
                style = MaterialTheme.typography.bodySmall
            )

            var langExpanded by remember { mutableStateOf(false) }
            ExposedDropdownMenuBox(
                expanded = langExpanded,
                onExpandedChange = { langExpanded = !langExpanded },
                modifier = Modifier.fillMaxWidth()
            ) {
                OutlinedTextField(
                    value = currentLang.labelWithFlag,
                    onValueChange = {},
                    readOnly = true,
                    label = { Text(AppTexts.get("settings_language_title", currentLang)) },
                    trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = langExpanded) },
                    colors = ExposedDropdownMenuDefaults.outlinedTextFieldColors(),
                    modifier = Modifier
                        .menuAnchor()
                        .fillMaxWidth()
                )
                ExposedDropdownMenu(
                    expanded = langExpanded,
                    onDismissRequest = { langExpanded = false }
                ) {
                    AppLanguage.entries.forEach { lang ->
                        DropdownMenuItem(
                            text = { Text(lang.labelWithFlag, style = MaterialTheme.typography.bodyLarge) },
                            onClick = {
                                AppSettings.setLanguage(lang)
                                langExpanded = false
                            }
                        )
                    }
                }
            }

            // Sezione Tema Grafico (Menu a tendina / Dropdown)
            HorizontalDivider()
            Text(AppTexts.get("settings_theme_title", currentLang), style = MaterialTheme.typography.titleMedium)
            Text(
                text = AppTexts.get("settings_theme_desc", currentLang),
                style = MaterialTheme.typography.bodySmall
            )

            var themeExpanded by remember { mutableStateOf(false) }
            ExposedDropdownMenuBox(
                expanded = themeExpanded,
                onExpandedChange = { themeExpanded = !themeExpanded },
                modifier = Modifier.fillMaxWidth()
            ) {
                OutlinedTextField(
                    value = AppTexts.get(currentThemeMode.labelKey, currentLang),
                    onValueChange = {},
                    readOnly = true,
                    label = { Text(AppTexts.get("settings_theme_title", currentLang)) },
                    trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = themeExpanded) },
                    colors = ExposedDropdownMenuDefaults.outlinedTextFieldColors(),
                    modifier = Modifier
                        .menuAnchor()
                        .fillMaxWidth()
                )
                ExposedDropdownMenu(
                    expanded = themeExpanded,
                    onDismissRequest = { themeExpanded = false }
                ) {
                    AppThemeMode.entries.forEach { mode ->
                        DropdownMenuItem(
                            text = { Text(AppTexts.get(mode.labelKey, currentLang), style = MaterialTheme.typography.bodyLarge) },
                            onClick = {
                                AppSettings.setThemeMode(mode)
                                themeExpanded = false
                            }
                        )
                    }
                }
            }

            // Sezione Avvio
            HorizontalDivider()
            Text(AppTexts.get("settings_startup_title", currentLang), style = MaterialTheme.typography.titleMedium)

            Column(Modifier.selectableGroup()) {
                StartupBehavior.entries.forEach { option ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .selectable(
                                selected = startup == option,
                                onClick = { AppSettings.setStartup(option) },
                                role = Role.RadioButton
                            )
                            .padding(vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RadioButton(
                            selected = startup == option,
                            onClick = null
                        )
                        Column(Modifier.padding(start = 12.dp)) {
                            Text(AppTexts.get(option.labelKey, currentLang), style = MaterialTheme.typography.bodyLarge)
                            Text(
                                AppTexts.get("${option.labelKey}_desc", currentLang),
                                style = MaterialTheme.typography.bodySmall
                            )
                        }
                    }
                }
            }

            // Sezione Risparmio durante l'attesa
            HorizontalDivider()
            Text(AppTexts.get("settings_encoder_idle_title", currentLang), style = MaterialTheme.typography.titleMedium)
            Text(
                text = AppTexts.get("settings_encoder_idle_desc", currentLang),
                style = MaterialTheme.typography.bodySmall
            )

            var sliderValue by remember(idleMinutes) {
                mutableStateOf(idleMinutes.toFloat())
            }
            Text(
                text = AppTexts.get("settings_encoder_idle_label", currentLang, sliderValue.roundToInt()),
                style = MaterialTheme.typography.bodyLarge
            )
            Slider(
                value = sliderValue,
                onValueChange = { sliderValue = it },
                onValueChangeFinished = {
                    AppSettings.setEncoderIdleMinutes(sliderValue.roundToInt())
                },
                valueRange = AppSettings.MIN_ENCODER_IDLE_MINUTES.toFloat()..
                        AppSettings.MAX_ENCODER_IDLE_MINUTES.toFloat(),
                steps = AppSettings.MAX_ENCODER_IDLE_MINUTES -
                        AppSettings.MIN_ENCODER_IDLE_MINUTES - 1,
                modifier = Modifier.fillMaxWidth()
            )

            // Sezione Orologio (Slider da 3 a 60, default 3)
            HorizontalDivider()
            Text(AppTexts.get("settings_clock_sync_title", currentLang), style = MaterialTheme.typography.titleMedium)
            Text(
                text = AppTexts.get("settings_clock_sync_desc", currentLang),
                style = MaterialTheme.typography.bodySmall
            )
            var pushValue by remember(clockPushCount) {
                mutableStateOf(clockPushCount.toFloat())
            }
            val currentPushInt = pushValue.roundToInt()
            Text(
                text = AppTexts.get("settings_clock_sync_label", currentLang, currentPushInt),
                style = MaterialTheme.typography.bodyLarge
            )
            Slider(
                value = pushValue,
                onValueChange = { pushValue = it },
                onValueChangeFinished = {
                    AppSettings.setClockPushCount(pushValue.roundToInt())
                },
                valueRange = AppSettings.MIN_CLOCK_PUSH_COUNT.toFloat()..
                        AppSettings.MAX_CLOCK_PUSH_COUNT.toFloat(),
                steps = AppSettings.MAX_CLOCK_PUSH_COUNT - AppSettings.MIN_CLOCK_PUSH_COUNT - 1,
                modifier = Modifier.fillMaxWidth()
            )

            // Sezione Diagnostica
            HorizontalDivider()
            Text(AppTexts.get("settings_diagnostics_entry", currentLang), style = MaterialTheme.typography.titleMedium)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(onClick = onOpenDiagnostics)
                    .padding(vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(Modifier.weight(1f)) {
                    Text(
                        text = AppTexts.get("settings_diagnostics_entry", currentLang),
                        style = MaterialTheme.typography.bodyLarge
                    )
                    Text(
                        text = AppTexts.get("settings_diagnostics_entry_desc", currentLang),
                        style = MaterialTheme.typography.bodySmall
                    )
                }
                Text(
                    text = if (diagLogEnabled) "LogON" else "LogOFF",
                    color = if (diagLogEnabled) Color(0xFFFF4444) else Color(0xFF4CAF50),
                    style = MaterialTheme.typography.labelLarge
                )
                Text(
                    text = " \u25B8",
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
                    style = MaterialTheme.typography.titleLarge
                )
            }

            // Sezione HUD Memorizzato
            HorizontalDivider()
            Text(AppTexts.get("hud_saved_card", currentLang), style = MaterialTheme.typography.titleMedium)

            Card(modifier = Modifier.fillMaxWidth()) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    val hud = savedHud
                    if (hud == null) {
                        Text(
                            AppTexts.get("hud_no_saved", currentLang),
                            style = MaterialTheme.typography.bodyMedium
                        )
                    } else {
                        Text(
                            text = hud.displayName,
                            style = MaterialTheme.typography.titleSmall
                        )
                        Text(
                            text = "SSID: ${hud.ssid}",
                            style = MaterialTheme.typography.bodySmall
                        )
                        Text(
                            text = "MAC: ${hud.mac ?: "-"}",
                            style = MaterialTheme.typography.bodySmall
                        )
                        Text(
                            text = "Modello: ${hud.modelId ?: "-"}   SN: ${hud.sn ?: "-"}",
                            style = MaterialTheme.typography.bodySmall
                        )
                    }

                    OutlinedButton(
                        onClick = { SavedHud.clear() },
                        enabled = savedHud != null,
                        modifier = Modifier.padding(top = 8.dp)
                    ) {
                        Text(AppTexts.get("common_clear", currentLang))
                    }
                }
            }
        }
    }
}

@androidx.compose.ui.tooling.preview.Preview(showBackground = true)
@Composable
fun SettingsScreenPreview() {
    com.nos.motobitride.ui.theme.MotobitRideTheme {
        SettingsScreen()
    }
}
