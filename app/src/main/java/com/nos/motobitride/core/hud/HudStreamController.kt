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
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.concurrent.atomic.AtomicBoolean

enum class StreamState {
    IDLE,                 // fermo (stop richiesto dall'utente)
    DISCOVERING,          // ricerca mDNS dell'HU
    CONNECTING,           // handshake PXC / EasyConn in corso
    WAITING_HUD,          // link/HU non raggiungibile: si ritenta all'infinito
    AWAITING_PROJECTION,  // sessione pronta, manca il consenso alla cattura schermo
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

    private val serviceType = "_EasyConn._tcp."
    private val expectedPackageName = "com.cfmoto.cfmotointernational"

    @Volatile
    private var isRunning = false
    @Volatile
    private var isDiscovering = false
    @Volatile
    private var stopRequested = false

    // Host fornito dal livello link (gateway Wi-Fi / GO Wi-Fi Direct):
    // quando presente, mDNS viene saltato (inaffidabile su reti non-default).
    @Volatile
    private var preferredHostIp: String? = null
    @Volatile
    private var preferredHostPort: Int = DEFAULT_HUD_PORT

    private var mirrorAttempts = 0
    private var watchdogActive = false

    // Riconnessione: illimitata finche' l'utente non preme Stop, con backoff.
    private var reconnectAttempt = 0
    private var reconnectDelayMs = RECONNECT_MIN_DELAY_MS
    private var reconnectPending = false

    // Dopo ENCODER_IDLE_TIMEOUT_MS di attesa l'encoder va in pausa: una sosta
    // breve resta "calda" (resume istantaneo), una lunga non consuma batteria.
    @Volatile
    private var encoderPaused = false
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
            "In attesa da ${encoderIdleTimeoutMs / 60_000L} min: " +
                    "encoding sospeso per risparmio batteria"
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

