package com.nos.motobitride.feature.hud

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.pm.PackageManager
import android.media.projection.MediaProjectionConfig
import android.media.projection.MediaProjectionManager
import android.os.Build
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.nos.motobitride.core.hud.HudSession
import com.nos.motobitride.core.hud.ScreenCaptureService
import com.nos.motobitride.core.hud.StreamState
import com.nos.motobitride.core.apps.AppInfo
import com.nos.motobitride.core.apps.InstalledApps
import com.nos.motobitride.core.apps.SavedApps
import com.nos.motobitride.core.garage.VehicleStore
import com.nos.motobitride.core.garage.vehicleDisplayName
import com.nos.motobitride.feature.home.AppGrid
import com.nos.motobitride.feature.home.AppPickerDialog
import com.nos.motobitride.feature.stream.StartBubbleService
import com.nos.motobitride.feature.stream.StreamLaunchActivity
import com.nos.motobitride.core.link.SavedHud
import com.nos.motobitride.core.log.AppLog
import com.nos.motobitride.core.qr.QrContent
import com.nos.motobitride.core.settings.AppSettings
import com.nos.motobitride.R
import com.nos.motobitride.core.settings.PermissionsHelper
import com.nos.motobitride.feature.ap.WifiApController
import com.nos.motobitride.ui.components.MotobitTopAppBar
import com.nos.motobitride.ui.i18n.AppLanguage
import com.nos.motobitride.ui.i18n.AppTexts
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.withContext

private const val TAG_UI = "HudScreen"

fun requiredWifiPermissions(): Array<String> = PermissionsHelper.requiredRuntimePermissions()

