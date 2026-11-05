package com.nos.motobitride.feature.diagnostics

import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.nos.motobitride.core.log.AppLog
import com.nos.motobitride.core.settings.AppSettings
import com.nos.motobitride.feature.hud.LogPanel
import com.nos.motobitride.ui.i18n.AppTexts

/**
 * Finestra Diagnostica: interruttore del log completo su file e il log in-app.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DiagnosticsScreen(
    onBack: () -> Unit = {}
) {
    val ctx = LocalContext.current
    val currentLang by AppSettings.language.collectAsState()
    val diagLogEnabled by AppSettings.diagLogEnabled.collectAsState()
    val periodicKeepalive by AppSettings.periodicClockKeepalive.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(AppTexts.get("diag_title", currentLang)) },
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
                .padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            HorizontalDivider()
            Text(AppTexts.get("diag_section_title", currentLang), style = MaterialTheme.typography.titleMedium)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(Modifier.weight(1f)) {
                    Text(
                        text = AppTexts.get("diag_switch_title", currentLang),
                        style = MaterialTheme.typography.bodyLarge
                    )
                    Text(
                        text = AppTexts.get("diag_switch_desc", currentLang),
                        style = MaterialTheme.typography.bodySmall
                    )
                }
                Switch(
                    checked = diagLogEnabled,
                    onCheckedChange = { enable ->
                        AppSettings.setDiagLogEnabled(enable)
                    }
                )
            }

            HorizontalDivider()
            Text(
                text = AppTexts.get("diag_footer", currentLang),
                style = MaterialTheme.typography.bodySmall
            )

            // Interruttore di bisazione: push orologio periodico sulla socket
            // di controllo PXC. Default OFF (causa misurata di intasamento).
            HorizontalDivider()
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(Modifier.weight(1f)) {
                    Text(
                        text = AppTexts.get("diag_keepalive_title", currentLang),
                        style = MaterialTheme.typography.bodyLarge
                    )
                    Text(
                        text = AppTexts.get("diag_keepalive_desc", currentLang),
                        style = MaterialTheme.typography.bodySmall
                    )
                }
                Switch(
                    checked = periodicKeepalive,
                    onCheckedChange = { enable ->
                        AppSettings.setPeriodicClockKeepalive(enable)
                    }
                )
            }

            HorizontalDivider()
            LogPanel(
                modifier = Modifier.fillMaxWidth(),
                onClear = {
                    AppLog.clear()
                    Toast.makeText(ctx, AppTexts.get("diag_toast_cleared", currentLang), Toast.LENGTH_SHORT).show()
                }
            )
        }
    }
}

@androidx.compose.ui.tooling.preview.Preview(showBackground = true)
@Composable
fun DiagnosticsScreenPreview() {
    com.nos.motobitride.ui.theme.MotobitRideTheme {
        DiagnosticsScreen()
    }
}