                nsdManager.resolveService(serviceInfo, object : NsdManager.ResolveListener {
                    override fun onResolveFailed(serviceInfo: NsdServiceInfo?, errorCode: Int) {
                        AppLog.w(TAG, "mDNS: resolve fallita (errore $errorCode)")
                    }

                    override fun onServiceResolved(resolved: NsdServiceInfo) {
                        if (!isDiscovering || completed.get()) return

                        try {
                            val attrs: Map<String, ByteArray> = resolved.attributes
                            val pkgAttr = attrs["packagename"]?.toString(Charsets.UTF_8)
                            val ipAttr = attrs["ip"]?.toString(Charsets.UTF_8)

                            val port = resolved.port
                            val ip = ipAttr ?: resolved.host.hostAddress
                            val packageName = pkgAttr ?: expectedPackageName

                            val matchesPackage =
                                expectedPackageName.isEmpty() || packageName == expectedPackageName

                            if (matchesPackage && ip != null) {
                                if (!completed.compareAndSet(false, true)) return
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
                    }
                })
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
        releaseMultiCastLock()
    }

    // ------------------------------------------------------------------
    // API pubblica
    // ------------------------------------------------------------------

    fun start() {
        if (isRunning || isDiscovering) {
            AppLog.w(TAG, "start() ignorato: running=$isRunning discovering=$isDiscovering")
            return
        }
        if (_state.value == StreamState.MIRRORING) {
            AppLog.w(TAG, "start() ignorato: mirroring gia' attivo")
            return
        }

        stopRequested = false
        mirrorAttempts = 0
        reconnectAttempt = 0
        reconnectDelayMs = RECONNECT_MIN_DELAY_MS
        reconnectPending = false
        _status.value = "Ricerca HUD..."
        beginDiscovery()
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
        AppLog.i(TAG, "stop() richiesto")
        stopRequested = true
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
                        _status.value = "Cattura schermo revocata: riavvia la trasmissione"
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
        if (!attachMirroring()) {
            _state.value = StreamState.AWAITING_PROJECTION
        } else {
            resumeEncoderIfPaused()
        }
    }

    // ------------------------------------------------------------------
    // Sessione
    // ------------------------------------------------------------------

    private fun beginDiscovery() {
        if (stopRequested) return
        _state.value = StreamState.DISCOVERING

        // Se il livello link conosce gia' l'IP dell'HU, saltiamo mDNS.
        val knownIp = preferredHostIp
        if (knownIp != null) {
            _status.value = "Connessione a $knownIp (dal link Wi-Fi)"
            AppLog.i(TAG, "Uso host dal link: $knownIp:$preferredHostPort")
            Thread {
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

    private fun connectToHost(host: StreamHost) {
        _state.value = StreamState.CONNECTING
        _status.value = "Connessione all'HUD..."
        AppLog.i(TAG, "Handshake sessione HUD in corso...")
        val started = System.currentTimeMillis()

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
                    AppLog.w(TAG, "Errore HUD non fatale: $message")
                }
            }

            override fun onEvent(time: Long, source: Long, command: Long, payload: ByteArray?) {
                handleHudEvent(source, command, payload)
            }

            override fun onStopped() {
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

        try {
            session.setECHost(host)
            val tz = java.util.TimeZone.getDefault()
            val tzOffsetSec = tz.getOffset(System.currentTimeMillis()) / 1000L
            session.setClockTimeZone(tz.id, tzOffsetSec)

            session.startSession()
        } catch (t: Throwable) {
            AppLog.e(TAG, "startSession fallita", t)
            // Caso tipico: TFT spento / fuori portata. NON e' un errore fatale:
            // la MediaProjection resta viva e si riprova piu' tardi.
            scheduleReconnect("handshake fallito: ${t.message}")
            return
        }

        val elapsed = System.currentTimeMillis() - started
        AppLog.i(TAG, "Sessione HUD pronta in ${elapsed} ms")

        mobileSession = session
        isRunning = true
        mirrorAttempts = 0
        reconnectAttempt = 0
        reconnectDelayMs = RECONNECT_MIN_DELAY_MS

        // ORDINE CORRETTO: solo adesso chiediamo (o usiamo) la MediaProjection.
        // Tenerla in attesa durante l'handshake la fa scadere su Android 14+.
        if (mediaProjection == null) {
            _state.value = StreamState.AWAITING_PROJECTION
            _status.value = "Consenti la cattura schermo"
            AppLog.i(TAG, "In attesa del consenso alla cattura schermo")
        } else if (attachMirroring()) {
            _state.value = StreamState.MIRRORING
            _status.value = "Mirroring ${videoWidth}x${videoHeight}"
            AppLog.i(TAG, "Video ripristinato sulla nuova sessione")
        } else {
            _state.value = StreamState.AWAITING_PROJECTION
        }

        // Se l'encoder era stato sospeso durante l'attesa, riparte subito.
        resumeEncoderIfPaused()

        startWatchdog()
    }

    /**
     * L'HU puo' chiudere la sessione (EOF/heartbeat perso). Invece di andare in
     * errore ricostruiamo solo la sessione Go, mantenendo MediaProjection,
     * encoder e VirtualDisplay: il video riprende appena l'handshake e' pronto.
     */
    private fun scheduleReconnect(reason: String) {
        if (stopRequested) return
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

        mainHandler.postDelayed({
            reconnectPending = false
            if (stopRequested) return@postDelayed
            beginDiscovery()
        }, delay)
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
        if (timeout <= 0L) return // 0 = non sospendere mai

        encoderIdleTimerPending = true
        mainHandler.postDelayed(pauseEncoderRunnable, timeout)
    }

    /** Timeout configurabile dalle Impostazioni (0 = mai). */
    fun setEncoderIdleTimeoutMs(timeoutMs: Long) {
        if (encoderIdleTimeoutMs == timeoutMs) return
        encoderIdleTimeoutMs = timeoutMs
        AppLog.i(TAG, "Timeout sospensione encoder: ${timeoutMs / 60_000L} min")

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
        if (!encoderPaused) return

        encoderPaused = false
        encoder?.resume()

        val display = virtualDisplay
        if (display != null) {
            try {
                display.resize(videoWidth, videoHeight, densityDpi())
                AppLog.i(TAG, "VirtualDisplay riallineato per il primo frame post-pausa")
            } catch (e: Exception) {
                AppLog.w(TAG, "resize del VirtualDisplay fallito: ${e.message}")
            }
        }
    }

    /** Chiude solo la sessione Go, senza toccare projection/encoder/display. */
    private fun closeSessionOnly() {
        isRunning = false
        stopDiscoveryInternal()
        mobileSession?.let { session ->
            try {
                session.stopSession()
            } catch (e: Exception) {
                AppLog.i(TAG, "stopSession su sessione gia' chiusa: ${e.message}")
            }
        }
        mobileSession = null
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
                    // Riferimento dinamico: la stessa istanza di encoder continua a
                    // funzionare anche dopo una riconnessione della sessione Go.
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

                if (isRunning && mediaProjection != null &&
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
        teardownMirroring()
        stopDiscoveryInternal()

        mobileSession?.let { session ->
            try {
                session.stopSession()
            } catch (e: Exception) {
                AppLog.e(TAG, "stopSession fallita", e)
            }
        }
        mobileSession = null

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
        private const val MAX_MIRROR_ATTEMPTS = 12

        // Riconnessione: illimitata, con backoff da 1 s a 10 s.
        private const val RECONNECT_MIN_DELAY_MS = 1_000L
        private const val RECONNECT_MAX_DELAY_MS = 10_000L

        /** Default: dopo 5 minuti di attesa l'encoder va in pausa (batteria). */
        private const val DEFAULT_ENCODER_IDLE_TIMEOUT_MS = 5 * 60 * 1_000L

        /** Porta dell'EC init handshake dell'HU. */
        private const val DEFAULT_HUD_PORT = 10930
    }
}
