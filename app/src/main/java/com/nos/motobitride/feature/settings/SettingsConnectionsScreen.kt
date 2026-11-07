package com.nos.motobitride.feature.settings

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import android.widget.Toast
import androidx.compose.ui.platform.LocalContext
import com.nos.motobitride.core.settings.AppSettings
import com.nos.motobitride.core.settings.StartupBehavior
import com.nos.motobitride.ui.i18n.AppTexts
import kotlin.math.roundToInt

/**
 * Pagina "Connessioni", nell'ordine richiesto:
 *  1) comportamento all'avvio
 *  2) vibrazione su connessione
 *  3) attesa attiva della riconnessione
 */
@Composable
fun SettingsConnectionsScreen(
    onBack: () -> Unit = {}
) {
    val ctx = LocalContext.current
    val currentLang by AppSettings.language.collectAsState()
    val startup by AppSettings.startup.collectAsState()
    val vibrateOnConnect by AppSettings.vibrateOnConnect.collectAsState()
    val activeWaitMinutes by AppSettings.activeWaitMinutes.collectAsState()

    SettingsSubPage(
        title = AppTexts.get("settings_cat_connections_title", currentLang),
        subtitle = AppTexts.get("settings_cat_connections_sub", currentLang),
        backLabel = AppTexts.get("common_back", currentLang),
        onBack = onBack
    ) {
        // ------------------------------------------------------------
        // 1) Comportamento all'avvio
        // ------------------------------------------------------------
        Text(
            text = AppTexts.get("settings_startup_title", currentLang),
            style = MaterialTheme.typography.titleSmall
        )

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
                    RadioButton(selected = startup == option, onClick = null)
                    Column(Modifier.padding(start = 12.dp)) {
                        Text(
                            AppTexts.get(option.labelKey, currentLang),
                            style = MaterialTheme.typography.bodyLarge
                        )
                        Text(
                            AppTexts.get("${option.labelKey}_desc", currentLang),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }

        HorizontalDivider()

        // ------------------------------------------------------------
        // 2) Vibrazione su connessione
        // ------------------------------------------------------------
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 2.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(Modifier.weight(1f)) {
                Text(
                    text = AppTexts.get("settings_vibrate_title", currentLang),
                    style = MaterialTheme.typography.bodyLarge
                )
                Text(
                    text = AppTexts.get("settings_vibrate_desc", currentLang),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Switch(
                checked = vibrateOnConnect,
                onCheckedChange = {
                    AppSettings.setVibrateOnConnect(it)
                    if (it) {
                        Toast.makeText(
                            ctx,
                            AppTexts.get("settings_vibrate_toast", currentLang),
                            Toast.LENGTH_SHORT
                        ).show()
                    }
                }
            )
        }

        HorizontalDivider()

        // ------------------------------------------------------------
        // 3) Attesa attiva della riconnessione
        // ------------------------------------------------------------
        Text(
            text = AppTexts.get("settings_encoder_idle_title", currentLang),
            style = MaterialTheme.typography.titleSmall
        )
        Text(
            text = AppTexts.get("settings_encoder_idle_desc", currentLang),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        var sliderValue by remember(activeWaitMinutes) {
            mutableStateOf(activeWaitMinutes.toFloat())
        }
        Text(
            text = AppTexts.get("settings_active_wait_label", currentLang, sliderValue.roundToInt()),
            style = MaterialTheme.typography.bodyLarge
        )
        Slider(
            value = sliderValue,
            onValueChange = { sliderValue = it },
            onValueChangeFinished = {
                AppSettings.setActiveWaitMinutes(sliderValue.roundToInt())
            },
            valueRange = AppSettings.MIN_ACTIVE_WAIT_MINUTES.toFloat()..
                    AppSettings.MAX_ACTIVE_WAIT_MINUTES.toFloat(),
            steps = AppSettings.MAX_ACTIVE_WAIT_MINUTES -
                    AppSettings.MIN_ACTIVE_WAIT_MINUTES - 1,
            modifier = Modifier.fillMaxWidth()
        )
    }
}