fun Context.getActivity(): ComponentActivity? = when (this) {
    is ComponentActivity -> this
    is ContextWrapper -> baseContext.getActivity()
    else -> null
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HudScreen(
    hudQrValue: QrContent?,
    onShowQrScanner: () -> Unit = {},
    onNavigateHome: () -> Unit = {},
    onNavigateGarage: () -> Unit = {},
    onNavigateSettings: () -> Unit = {}
) {
    val ctx = LocalContext.current
    val isPreview = LocalInspectionMode.current

    // La sessione vive nel processo (HudSession), non nel Composable: cosi'
    // sopravvive al passaggio in background e alla distruzione dell'Activity.
    if (!isPreview) {
        remember(ctx) { HudSession.ensure(ctx) }
    }

    val hudStreamController = if (isPreview) null else HudSession.stream
    val wifiController = if (isPreview) null else HudSession.link

    val currentLang by AppSettings.language.collectAsState()
    val permissions = remember { requiredWifiPermissions() }

    // Flussi di fallback: collectAsState va chiamato sempre, anche in Preview.
    val idleStateFlow = remember { MutableStateFlow(StreamState.IDLE) }
    val idleStatusFlow = remember { MutableStateFlow("") }
    val streamState by (hudStreamController?.state ?: idleStateFlow).collectAsState()
    val statusText by (hudStreamController?.status ?: idleStatusFlow).collectAsState()

    val savedHud by SavedHud.current.collectAsState()
    val autoConnect by AppSettings.autoConnect.collectAsState()
    // Auto-connessione di SESSIONE (attivata dal tap sull'app): stessa procedura
    // dell'auto delle impostazioni, solo in memoria.
    val sessionAuto by HudSession.autoConnectForSession.collectAsState()
    val showHomeDiagnostics by AppSettings.showHomeDiagnostics.collectAsState()
    /** Annullamento dell'attesa automatica, valido solo per questa sessione app. */
    var autoCancelled by remember { mutableStateOf(false) }

    val mediaProjectionManager = remember {
        if (isPreview) null
        else ctx.getSystemService(Context.MEDIA_PROJECTION_SERVICE) as? MediaProjectionManager
    }

    // ------------------------------------------------------------------
    // PERCORSO UNICO DEL CONSENSO
    //
    // La Home NON chiede piu' la cattura schermo: lo fa SOLO
    // `StreamLaunchActivity` (activity visibile, dialoghi sopra l'app scelta).
    //
    // Prima esistevano due percorsi (Home + StreamLaunchActivity): quando lo
    // stato diventava AWAITING_PROJECTION la Home apriva un SECONDO dialogo e,
    // partendo dalla sua Activity, portava MotoBit in primo piano mandando in
    // background l'app scelta. Il flag `active` era una toppa che non copriva
    // tutti i casi (activity distrutta, revoca della proiezione, ecc.).
    //
    // Se serve il consenso, la Home delega a StreamLaunchActivity (senza app da
    // aprire), che lo gestisce con le stesse regole di sempre.
    // ------------------------------------------------------------------
    fun requestConsentThroughLauncher() {
        if (isPreview) return
        AppLog.i(TAG_UI, "Consenso: delego a StreamLaunchActivity")
        runCatching {
            ctx.startActivity(StreamLaunchActivity.intent(ctx, "", openApp = false))
        }.onFailure { AppLog.e(TAG_UI, "Avvio StreamLaunchActivity fallito", it) }
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { result ->
        val allGranted = result.values.all { it }
        if (!allGranted) {
            AppLog.w(TAG_UI, "Permessi Wi-Fi/posizione negati")
        } else {
            (hudQrValue as? QrContent.HudWifi)?.let { qr ->
                SavedHud.save(qr)
                wifiController?.connect(qr)
            }
        }
    }

    if (!isPreview) {
        LaunchedEffect(hudQrValue) {
            when (hudQrValue) {
                is QrContent.HudWifi -> {
                    SavedHud.save(hudQrValue)

                    val allGranted = permissions.all { perm ->
                        ContextCompat.checkSelfPermission(ctx, perm) ==
                                PackageManager.PERMISSION_GRANTED
                    }
                    if (!allGranted) {
                        permissionLauncher.launch(permissions)
                    } else {
                        wifiController?.connect(hudQrValue)
                    }
                }
                is QrContent.BadQr -> AppLog.w(TAG_UI, "QR HUD non valido")
                else -> Unit
            }
        }
    }

    var startupApplied by remember { mutableStateOf(false) }
    if (!isPreview) {
        // Reagisce allo switch (sia all'avvio sia se l'utente lo cambia a runtime):
        // con l'automatico attivo si stabilisce SOLO la connessione.
        LaunchedEffect(autoConnect, sessionAuto, savedHud) {
            if ((!autoConnect && !sessionAuto) || autoCancelled) return@LaunchedEffect
            val hud = savedHud ?: return@LaunchedEffect
            if (streamState == StreamState.IDLE || streamState == StreamState.ERROR) {
                AppLog.i(
                    TAG_UI,
                    "Auto: connessione verso ${hud.displayName} " +
                            "(impostazione=$autoConnect, sessione=$sessionAuto)"
                )
                // STESSA procedura dell'auto delle impostazioni.
                HudSession.connect()
            }
        }
        // Se l'utente spegne l'automatico, l'annullamento locale non serve piu'.
        LaunchedEffect(autoConnect) {
            if (!autoConnect) autoCancelled = false
        }
    }

    // Stati per i pulsanti: connessione separata dal mirroring.
    val connected = streamState == StreamState.CONNECTED ||
            streamState == StreamState.MIRRORING ||
            streamState == StreamState.AWAITING_PROJECTION
    val working = streamState == StreamState.DISCOVERING ||
            streamState == StreamState.CONNECTING ||
            streamState == StreamState.WAITING_HUD
    val mirroring = streamState == StreamState.MIRRORING
    val noBike = savedHud == null
    // Automatico attivo, nessun tentativo in corso: "in attesa del TFT".
    // Lo stato mostrato considera anche l'auto di sessione: prima guardava solo
    // l'impostazione, quindi il pulsante restava "manuale".
    val autoWaiting = !noBike && (autoConnect || sessionAuto) &&
            !autoCancelled && !connected && !working


    val linkConnected = wifiController?.isHudConnected ?: false
    val hudName = savedHud?.displayName ?: "-"
    // Nome mostrato: quello scritto dall'utente, altrimenti quello dell'HUD.
    val profile by VehicleStore.current.collectAsState()
    val displayName = vehicleDisplayName(profile, savedHud?.displayName)

    // Riga piccola in fondo al pulsante: le stesse informazioni della vecchia card.
    val details = buildString {
        append("Link ").append(if (linkConnected) "ON" else "OFF")
        append(" · ").append(wifiController?.hudIp ?: "-")
        append(" · ").append(streamState.name)
        append(" · ").append(hudStreamController?.negotiatedWidth ?: 592)
        append("x").append(hudStreamController?.negotiatedHeight ?: 752)
        if (hudStreamController?.isLiveMirroring == true) append(" · MIRROR")
    }

    // ---------------- App in Home ----------------
    val appPackages by SavedApps.packages.collectAsState()

    // La Home carica SOLO le app scelte (2-6), non tutte quelle installate:
    // label + icona per 3 app sono pochi ms, la griglia appare subito.
    // L'elenco completo serve solo al selettore AGGIUNGI APP, che lo carica
    // quando si apre (con il suo spinner).
    val selectedApps by produceState(initialValue = emptyList<AppInfo>(), appPackages) {
        if (isPreview || appPackages.isEmpty()) {
            value = emptyList()
            return@produceState
        }
        val loaded = withContext(Dispatchers.IO) {
            InstalledApps.loadFor(ctx, appPackages)
        }
        value = loaded
        // Potatura: se un pacchetto salvato non esiste più, si toglie dalla lista.
        if (loaded.size != appPackages.size) {
            SavedApps.pruneMissing(loaded.map { it.packageName }.toSet())
        }
    }
    var showAppPicker by remember { mutableStateOf(false) }

    fun onAppTap(app: AppInfo) {
        // 1) Se NON siamo connessi si avvia la procedura AUTOMATICA (la stessa
        //    delle impostazioni), valida solo per questa sessione. Se l'auto e'
        //    gia' attiva non cambia nulla: `connect()` e' idempotente.
        //    Copre anche il caso "link su ma sessione no" (es. sessione caduta).
        if (!connected) {
            AppLog.i(TAG_UI, "App scelta senza sessione attiva: avvio procedura automatica")
            HudSession.requestAutoConnect()
        }

        if (connected) {
            // 2a) Sessione pronta: consenso + streaming + apertura app.
            AppLog.i(TAG_UI, "App scelta (connesso): ${app.packageName}")
            runCatching {
                ctx.startActivity(
                    StreamLaunchActivity.intent(ctx, app.packageName, openApp = true)
                )
            }.onFailure { AppLog.e(TAG_UI, "Avvio flusso streaming fallito", it) }
        } else {
            // 2b) Connessione in corso: apro l'app e mostro la bolla AVVIA,
            //     che si occupera' SOLO dello streaming.
            AppLog.i(TAG_UI, "App scelta (connessione in corso): ${app.packageName}")
            StartBubbleService.show(ctx, app.packageName)
            InstalledApps.launch(ctx, app.packageName)
        }
    }

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
                .padding(12.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(10.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Card diagnostica: nascosta di default (Opzioni sviluppo -> switch).
            if (showHomeDiagnostics) {
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Text(
                        text = "HUD: $hudName",
                        style = MaterialTheme.typography.titleMedium
                    )
                    Text(
                        text = AppTexts.get("hud_status", currentLang) +
                                (if (linkConnected) "ONLINE" else "OFFLINE") +
                                "   ip=${wifiController?.hudIp ?: "-"}",
                        style = MaterialTheme.typography.bodyMedium
                    )
                    Text(
                        text = "Stato stream: ${streamState.name}",
                        style = MaterialTheme.typography.bodyMedium
                    )
                    Text(text = statusText, style = MaterialTheme.typography.bodySmall)
                    Text(
                        text = friendlyLinkEvent(wifiController?.lastEvent, currentLang),
                        style = MaterialTheme.typography.bodySmall
                    )
                    val mirroringSuffix =
                        if (hudStreamController?.isLiveMirroring == true) "  (mirroring ON)" else ""
                    Text(
                        text = "Video: " + (hudStreamController?.negotiatedWidth ?: 592) + "x" +
                                (hudStreamController?.negotiatedHeight ?: 752) + mirroringSuffix,
                        style = MaterialTheme.typography.bodySmall
                    )
                    Text(
                        text = AppTexts.get("settings_auto_connect_title", currentLang) + ": " +
                                (if (autoConnect) "ON" else "OFF"),
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }
            }

            Button(
                    onClick = {
                        when {
                            // Nessuna moto: apre la scansione
                            savedHud == null -> onShowQrScanner

                            // In corso o in attesa: ANNULLA con feedback
                            working || autoWaiting -> {
                                AppLog.i(TAG_UI, "Annullo la connessione")
                                autoCancelled = true
                                HudSession.disconnect()
                                Toast.makeText(
                                    ctx,
                                    AppTexts.get("hud_connect_cancelled", currentLang),
                                    Toast.LENGTH_SHORT
                                ).show()
                            }

                            // Connessa: nessuna azione (D1)
                            connected -> Unit

                            // Disconnessa: connette
                            else -> ctx.getActivity()?.let { act ->
                            when {
                                ContextCompat.checkSelfPermission(
                                    ctx,
                                    Manifest.permission.NEARBY_WIFI_DEVICES
                                ) == PackageManager.PERMISSION_GRANTED -> {
                                    // Il pulsante CONNETTE e basta: il video
                                    // parte con "Avvia mirroring".
                                    HudSession.connect()
                                }

                                ActivityCompat.shouldShowRequestPermissionRationale(
                                    act,
                                    Manifest.permission.NEARBY_WIFI_DEVICES
                                ) -> Toast.makeText(
                                    ctx,
                                    "Servono i permessi Wi-Fi per trovare la moto",
                                    Toast.LENGTH_LONG
                                ).show()

                                else -> {
                                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                                        ActivityCompat.requestPermissions(
                                            act,
                                            arrayOf(Manifest.permission.NEARBY_WIFI_DEVICES),
                                            0
                                        )
                                    }
                                }
                            }
                        }
                        }
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(140.dp),
                    contentPadding = PaddingValues(horizontal = 18.dp, vertical = 14.dp),
                    // Angoli morbidi ma NON a pillola: il pulsante deve sembrare una card.
                    shape = RoundedCornerShape(18.dp),
                    elevation = ButtonDefaults.buttonElevation(
                        defaultElevation = 6.dp,
                        pressedElevation = 2.dp
                    ),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = when {
                            noBike -> Color(0xFFD32F2F)
                            working || autoWaiting -> Color(0xFFF9A825)
                            else -> Color(0xFF2E7D32)
                        }
                    )
                ) {
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            // Icona a SINISTRA: QR (nessuna moto) / catena spezzata
                            // (in connessione o disconnessa) / spunta (connessa).
                            if (noBike) {
                                Icon(
                                    painter = painterResource(R.drawable.ic_qr),
                                    contentDescription = null,
                                    tint = Color.White,
                                    modifier = Modifier.size(28.dp)
                                )
                            } else if (connected) {
                                Icon(
                                    imageVector = Icons.Default.Check,
                                    contentDescription = null,
                                    tint = Color.White,
                                    modifier = Modifier.size(28.dp)
                                )
                            } else {
                                Icon(
                                    painter = painterResource(R.drawable.ic_link_off),
                                    contentDescription = null,
                                    tint = Color.White,
                                    modifier = Modifier.size(28.dp)
                                )
                            }
                            Spacer(Modifier.width(14.dp))
                            Column(Modifier.weight(1f), horizontalAlignment = Alignment.Start) {
                                Text(
                                    text = if (noBike) {
                                        AppTexts.get("hud_connect_register", currentLang)
                                    } else {
                                        displayName
                                    },
                                    style = MaterialTheme.typography.titleLarge,
                                    fontWeight = FontWeight.Bold,
                                    color = Color.White
                                )
                                Text(
                                    text = when {
                                        noBike -> AppTexts.get("hud_connect_register_sub", currentLang)
                                        working -> AppTexts.get("hud_connect_connecting", currentLang) +
                                                " \u00b7 " + AppTexts.get("hud_tap_to_cancel", currentLang)
                                        autoWaiting -> AppTexts.get("hud_connect_waiting", currentLang) +
                                                " \u00b7 " + AppTexts.get("hud_tap_to_cancel_manual", currentLang)
                                        connected -> AppTexts.get("hud_connect_connected", currentLang)
                                        else -> AppTexts.get("hud_connect_tap", currentLang)
                                    },
                                    style = MaterialTheme.typography.labelMedium,
                                    color = Color.White.copy(alpha = 0.92f)
                                )
                            }
                            if (working || autoWaiting) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(20.dp),
                                    color = Color.White,
                                    strokeWidth = 2.dp
                                )
                            }
                        }

                        HorizontalDivider(color = Color.White.copy(alpha = 0.25f))

                        // Progressioni: le stesse info della vecchia card, in piccolo.
                        Text(
                            text = details,
                            style = MaterialTheme.typography.labelSmall,
                            color = Color.White.copy(alpha = 0.85f)
                        )
                    }
                }

                OutlinedButton(
                    onClick = {
                        if (mirroring) {
                            AppLog.i(TAG_UI, "Ferma mirroring")
                            HudSession.stopStream()
                        } else {
                            AppLog.i(TAG_UI, "Avvia mirroring")
                            // Se manca la proiezione il consenso lo chiede
                            // StreamLaunchActivity (percorso unico).
                            if (ScreenCaptureService.mediaProjectionFlow.value == null) {
                                requestConsentThroughLauncher()
                            } else {
                                HudSession.startStream()
                            }
                        }
                    },
                    enabled = connected
                ) {
                    Text(
                        if (mirroring) AppTexts.get("hud_mirroring_stop", currentLang)
                        else AppTexts.get("hud_mirroring_start", currentLang)
                    )
                }

            // ---------------- Griglia app (2 colonne) ----------------
            AppGrid(
                apps = selectedApps,
                // App scelte ma label/icone non ancora caricate (sono su IO)
                loading = appPackages.isNotEmpty() && selectedApps.isEmpty(),
                onAppClick = { app -> onAppTap(app) },
                onAddApp = { showAppPicker = true },
                emptyText = "Nessuna app in Home: aggiungine una con AGGIUNGI APP",
                addLabel = "AGGIUNGI APP"
            )

            if (showAppPicker) {
                // Il selettore carica da solo l'elenco completo (con spinner).
                AppPickerDialog(
                    selectedPackages = appPackages.toSet(),
                    onToggle = { SavedApps.toggle(it) },
                    onDismiss = { showAppPicker = false }
                )
            }

            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedButton(
                    onClick = {
                        AppLog.i(TAG_UI, "Chiudi premuto")
                        // Prima si ferma il servizio (rimuove la notifica e chiude
                        // la MediaProjection), poi sessione e link: cosi'
                        // `closeAll()` non prova ad aggiornare una notifica che
                        // non esiste piu'.
                        ScreenCaptureService.stopService(ctx)
                        StartBubbleService.hide(ctx)
                        HudSession.closeAll()
                        ctx.getActivity()?.finishAndRemoveTask()
                    }
                ) {
                    Text(AppTexts.get("common_close", currentLang))
                }
            }
        }
    }
}

/**
 * Traduce l'evento tecnico del link in un messaggio comprensibile.
 * Gli eventi non riconosciuti restano visibili cosi' come sono (utili in diagnosi).
 */
private fun friendlyLinkEvent(event: String?, lang: AppLanguage): String {
    if (event.isNullOrBlank()) return "link: -"
    return when {
        event.contains("Wi-Fi Direct disattivato") ->
            AppTexts.get("link_cause_p2p_off", lang)

        event.contains("BUSY") ->
            AppTexts.get("link_cause_busy", lang)

        event.contains("nessun peer") ->
            AppTexts.get("link_cause_no_peer", lang)

        else -> "link: $event"
    }
}
