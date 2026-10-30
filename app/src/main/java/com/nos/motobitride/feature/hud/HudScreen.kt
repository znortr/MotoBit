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
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.nos.motobitride.core.hud.HudStreamController
import com.nos.motobitride.core.hud.ScreenCaptureService
import com.nos.motobitride.core.hud.StreamState
import com.nos.motobitride.core.link.SavedHud
import com.nos.motobitride.core.log.AppLog
import com.nos.motobitride.core.qr.QrContent
import com.nos.motobitride.core.settings.AppSettings
import com.nos.motobitride.core.settings.StartupBehavior
import com.nos.motobitride.feature.ap.WifiApController

private const val TAG_UI = "HudScreen"

@Composable
fun rememberHudStreamController(): HudStreamController {
    val appContext = LocalContext.current.applicationContext
    val controller = remember { HudStreamController(appContext) }

    DisposableEffect(Unit) {
        onDispose { controller.stop() }
    }

    return controller
}

@Composable
fun rememberWifiApController(
    onHudConnected: (String?) -> Unit,
    onHudDisconnected: () -> Unit = {}
): WifiApController {
    val context = LocalContext.current.applicationContext
    val connected = rememberUpdatedState(onHudConnected)
    val disconnected = rememberUpdatedState(onHudDisconnected)

    val controller = remember(context) {
        WifiApController(
            context = context,
            onHudConnected = { ip -> connected.value(ip) },
            onHudDisconnected = { disconnected.value() }
        )
    }

    DisposableEffect(controller) {
        onDispose { controller.dispose() }
    }

    return controller
}

fun requiredWifiPermissions(): Array<String> {
    val list = mutableListOf<String>()
    list += Manifest.permission.ACCESS_FINE_LOCATION

    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        list += Manifest.permission.NEARBY_WIFI_DEVICES
        list += Manifest.permission.POST_NOTIFICATIONS
    }
    return list.toTypedArray()
}

