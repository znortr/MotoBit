package com.nos.motobitride.core.display

import android.content.Context
import android.media.AudioManager
import android.media.VolumeProvider
import android.media.session.MediaSession
import android.media.session.PlaybackState
import android.os.SystemClock
import com.nos.motobitride.core.log.AppLog
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.math.abs

private const val TAG = "VolumeKeys"

/**
 * Comandi di emergenza tramite i tasti volume.
 *
 * Il canale è una `MediaSession` con un `VolumeProvider` remoto: è l'unico modo
 * per ricevere i tasti volume da un'app che non è in primo piano (bolla,
 * navigatore aperto, telefono in tasca).
 *
 * Regole (direttiva utente aggiornata):
 *  - l'opzione "modalità Volume +" è stata **eliminata**: il display si comanda
 *    con la bolla;
 *  - **Volume + e − insieme** (entro 200 ms) → **ripristino totale** di
 *    emergenza;
 *  - un tasto solo → **solo volume**, come sempre.
 *  - La rete principale resta il **tasto accensione** (non dipende da questa
 *    sessione audio).
 *
 * Limite noto: se un'altra app sta riproducendo audio, il sistema consegna i
 * tasti a quella e il comando non arriva (il volume funziona comunque).
 */
object VolumeKeyController {

    /**
     * Finestra per riconoscere **due tasti davvero insieme**.
     * Era 1,2 s: così un normalissimo "alzo e poi abbasso" scatenava il
     * ripristino totale. 200 ms è la simultaneità reale di due dita.
     */
    private const val BOTH_WINDOW_MS = 200L

    /** Anti-ripetizione: tenendo premuto il tasto non si commuta più volte. */
    private const val MASTER_DEBOUNCE_MS = 900L

    private var session: MediaSession? = null
    private var appContext: Context? = null

    @Volatile private var lastUp = 0L
    @Volatile private var lastDown = 0L
    @Volatile private var lastMasterAt = 0L

    /** Evita che due comandi (toggle/ripristino) girino insieme. */
    private val busy = AtomicBoolean(false)

    fun start(context: Context) {
        if (session != null) return
        appContext = context.applicationContext

        runCatching {
            val audio = appContext!!.getSystemService(Context.AUDIO_SERVICE) as AudioManager
            val max = audio.getStreamMaxVolume(AudioManager.STREAM_MUSIC).coerceAtLeast(1)
            val current = audio.getStreamVolume(AudioManager.STREAM_MUSIC)

            val provider = object : VolumeProvider(
                VOLUME_CONTROL_RELATIVE,
                max,
                current
            ) {
                override fun onAdjustVolume(direction: Int) {
                    // Il volume cambia SEMPRE: qualunque sia il comando, il
                    // comportamento normale non si perde (D6).
                    runCatching {
                        audio.adjustStreamVolume(
                            AudioManager.STREAM_MUSIC,
                            if (direction >= 0) AudioManager.ADJUST_RAISE
                            else AudioManager.ADJUST_LOWER,
                            0
                        )
                    }

                    val now = SystemClock.elapsedRealtime()
                    if (direction >= 0) lastUp = now else lastDown = now

                    // Due tasti insieme = ripristino totale immediato (D2).
                    if (lastUp > 0 && lastDown > 0 &&
                        abs(lastUp - lastDown) <= BOTH_WINDOW_MS
                    ) {
                        lastUp = 0
                        lastDown = 0
                        AppLog.w(TAG, "Volume + e - insieme: ripristino totale")
                        runCommand { restore() }
                        return
                    }

                    // Un tasto solo non comanda il display: solo volume.
                    return
                }
            }

            session = MediaSession(appContext!!, "MotobitVolumeKeys").also {
                it.setPlaybackToRemote(provider)
                // Senza uno stato di riproduzione Android spesso NON consegna i
                // tasti volume alla sessione remota. Non riproduciamo audio: lo
                // stato serve solo a ricevere i tasti.
                it.setPlaybackState(
                    PlaybackState.Builder()
                        .setActions(PlaybackState.ACTION_PLAY_PAUSE)
                        .setState(
                            PlaybackState.STATE_PLAYING,
                            PlaybackState.PLAYBACK_POSITION_UNKNOWN,
                            1.0f
                        )
                        .build()
                )
                it.isActive = true
            }
            AppLog.i(TAG, "Comandi volume attivi")
        }.onFailure {
            AppLog.w(TAG, "Comandi volume non attivabili: ${it.message}")
        }
    }

    fun stop() {
        runCatching { session?.isActive = false; session?.release() }
        session = null
    }

    // ------------------------------------------------------------------
    // Esecuzione fuori dal thread di callback
    // ------------------------------------------------------------------

    /**
     * I comandi toccano shell e servizio di sistema: mai sul thread che riceve
     * il tasto. Un solo comando alla volta: se arriva mentre un altro è in
     * corso, viene scartato (evita doppie commutazioni).
     */
    private fun runCommand(block: () -> Unit) {
        if (appContext == null) return
        if (!busy.compareAndSet(false, true)) {
            AppLog.i(TAG, "Comando in corso: richiesta ignorata")
            return
        }
        Thread {
            try {
                block()
            } catch (t: Throwable) {
                AppLog.e(TAG, "Comando volume fallito", t)
            } finally {
                busy.set(false)
            }
        }.start()
    }

    /** Ripristino totale, sempre possibile (anche a Shizuku spento). */
    private fun restore() {
        val ctx = appContext ?: return
        val result = DisplayMaster.restoreAll(ctx)
        AppLog.i(TAG, "Ripristino → $result")
    }
}
