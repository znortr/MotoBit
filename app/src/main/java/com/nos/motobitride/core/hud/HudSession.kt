package com.nos.motobitride.core.hud

import android.content.Context
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import com.nos.motobitride.core.log.AppLog
import com.nos.motobitride.core.settings.AppSettings
import com.nos.motobitride.feature.ap.WifiApController
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * Sessione HUD a vita di processo: possiede il link Wi-Fi Direct e il controller
 * dello streaming.
 *
 * ## Perche' esiste
 * Prima i due controller nascevano dentro il Composable di `HudScreen` e venivano
 * distrutti in `onDispose`:
 *  - `HudStreamController.stop()` -> `streamPaused = true` (riconnessione bloccata)
 *  - `WifiApController.dispose()` -> `autoRetryEnabled = false`, `savedQr = null`
 *    (loop di retry P2P ucciso per sempre)
 *
 * Se in background Android distrugge l'Activity (pressione memoria, swipe del
 * task, "Non mantenere attivita'"), la sessione moriva e non ripartiva nemmeno
 * tornando in primo piano. Ora la proprieta' e' qui: la UI e' solo un osservatore.
 *
 * ## Riconnessione automatica
 * Il link e' auto-ripristinante: quando il gruppo P2P torna su, `onHudConnected`
 * aggiorna l'host dello streaming e sollecita **subito** la riconnessione
 * (`onLinkRestored`), senza aspettare il timer di backoff.
 *
 * ## Attesa attiva -> ascolto passivo
 * Per i primi N minuti (Impostazioni, default 5) l'app tenta attivamente la
 * riconnessione. Oltre la soglia smette di tentare e resta in ascolto: il
 * receiver del link rimane registrato, quindi alla riaccensione del TFT la
 * riconnessione avviene comunque.
 */
object HudSession {

    private const val TAG = "HudSession"

    @Volatile
    private var initialized = false

    private var scope: CoroutineScope? = null
    private var appContext: Context? = null

    private val handler = Handler(Looper.getMainLooper())
    private var passiveRunnable: Runnable? = null

    /** Controller dello streaming (null solo in Compose Preview). */
    @Volatile
    var stream: HudStreamController? = null
        private set

    /** Controller del link Wi-Fi Direct (null solo in Compose Preview). */
    @Volatile
    var link: WifiApController? = null
        private set

    /**
     * L'utente ha chiesto il video: quando il link torna su si riparte da soli.
     * Diventa false solo con Stop/Chiudi espliciti.
     */
    @Volatile
    var wantsStream: Boolean = false
        private set

    /** Crea (una sola volta) i controller e collega i flussi di stato. */
    fun ensure(context: Context) {
        if (initialized) return
        synchronized(this) {
            if (initialized) return

            val app = context.applicationContext
            appContext = app
            val streamController = HudStreamController(app)

            val linkController = WifiApController(
                context = app,
                onHudConnected = { ip ->
                    AppLog.i(TAG, "Link P2P attivo, ip=$ip")
                    cancelActiveWait()
                    streamController.setPreferredHost(ip, WifiApController.DEFAULT_HUD_PORT)
                    vibrate()

                    // Riconnessione immediata: azzera il backoff e fa partire
                    // subito il probe, invece di aspettare il timer (fino a 10 s).
                    streamController.onLinkRestored(autoResume = wantsStream)
                },
                onHudDisconnected = {
                    AppLog.w(TAG, "Link P2P perso: lo streaming resta in attesa")
                    vibrate()
                    armActiveWait()
                }
            )

            // Se i probe TCP falliscono a ripetizione il gruppo P2P e' morto:
            // il solo probe non puo' ricrearlo, serve una sollecitazione al link.
            streamController.onLinkStalled = {
                linkController.nudge("probe TCP falliti")
            }

            stream = streamController
            link = linkController
            initialized = true

            val sc = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
            scope = sc

            // MediaProjection: appena il servizio di cattura la ottiene, va passata
            // al controller. Prima lo faceva un LaunchedEffect della UI, che in
            // background non gira.
            sc.launch {
                ScreenCaptureService.mediaProjectionFlow.collect { mp ->
                    if (mp != null) streamController.setMediaProjection(mp)
                }
            }

            // Notifica con lo stato reale (Connesso / Riconnessione / In attesa).
            sc.launch {
                streamController.status.collect { text ->
                    val ctx = appContext ?: return@collect
                    ScreenCaptureService.updateStatus(
                        ctx,
                        text.ifBlank { "Invio dello schermo all'HUD..." }
                    )
                }
            }

            AppLog.i(TAG, "Sessione HUD inizializzata (vita di processo)")
        }
    }

    // ------------------------------------------------------------------
    // Attesa attiva -> ascolto passivo
    // ------------------------------------------------------------------

    private fun armActiveWait() {
        cancelActiveWaitTimer()
        val minutes = AppSettings.activeWaitMinutes.value
        val runnable = Runnable {
            AppLog.w(TAG, "Attesa attiva scaduta ($minutes min): passo in ascolto passivo")
            link?.setPassiveWait(true)
            stream?.setPassiveWait(true)
            appContext?.let {
                ScreenCaptureService.updateStatus(it, "In attesa passiva del TFT")
            }
        }
        passiveRunnable = runnable
        handler.postDelayed(runnable, minutes * 60_000L)
        AppLog.i(TAG, "Attesa attiva: $minutes min")
    }

    private fun cancelActiveWaitTimer() {
        passiveRunnable?.let { handler.removeCallbacks(it) }
        passiveRunnable = null
    }

    private fun cancelActiveWait() {
        cancelActiveWaitTimer()
        link?.setPassiveWait(false)
        stream?.setPassiveWait(false)
    }

    // ------------------------------------------------------------------
    // Feedback aptico
    // ------------------------------------------------------------------

    private fun vibrate() {
        if (!AppSettings.vibrateOnConnect.value) return
        val ctx = appContext ?: return
        runCatching {
            val effect = VibrationEffect.createOneShot(60, VibrationEffect.DEFAULT_AMPLITUDE)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                ctx.getSystemService(VibratorManager::class.java)
                    ?.defaultVibrator
                    ?.vibrate(effect)
            } else {
                @Suppress("DEPRECATION")
                (ctx.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator)?.vibrate(effect)
            }
        }.onFailure { AppLog.w(TAG, "vibrazione fallita: ${it.message}") }
    }

    // ------------------------------------------------------------------
    // API
    // ------------------------------------------------------------------

    /**
     * Registra che l'utente vuole il video, senza avviare nulla: serve quando
     * prima va ricostruito il link (il video parte da solo al ritorno del link).
     */
    fun requestStream() {
        wantsStream = true
    }

    /** Avvia (o riprende) lo streaming e ricorda che l'utente lo vuole. */
    fun startStream() {
        wantsStream = true
        stream?.start()
    }

    /** Pausa richiesta dall'utente: la sessione resta viva, si ferma solo il video. */
    fun stopStream() {
        wantsStream = false
        cancelActiveWaitTimer()
        stream?.stop()
        appContext?.let { ScreenCaptureService.updateStatus(it, "In pausa") }
    }

    /** Chiusura completa (pulsante Chiudi): sessione, mirroring e link. */
    fun closeAll() {
        wantsStream = false
        cancelActiveWaitTimer()
        stream?.close()
        link?.disconnect()
        appContext?.let { ScreenCaptureService.updateStatus(it, "Fermo") }
    }
}
