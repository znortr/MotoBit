package com.nos.motobitride.feature.hud

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.pm.PackageManager
import android.media.projection.MediaProjectionManager
import android.os.Build
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
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
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.unit.dp
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.nos.motobitride.core.hud.HudSession
import com.nos.motobitride.core.hud.ScreenCaptureService
import com.nos.motobitride.core.hud.StreamState
import com.nos.motobitride.core.link.SavedHud
import com.nos.motobitride.core.log.AppLog
import com.nos.motobitride.core.qr.QrContent
import com.nos.motobitride.core.settings.AppSettings
import com.nos.motobitride.core.settings.PermissionsHelper
import com.nos.motobitride.core.settings.StartupBehavior
import com.nos.motobitride.feature.ap.WifiApController
import com.nos.motobitride.ui.components.MotobitTopAppBar
import com.nos.motobitride.ui.i18n.AppLanguage
import com.nos.motobitride.ui.i18n.AppTexts
import kotlinx.coroutines.flow.MutableStateFlow

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
    val startupBehavior by AppSettings.startup.collectAsState()

    val mediaProjectionManager = remember {
        if (isPreview) null
        else ctx.getSystemService(Context.MEDIA_PROJECTION_SERVICE) as? MediaProjectionManager
    }

    var projectionRequested by remember { mutableStateOf(false) }

    val screenCaptureLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        projectionRequested = false
        if (result.resultCode == Activity.RESULT_OK && result.data != null) {
            AppLog.i(TAG_UI, "Consenso cattura schermo concesso")
            try {
                ScreenCaptureService.startService(ctx, result.resultCode, result.data!!)
            } catch (t: Throwable) {
                AppLog.e(TAG_UI, "Avvio ScreenCaptureService fallito", t)
                Toast.makeText(
                    ctx,
                    "Cattura schermo fallita: ${t.message}",
                    Toast.LENGTH_LONG
                ).show()
            }
        } else {
            AppLog.w(TAG_UI, "Consenso cattura schermo negato")
        }
    }

    fun requestScreenCapture() {
        if (projectionRequested || isPreview) return
        projectionRequested = true
        try {
            AppLog.i(TAG_UI, "Richiesta consenso cattura schermo")
            mediaProjectionManager?.let { screenCaptureLauncher.launch(it.createScreenCaptureIntent()) }
        } catch (t: Throwable) {
            projectionRequested = false
            AppLog.e(TAG_UI, "Impossibile aprire il dialog di cattura", t)
        }
    }

    if (!isPreview) {
        LaunchedEffect(streamState) {
            when (streamState) {
                StreamState.AWAITING_PROJECTION -> requestScreenCapture()
                else -> projectionRequested = false
            }
        }
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
        LaunchedEffect(savedHud) {
            if (startupApplied) return@LaunchedEffect
            val hud = savedHud ?: return@LaunchedEffect
            startupApplied = true

            AppLog.i(
                TAG_UI,
                "Avvio: modalita' ${startupBehavior.name}, HUD memorizzato " +
                        hud.displayName
            )

            when (startupBehavior) {
                StartupBehavior.MANUAL -> Unit

                StartupBehavior.AUTO_LINK -> {
                    if (wifiController?.isHudConnected == false) {
                        wifiController.connect(hud.toQrContent())
                    }
                }

                StartupBehavior.AUTO_LINK_AND_STREAM -> {
                    HudSession.requestStream()
                    if (wifiController?.isHudConnected == false) {
                        wifiController.connect(hud.toQrContent())
                    }
                    if (wifiController?.isHudConnected == true) {
                        hudStreamController?.start()
                    }
                }
            }
        }
    }

    val isActive = streamState != StreamState.IDLE && streamState != StreamState.ERROR
    val linkConnected = wifiController?.isHudConnected ?: false
    val hudName = savedHud?.displayName ?: "-"

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
                        text = AppTexts.get("settings_startup_title", currentLang) +
                                ": " + AppTexts.get(startupBehavior.labelKey, currentLang),
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }

            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Button(
                    onClick = {
                        ctx.getActivity()?.let { act ->
                            when {
                                ContextCompat.checkSelfPermission(
                                    ctx,
                                    Manifest.permission.NEARBY_WIFI_DEVICES
                                ) == PackageManager.PERMISSION_GRANTED -> {
                                    AppLog.i(TAG_UI, "Avvia premuto")
                                    // Ricorda che l'utente vuole il video: al ritorno
                                    // del link la sessione riparte da sola.
                                    HudSession.requestStream()

                                    val hud = savedHud
                                    if (hud != null && wifiController?.isHudConnected == false) {
                                        wifiController.connect(hud.toQrContent())
                                    } else if (wifiController?.isHudConnected == true) {
                                        hudStreamController?.setPreferredHost(
                                            wifiController.hudIp,
                                            WifiApController.DEFAULT_HUD_PORT
                                        )
                                        hudStreamController?.start()
                                    } else {
                                        AppLog.w(TAG_UI, "Nessun HUD: scansiona il QR")
                                        Toast.makeText(
                                            ctx,
                                            AppTexts.get("hud_no_saved", currentLang),
                                            Toast.LENGTH_SHORT
                                        ).show()
                                    }
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
                    },
                    enabled = !isActive
                ) {
                    Text(
                        if (streamState == StreamState.ERROR)
                            AppTexts.get("common_retry", currentLang)
                        else
                            AppTexts.get("common_start", currentLang)
                    )
                }

                OutlinedButton(
                    onClick = {
                        AppLog.i(TAG_UI, "Stop premuto")
                        HudSession.stopStream()
                    },
                    enabled = isActive
                ) {
                    Text(AppTexts.get("common_stop", currentLang))
                }
            }

            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedButton(
                    onClick = onShowQrScanner
                ) {
                    Text(AppTexts.get("hud_scan_qr", currentLang))
                }

                OutlinedButton(
                    onClick = {
                        AppLog.i(TAG_UI, "Chiudi premuto")
                        HudSession.closeAll()
                        ScreenCaptureService.stopService(ctx)
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
