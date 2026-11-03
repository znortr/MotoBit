package com.nos.motobitride.feature.diagnostics

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
import androidx.compose.ui.unit.dp
import com.nos.motobitride.core.settings.AppSettings
import com.nos.motobitride.feature.hud.LogPanel

/**
 * Finestra Diagnostica: interruttore del log completo e il log stesso.
 * Il salvataggio e' automatico in Documenti (gia' gestito da AppLog),
 * per questo non ci sono tasti Copia/Salva.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DiagnosticsScreen(
    onBack: () -> Unit = {}
) {
    val diagLogEnabled by AppSettings.diagLogEnabled.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Diagnostica") },
                actions = {
                    TextButton(onClick = onBack) { Text("Indietro") }
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
            Text("Log diagnostica", style = MaterialTheme.typography.titleMedium)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(Modifier.weight(1f)) {
                    Text(
                        text = "Log completo (canali, sessione, protocollo)",
                        style = MaterialTheme.typography.bodyLarge
                    )
                    Text(
                        text = "Se attivo, il log viene salvato in automatico " +
                                "nella cartella Documenti dell'app.",
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
                text = "Il log attivo si salva in Documenti; con l'interruttore " +
                        "spento non viene prodotto o scritto nulla.",
                style = MaterialTheme.typography.bodySmall
            )

            HorizontalDivider()
            if (diagLogEnabled) {
                LogPanel(modifier = Modifier.fillMaxWidth())
            } else {
                Text(
                    text = "Log disattivato: attiva l'interruttore per vedere " +
                            "gli eventi e salvarli in Documenti.",
                    style = MaterialTheme.typography.bodyMedium
                )
            }
        }
    }
}