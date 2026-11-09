package com.nos.motobitride.feature.stream

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.media.projection.MediaProjectionConfig
import android.media.projection.MediaProjectionManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.unit.dp
import com.nos.motobitride.core.apps.InstalledApps
import com.nos.motobitride.core.apps.SavedApps
import com.nos.motobitride.core.hud.HudSession
import com.nos.motobitride.core.hud.ScreenCaptureService
import com.nos.motobitride.core.hud.StreamState
import com.nos.motobitride.core.link.SavedHud
import com.nos.motobitride.core.log.AppLog
import com.nos.motobitride.core.settings.AppSettings
import com.nos.motobitride.ui.theme.MotobitRideTheme
import kotlinx.coroutines.flow.MutableStateFlow

/**
 * Activity **trasparente** che avvia il mirroring con il **minor numero di
 * passaggi possibile**.
 *
 * ```
 * tap app / bolla → [consenso Android]  ┐  (nessun dialogo intermedio)
 *                   [connessione TFT]   ┘  in parallelo
 *                 → appena pronti: streaming (+ apertura app)
 * ```
 *
 * Riusa `HudSession` e `ScreenCaptureService`: nessuna logica di protocollo qui.
 */
class StreamLaunchActivity : ComponentActivity() {

    companion object {
        private const val EXTRA_PACKAGE = "extra_package"
        private const val EXTRA_OPEN_APP = "extra_open_app"

        /**
         * Vero mentre questa Activity è in vita.
         *
         * Serve a `HudScreen` (M2): quando il flusso è attivo il consenso lo
         * gestisce SOLO questa Activity. Se la Home lo chiedesse in parallelo
         * (e lei può farlo anche da background grazie al permesso overlay)
         * comparirebbe un secondo dialog e il suo task porterebbe MotoBit in
         * primo piano, mandando in background l'app scelta dall'utente.
         */
        @Volatile
        var active: Boolean = false
            private set

        /** `openApp = true` → scenario 1 (TFT connesso); `false` → scenario 2 (bolla). */
        fun intent(context: Context, packageName: String, openApp: Boolean): Intent =
            Intent(context, StreamLaunchActivity::class.java).apply {
                putExtra(EXTRA_PACKAGE, packageName)
                putExtra(EXTRA_OPEN_APP, openApp)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_NO_ANIMATION)
            }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        AppSettings.init(applicationContext)
        SavedHud.init(applicationContext)
        SavedApps.init(applicationContext)
        HudSession.ensure(applicationContext)

        active = true

        val packageName = intent.getStringExtra(EXTRA_PACKAGE).orEmpty()
        val openApp = intent.getBooleanExtra(EXTRA_OPEN_APP, true)

        setContent {
            MotobitRideTheme {
                StreamLaunchScreen(
                    packageName = packageName,
                    openApp = openApp,
                    onFinish = { finish() }
                )
            }
        }
    }

    override fun onDestroy() {
        active = false
        super.onDestroy()
    }
}

