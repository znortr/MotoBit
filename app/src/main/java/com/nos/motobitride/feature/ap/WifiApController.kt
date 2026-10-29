package com.nos.motobitride.feature.ap

import android.annotation.SuppressLint
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.MacAddress
import android.net.wifi.p2p.WifiP2pConfig
import android.net.wifi.p2p.WifiP2pInfo
import android.net.wifi.p2p.WifiP2pManager
import android.os.Handler
import android.os.Looper
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.content.ContextCompat
import com.nos.motobitride.core.log.AppLog
import com.nos.motobitride.core.qr.QrContent

/**
 * Link verso l'HU tramite **Wi-Fi Direct**.
 *
 * Scelta confermata dai test: l'HU e' un group owner P2P, quindi si usa il MAC
 * del QR come `deviceAddress`. Il sottorete del gruppo P2P ha una rotta propria,
 * per cui il `net.Dial` interno della libreria Go raggiunge l'HU senza bisogno
 * di `bindProcessToNetwork`, e i dati mobili restano la rete di default.
 *
 * Il link e' **auto-ripristinante**: se il gruppo cade (TFT spento, fuori
 * portata, sosta) viene ritentato all'infinito con backoff usando le credenziali
 * dell'ultimo QR, finche' l'utente non chiama [disconnect]/[dispose].
 * Cosi' riaccendendo il TFT la connessione torna senza toccare nulla e senza
 * perdere la MediaProjection lato streaming.
 */
