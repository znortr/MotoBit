package com.nos.motobitride.core.hud

import android.content.Context
import android.graphics.PixelFormat
import android.hardware.display.DisplayManager
import android.hardware.display.VirtualDisplay
import android.media.ImageReader
import android.media.projection.MediaProjection
import android.net.nsd.NsdManager
import android.net.nsd.NsdServiceInfo
import android.net.wifi.WifiManager
import android.os.Build
import android.os.Handler
import android.os.HandlerThread
import android.os.Looper
import android.util.DisplayMetrics
import api.Api
import api.MobileCallback
import api.MobileConfig
import api.MobileSession
import api.StreamHost
import com.nos.motobitride.core.log.AppLog
import com.nos.motobitride.core.settings.AppSettings
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.net.Inet4Address
import java.util.concurrent.Executor
import java.util.concurrent.atomic.AtomicBoolean

enum class StreamState {
    IDLE,                 // fermo (stop richiesto dall'utente)
    DISCOVERING,          // ricerca mDNS dell'HU
    CONNECTING,           // handshake PXC / EasyConn in corso
    WAITING_HUD,          // link/HU non raggiungibile: si ritenta all'infinito
    AWAITING_PROJECTION,  // sessione pronta, manca il consenso alla cattura schermo
    CONNECTED,            // sessione HUD pronta, video NON avviato (scelta utente)
    MIRRORING,            // encoder + VirtualDisplay attivi
    ERROR                 // errore non recuperabile
}

/**
 * Controller della sessione HUD + mirroring dello schermo del telefono.
 *
 * Note di protocollo ricavate dal sorgente Go della libreria:
 *  - `MuxSource{NoSignal, Live}`: con StaticSignal vuoto non esiste fallback
 *    statico -> nessun video di test.
 *  - `MediaCtrlInit` (cmd 16) porta la risoluzione in forma BINARIA:
 *    width = u16 LE [0:2], height = u16 LE [2:4], allineati a 16.
 *    Gli altri eventi (ScreenConf = cmd 96) sono JSON e NON vanno interpretati.
 *  - `PushFrame` scarta i frame finche' non arriva un IDR, quindi l'encoder
 *    usa keyint = 1 (IDR su ogni frame).
 *  - La `MediaProjection` NON va tenuta "a vuoto" mentre si fa l'handshake:
 *    viene richiesta solo quando la sessione e' pronta (vedi StreamState).
 */
