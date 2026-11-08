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
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
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
import com.nos.motobitride.core.hud.HudSession
import com.nos.motobitride.core.hud.ScreenCaptureService
import com.nos.motobitride.core.hud.StreamState
import com.nos.motobitride.core.link.SavedHud
import com.nos.motobitride.core.log.AppLog
import com.nos.motobitride.core.settings.AppSettings
import com.nos.motobitride.ui.theme.MotobitRideTheme
import kotlinx.coroutines.flow.MutableStateFlow
import com.nos.motobitride.core.apps.SavedApps

/**
 * Activity **trasparente** che orchestra l'avvio del mirroring.
 *
 * Non mostra nessuna schermata dell'app: solo due dialoghi (connessione in
 * corso / conferma). Serve perché il consenso MediaProjection su Android 12+
 * può essere chiesto **solo da un'Activity visibile**, e perché così l'app
 * scelta resta dietro senza riportare in primo piano la Home di MotoBit.
 *
 * Riusa integralmente il sistema esistente: `HudSession` (link + sessione) e
 * `ScreenCaptureService` (cattura). Nessuna logica di protocollo qui.
 */
class StreamLaunchActivity : ComponentActivity() {

    companion object {
        private const val TAG = "StreamLaunch"
        private const val EXTRA_PACKAGE = "extra_package"
        private const val EXTRA_OPEN_APP = "extra_open_app"

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

    var connectRequested by remember { mutableStateOf(false) }
    var showConfirm by remember { mutableStateOf(false) }

    val hasHud = SavedHud.current.value != null

    // Connessione: richiesta una sola volta; se già connesso si passa subito
    // al dialogo di conferma.
    LaunchedEffect(connected) {
        if (connected) {
            showConfirm = true
        } else if (!connectRequested) {
            connectRequested = true
            AppLog.i("StreamLaunch", "Connessione al TFT richiesta")
            HudSession.connect()
        }
    }

    val consentLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK && result.data != null) {
            AppLog.i("StreamLaunch", "Consenso cattura concesso")
            ScreenCaptureService.startService(ctx, result.resultCode, result.data!!)
            HudSession.startStream()
            if (openApp) InstalledApps.launch(ctx, packageName)
            onFinish()
        } else {
            AppLog.w("StreamLaunch", "Consenso cattura negato: mirroring non avviato")
            onFinish()
        }
    }

    fun startMirroring() {
        if (projection != null) {
            AppLog.i("StreamLaunch", "Proiezione già attiva: avvio diretto")
            HudSession.startStream()
            if (openApp) InstalledApps.launch(ctx, packageName)
            onFinish()
            return
        }
        val manager = ctx.getSystemService(Context.MEDIA_PROJECTION_SERVICE)
                as? MediaProjectionManager
        if (manager == null) {
            onFinish()
            return
        }
        val intent = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            manager.createScreenCaptureIntent(
                MediaProjectionConfig.createConfigForDefaultDisplay()
            )
        } else {
            manager.createScreenCaptureIntent()
        }
        consentLauncher.launch(intent)
    }

    when {
        !hasHud -> SimpleDialog(
            title = "Nessuna moto",
            message = "Registra prima il TFT dalla Home di MotoBit.",
            confirm = "Chiudi",
            onConfirm = onFinish,
            onDismiss = onFinish
        )

        showConfirm -> SimpleDialog(
            title = "Avvia mirroring?",
            message = if (openApp && packageName.isNotBlank())
                "La trasmissione verso il TFT verrà avviata e poi si aprirà l'app scelta."
            else
                "La trasmissione verso il TFT verrà avviata.",
            confirm = "Avvia",
            onConfirm = { startMirroring() },
            dismiss = "Annulla",
            onDismiss = onFinish
        )

        else -> WaitingDialog(onCancel = onFinish)
    }
}

@Composable
private fun WaitingDialog(onCancel: () -> Unit) {
    AlertDialog(
        onDismissRequest = onCancel,
        title = { Text("Connessione al TFT") },
        text = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                CircularProgressIndicator(
                    modifier = Modifier.size(22.dp),
                    strokeWidth = 2.dp
                )
                Spacer(Modifier.width(14.dp))
                Text(
                    text = "Attendo che la moto risponda…",
                    style = MaterialTheme.typography.bodyMedium
                )
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
    dismiss: String? = null,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Text(
                text = message,
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.padding(vertical = 4.dp)
            )
        },
        confirmButton = {
            TextButton(onClick = onConfirm) { Text(confirm) }
        },
        dismissButton = if (dismiss != null) {
            {
                TextButton(onClick = onDismiss) { Text(dismiss) }
            }
        } else null
    )
}