@Composable
private fun StreamLaunchScreen(
    packageName: String,
    openApp: Boolean,
    onFinish: () -> Unit
) {
    val ctx = LocalContext.current

    val stateFlow = remember {
        HudSession.stream?.state ?: MutableStateFlow(StreamState.IDLE)
    }
    val streamState by stateFlow.collectAsState()
    val projection by ScreenCaptureService.mediaProjectionFlow.collectAsState()

    val connected = streamState == StreamState.CONNECTED ||
            streamState == StreamState.MIRRORING ||
            streamState == StreamState.AWAITING_PROJECTION

    val hasHud = SavedHud.current.value != null

    var cancelled by remember { mutableStateOf(false) }

    // Guardia: il consenso si chiede UNA volta per istanza di Activity.
    // (Con `noHistory` nel manifest l'Activity veniva finita quando compariva il
    // dialogo di sistema: al ritorno rinasceva e richiedeva il consenso in loop.)
    var consentRequested by remember { mutableStateOf(false) }

    val consentLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK && result.data != null) {
            AppLog.i("StreamLaunch", "Consenso cattura concesso")
            ScreenCaptureService.startService(ctx, result.resultCode, result.data!!)
            // Lo streaming parte dall'effetto qui sotto, appena c'è proiezione
            // e connessione.
        } else {
            AppLog.w("StreamLaunch", "Consenso negato: mirroring non avviato")
            cancelled = true
            onFinish()
        }
    }

    fun requestConsentIfNeeded() {
        if (consentRequested) return
        if (projection != null) {
            AppLog.i("StreamLaunch", "Proiezione già attiva: nessun dialogo")
            return
        }
        consentRequested = true
        val manager = ctx.getSystemService(Context.MEDIA_PROJECTION_SERVICE)
                as? MediaProjectionManager ?: return
        val intent = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            manager.createScreenCaptureIntent(
                MediaProjectionConfig.createConfigForDefaultDisplay()
            )
        } else {
            manager.createScreenCaptureIntent()
        }
        consentLauncher.launch(intent)
    }

    // NESSUN dialogo intermedio: al primo frame partono consenso e connessione.
    LaunchedEffect(Unit) {
        if (!hasHud) return@LaunchedEffect
        AppLog.i("StreamLaunch", "Avvio diretto: consenso + connessione in parallelo")

        // M4 — il completamento vive in HudSession: questa Activity e' solo UI.
        // Se viene distrutta durante l'attesa (TFT spento per minuti), lo
        // streaming parte lo stesso quando la sessione e la proiezione sono
        // pronte, e l'app viene riaperta.
        HudSession.armPendingLaunch(packageName, openApp)

        requestConsentIfNeeded()

        // NON si chiama connect() qui.
        //
        // La connessione e' gia' in corso (auto di sessione avviata dal tocco
        // sull'app). `HudSession.connect()` con il link non ancora attivo
        // esegue `teardownP2p()` e RIparte da zero: premere AVVIA a meta'
        // connessione la azzerava (era questa la causa di "9 volte su 10 non si
        // connette").
    }

    // Quando lo streaming parte, questa schermata ha finito il suo lavoro.
    LaunchedEffect(streamState) {
        if (streamState == StreamState.MIRRORING) onFinish()
    }

    if (!hasHud) {
        SimpleDialog(
            title = "Nessuna moto",
            message = "Registra prima il TFT dalla Home di MotoBit.",
            confirm = "Chiudi",
            onConfirm = onFinish,
            onDismiss = onFinish
        )
    } else if (!cancelled) {
        WaitingDialog(
            message = "Consenso e connessione al TFT in corso…",
            onCancel = {
                cancelled = true
                HudSession.cancelPendingLaunch()
                // Se il consenso era già stato concesso il servizio tiene la
                // proiezione: va fermato, altrimenti l'indicatore di
                // condivisione resta acceso senza trasmettere nulla.
                ScreenCaptureService.stopService(ctx)
                onFinish()
            }
        )
    }
}

@Composable
private fun WaitingDialog(message: String, onCancel: () -> Unit) {
    AlertDialog(
        onDismissRequest = onCancel,
        title = { Text("Avvio in corso") },
        text = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                CircularProgressIndicator(
                    modifier = Modifier.size(22.dp),
                    strokeWidth = 2.dp
                )
                Spacer(Modifier.width(14.dp))
                Text(text = message, style = MaterialTheme.typography.bodyMedium)
            }
        },
        confirmButton = {
            TextButton(onClick = onCancel) { Text("Annulla") }
        }
    )
}

@Composable
private fun SimpleDialog(
    title: String,
    message: String,
    confirm: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = { Text(text = message, style = MaterialTheme.typography.bodyMedium) },
        confirmButton = {
            TextButton(onClick = onConfirm) { Text(confirm) }
        }
    )
}