class HudStreamController(
    private val context: Context
) {
    private var mobileSession: MobileSession? = null

    /**
     * Sessione in fase di `startSession()` (chiamata bloccante). Finche' non torna
     * puo' tenere occupate le porte 10920/10921/10922: senza chiuderla qui, un
     * Avvia subito dopo uno Stop trovava `listen tcp :10922: bind: address already
     * in use` (visto in log).
     */
    private var pendingSession: MobileSession? = null
    private var encoder: HudEncoder? = null

    @Volatile
    private var mediaProjection: MediaProjection? = null
    @Volatile
    private var virtualDisplay: VirtualDisplay? = null
    private var projectionCallback: MediaProjection.Callback? = null

    // Cattura del VirtualDisplay tramite ImageReader: cosi' il frame viene
    // riaccodato dall'encoder a 30 fps anche se lo schermo e' fermo.
    private var imageReader: ImageReader? = null
    private var captureThread: HandlerThread? = null

    @Volatile
    private var videoWidth = DEFAULT_WIDTH
    @Volatile
    private var videoHeight = DEFAULT_HEIGHT

    private val targetFps = 30
    // all-I (keyint=1) a 592x752: 2 Mbps erano insufficienti per un flusso continuo
    private val targetBitrate = 3_000_000

    /** Vuoto => modalita' live-only: nessun video di test trasmesso. */
    private val staticSignalBytes: ByteArray = ByteArray(0)

    private val displayManager =
        context.getSystemService(Context.DISPLAY_SERVICE) as DisplayManager
    private val wifiManager =
        context.applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager
    private val nsdManager =
        context.getSystemService(Context.NSD_SERVICE) as NsdManager
    private val mainHandler = Handler(Looper.getMainLooper())

    private var multicastLock: WifiManager.MulticastLock? = null
    private var discoveryListener: NsdManager.DiscoveryListener? = null

    /**
     * Chiusura del callback mDNS "moderno" (API 34+): null quando non c'e' una
     * risoluzione in sospeso, sempre null su API 29..33.
     * Tipo volutamente `() -> Unit` invece di `NsdManager.ServiceInfoCallback`,
     * cosi' nessuna classe introdotta in API 34 compare nella firma di un campo.
     */
    @Volatile
    private var unregisterServiceInfoCallback: (() -> Unit)? = null

    private val serviceType = "_EasyConn._tcp."
    private val expectedPackageName = "com.cfmoto.cfmotointernational"

    @Volatile
    private var isRunning = false
    @Volatile
    private var isDiscovering = false

    // Lock radio tenuto per tutto il mirroring: senza, il power-save del Wi-Fi
    // Direct puo' addormentare la radio e uccidere i canali TCP verso il TFT
    // (l'app ufficiale Carbit-Ride non presenta il problema, quindi e' un
    // comportamento dipendente dalla nostra app).
    private var wifiLock: WifiManager.WifiLock? = null
    @Volatile
    private var stopRequested = false

    // Host fornito dal livello link (gateway Wi-Fi / GO Wi-Fi Direct):
    // quando presente, mDNS viene saltato (inaffidabile su reti non-default).
    @Volatile
    private var preferredHostIp: String? = null
    @Volatile
    private var preferredHostPort: Int = DEFAULT_HUD_PORT

    /**
     * Sollecitazione verso il livello link: dopo [LINK_STALL_THRESHOLD] cicli di
     * probe falliti consecutivi il gruppo P2P e' probabilmente morto e va
     * ricreato (il solo probe TCP non lo puo' fare).
     */
    @Volatile
    var onLinkStalled: (() -> Unit)? = null

    /** Cicli di probe consecutivi falliti: azzerato appena l'HU risponde. */
    @Volatile
    private var consecutiveProbeFailures = 0

    /**
     * Ascolto passivo: scaduta l'"attesa attiva" non si arma piu' nessun
     * tentativo. Il receiver del link resta in ascolto, quindi alla
     * riaccensione del TFT la riconnessione avviene comunque.
     */
    @Volatile
    private var passiveWait = false

    /** Runnable del prossimo tentativo, per poterlo annullare al ritorno del link. */
    private var reconnectRunnable: Runnable? = null

    /** Vero mentre un thread di probe TCP e' in corso (evita probe duplicati). */
    @Volatile
    private var probeInFlight = false

    /**
     * Vero mentre un handshake (`connectToHost`) e' in corso.
     *
     * Senza questa guardia, una sollecitazione esterna (`onLinkRestored`) che
     * arriva durante l'handshake faceva partire un SECONDO `connectToHost`:
     * due `MobileSession` concorrenti sullo stesso HU, una orfana. L'HU chiude
     * la piu' vecchia e lo streaming entra in un ciclo di riconnessioni che il
     * TFT vede come immagine intermittente.
     */
    @Volatile
    private var connecting = false

    /** Momento in cui e' iniziato l'handshake corrente (per la guardia anti-blocco). */
    @Volatile
    private var connectingSince = 0L

    /**
     * Handshake considerato bloccato oltre questa soglia: la guardia si
     * auto-ripara invece di impedire per sempre nuove riconnessioni.
     */
    private fun isConnecting(): Boolean =
        connecting && (System.currentTimeMillis() - connectingSince) < CONNECT_STUCK_MS

    private var mirrorAttempts = 0
    private var watchdogActive = false

    /** Timer diagnostico aggregato dei tre canali: nessun evento grezzo. */
    private var channelDiagnosticsActive = false
    private val channelDiagnosticsRunnable = object : Runnable {
        override fun run() {
            if (!channelDiagnosticsActive) return
            val session = mobileSession
            if (isRunning && session != null) {
                // Diagnostica disabilitata (Impostazioni -> Diagnostica): il
                // tick resta (costo nullo) ma non si costruisce lo snapshot.
                if (AppLog.isDiagnosticsEnabled()) {
                    try {
                        AppLog.i(TAG, "CHANNEL_DIAG ${session.diagnosticsSnapshot()}")
                    } catch (t: Throwable) {
                        AppLog.w(TAG, "CHANNEL_DIAG snapshot non disponibile: ${t.message}")
                    }
                }
                mainHandler.postDelayed(this, CHANNEL_DIAGNOSTICS_INTERVAL_MS)
            } else {
                channelDiagnosticsActive = false
            }
        }
    }

    // Riconnessione: illimitata finche' l'utente non preme Stop, con backoff.
    private var reconnectAttempt = 0
    private var reconnectDelayMs = RECONNECT_MIN_DELAY_MS
    private var reconnectPending = false

    // Dopo ENCODER_IDLE_TIMEOUT_MS di attesa l'encoder va in pausa: una sosta
    // breve resta "calda" (resume istantaneo), una lunga non consuma batteria.
    @Volatile
    private var encoderPaused = false

    /**
     * Stop dell'utente = PAUSA: la sessione Go resta VIVA, si smette solo di
     * inviare frame. Chiuderla faceva rifiutare all'HU il nuovo handshake per
     * ~45 s (visto in log: stop 09:54:20 -> ripresa solo 09:55:11).
     */
    /**
     * Vero quando l'utente ha fermato il video ("Ferma mirroring"):
     * la sessione resta viva e la riconnessione resta attiva.
     * Diverso da [stopRequested], che è la chiusura totale ("Chiudi").
     */
    @Volatile
    private var streamPaused = false

    /**
     * Vero quando l'utente vuole il video. Se false la sessione si stabilisce
     * comunque (stato CONNECTED) ma encoder e VirtualDisplay non vengono creati.
     */
    @Volatile
    private var videoWanted = false
    @Volatile
    private var encoderIdleTimeoutMs = DEFAULT_ENCODER_IDLE_TIMEOUT_MS
    private var encoderIdleTimerPending = false
    private val pauseEncoderRunnable = Runnable {
        encoderIdleTimerPending = false
        if (stopRequested || _state.value != StreamState.WAITING_HUD) return@Runnable
        val enc = encoder ?: return@Runnable
        if (encoderPaused) return@Runnable

        encoderPaused = true
        enc.pause()
        AppLog.i(
            TAG,
            "In attesa da ${encoderIdleTimeoutMs / 1_000L} s: " +
                    "encoding sospeso (CPU libera per le altre app)"
        )
    }

    private val _state = MutableStateFlow(StreamState.IDLE)
    val state: StateFlow<StreamState> = _state.asStateFlow()

    private val _status = MutableStateFlow("Pronto")
    val status: StateFlow<String> = _status.asStateFlow()

    val isLiveMirroring: Boolean
        get() = encoder != null && virtualDisplay != null

    val negotiatedWidth: Int get() = videoWidth
    val negotiatedHeight: Int get() = videoHeight

    // ------------------------------------------------------------------
    // mDNS discovery
    // ------------------------------------------------------------------

    private fun acquireMulticastLock() {
        if (multicastLock?.isHeld == true) return
        multicastLock = wifiManager.createMulticastLock("mdns_lock").apply {
            setReferenceCounted(false)
            acquire()
        }
    }

    private fun releaseMultiCastLock() {
        multicastLock?.let {
            if (it.isHeld) it.release()
        }
        multicastLock = null
    }

    private fun discoverHost(timeoutMs: Long, onResult: (Result<StreamHost>) -> Unit) {
        if (isDiscovering) {
            onResult(Result.failure(IllegalStateException("discovery gia' in corso")))
            return
        }

        acquireMulticastLock()
        isDiscovering = true
        val completed = AtomicBoolean(false)

        val listener = object : NsdManager.DiscoveryListener {
            override fun onDiscoveryStarted(serviceType: String?) {
                AppLog.i(TAG, "mDNS: discovery avviata ($serviceType)")
            }

            override fun onDiscoveryStopped(serviceType: String?) {
                AppLog.i(TAG, "mDNS: discovery fermata")
            }

            override fun onServiceFound(serviceInfo: NsdServiceInfo?) {
                if (serviceInfo == null) return
                if (!serviceInfo.serviceType.endsWith(serviceType)) return

                AppLog.i(TAG, "mDNS: servizio trovato ${serviceInfo.serviceName}")

                resolveServiceInfo(
                    serviceInfo = serviceInfo,
                    onResolved = { hostAddress, port, attrs ->
                        if (!isDiscovering || completed.get()) return@resolveServiceInfo

                        try {
                            val pkgAttr = attrs["packagename"]?.toString(Charsets.UTF_8)
                            val ipAttr = attrs["ip"]?.toString(Charsets.UTF_8)

                            val ip = ipAttr ?: hostAddress
                            val packageName = pkgAttr ?: expectedPackageName

                            val matchesPackage =
                                expectedPackageName.isEmpty() || packageName == expectedPackageName

                            if (matchesPackage && ip != null) {
                                if (!completed.compareAndSet(false, true)) return@resolveServiceInfo
                                stopDiscoveryInternal()

                                AppLog.i(TAG, "mDNS: host risolto $ip:$port (pkg=$packageName)")
                                val ecHost = Api.newStreamHost(ip, port.toString(), packageName)
                                onResult(Result.success(ecHost))
                            } else {
                                AppLog.w(TAG, "mDNS: servizio ignorato (pkg=$packageName)")
                            }
                        } catch (t: Throwable) {
                            AppLog.e(TAG, "mDNS: errore resolve", t)
                        }
                    },
                    onFailed = { errorCode ->
                        AppLog.w(TAG, "mDNS: resolve fallita (errore $errorCode)")
                    }
                )
            }

            override fun onServiceLost(serviceInfo: NsdServiceInfo?) {
                AppLog.w(TAG, "mDNS: servizio perso ${serviceInfo?.serviceName}")
            }

            override fun onStartDiscoveryFailed(serviceType: String?, errorCode: Int) {
                AppLog.e(TAG, "mDNS: start fallita (errore $errorCode)")
                stopDiscoveryInternal()
                if (completed.compareAndSet(false, true)) {
                    onResult(Result.failure(RuntimeException("NSD start failed: $errorCode")))
                }
            }

            override fun onStopDiscoveryFailed(serviceType: String?, errorCode: Int) {
                AppLog.w(TAG, "mDNS: stop fallita (errore $errorCode)")
                stopDiscoveryInternal()
            }
        }

        discoveryListener = listener

        try {
            nsdManager.discoverServices(serviceType, NsdManager.PROTOCOL_DNS_SD, listener)
        } catch (e: Exception) {
            AppLog.e(TAG, "mDNS: discoverServices ha lanciato", e)
            stopDiscoveryInternal()
            if (completed.compareAndSet(false, true)) onResult(Result.failure(e))
            return
        }

        mainHandler.postDelayed({
            if (!isDiscovering || !completed.compareAndSet(false, true)) return@postDelayed
            AppLog.w(TAG, "mDNS: timeout dopo $timeoutMs ms")
            stopDiscoveryInternal()
            onResult(Result.failure(Exception("mDNS discovery timeout")))
        }, timeoutMs)
    }

    /**
     * Risolve IP / porta / attributi TXT di un servizio mDNS trovato.
     *
     * Due strade con lo stesso contratto (una sola callback: `onResolved` oppure
     * `onFailed`):
     *  - API 34+ : `registerServiceInfoCallback` + `hostAddresses`, perche'
     *              `resolveService` e `NsdServiceInfo.host` sono deprecate da Android 14.
     *  - API 29..33: `resolveService` legacy (unica disponibile), con @Suppress mirato.
     *
     * L'IP restituito e' quello IPv4 quando presente, come faceva il vecchio
     * `resolved.host`, per non cambiare la destinazione della connessione.
     */
    @Suppress("DEPRECATION")
    private fun resolveServiceInfo(
        serviceInfo: NsdServiceInfo,
        onResolved: (hostAddress: String?, port: Int, attributes: Map<String, ByteArray>) -> Unit,
        onFailed: (errorCode: Int) -> Unit
    ) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            val settled = AtomicBoolean(false)
            lateinit var callback: NsdManager.ServiceInfoCallback
            val unregister = {
                unregisterServiceInfoCallback = null
                try {
                    nsdManager.unregisterServiceInfoCallback(callback)
                } catch (e: Exception) {
                    AppLog.w(TAG, "mDNS: unregisterServiceInfoCallback: ${e.message}")
                }
            }
            callback = object : NsdManager.ServiceInfoCallback {
                override fun onServiceInfoCallbackRegistrationFailed(errorCode: Int) {
                    if (!settled.compareAndSet(false, true)) return
                    unregisterServiceInfoCallback = null
                    onFailed(errorCode)
                }

                override fun onServiceUpdated(info: NsdServiceInfo) {
                    if (!settled.compareAndSet(false, true)) return
                    unregister()
                    // hostAddresses e' una List<InetAddress> (API 34+): preferiamo IPv4
                    // come faceva il vecchio `host`, per non cambiare la destinazione.
                    val addresses = info.hostAddresses
                    val preferred = addresses.firstOrNull { it is Inet4Address }
                        ?: addresses.firstOrNull()
                    onResolved(preferred?.hostAddress, info.port, info.attributes)
                }

                override fun onServiceLost() {
                    // Servizio sparito prima di essere risolto: nessun codice NsdManager
                    // corrispondente, usiamo -2 (stesso trattamento degli altri errori).
                    if (!settled.compareAndSet(false, true)) return
                    unregisterServiceInfoCallback = null
                    onFailed(-2)
                }

                override fun onServiceInfoCallbackUnregistered() {
                    // chiusura confermata dal sistema: nessuna azione necessaria
                }
            }
            unregisterServiceInfoCallback = unregister
            try {
                nsdManager.registerServiceInfoCallback(
                    serviceInfo,
                    Executor { command -> mainHandler.post(command) },
                    callback
                )
            } catch (e: Exception) {
                if (settled.compareAndSet(false, true)) {
                    unregisterServiceInfoCallback = null
                    AppLog.e(TAG, "mDNS: registerServiceInfoCallback ha lanciato", e)
                    onFailed(-1)
                }
            }
            return
        }

        // API 29..33: unica API disponibile (deprecata solo a partire da API 34).
        nsdManager.resolveService(serviceInfo, object : NsdManager.ResolveListener {
            override fun onResolveFailed(info: NsdServiceInfo?, errorCode: Int) {
                onFailed(errorCode)
            }

            override fun onServiceResolved(resolved: NsdServiceInfo) {
                onResolved(resolved.host?.hostAddress, resolved.port, resolved.attributes)
            }
        })
    }

    fun stopDiscovery() = stopDiscoveryInternal()

    private fun stopDiscoveryInternal() {
        if (!isDiscovering) return
        isDiscovering = false

        discoveryListener?.let { listener ->
            try {
                nsdManager.stopServiceDiscovery(listener)
            } catch (e: IllegalArgumentException) {
                AppLog.w(TAG, "mDNS: stopServiceDiscovery gia' fermo")
            } catch (e: Exception) {
                AppLog.e(TAG, "mDNS: stopServiceDiscovery ha lanciato", e)
            }
        }
        discoveryListener = null
        unregisterServiceInfoCallback?.let { it() }
        unregisterServiceInfoCallback = null
        releaseMultiCastLock()
    }

    // ------------------------------------------------------------------
    // API pubblica
    // ------------------------------------------------------------------

    fun start() {
        videoWanted = true
        stopRequested = false
        streamPaused = false

        // Sessione gia' viva ma video spento: basta agganciare il mirroring,
        // senza rifare l'handshake.
        if (isRunning) {
            if (_state.value == StreamState.MIRRORING) {
                AppLog.i(TAG, "start(): mirroring gia' attivo")
                return
            }
            acquireStreamLocks()
            resumeEncoderIfPaused()
            if (mediaProjection != null && attachMirroring()) {
                _state.value = StreamState.MIRRORING
                _status.value = "Mirroring ${videoWidth}x${videoHeight}"
                encoder?.requestSyncFrame()
                AppLog.i(TAG, "Video avviato sulla sessione gia' connessa")
            } else {
                _state.value = StreamState.AWAITING_PROJECTION
            }
            startWatchdog()
            return
        }

        if (isDiscovering) {
            AppLog.w(TAG, "start() ignorato: discovering gia' attivo")
            return
        }

        acquireStreamLocks()
        mirrorAttempts = 0
        reconnectAttempt = 0
        reconnectDelayMs = RECONNECT_MIN_DELAY_MS
        reconnectPending = false

        // Stop era una pausa: se la sessione e' ancora viva si riprende SUBITO,
        // senza handshake (l'HU non ha nulla da negoziare).
        val alive = mobileSession?.let { runCatching { it.isRunning() }.getOrDefault(false) }
        if (alive == true) {
            AppLog.i(TAG, "Sessione ancora viva: ripresa immediata senza handshake")
            isRunning = true
            resumeEncoderIfPaused()
            if (mediaProjection != null && attachMirroring()) {
                _state.value = StreamState.MIRRORING
                _status.value = "Mirroring ${videoWidth}x${videoHeight}"
                encoder?.requestSyncFrame()
            } else {
                _state.value = StreamState.AWAITING_PROJECTION
            }
            startWatchdog()
            return
        }

        _status.value = "Ricerca HUD..."
        beginDiscovery()
    }

    /**
     * Stabilisce la **connessione** con l'HUD senza avviare il video.
     *
     * Stato finale: CONNECTED (sessione pronta, encoder non creato).
     * Se la sessione e' gia' viva non fa nulla. Il video si avvia poi con
     * [start].
     */
    fun connectOnly() {
        videoWanted = false
        stopRequested = false
        streamPaused = true

        if (isRunning) {
            _state.value = StreamState.CONNECTED
            _status.value = "Connesso all'HUD"
            AppLog.i(TAG, "connectOnly(): sessione gia' viva")
            return
        }
        if (isDiscovering) return
        if (isConnecting()) {
            AppLog.i(TAG, "connectOnly(): handshake gia' in corso")
            return
        }

        acquireStreamLocks()
        mirrorAttempts = 0
        reconnectAttempt = 0
        reconnectDelayMs = RECONNECT_MIN_DELAY_MS
        reconnectPending = false

        _status.value = "Connessione all'HUD..."
        AppLog.i(TAG, "connectOnly(): avvio connessione (video non richiesto)")
        beginDiscovery()
    }

    /**
     * Acquisisce WifiLock in modalita' FULL_HIGH_PERF + MulticastLock: tengono
     * sveglia la radio Wi-Fi per tutto il mirroring e impediscono al power-save
     * di degradare i canali TCP verso il TFT.
     */
    private fun acquireStreamLocks() {
        if (wifiLock?.isHeld == true) return
        try {
            wifiLock = wifiManager.createWifiLock(
                WifiManager.WIFI_MODE_FULL_HIGH_PERF, "motobit:stream"
            ).apply {
                setReferenceCounted(false)
                acquire()
            }
            AppLog.i(TAG, "WIFI_LOCK acquisito (WIFI_MODE_FULL_HIGH_PERF)")
        } catch (t: Throwable) {
            AppLog.w(TAG, "WIFI_LOCK non acquisito: ${t.message}")
        }
    }

    private fun releaseStreamLocks() {
        try {
            if (wifiLock?.isHeld == true) {
                wifiLock?.release()
                AppLog.i(TAG, "WIFI_LOCK rilasciato")
            }
        } catch (t: Throwable) {
            AppLog.w(TAG, "WIFI_LOCK release fallito: ${t.message}")
        }
        wifiLock = null
    }

    /**
     * Imposta l'host dell'HU ricavato dal link Wi-Fi (gateway o group owner P2P).
     * Se valorizzato, `start()` non usa mDNS.
     */
    fun setPreferredHost(ip: String?, port: Int = DEFAULT_HUD_PORT) {
        preferredHostIp = ip
        preferredHostPort = port
        if (ip != null) {
            AppLog.i(TAG, "Host HUD dal link: $ip:$port (mDNS verra' saltato)")
        }
    }

    fun stop() {
        AppLog.i(TAG, "stop(): video fermo, sessione e link restano attivi")
        videoWanted = false
        streamPaused = true
        cancelEncoderIdleTimer()

        // NB: NON si ferma il watchdog e NON si rilasciano i lock: la sessione
        // e il link devono restare vivi (e riconnettersi da soli se cadono).
        if (!encoderPaused) {
            encoderPaused = true
            encoder?.pause()
        }

        _state.value = if (isRunning) StreamState.CONNECTED else StreamState.IDLE
        _status.value = "Connesso · video fermo"
    }

    /** Chiusura completa: sessione + mirroring + projection. Usata da "Chiudi". */
    fun close() {
        AppLog.i(TAG, "close() richiesto (chiusura completa)")
        stopRequested = true
        streamPaused = false
        stopWatchdog()
        cancelEncoderIdleTimer()
        encoderPaused = false
        _status.value = "Fermo"
        stopInternal()
        _state.value = StreamState.IDLE
    }

    /**
     * Chiamata quando l'utente ha concesso la cattura schermo.
     * NON deve essere chiamata prima che la sessione sia pronta: la projection
     * va usata subito (Android 14+), altrimenti viene invalidata.
     */
    fun setMediaProjection(mp: MediaProjection?) {
        unregisterProjectionCallback()
        mediaProjection = mp

        if (mp == null) {
            AppLog.w(TAG, "MediaProjection = null")
            return
        }

        try {
            val cb = object : MediaProjection.Callback() {
                override fun onStop() {
                    AppLog.w(TAG, "MediaProjection revocata dal sistema/utente")
                    releaseVirtualDisplay()
                    unregisterProjectionCallback()
                    mediaProjection = null
                    if (isRunning) {
                        _state.value = StreamState.AWAITING_PROJECTION
                        // Su Android 14+ il consenso puo' essere chiesto solo con
                        // l'app visibile: se la revoca arriva in background il
                        // flusso resta fermo finche' l'utente non riapre l'app.
                        // Il messaggio finisce anche nella notifica.
                        _status.value =
                            "Cattura schermo revocata: riapri MotoBit per riprendere"
                        AppLog.w(TAG, "Serve un nuovo consenso alla cattura schermo")
                    }
                }
            }
            mp.registerCallback(cb, mainHandler)
            projectionCallback = cb
        } catch (e: Exception) {
            AppLog.e(TAG, "registerCallback fallita", e)
        }

        AppLog.i(TAG, "MediaProjection acquisita")
        mirrorAttempts = 0

        // M1 — La proiezione è solo una RISORSA: non decide lo stato.
        //
        // Prima, se la sessione non era ancora pronta (isRunning=false, cioè
        // sempre nei primi secondi dalla connessione) `attachMirroring()`
        // restituiva false e qui si forzava AWAITING_PROJECTION. Quello stato
        // faceva scattare il consenso "legacy" di HudScreen subito DOPO il
        // consenso appena dato: doppio dialogo e salto di task (MotoBit davanti,
        // app scelta dietro).
        //
        // Ora si aggancia solo se è possibile; altrimenti ci penserà
        // `connectToHost()` quando la sessione sarà pronta.
        if (attachMirroring()) {
            resumeEncoderIfPaused()
        } else {
            AppLog.i(TAG, "Proiezione pronta: mirroring agganciato alla connessione")
        }
    }

    // ------------------------------------------------------------------
    // Sessione
    // ------------------------------------------------------------------

    private fun beginDiscovery() {
        if (stopRequested) return
        if (isConnecting()) {
            AppLog.i(TAG, "Handshake gia' in corso: salto la discovery")
            return
        }
        _state.value = StreamState.DISCOVERING

        // Se il livello link conosce gia' l'IP dell'HU, saltiamo mDNS.
        val knownIp = preferredHostIp
        if (knownIp != null) {
            if (probeInFlight) {
                AppLog.i(TAG, "Probe gia' in corso: ignoro la richiesta")
                return
            }
            probeInFlight = true
            _status.value = "Connessione a $knownIp (dal link Wi-Fi)"
            AppLog.i(TAG, "Uso host dal link: $knownIp:$preferredHostPort")
            Thread {
                try {
                    // PROBE: l'HU accetta l'handshake solo quando il suo server e'
                    // pronto. Una connect TCP da 700 ms lo dice subito; il dial cieco
                    // della libreria costa 5-10 s a tentativo (visto in log).
                    if (!waitForHudPort(knownIp, preferredHostPort)) {
                        consecutiveProbeFailures++
                        if (consecutiveProbeFailures >= LINK_STALL_THRESHOLD) {
                            consecutiveProbeFailures = 0
                            AppLog.w(
                                TAG,
                                "Link fermo da $LINK_STALL_THRESHOLD cicli: chiedo al link di ricreare il gruppo P2P"
                            )
                            onLinkStalled?.invoke()
                        }
                        scheduleReconnect("porta $knownIp:$preferredHostPort chiusa")
                        return@Thread
                    }
                    consecutiveProbeFailures = 0
                    try {
                        val host = Api.newStreamHost(
                            knownIp,
                            preferredHostPort.toString(),
                            expectedPackageName
                        )
                        connectToHost(host)
                    } catch (t: Throwable) {
                        AppLog.e(TAG, "Host dal link non valido", t)
                        scheduleReconnect("host dal link non valido: ${t.message}")
                    }
                } finally {
                    probeInFlight = false
                }
            }.start()
            return
        }

        _status.value = "Ricerca HUD..."
        AppLog.i(TAG, "Ricerca HUD (mDNS)")

        try {
            discoverHost(DISCOVERY_TIMEOUT_MS) { result ->
                result.onSuccess { host ->
                    _status.value = "HUD trovato"
                    connectToHost(host)
                }
                result.onFailure { err ->
                    AppLog.e(TAG, "Discovery fallita", err)
                    // Nessun limite: si continua finche' non arriva Stop.
                    scheduleReconnect("HUD non trovato")
                }
            }
        } catch (t: Throwable) {
            AppLog.e(TAG, "discoverHost ha lanciato", t)
            scheduleReconnect("errore discovery: ${t.message}")
        }
    }

    /** La porta dell'HU risponde? Costo massimo [PROBE_TIMEOUT_MS]. */
    private fun isHudPortOpen(host: String, port: Int): Boolean = try {
        java.net.Socket().use { socket ->
            socket.connect(java.net.InetSocketAddress(host, port), PROBE_TIMEOUT_MS)
            true
        }
    } catch (_: Throwable) {
        false
    }

    /** Attende che l'HU sia pronto; false se non lo diventa entro [PROBE_MAX_WAIT_MS]. */
    private fun waitForHudPort(host: String, port: Int): Boolean {
        var waited = 0L
        while (waited < PROBE_MAX_WAIT_MS) {
            if (stopRequested) return false
            if (isHudPortOpen(host, port)) {
                AppLog.i(TAG, "HUD pronto su $host:$port dopo ${waited} ms")
                return true
            }
            try {
                Thread.sleep(PROBE_INTERVAL_MS)
            } catch (_: InterruptedException) {
                return false
            }
            waited += PROBE_INTERVAL_MS
        }
        return false
    }

    private fun connectToHost(host: StreamHost) {
        if (isConnecting()) {
            AppLog.i(TAG, "Handshake gia' in corso: ignoro la nuova richiesta")
            return
        }
        if (connecting) {
            AppLog.w(
                TAG,
                "Handshake precedente bloccato da oltre ${CONNECT_STUCK_MS / 1000}s: lo considero caduto"
            )
        }
        connecting = true
        connectingSince = System.currentTimeMillis()
        try {
            connectToHostInternal(host)
        } finally {
            connecting = false
            connectingSince = 0L
        }
    }

    private fun connectToHostInternal(host: StreamHost) {
        _state.value = StreamState.CONNECTING
        _status.value = "Connessione all'HUD..."
        AppLog.i(TAG, "Handshake sessione HUD in corso...")
        val started = System.currentTimeMillis()
        AppLog.i(
            TAG,
            "SESSION_DIAG start wall=${java.text.SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSSXXX", java.util.Locale.US).format(java.util.Date())} " +
                    "elapsedRealtimeMs=${android.os.SystemClock.elapsedRealtime()} " +
                    "host=${preferredHostIp ?: "mDNS"}:$preferredHostPort"
        )

        // staticSignalBytes vuoto => nessun video di test
        val cfg: MobileConfig = Api.newMobileConfig(
            staticSignalBytes,
            targetFps.toLong(),
            10L,
            5L,
            10L,
            3L
        )

        val callback = object : MobileCallback {
            override fun onError(msg: String?, fatal: Boolean) {
                val message = msg ?: "unknown HUD error"
                if (message.contains("READ_TIMEOUT") || message.contains("EOF") ||
                    message.contains("WRITE_ERROR") || message.contains("connection abort")
                ) {
                    AppLog.w(
                        TAG,
                        "SESSION_DIAG callback wall=${java.text.SimpleDateFormat("HH:mm:ss.SSS", java.util.Locale.US).format(java.util.Date())} " +
                                "elapsedRealtimeMs=${android.os.SystemClock.elapsedRealtime()} fatal=$fatal reason=$message"
                    )
                }
                // Durante uno stop volontario il socket viene chiuso da noi:
                // non e' un errore da segnalare all'utente.
                if (stopRequested || !isRunning) {
                    AppLog.i(TAG, "Errore HUD durante la chiusura (ignorato): $message")
                    return
                }
                if (fatal) {
                    AppLog.e(TAG, "Errore HUD FATALE: $message")
                    scheduleReconnect("errore fatale: $message")
                } else {
                    // Errore non fatale (es. READ_TIMEOUT della socket PXC :54612
                    // condannata dopo ~14.5 min): la socket orfana si chiude in Go,
                    // ma le socket attive (:54613 Heartbeat, :54614 MediaControl,
                    // :54615 MediaStream) restano 100% aperte e lo streaming video
                    // continua senza alcuna interruzione e senza mai mostrare QR.
                    AppLog.w(TAG, "Errore HUD non fatale (ignorato): $message")
                }
            }

            override fun onEvent(time: Long, source: Long, command: Long, payload: ByteArray?) {
                handleHudEvent(source, command, payload)
            }

            override fun onStopped() {
                AppLog.w(
                    TAG,
                    "SESSION_DIAG stopped wall=${java.text.SimpleDateFormat("HH:mm:ss.SSS", java.util.Locale.US).format(java.util.Date())} " +
                            "elapsedRealtimeMs=${android.os.SystemClock.elapsedRealtime()} stopRequested=$stopRequested paused=$streamPaused"
                )
                if (stopRequested) {
                    AppLog.i(TAG, "Sessione HUD chiusa (stop richiesto)")
                    return
                }
                AppLog.w(TAG, "Sessione HUD terminata dall'HU")
                // L'encoder e il VirtualDisplay restano vivi: riconnettiamo solo
                // la sessione Go, cosi' il video riprende senza nuovo consenso.
                scheduleReconnect("sessione chiusa dall'HU")
            }
        }

        val session: MobileSession = try {
            Api.newMobileSession(cfg, callback)
        } catch (t: Throwable) {
            AppLog.e(TAG, "Creazione MobileSession fallita", t)
            scheduleReconnect("creazione sessione fallita: ${t.message}")
            return
        } as MobileSession
        pendingSession = session

        try {
            session.setECHost(host)
            val tz = java.util.TimeZone.getDefault()
            val tzOffsetSec = tz.getOffset(System.currentTimeMillis()) / 1000L
            session.setClockTimeZone(tz.id, tzOffsetSec)
            // Numero di push della raffica orologio scelto nelle Impostazioni
            // (bisezione 3..60, default 3).
            session.setClockPushCount(AppSettings.clockPushCount.value.toLong())
            // Push dell'ora NON richiesto ogni 5 min: default OFF. Misurato che
            // intasa la socket di controllo PXC (:54612): resta in coda TCP non
            // confermata e blocca gli ACK 0x10631 verso il TFT.
            session.setPeriodicClockKeepalive(AppSettings.periodicClockKeepalive.value)

            session.startSession()

            // Stop/Pausa puo' arrivare mentre startSession() e' bloccato: senza
            // questo controllo la sessione appena creata rivitalizzerebbe lo stream.
            if (stopRequested) {
                AppLog.i(TAG, "Chiudi durante l'handshake: chiudo la sessione creata")
                closeSessionOnly()
                return
            }
        } catch (t: Throwable) {
            AppLog.e(TAG, "startSession fallita", t)
            if (pendingSession === session) pendingSession = null
            // Caso tipico: TFT spento / fuori portata. NON e' un errore fatale:
            // la MediaProjection resta viva e si riprova piu' tardi.
            scheduleReconnect("handshake fallito: ${t.message}")
            return
        }

        val elapsed = System.currentTimeMillis() - started
        AppLog.i(
            TAG,
            "SESSION_DIAG ready wall=${java.text.SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSSXXX", java.util.Locale.US).format(java.util.Date())} " +
                    "elapsedRealtimeMs=${android.os.SystemClock.elapsedRealtime()} handshakeMs=$elapsed"
        )
        AppLog.i(TAG, "Sessione HUD pronta in ${elapsed} ms")

        if (pendingSession === session) pendingSession = null

        // Sicurezza: se esistesse ancora una sessione precedente, va chiusa.
        // Due sessioni vive sullo stesso HU vengono percepite dal TFT come
        // immagine intermittente (alterna i frame delle due).
        mobileSession?.let { old ->
            if (old !== session) {
                AppLog.w(TAG, "Sessione precedente ancora viva: la chiudo prima della nuova")
                runCatching { old.stopSession() }
            }
        }
        mobileSession = session

        isRunning = true
        mirrorAttempts = 0
        reconnectAttempt = 0
        reconnectDelayMs = RECONNECT_MIN_DELAY_MS

        // ORDINE CORRETTO: solo adesso chiediamo (o usiamo) la MediaProjection.
        // Tenerla in attesa durante l'handshake la fa scadere su Android 14+.
        if (!videoWanted) {
            // L'utente vuole solo la connessione: nessun encoder, nessuna cattura.
            _state.value = StreamState.CONNECTED
            _status.value = "Connesso all'HUD"
            AppLog.i(TAG, "Sessione pronta: video non richiesto (CONNECTED)")
        } else if (mediaProjection == null) {
            _state.value = StreamState.AWAITING_PROJECTION
            _status.value = "Consenti la cattura schermo"
            AppLog.i(TAG, "In attesa del consenso alla cattura schermo")
        } else if (attachMirroring()) {
            // Riconnessione: l'encoder puo' essere in pausa (timeout di attesa
            // scaduto) e il VirtualDisplay non ha prodotto frame durante il down.
            // Senza questo il TFT resta sull'immagine stantia finche' lo schermo
            // del telefono non cambia (sembra "frizzato" fino al primo tocco).
            resumeEncoderIfPaused()
            _state.value = StreamState.MIRRORING
            _status.value = "Mirroring ${videoWidth}x${videoHeight}"
            AppLog.i(TAG, "Video ripristinato sulla nuova sessione")
        } else {
            _state.value = StreamState.AWAITING_PROJECTION
        }

        // Se l'encoder era stato sospeso durante l'attesa, riparte subito.
        resumeEncoderIfPaused()

        startWatchdog()
        startChannelDiagnostics()
    }

    /** Avvia il riepilogo diagnostico aggregato dei canali a bassa frequenza. */
    private fun startChannelDiagnostics() {
        if (channelDiagnosticsActive) return
        channelDiagnosticsActive = true
        AppLog.i(TAG, "CHANNEL_DIAG start interval=${CHANNEL_DIAGNOSTICS_INTERVAL_MS}ms")
        mainHandler.postDelayed(channelDiagnosticsRunnable, CHANNEL_DIAGNOSTICS_INTERVAL_MS)
    }

    private fun stopChannelDiagnostics() {
        channelDiagnosticsActive = false
        mainHandler.removeCallbacks(channelDiagnosticsRunnable)
    }

    /**
     * L'HU puo' chiudere la sessione (EOF/heartbeat perso). Invece di andare in
     * errore ricostruiamo solo la sessione Go, mantenendo MediaProjection,
     * encoder e VirtualDisplay: il video riprende appena l'handshake e' pronto.
     */
    private fun scheduleReconnect(reason: String) {
        if (stopRequested) return
        // NB: `streamPaused` (video fermo) NON blocca più la riconnessione:
        // la connessione deve restare viva anche a video spento, altrimenti
        // "connetti e poi avvia il mirroring" non regge una caduta di link.

        if (passiveWait) {
            // Attesa attiva scaduta: nessun tentativo. Il link resta in ascolto
            // e al ritorno del peer chiamera' onLinkRestored().
            AppLog.i(TAG, "Ascolto passivo: nessun tentativo ($reason)")
            if (mobileSession != null || pendingSession != null) closeSessionOnly()
            return
        }
        if (reconnectPending) {
            AppLog.i(TAG, "Riconnessione gia' in corso, ignoro: $reason")
            return
        }

        reconnectPending = true
        reconnectAttempt++
        val delay = reconnectDelayMs

        AppLog.w(TAG, "Riconnessione #$reconnectAttempt tra ${delay} ms ($reason)")

        // Lo stato resta "in attesa": MediaProjection, encoder e VirtualDisplay
        // NON vengono rilasciati, quindi al ritorno dell'HU il video riparte
        // senza un nuovo consenso alla cattura schermo.
        _state.value = StreamState.WAITING_HUD
        _status.value = if (preferredHostIp != null) {
            "In attesa dell'HUD (tentativo $reconnectAttempt)"
        } else {
            "HUD non trovato (tentativo $reconnectAttempt)"
        }
        stopWatchdog()
        closeSessionOnly()

        // backoff esponenziale fino al tetto
        reconnectDelayMs = (reconnectDelayMs * 2).coerceAtMost(RECONNECT_MAX_DELAY_MS)

        armEncoderIdleTimer()

        val runnable = Runnable {
            reconnectPending = false
            reconnectRunnable = null
            if (stopRequested) return@Runnable
            beginDiscovery()
        }
        reconnectRunnable = runnable
        mainHandler.postDelayed(runnable, delay)
    }

    /**
     * Il link Wi-Fi Direct e' tornato (peer riapparso).
     *
     * Azzera il backoff e fa partire **subito** la riconnessione, invece di
     * aspettare il timer (che nel frattempo puo' essere arrivato a 10 s).
     * È la correzione principale della riconnessione lenta (20-30 s).
     */
    fun onLinkRestored(autoConnect: Boolean) {
        // Il link e' tornato: vale come una nuova richiesta di connessione, e
        // azzera lo `stopRequested` ereditato da un "Chiudi" (vive in
        // HudSession, quindi a livello di PROCESSO).
        stopRequested = false
        if (!autoConnect) {
            // Nessuna connessione richiesta: si azzera solo il backoff.
            passiveWait = false
            return
        }

        passiveWait = false
        reconnectRunnable?.let { mainHandler.removeCallbacks(it) }
        reconnectRunnable = null
        reconnectPending = false
        reconnectAttempt = 0
        reconnectDelayMs = RECONNECT_MIN_DELAY_MS

        if (isRunning) return // sessione gia' viva: nulla da fare
        if (isConnecting()) {
            AppLog.i(TAG, "Link tornato: handshake gia' in corso, non ne avvio un altro")
            return
        }

        AppLog.i(TAG, "Link tornato: riconnessione immediata (backoff azzerato)")
        _status.value = "Link tornato: connessione all'HUD..."
        beginDiscovery()
    }

    /** Attiva/disattiva l'ascolto passivo (nessun tentativo, solo attesa del peer). */
    fun setPassiveWait(enabled: Boolean) {
        if (passiveWait == enabled) return
        passiveWait = enabled
        if (enabled) {
            reconnectRunnable?.let { mainHandler.removeCallbacks(it) }
            reconnectRunnable = null
            reconnectPending = false
            AppLog.i(TAG, "Passaggio in ascolto passivo")
        } else {
            AppLog.i(TAG, "Uscita dall'ascolto passivo")
        }
    }

    /**
     * Arma il timer che mette l'encoder in pausa dopo un'assenza prolungata
     * dell'HU. Una sosta breve (rifornimento) resta sotto la soglia, quindi il
     * video riprende all'istante; oltre la soglia si smette di encodare.
     */
    private fun armEncoderIdleTimer() {
        if (_state.value != StreamState.WAITING_HUD) return
        if (encoderIdleTimerPending || encoderPaused) return
        val timeout = encoderIdleTimeoutMs

        encoderIdleTimerPending = true
        // timeout <= 0 => nessuna grazia: pausa SUBITO. Non esiste piu' un
        // "mai": tenere l'encoder attivo a vuoto e' proprio la causa del video
        // congelato sul telefono (30 fps all-I senza consumatore).
        mainHandler.postDelayed(pauseEncoderRunnable, if (timeout > 0L) timeout else 0L)
    }

    /** Timeout configurabile dalle Impostazioni (0 = mai). */
    fun setEncoderIdleTimeoutMs(timeoutMs: Long) {
        if (encoderIdleTimeoutMs == timeoutMs) return
        encoderIdleTimeoutMs = timeoutMs
        AppLog.i(TAG, "Grazia sospensione encoder: ${timeoutMs / 1_000L} s")

        // Se un timer era gia' armato lo riarmo con il nuovo valore
        if (encoderIdleTimerPending) {
            mainHandler.removeCallbacks(pauseEncoderRunnable)
            encoderIdleTimerPending = false
            armEncoderIdleTimer()
        }
    }

    private fun cancelEncoderIdleTimer() {
        if (encoderIdleTimerPending) {
            mainHandler.removeCallbacks(pauseEncoderRunnable)
            encoderIdleTimerPending = false
        }
    }

    /**
     * Riattiva l'encoder se era in pausa e forza un nuovo frame dal
     * VirtualDisplay (un resize genera un danno -> SurfaceFlinger ricompone),
     * cosi' il TFT non resta su un fotogramma vecchio.
     */
    private fun resumeEncoderIfPaused() {
        cancelEncoderIdleTimer()
        val wasPaused = encoderPaused
        if (wasPaused) {
            encoderPaused = false
            encoder?.resume()
        }
        // Il `resize` del VirtualDisplay e' invasivo (ricrea la superficie del
        // display virtuale): si fa SOLO quando si esce da una pausa vera.
        // Su una semplice riconnessione basta chiedere un IDR al codec.
        forceFreshFrame(resize = wasPaused)
    }

    /**
     * Chiede al codec un frame di sincronizzazione (IDR).
     *
     * @param resize se true forza anche un `resize` del VirtualDisplay per
     *        obbligare SurfaceFlinger a ricomporre subito un frame. Da usare
     *        con parsimonia: e' l'operazione piu' invasiva del mirroring.
     */
    private fun forceFreshFrame(resize: Boolean) {
        if (resize) {
            val display = virtualDisplay
            if (display != null) {
                try {
                    display.resize(videoWidth, videoHeight, densityDpi())
                    AppLog.i(TAG, "VirtualDisplay riallineato dopo la pausa")
                } catch (e: Exception) {
                    AppLog.w(TAG, "resize del VirtualDisplay fallito: ${e.message}")
                }
            }
        }
        encoder?.requestSyncFrame()
    }

    /** Chiude solo la sessione Go, senza toccare projection/encoder/display. */
    private fun closeSessionOnly() {
        isRunning = false
        stopChannelDiagnostics()
        stopDiscoveryInternal()

        val toStop = ArrayList<MobileSession>(2)
        pendingSession?.let { toStop.add(it) }
        pendingSession = null
        mobileSession?.let { toStop.add(it) }
        mobileSession = null

        if (toStop.isNotEmpty()) {
            // stopSession() e' bloccante (chiude socket TCP): mai sul thread
            // chiamante, che qui e' il thread dei callback della libreria Go.
            Thread({
                toStop.forEach { session ->
                    try {
                        session.stopSession()
                    } catch (e: Exception) {
                        AppLog.i(TAG, "stopSession: ${e.message}")
                    }
                }
            }, "HudSessionStop").start()
        }
    }

    /**
     * Errore terminale "soft": chiude la sessione Go ma **non** rilascia la
     * MediaProjection, cosi' un nuovo tentativo non richiede un altro consenso.
     * La projection viene rilasciata solo da [stop] (scelta dell'utente) o
     * quando il sistema la revoca.
     *
     * NB: e' l'unico punto di uscita "terminale". Oggi non ci sono chiamanti
     * perche' ogni fallimento di link/Handshake viene ritentato da
     * [scheduleReconnect]; resta disponibile per errori non recuperabili.
     */
    @Suppress("unused")
    private fun fail(message: String) {
        AppLog.e(TAG, "FALLIMENTO: $message")
        stopWatchdog()
        closeSessionOnly()
        if (mediaProjection == null) {
            _state.value = StreamState.AWAITING_PROJECTION
            _status.value = "$message - consenti la cattura schermo"
        } else {
            _state.value = StreamState.ERROR
            _status.value = message
        }
    }

    // ------------------------------------------------------------------
    // Mirroring
    // ------------------------------------------------------------------

    private fun densityDpi(): Int {
        val dm = DisplayMetrics()
        @Suppress("DEPRECATION")
        displayManager.getDisplay(android.view.Display.DEFAULT_DISPLAY)?.getRealMetrics(dm)
            ?: dm.setTo(context.resources.displayMetrics)
        return if (dm.densityDpi > 0) dm.densityDpi else DisplayMetrics.DENSITY_DEFAULT
    }

    /** @return true se encoder + VirtualDisplay sono attivi */
    private fun attachMirroring(): Boolean {
        if (encoder != null && virtualDisplay != null) return true

        val mp = mediaProjection ?: return false
        val session = mobileSession ?: return false
        if (!isRunning) return false

        mirrorAttempts++

        if (encoder == null) {
            val enc = try {
                HudEncoder(
                    width = videoWidth,
                    height = videoHeight,
                    fps = targetFps,
                    bitrate = targetBitrate
                ) { avccSample ->
                    // Spinta SINCRONA come nel codice originale.
                    // `PushFrame` lato Go scrive in un ring buffer in MEMORIA (non
                    // sulla socket), quindi non blocca il thread di drain.
                    // La coda con scarto introdotta in 1.1.7 faceva perdere frame
                    // quando il buffer si riempiva -> video a scatti.
                    val active = mobileSession
                    if (active != null) {
                        try {
                            active.pushFrame(avccSample)
                        } catch (e: Exception) {
                            AppLog.e(TAG, "pushFrame fallita", e)
                        }
                    }
                }
            } catch (t: Throwable) {
                AppLog.e(TAG, "Encoder ${videoWidth}x${videoHeight} non creabile", t)
                // fallback sulle dimensioni di default della libreria
                if (videoWidth != DEFAULT_WIDTH || videoHeight != DEFAULT_HEIGHT) {
                    videoWidth = DEFAULT_WIDTH
                    videoHeight = DEFAULT_HEIGHT
                    AppLog.w(TAG, "Ripiego su ${DEFAULT_WIDTH}x${DEFAULT_HEIGHT}")
                }
                return false
            }
            enc.start()
            encoder = enc
            AppLog.i(TAG, "Encoder avviato a ${videoWidth}x${videoHeight} @${targetFps}fps")
        }

        val enc = encoder ?: return false

        if (virtualDisplay == null) {
            try {
                val thread = HandlerThread("MirrorCapture").also { it.start() }
                captureThread = thread
                val handler = Handler(thread.looper)

                val reader = ImageReader.newInstance(
                    videoWidth,
                    videoHeight,
                    PixelFormat.RGBA_8888,
                    2
                )
                reader.setOnImageAvailableListener({ r ->
                    val image = try {
                        r.acquireLatestImage()
                    } catch (t: Throwable) {
                        null
                    }
                    if (image != null) {
                        try {
                            enc.submitImage(image)
                        } finally {
                            try {
                                image.close()
                            } catch (_: Throwable) {
                            }
                        }
                    }
                }, handler)
                imageReader = reader

                virtualDisplay = mp.createVirtualDisplay(
                    "MotoBitMirror",
                    videoWidth,
                    videoHeight,
                    densityDpi(),
                    DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,
                    reader.surface,
                    null,
                    null
                )
                AppLog.i(
                    TAG,
                    "MIRRORING ATTIVO ${videoWidth}x${videoHeight} (tentativo $mirrorAttempts)"
                )
                enc.requestSyncFrame()
            } catch (t: Throwable) {
                AppLog.e(TAG, "createVirtualDisplay fallita (tentativo $mirrorAttempts)", t)
                releaseCapture()
                return false
            }
        }

        if (virtualDisplay != null) {
            _state.value = StreamState.MIRRORING
            _status.value = "Mirroring ${videoWidth}x${videoHeight}"
            return true
        }
        return false
    }

    /**
     * Watchdog: se la sessione e' pronta ma il mirroring non si aggancia,
     * riprova. Evita il "primo tentativo non parte".
     */
    private fun startWatchdog() {
        if (watchdogActive) return
        watchdogActive = true

        val runnable = object : Runnable {
            override fun run() {
                if (!watchdogActive) return

                if (isRunning && videoWanted && mediaProjection != null &&
                    (encoder == null || virtualDisplay == null)
                ) {
                    if (mirrorAttempts >= MAX_MIRROR_ATTEMPTS) {
                        _status.value = "Mirroring non agganciato: riavvia la trasmissione"
                        _state.value = StreamState.ERROR
                        AppLog.e(
                            TAG,
                            "Mirroring non agganciato dopo $mirrorAttempts tentativi"
                        )
                    } else {
                        attachMirroring()
                    }
                }

                if (watchdogActive) mainHandler.postDelayed(this, WATCHDOG_INTERVAL_MS)
            }
        }

        mainHandler.postDelayed(runnable, WATCHDOG_INTERVAL_MS)
    }

    private fun stopWatchdog() {
        watchdogActive = false
    }

    private fun releaseVirtualDisplay() {
        virtualDisplay?.let {
            try {
                it.release()
            } catch (e: Exception) {
                AppLog.e(TAG, "release virtual display fallita", e)
            }
        }
        virtualDisplay = null
        releaseCapture()
    }

    private fun releaseCapture() {
        imageReader?.let {
            try {
                it.setOnImageAvailableListener(null, null)
                it.close()
            } catch (e: Exception) {
                AppLog.e(TAG, "chiusura ImageReader fallita", e)
            }
        }
        imageReader = null

        captureThread?.let {
            try {
                it.quitSafely()
            } catch (_: Throwable) {
            }
        }
        captureThread = null
    }

    private fun tearDownEncoderAndDisplay() {
        releaseVirtualDisplay()
        encoder?.let {
            try {
                it.stop()
            } catch (e: Exception) {
                AppLog.e(TAG, "stop encoder fallita", e)
            }
        }
        encoder = null
    }

    private fun unregisterProjectionCallback() {
        projectionCallback?.let { cb ->
            try {
                mediaProjection?.unregisterCallback(cb)
            } catch (_: Exception) {
            }
        }
        projectionCallback = null
    }

    private fun teardownMirroring() {
        tearDownEncoderAndDisplay()
        unregisterProjectionCallback()
        mediaProjection?.let {
            try {
                it.stop()
            } catch (_: Exception) {
            }
        }
        mediaProjection = null
    }

    private fun stopInternal() {
        isRunning = false
        releaseStreamLocks()
        teardownMirroring()
        stopDiscoveryInternal()

        val toStop = ArrayList<MobileSession>(2)
        pendingSession?.let { toStop.add(it) }
        pendingSession = null
        mobileSession?.let { toStop.add(it) }
        mobileSession = null

        if (toStop.isNotEmpty()) {
            // stopSession() e' bloccante: mai sul thread della UI ("Chiudi").
            Thread({
                toStop.forEach { session ->
                    try {
                        session.stopSession()
                    } catch (e: Exception) {
                        AppLog.e(TAG, "stopSession fallita", e)
                    }
                }
            }, "HudSessionStop").start()
        }

        if (state.value != StreamState.ERROR) {
            _state.value = StreamState.IDLE
        }
    }

    // ------------------------------------------------------------------
    // Eventi HUD
    // ------------------------------------------------------------------

    private fun handleHudEvent(source: Long, command: Long, payload: ByteArray?) {
        AppLog.i(
            TAG,
            "Evento HUD ${sourceName(source)}/${commandName(command)} " +
                    "payload=${payload?.size ?: 0}B"
        )

        // Solo MediaCtrlInit (16) contiene la risoluzione, in forma binaria.
        if (source == EVENT_SOURCE_MEDIA_CONTROL &&
            command == MEDIA_CTRL_INIT &&
            payload != null
        ) {
            val size = parseNegotiatedSize(payload)
            if (size != null) {
                val (w, h) = size
                if (w != videoWidth || h != videoHeight) {
                    videoWidth = w
                    videoHeight = h
                    AppLog.i(TAG, "Nuova risoluzione negoziata ${w}x${h}: riallineo encoder")

                    if (isRunning && (encoder != null || virtualDisplay != null)) {
                        tearDownEncoderAndDisplay()
                        mirrorAttempts = 0
                    }
                }
            }
        }

        // MediaCtrlChk = l'HU inizia a consumare il video: chiediamo subito un IDR
        if (source == EVENT_SOURCE_MEDIA_CONTROL && command == MEDIA_CTRL_CHK) {
            encoder?.requestSyncFrame()
        }

        if (isRunning && mediaProjection != null && virtualDisplay == null) {
            attachMirroring()
        }
    }

    private fun sourceName(source: Long): String = when (source) {
        1L -> "Unknown"
        2L -> "PXC"
        3L -> "MediaCtrl"
        else -> "src$source"
    }

    private fun commandName(command: Long): String = when (command) {
        MEDIA_CTRL_INIT -> "Init(16)"
        MEDIA_CTRL_SCREEN_CONF -> "ScreenConf(96)"
        MEDIA_CTRL_CHK -> "VideoStart(112)"
        64L -> "Ping(64)"
        else -> "cmd$command"
    }

    private fun parseNegotiatedSize(payload: ByteArray): Pair<Int, Int>? {
        if (payload.size < 4) return null

        val requestedWidth = (payload[0].toInt() and 0xFF) or
                ((payload[1].toInt() and 0xFF) shl 8)
        val requestedHeight = (payload[2].toInt() and 0xFF) or
                ((payload[3].toInt() and 0xFF) shl 8)

        val alignedWidth = requestedWidth and 0x0F.inv()
        val alignedHeight = requestedHeight and 0x0F.inv()

        if (alignedWidth !in 160..1920 || alignedHeight !in 120..1080) {
            AppLog.w(
                TAG,
                "Risoluzione scartata (${requestedWidth}x${requestedHeight}): payload non di negoziazione"
            )
            return null
        }

        AppLog.i(TAG, "Risoluzione negoziata dall'HU: ${alignedWidth}x${alignedHeight}")
        return alignedWidth to alignedHeight
    }

    companion object {
        private const val TAG = "HudStream"

        private const val DEFAULT_WIDTH = 800
        private const val DEFAULT_HEIGHT = 384

        private const val EVENT_SOURCE_MEDIA_CONTROL = 3L

        // hud/net/stream_ctrl.go
        private const val MEDIA_CTRL_INIT = 16L        // negoziazione risoluzione (binaria)
        private const val MEDIA_CTRL_SCREEN_CONF = 96L // JSON viewAreaConfig
        private const val MEDIA_CTRL_CHK = 112L        // l'HU inizia a consumare video

        private const val DISCOVERY_TIMEOUT_MS = 8_000L

        private const val WATCHDOG_INTERVAL_MS = 1_000L
        private const val CHANNEL_DIAGNOSTICS_INTERVAL_MS = 30_000L
        private const val MAX_MIRROR_ATTEMPTS = 12

        // Riconnessione: illimitata, istantanea a 100 ms (senza tempi morti), con backoff fino a 10 s.
        private const val RECONNECT_MIN_DELAY_MS = 100L
        private const val RECONNECT_MAX_DELAY_MS = 10_000L

        /**
         * Default: dopo 5 SECONDI di attesa l'encoder va in pausa.
         *
         * Era 5 minuti. Durante l'attesa l'encoder continuava a produrre 30 fps
         * all-I (ogni frame un IDR) che nessuno consumava: quel carico saturava
         * la CPU del telefono e congelava il layer video delle altre app
         * (YouTube/navigatore) finche' non si faceva pause/play. La pausa e'
         * istantanea da annullare (`resume()` non riavvia il codec), quindi una
         * grazia corta non costa nulla in velocita' di ripresa.
         */
        private const val DEFAULT_ENCODER_IDLE_TIMEOUT_MS = 5_000L

        /** Porta dell'EC init handshake dell'HU. */
        private const val DEFAULT_HUD_PORT = 10930

        /** Probe di raggiungibilita' dell'HU prima dell'handshake. */
        private const val PROBE_TIMEOUT_MS = 700
        private const val PROBE_INTERVAL_MS = 1_000L

        /**
         * Attesa massima per singolo ciclo di probe. Era 60 s: un ciclo cosi'
         * lungo teneva il loop "impegnato" anche quando il TFT era gia' tornato
         * su, ritardando la riconnessione. 8 s bastano (l'HU apre in ~1 s).
         */
        private const val PROBE_MAX_WAIT_MS = 8_000L

        /** Dopo N cicli falliti si chiede al link di ricreare il gruppo P2P. */
        private const val LINK_STALL_THRESHOLD = 3

        /**
         * Oltre questa soglia un handshake e' considerato bloccato e la guardia
         * `connecting` si auto-ripara. L'handshake Go ha startupTimeout=10s:
         * 90 s sono un margine piu' che sufficiente.
         */
        private const val CONNECT_STUCK_MS = 90_000L
    }
}

