package com.nos.motobitride.feature.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.nos.motobitride.core.shizuku.ShizukuStatus
import com.nos.motobitride.core.settings.AppSettings
import com.nos.motobitride.ui.i18n.AppTexts
import com.nos.motobitride.ui.shizuku.actionKey
import com.nos.motobitride.ui.shizuku.labelKey
import com.nos.motobitride.ui.shizuku.performAction
import com.nos.motobitride.ui.shizuku.rememberShizukuStatus

/**
 * Pagina "Risoluzione".
 *
 * **Fase 1**: solo fondamenta Shizuku (interruttore generale + stato + azione).
 * Le impostazioni vere (percentuale, oscuramento, rotazione, barra) arrivano
 * nella fase 2, senza toccare questa struttura.
 */
@Composable
fun SettingsResolutionScreen(
    onBack: () -> Unit = {},
    onOpenShizukuGuide: () -> Unit = {}
) {
    val currentLang by AppSettings.language.collectAsState()
    val shizukuEnabled by AppSettings.shizukuEnabled.collectAsState()
    val shizukuStatus = rememberShizukuStatus(shizukuEnabled)

    SettingsSubPage(
        title = AppTexts.get("settings_cat_resolution_title", currentLang),
        subtitle = AppTexts.get("settings_cat_resolution_sub", currentLang),
        backLabel = AppTexts.get("common_back", currentLang),
        onBack = onBack
    ) {
        // ---------------- Shizuku ----------------
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 2.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(Modifier.weight(1f)) {
                Text(
                    text = AppTexts.get("shizuku_switch_title", currentLang),
                    style = MaterialTheme.typography.bodyLarge
                )
                Text(
                    text = AppTexts.get("shizuku_switch_desc", currentLang),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Switch(
                checked = shizukuEnabled,
                onCheckedChange = { AppSettings.setShizukuEnabled(it) }
            )
        }

        // Stato + azione contestuale
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 2.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = AppTexts.get(shizukuStatus.labelKey(), currentLang),
                style = MaterialTheme.typography.bodyMedium,
                color = if (shizukuStatus == ShizukuStatus.READY) {
                    MaterialTheme.colorScheme.primary
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                },
                modifier = Modifier.weight(1f)
            )
            shizukuStatus.actionKey()?.let { key ->
                TextButton(onClick = { shizukuStatus.performAction() }) {
                    Text(AppTexts.get(key, currentLang))
                }
            }
        }

        // Guida passo-passo: serve al primo avvio e quando qualcosa si blocca.
        TextButton(
            onClick = onOpenShizukuGuide,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(AppTexts.get("guide_link", currentLang))
        }

        HorizontalDivider()

        // ---------------- Prossima fase ----------------
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(
                text = AppTexts.get("resolution_next_phase_title", currentLang),
                style = MaterialTheme.typography.titleSmall
            )
            Text(
                text = AppTexts.get("resolution_next_phase_desc", currentLang),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}