class WifiApController internal constructor(
    private val context: Context,
    private val onHudConnected: (hudIp: String?) -> Unit,
    private val onHudDisconnected: () -> Unit
) {
    var isHudConnected by mutableStateOf(false)
        private set

    /** IP dell'HU (group owner address). */
    var hudIp: String? = null
        private set

    var lastEvent: String = ""
        private set

    private var p2pManager: WifiP2pManager? = null
    private var p2pChannel: WifiP2pManager.Channel? = null
    private var p2pReceiver: BroadcastReceiver? = null

    // Credenziali dell'ultimo QR: servono per il ripristino automatico.
    private var savedQr: QrContent.HudWifi? = null
    private var autoRetryEnabled = false
    private var retryPending = false
    private var retryAttempt = 0
    private var retryDelayMs = RETRY_MIN_DELAY_MS
    private val handler = Handler(Looper.getMainLooper())
    private var retryRunnable: Runnable? = null

    // ------------------------------------------------------------------
    // API
    // ------------------------------------------------------------------

    fun connect(qr: QrContent.HudWifi) {
        savedQr = qr
        autoRetryEnabled = true
        retryAttempt = 0
        retryDelayMs = RETRY_MIN_DELAY_MS
        cancelPendingRetry()
        startAttempt(qr, firstAttempt = true)
    }

    /** Ripristino manuale dalle credenziali salvate (pulsante Avvia). */
    fun reconnectSaved(): Boolean {
        val qr = savedQr ?: return false
        connect(qr)
        return true
    }

    fun disconnect() {
        autoRetryEnabled = false
        savedQr = null
        cancelPendingRetry()
        teardownP2p()

        if (isHudConnected) {
            isHudConnected = false
            hudIp = null
            onHudDisconnected()
        }
        hudIp = null
        lastEvent = "link chiuso"
    }

    fun dispose() {
        disconnect()
    }

    // ------------------------------------------------------------------
    // Ciclo di connessione
    // ------------------------------------------------------------------

    private fun startAttempt(qr: QrContent.HudWifi, firstAttempt: Boolean) {
        teardownP2p()

        if (firstAttempt) {
            lastEvent = "connessione P2P"
            AppLog.i(TAG, "Wi-Fi Direct verso ${qr.ssid} (mac=${qr.mac ?: "-"})")
        } else {
            AppLog.i(TAG, "Nuovo tentativo P2P verso ${qr.ssid} (tentativo $retryAttempt)")
        }

        try {
            val manager = context.getSystemService(Context.WIFI_P2P_SERVICE)
                    as? WifiP2pManager
            if (manager == null) {
                AppLog.e(TAG, "WifiP2pManager non disponibile su questo device")
                lastEvent = "P2P non disponibile"
                autoRetryEnabled = false
                return
            }
            p2pManager = manager

            // ChannelListener.onChannelDisconnected() non ha parametri
            val channel = manager.initialize(context, Looper.getMainLooper()) {
                AppLog.w(TAG, "canale Wi-Fi Direct disconnesso")
                scheduleRetry("canale disconnesso")
            }
            if (channel == null) {
                AppLog.e(TAG, "initialize ha restituito un canale nullo")
                lastEvent = "P2P init fallita"
                scheduleRetry("init fallita")
                return
            }
            p2pChannel = channel

            registerReceiver(manager, channel)

            val mac = qr.mac
            if (!mac.isNullOrBlank()) {
                connectToPeer(manager, channel, mac, qr)
            } else {
                AppLog.w(TAG, "MAC assente nel QR: discoverPeers per cercarlo")
                discoverAndConnect(manager, channel, qr)
            }
        } catch (t: Throwable) {
            AppLog.e(TAG, "connessione P2P fallita", t)
            lastEvent = "errore: ${t.message}"
            scheduleRetry("eccezione: ${t.message}")
        }
    }

    private fun scheduleRetry(reason: String) {
        if (!autoRetryEnabled || savedQr == null || retryPending) return

        retryPending = true
        retryAttempt++
        val delay = retryDelayMs

        AppLog.w(TAG, "Retry P2P #$retryAttempt tra ${delay} ms ($reason)")
        lastEvent = "retry #$retryAttempt ($reason)"

        retryDelayMs = (retryDelayMs * 2).coerceAtMost(RETRY_MAX_DELAY_MS)

        val runnable = Runnable {
            retryPending = false
            retryRunnable = null
            if (!autoRetryEnabled) return@Runnable
            val qr = savedQr ?: return@Runnable
            startAttempt(qr, firstAttempt = false)
        }
        retryRunnable = runnable
        handler.postDelayed(runnable, delay)
    }

    private fun cancelPendingRetry() {
        retryRunnable?.let { handler.removeCallbacks(it) }
        retryRunnable = null
        retryPending = false
        retryAttempt = 0
        retryDelayMs = RETRY_MIN_DELAY_MS
    }

    // ------------------------------------------------------------------
    // P2P
    // ------------------------------------------------------------------

    @SuppressLint("MissingPermission")
    private fun discoverAndConnect(
        manager: WifiP2pManager,
        channel: WifiP2pManager.Channel,
        qr: QrContent.HudWifi
    ) {
        manager.discoverPeers(channel, object : WifiP2pManager.ActionListener {
            override fun onSuccess() {
                AppLog.i(TAG, "discoverPeers avviata")
                lastEvent = "P2P discovery avviata"
            }

            override fun onFailure(reason: Int) {
                AppLog.e(TAG, "discoverPeers fallita: ${reasonText(reason)}")
                lastEvent = "P2P discovery fallita (${reasonText(reason)})"
                scheduleRetry("discovery fallita")
            }
        })

        val runnable = Runnable {
            val mgr = p2pManager
            val ch = p2pChannel
            if (mgr == null || ch == null || isHudConnected) return@Runnable

            mgr.requestPeers(ch) { peers ->
                val match = peers.deviceList.firstOrNull { device ->
                    device.deviceAddress.equals(qr.mac, ignoreCase = true) ||
                            (device.deviceName.isNotEmpty() &&
                                    qr.ssid.contains(device.deviceName, ignoreCase = true)) ||
                            (device.deviceName.isNotEmpty() &&
                                    device.deviceName.contains("VOGE", ignoreCase = true))
                }
                if (match == null) {
                    AppLog.w(TAG, "nessun peer corrispondente (${peers.deviceList.size} trovati)")
                    lastEvent = "P2P: nessun peer"
                    scheduleRetry("nessun peer")
                } else {
                    AppLog.i(TAG, "peer trovato: ${match.deviceName} ${match.deviceAddress}")
                    connectToPeer(mgr, ch, match.deviceAddress, qr)
                }
            }
        }
        handler.postDelayed(runnable, PEER_LOOKUP_DELAY_MS)
    }

    private fun connectToPeer(
        manager: WifiP2pManager,
        channel: WifiP2pManager.Channel,
        deviceAddress: String,
        qr: QrContent.HudWifi
    ) {
        val macAddress = try {
            MacAddress.fromString(deviceAddress)
        } catch (t: Throwable) {
            AppLog.e(TAG, "MAC non valido: $deviceAddress", t)
            lastEvent = "P2P MAC non valido"
            // MAC sbagliato: nessun retry automatico utile
            autoRetryEnabled = false
            return
        }

        val configBuilder = WifiP2pConfig.Builder()
            .setDeviceAddress(macAddress)
            .setNetworkName(qr.ssid)

        val pwd = qr.password
        if (!pwd.isNullOrEmpty()) {
            runCatching { configBuilder.setPassphrase(pwd) }
                .onFailure { AppLog.w(TAG, "passphrase rifiutata: ${it.message}") }
        }

        val config = try {
            configBuilder.build()
        } catch (t: Throwable) {
            AppLog.e(TAG, "config P2P non valida", t)
            lastEvent = "P2P config non valida"
            return
        }

        manager.connect(channel, config, object : WifiP2pManager.ActionListener {
            override fun onSuccess() {
                AppLog.i(TAG, "connect richiesta accettata, attendo groupOwner")
                lastEvent = "P2P connect in corso"
            }

            override fun onFailure(reason: Int) {
                AppLog.e(TAG, "connect fallita: ${reasonText(reason)}")
                lastEvent = "P2P connect fallita (${reasonText(reason)})"
                scheduleRetry("connect fallita (${reasonText(reason)})")
            }
        })
    }

    @SuppressLint("MissingPermission")
    private fun registerReceiver(
        manager: WifiP2pManager,
        channel: WifiP2pManager.Channel
    ) {
        if (p2pReceiver != null) return

        val receiver = object : BroadcastReceiver() {
            override fun onReceive(ctx: Context, intent: Intent) {
                val ch = p2pChannel ?: return
                when (intent.action) {
                    WifiP2pManager.WIFI_P2P_CONNECTION_CHANGED_ACTION ->
                        manager.requestConnectionInfo(ch) { info ->
                            handleConnectionInfo(info)
                        }

                    WifiP2pManager.WIFI_P2P_PEERS_CHANGED_ACTION ->
                        AppLog.i(TAG, "lista peer aggiornata")

                    WifiP2pManager.WIFI_P2P_STATE_CHANGED_ACTION -> {
                        val state = intent.getIntExtra(
                            WifiP2pManager.EXTRA_WIFI_STATE,
                            WifiP2pManager.WIFI_P2P_STATE_DISABLED
                        )
                        val enabled = state == WifiP2pManager.WIFI_P2P_STATE_ENABLED
                        AppLog.i(
                            TAG,
                            "stato Wi-Fi Direct: " + if (enabled) "ENABLED" else "DISABLED"
                        )
                        if (!enabled) {
                            lastEvent = "Wi-Fi Direct disattivato"
                            markDisconnected("Wi-Fi Direct disattivato")
                        }
                    }
                }
            }
        }
        p2pReceiver = receiver

        val filter = IntentFilter().apply {
            addAction(WifiP2pManager.WIFI_P2P_CONNECTION_CHANGED_ACTION)
            addAction(WifiP2pManager.WIFI_P2P_PEERS_CHANGED_ACTION)
            addAction(WifiP2pManager.WIFI_P2P_STATE_CHANGED_ACTION)
        }
        ContextCompat.registerReceiver(
            context,
            receiver,
            filter,
            ContextCompat.RECEIVER_NOT_EXPORTED
        )
        AppLog.i(TAG, "receiver P2P registrato")
    }

    @SuppressLint("MissingPermission")
    private fun handleConnectionInfo(info: WifiP2pInfo?) {
        if (info == null) {
            AppLog.w(TAG, "connection info nulla")
            return
        }
        if (!info.groupFormed) {
            AppLog.w(TAG, "gruppo P2P non formato")
            markDisconnected("gruppo P2P non formato")
            return
        }

        val ip = info.groupOwnerAddress?.hostAddress
        AppLog.i(TAG, "gruppo formato: owner=${info.isGroupOwner} ip=$ip")
        lastEvent = "P2P owner=${info.isGroupOwner} ip=$ip"

        if (ip != null) {
            markConnected(ip)
        } else {
            AppLog.w(TAG, "groupOwnerAddress nulla")
            scheduleRetry("groupOwnerAddress nulla")
        }
    }

    private fun teardownP2p() {
        p2pReceiver?.let {
            runCatching { context.unregisterReceiver(it) }
        }
        p2pReceiver = null

        p2pManager?.let { manager ->
            p2pChannel?.let { channel ->
                runCatching {
                    manager.removeGroup(
                        channel,
                        object : WifiP2pManager.ActionListener {
                            override fun onSuccess() = Unit
                            override fun onFailure(reason: Int) = Unit
                        }
                    )
                }
            }
        }
        p2pChannel = null
        p2pManager = null
    }

    // ------------------------------------------------------------------
    // Stato
    // ------------------------------------------------------------------

    private fun markConnected(ip: String?) {
        retryAttempt = 0
        retryDelayMs = RETRY_MIN_DELAY_MS

        if (isHudConnected && hudIp == ip) return
        isHudConnected = true
        hudIp = ip
        lastEvent = "connesso ip=$ip"
        AppLog.i(TAG, "LINK P2P ATTIVO, ip=$ip")
        onHudConnected(ip)
    }

    private fun markDisconnected(reason: String) {
        if (isHudConnected) {
            isHudConnected = false
            hudIp = null
            AppLog.w(TAG, "Link P2P perso: $reason")
            onHudDisconnected()
        }
        hudIp = null
        // TFT spento / fuori portata: riprova da solo finche' non si ferma l'utente
        scheduleRetry(reason)
    }

    private fun reasonText(reason: Int): String = when (reason) {
        WifiP2pManager.P2P_UNSUPPORTED -> "P2P_UNSUPPORTED"
        WifiP2pManager.BUSY -> "BUSY"
        WifiP2pManager.ERROR -> "ERROR"
        WifiP2pManager.NO_SERVICE_REQUESTS -> "NO_SERVICE_REQUESTS"
        else -> "reason=$reason"
    }

    companion object {
        private const val TAG = "WifiP2pLink"

        /** Porta dell'EC init handshake dell'HU. */
        const val DEFAULT_HUD_PORT = 10930

        private const val PEER_LOOKUP_DELAY_MS = 6_000L

        // Retry del gruppo P2P: da 3 s a 10 s, illimitati finche' non si ferma.
        private const val RETRY_MIN_DELAY_MS = 3_000L
        private const val RETRY_MAX_DELAY_MS = 10_000L
    }
}