fun Context.getActivity(): ComponentActivity? = when (this) {
    is ComponentActivity -> this
    is ContextWrapper -> baseContext.getActivity()
    else -> null
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HudScreen(
    hudQrValue: QrContent?,
    hudStreamController: HudStreamController = rememberHudStreamController(),
    onShowQrScanner: () -> Unit = {},
    onOpenSettings: () -> Unit = {}
) {
    val ctx = LocalContext.current
    val permissions = remember { requiredWifiPermissions() }

    val streamState by hudStreamController.state.collectAsState()
    val statusText by hudStreamController.status.collectAsState()
    val savedHud by SavedHud.current.collectAsState()
    val startupBehavior by AppSettings.startup.collectAsState()
    val encoderIdleMinutes by AppSettings.encoderIdleMinutes.collectAsState()

    // Timeout di sospensione encoder scelto nelle Impostazioni (0 = mai)
    LaunchedEffect(encoderIdleMinutes) {
        hudStreamController.setEncoderIdleTimeoutMs(encoderIdleMinutes.toLong() * 60_000L)
    }

    val mediaProjectionManager = remember {
        ctx.getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
    }

    val mediaProjectionState by ScreenCaptureService.mediaProjectionFlow.collectAsState()

    LaunchedEffect(mediaProjectionState) {
        mediaProjectionState?.let { hudStreamController.setMediaProjection(it) }
    }

    var projectionRequested by remember { mutableStateOf(false) }
    // true quando l'utente (o la modalita' di avvio) vuole il video
    var streamRequested by remember { mutableStateOf(false) }

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
        if (projectionRequested) return
        projectionRequested = true
        try {
            AppLog.i(TAG_UI, "Richiesta consenso cattura schermo")
            screenCaptureLauncher.launch(mediaProjectionManager.createScreenCaptureIntent())
        } catch (t: Throwable) {
            projectionRequested = false
            AppLog.e(TAG_UI, "Impossibile aprire il dialog di cattura", t)
        }
    }

    LaunchedEffect(streamState) {
        when (streamState) {
            StreamState.AWAITING_PROJECTION -> requestScreenCapture()
            else -> projectionRequested = false
        }
    }

    val wifiController = rememberWifiApController(
        onHudConnected = { ip ->
            AppLog.i(TAG_UI, "Link P2P attivo, ip=$ip")
            hudStreamController.setPreferredHost(ip, WifiApController.DEFAULT_HUD_PORT)

            val wantsStream = streamRequested ||
                    startupBehavior == StartupBehavior.AUTO_LINK_AND_STREAM
            if (wantsStream &&
                (hudStreamController.state.value == StreamState.IDLE ||
                        hudStreamController.state.value == StreamState.ERROR)
            ) {
                hudStreamController.start()
            }
        },
        onHudDisconnected = {
            AppLog.w(TAG_UI, "Link P2P perso")
        }
    )

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { result ->
        val allGranted = result.values.all { it }
        if (!allGranted) {
            AppLog.w(TAG_UI, "Permessi Wi-Fi/posizione negati")
        } else {
            (hudQrValue as? QrContent.HudWifi)?.let { qr ->
                SavedHud.save(qr)
                wifiController.connect(qr)
            }
        }
    }

    LaunchedEffect(hudQrValue) {
        when (hudQrValue) {
            is QrContent.HudWifi -> {
                // Nuovo QR: lo memorizziamo (sovrascrive il precedente)
                SavedHud.save(hudQrValue)

                val allGranted = permissions.all { perm ->
                    ContextCompat.checkSelfPermission(ctx, perm) ==
                            PackageManager.PERMISSION_GRANTED
                }
                if (!allGranted) {
                    permissionLauncher.launch(permissions)
                } else {
                    wifiController.connect(hudQrValue)
                }
            }
            is QrContent.BadQr -> AppLog.w(TAG_UI, "QR HUD non valido")
            else -> Unit
        }
    }

    // Applica la modalita' di avvio una sola volta per sessione app,
    // usando l'HUD memorizzato (nessun QR necessario).
    var startupApplied by remember { mutableStateOf(false) }
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
                if (!wifiController.isHudConnected) {
                    wifiController.connect(hud.toQrContent())
                }
            }

            StartupBehavior.AUTO_LINK_AND_STREAM -> {
                streamRequested = true
                if (!wifiController.isHudConnected) {
                    wifiController.connect(hud.toQrContent())
                }
                if (wifiController.isHudConnected) {
                    hudStreamController.start()
                }
            }
        }
    }

    val isActive = streamState != StreamState.IDLE && streamState != StreamState.ERROR
    val linkConnected = wifiController.isHudConnected
    val hudName = savedHud?.displayName ?: "-"

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("MotoBit") },
                actions = {
                    TextButton(onClick = onShowQrScanner) { Text("Scan QR") }
                    TextButton(onClick = onOpenSettings) { Text("Impostazioni") }
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
                        text = "Link Wi-Fi Direct: " +
                                (if (linkConnected) "ONLINE" else "OFFLINE") +
                                "   ip=${wifiController.hudIp ?: "-"}",
                        style = MaterialTheme.typography.bodyMedium
                    )
                    Text(
                        text = "Stato stream: ${streamState.name}",
                        style = MaterialTheme.typography.bodyMedium
                    )
                    Text(text = statusText, style = MaterialTheme.typography.bodySmall)
                    Text(
                        text = if (wifiController.lastEvent.isNotEmpty())
                            "link: ${wifiController.lastEvent}" else "link: -",
                        style = MaterialTheme.typography.bodySmall
                    )
                    val mirroringSuffix =
                        if (hudStreamController.isLiveMirroring) "  (mirroring ON)" else ""
                    Text(
                        text = "Video: " + hudStreamController.negotiatedWidth + "x" +
                                hudStreamController.negotiatedHeight + mirroringSuffix,
                        style = MaterialTheme.typography.bodySmall
                    )
                    Text(
                        text = "Avvio: ${startupBehavior.label}",
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
                                    streamRequested = true

                                    val hud = savedHud
                                    if (hud != null && !wifiController.isHudConnected) {
                                        // Ricostruisce il link dalle credenziali salvate
                                        wifiController.connect(hud.toQrContent())
                                    } else if (wifiController.isHudConnected) {
                                        hudStreamController.setPreferredHost(
                                            wifiController.hudIp,
                                            WifiApController.DEFAULT_HUD_PORT
                                        )
                                        hudStreamController.start()
                                    } else {
                                        AppLog.w(TAG_UI, "Nessun HUD: scansiona il QR")
                                        Toast.makeText(
                                            ctx,
                                            "Scansiona il QR dell'HUD",
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
                    Text(if (streamState == StreamState.ERROR) "Riprova" else "Avvia")
                }

                OutlinedButton(
                    onClick = {
                        AppLog.i(TAG_UI, "Stop premuto")
                        streamRequested = false
                        // Stop = PAUSA: la cattura resta attiva, cosi' Avvia riparte subito.
                        // (Il servizio non va fermato: ucciderebbe la MediaProjection.)
                        hudStreamController.stop()
                    },
                    enabled = isActive
                ) {
                    Text("Stop")
                }
            }

            OutlinedButton(
                onClick = {
                    // Chiudi: ferma lo stream, rilascia il link e termina l'app.
                    // Usa solo API gia' presenti (stop/disconnect/stopService):
                    // nessuna modifica alla logica di connessione.
                    AppLog.i(TAG_UI, "Chiudi premuto")
                    streamRequested = false
                    hudStreamController.close()
                    wifiController.disconnect()
                    ScreenCaptureService.stopService(ctx)
                    ctx.getActivity()?.finishAndRemoveTask()
                }
            ) {
                Text("Chiudi")
            }

            LogPanel(
                modifier = Modifier.fillMaxWidth(),
                onCopied = { n ->
                    Toast.makeText(ctx, "Log copiato ($n righe)", Toast.LENGTH_SHORT).show()
                },
                onSaved = { path ->
                    Toast.makeText(ctx, "Log salvato:\n$path", Toast.LENGTH_LONG).show()
                },
                onError = { message ->
                    Toast.makeText(ctx, message, Toast.LENGTH_LONG).show()
                }
            )
        }
    }
}
