package com.nos.motobitride.feature.settings

import android.widget.Toast
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.nos.motobitride.core.log.AppLog
import com.nos.motobitride.core.settings.AppSettings
import com.nos.motobitride.feature.hud.LogPanel
import com.nos.motobitride.ui.i18n.AppTexts
import kotlin.math.roundToInt

/**
 * Pagina "Opzioni Sviluppo" (sostituisce la vecchia pagina Diagnostica).
 * Contenuto inline, nell'ordine richiesto:
 *  1) sincronizzazione orologio TFT
 *  2) log e diagnostica
 */
@Composable
fun SettingsDevelopmentScreen(
    onBack: () -> Unit = {}
) {
    val ctx = LocalContext.current
    val currentLang by AppSettings.language.collectAsState()
    val clockPushCount by AppSettings.clockPushCount.collectAsState()
    val diagLogEnabled by AppSettings.diagLogEnabled.collectAsState()
    val periodicKeepalive by AppSettings.periodicClockKeepalive.collectAsState()

    SettingsSubPage(
        title = AppTexts.get("settings_cat_development_title", currentLang),
        subtitle = AppTexts.get("settings_cat_development_sub", currentLang),
        backLabel = AppTexts.get("common_back", currentLang),
        onBack = onBack
    ) {
        // ------------------------------------------------------------
        // 1) Sincronizzazione orologio TFT
        // ------------------------------------------------------------
        Text(
            text = AppTexts.get("settings_clock_sync_title", currentLang),
            style = MaterialTheme.typography.titleSmall
        )
        Text(
            text = AppTexts.get("settings_clock_sync_desc", currentLang),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        var pushValue by remember(clockPushCount) {
            mutableStateOf(clockPushCount.toFloat())
        }
        Text(
            text = AppTexts.get(
                "settings_clock_sync_label",
                currentLang,
                pushValue.roundToInt()
            ),
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

        HorizontalDivider()

        // ------------------------------------------------------------
        // 2) Log e diagnostica
        // ------------------------------------------------------------
        Text(
            text = AppTexts.get("diag_section_title", currentLang),
            style = MaterialTheme.typography.titleSmall
        )

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 2.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(Modifier.weight(1f)) {
                Text(
                    text = AppTexts.get("diag_switch_title", currentLang),
                    style = MaterialTheme.typography.bodyLarge
                )
                Text(
                    text = AppTexts.get("diag_switch_desc", currentLang),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Switch(
                checked = diagLogEnabled,
                onCheckedChange = { AppSettings.setDiagLogEnabled(it) }
            )
        }

        Text(
            text = AppTexts.get("diag_footer", currentLang),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        HorizontalDivider()

        // Interruttore di bisazione: push orologio periodico sulla socket PXC.
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 2.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(Modifier.weight(1f)) {
                Text(
                    text = AppTexts.get("diag_keepalive_title", currentLang),
                    style = MaterialTheme.typography.bodyLarge
                )
                Text(
                    text = AppTexts.get("diag_keepalive_desc", currentLang),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Switch(
                checked = periodicKeepalive,
                onCheckedChange = { AppSettings.setPeriodicClockKeepalive(it) }
            )
        }

        HorizontalDivider()

        LogPanel(
            modifier = Modifier.fillMaxWidth(),
            onClear = {
                AppLog.clear()
                Toast.makeText(
                    ctx,
                    AppTexts.get("diag_toast_cleared", currentLang),
                    Toast.LENGTH_SHORT
                ).show()
            }
        )
    }
}
