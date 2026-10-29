package com.nos.motobitride.feature.settings

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.nos.motobitride.core.link.SavedHud
import com.nos.motobitride.core.log.AppLog
import com.nos.motobitride.core.settings.AppSettings
import com.nos.motobitride.core.settings.BatteryOptimization
import com.nos.motobitride.core.settings.StartupBehavior
import kotlinx.coroutines.delay
import kotlin.math.roundToInt

private const val TAG = "SettingsScreen"

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onBack: () -> Unit = {}
) {
    val ctx = LocalContext.current
    val startup by AppSettings.startup.collectAsState()
    val savedHud by SavedHud.current.collectAsState()
    val idleMinutes by AppSettings.encoderIdleMinutes.collectAsState()

    // Stato dell'esenzione batteria, riletto periodicamente: al ritorno dal
    // dialog di sistema la schermata non riceve altre notifiche.
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
                title = { Text("Impostazioni") },
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
                .padding(12.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            HorizontalDivider()
            Text("Avvio", style = MaterialTheme.typography.titleMedium)
            Text(
                text = "Cosa fa l'app quando la apri e c'e' un HUD gia' memorizzato.",
                style = MaterialTheme.typography.bodySmall
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
                        RadioButton(
                            selected = startup == option,
                            onClick = null
                        )
                        Column(Modifier.padding(start = 12.dp)) {
                            Text(option.label, style = MaterialTheme.typography.bodyLarge)
                            Text(
                                option.description,
                                style = MaterialTheme.typography.bodySmall
                            )
                        }
                    }
                }
            }

            HorizontalDivider()
            Text("Batteria", style = MaterialTheme.typography.titleMedium)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(Modifier.weight(1f)) {
                    Text(
                        text = "Escludi dal risparmio energetico",
                        style = MaterialTheme.typography.bodyLarge
                    )
                    Text(
                        text = if (batteryExempt) {
                            "Attiva: Android non termina il servizio di cattura."
                        } else {
                            "Disattiva: Doze puo' interrompere la trasmissione e " +
                                    "richiedere un nuovo consenso alla cattura."
                        },
                        style = MaterialTheme.typography.bodySmall
                    )
                }
                Switch(
                    checked = batteryExempt,
                    onCheckedChange = { enable ->
                        AppLog.i(TAG, "Esclusione batteria -> $enable")
                        val intent = try {
                            if (enable) {
                                BatteryOptimization.requestIgnoreIntent(ctx)
                            } else {
                                BatteryOptimization.listSettingsIntent()
                            }
                        } catch (t: Throwable) {
                            AppLog.e(TAG, "Intent batteria non disponibile", t)
                            null
                        }
                        if (intent != null) {
                            runCatching { batteryLauncher.launch(intent) }
                        }
                    }
                )
            }

            HorizontalDivider()
            Text("Risparmio durante l'attesa", style = MaterialTheme.typography.titleMedium)
            Text(
                text = "Se l'HUD sparisce (TFT spento, sosta), la trasmissione viene " +
                        "sospesa dopo questo tempo per non consumare batteria. La " +
                        "connessione continua a essere ritentata e il video riprende da " +
                        "solo al ritorno dell'HUD, senza nuovi consensi.",
                style = MaterialTheme.typography.bodySmall
            )

            var sliderValue by remember(idleMinutes) {
                mutableStateOf(idleMinutes.toFloat())
            }
            Text(
                text = if (sliderValue.roundToInt() == 0) {
                    "Sospensione: mai"
                } else {
                    "Sospensione dopo ${sliderValue.roundToInt()} min"
                },
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

            HorizontalDivider()
            Text("HUD memorizzato", style = MaterialTheme.typography.titleMedium)

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
                            "Nessun HUD memorizzato: scansiona il QR.",
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
                        Text("Dimentica HUD")
                    }
                }
            }

            HorizontalDivider()
            Text("Note", style = MaterialTheme.typography.titleMedium)
            Text(
                text = "Il consenso alla cattura schermo non e' memorizzabile: e' una " +
                        "misura di sicurezza di Android e va riconfermato a ogni avvio " +
                        "dell'app. Non serve invece riconfermarlo quando cade e torna " +
                        "il link verso l'HUD, finche' l'app resta in esecuzione.",
                style = MaterialTheme.typography.bodySmall
            )
        }
    }
}
