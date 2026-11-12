package com.nos.motobitride

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.navigation.compose.rememberNavController
import com.nos.motobitride.core.apps.SavedApps
import com.nos.motobitride.core.display.DisplayBridge
import com.nos.motobitride.core.display.DisplayMaster
import com.nos.motobitride.core.display.DisplayModeController
import com.nos.motobitride.core.display.ScreenOffRestoreGuard
import com.nos.motobitride.core.display.VolumeKeyController
import com.nos.motobitride.core.garage.DeadlineWorker
import com.nos.motobitride.core.garage.VehicleStore
import com.nos.motobitride.core.link.SavedHud
import com.nos.motobitride.core.log.AppLog
import com.nos.motobitride.core.settings.AppSettings
import com.nos.motobitride.core.shizuku.ShizukuBridge
import com.nos.motobitride.navigation.AppNavGraph
import com.nos.motobitride.ui.theme.MotobitRideTheme
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Impostazioni, HUD memorizzato e profilo veicolo vanno caricati prima della UI
        AppSettings.init(applicationContext)

        // Testi: contesto per leggerli dagli XML in assets/texts/ (una lingua = una cartella)
        com.nos.motobitride.ui.i18n.AppTexts.init(applicationContext)
        SavedHud.init(applicationContext)
        VehicleStore.init(applicationContext)
        SavedApps.init(applicationContext)
        ShizukuBridge.init(applicationContext)
        DisplayBridge.init(applicationContext)

        // SICUREZZA: se l'app parte e trova il display modificato da noi, lo
        // rimette a posto SUBITO (copre riavvio del telefono, crash, riavvio
        // dell'app). La guardia sullo spegnimento dello schermo sta in un
        // oggetto condiviso, così resta viva anche quando l'app è in background
        // dentro il servizio di mirroring.
        ScreenOffRestoreGuard.register(applicationContext)

        // UNA SOLA VOLTA PER PROCESSO (non per Activity).
        // Prima questo girava a ogni onCreate: cambiare risoluzione ricreava
        // l'Activity, il ripristino annullava quello che era appena stato
        // applicato → ciclo applica/ripristina, schermo nero a intermittenza.
        runStartupDisplaySyncOnce()

        // Comandi da tasti volume (Volume+ = master, Volume+/− insieme = ripristino)
        VolumeKeyController.start(applicationContext)


        // Controllo giornaliero delle scadenze (revisione/tassa/assicurazione).
        // Idempotente: WorkManager non duplica il lavoro gia' pianificato.
        //
        // FUORI dal main thread: WorkManager inizializza un database Room
        // (SQLite) e sul thread principale rallentava l'avvio dell'app.
        CoroutineScope(Dispatchers.IO).launch {
            runCatching { DeadlineWorker.schedule(applicationContext) }
        }

        // Abilita il salvataggio automatico del log su file (Documenti):
        // i test lunghi superano il ring buffer e perderebbero l'inizio.
        AppLog.attach(applicationContext)
        try {
            AppLog.i("AppLog", "Log su file: " + AppLog.currentLogFileName())
        } catch (_: Throwable) {
        }

        setContent {
            MotobitRideTheme {
                DaemonApp()
            }
        }
    }

    /**
     * La bolla deve essere **sempre presente** nella modalità "telefono sul
     * manubrio": se il sistema l'ha uccisa mentre l'app era in background,
     * tornando in primo piano viene rimessa.
     */
    override fun onResume() {
        super.onResume()
        // Barra comandi: va nascosta **dentro la nostra finestra**. `policy_control`
        // da solo non toglie lo spazio dei gesti, che l'app continua a riservare
        // e che nel video (senza trasparenza) diventa una banda nera opaca.
        applyNavBarVisibility()
        Thread {
            runCatching { DisplayModeController.apply(applicationContext) }
        }.start()

        // Secondo tentativo ritardato: al primo avvio l'app non è ancora
        // considerata in primo piano e ColorOS rifiuta l'avvio del servizio
        // della bolla → la bolla apparirebbe solo dopo un tocco o un cambio
        // schermata. Riprovare dopo un istante la fa comparire subito.
        android.os.Handler(mainLooper).postDelayed({
            runCatching { DisplayModeController.apply(applicationContext) }
        }, 1500)
    }

    /**
     * Barra dei **comandi** (non quella di stato: batteria e ora restano).
     *
     * Regola: nascosta **solo** se lo switch è ON **e** il display è modificato.
     * A risoluzione normale deve restare visibile, altrimenti il telefono non si
     * usa più normalmente.
     */
    private fun applyNavBarVisibility() {
        val hide = runCatching {
            AppSettings.displayHideNavBar.value && AppSettings.displayOverride.value
        }.getOrDefault(false)
        runCatching {
            // Modalità immersiva "vera": il contenuto occupa anche lo spazio
            // della barra. Senza questo la finestra RISERVA la fascia in fondo
            // e la disegna col proprio sfondo → resta una banda scura senza
            // pulsanti (era esattamente il difetto segnalato).
            androidx.core.view.WindowCompat
                .setDecorFitsSystemWindows(window, !hide)

            val controller = androidx.core.view.WindowCompat
                .getInsetsController(window, window.decorView)
            if (hide) {
                controller.hide(androidx.core.view.WindowInsetsCompat.Type.navigationBars())
                controller.systemBarsBehavior =
                    androidx.core.view.WindowInsetsControllerCompat
                        .BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            } else {
                controller.show(androidx.core.view.WindowInsetsCompat.Type.navigationBars())
            }
            AppLog.i("MainActivity", "Barra comandi: ${if (hide) "nascosta" else "visibile"}")
        }
    }

    /** Il cambio di risoluzione ricrea la configurazione: la barra torna. */
    override fun onConfigurationChanged(newConfig: android.content.res.Configuration) {
        super.onConfigurationChanged(newConfig)
        applyNavBarVisibility()
    }

    override fun onDestroy() {
        // La guardia NON si toglie più qui: la toglie il servizio quando muore.
        // Toglierla qui significava perdere il ripristino col tasto accensione
        // appena l'app veniva chiusa.
        super.onDestroy()
    }

    /**
     * Sincronizzazione del display all'avvio, **una sola volta per processo**.
     *
     * Due casi, mai insieme:
     *  - il display è ancora modificato da un avvio precedente (crash, app
     *    uccisa) → si ripristina: è la rete di sicurezza;
     *  - il display è normale → si applica la modalità automatica scelta
     *    (in tasca / sempre acceso). In modalità Volume + non si tocca nulla.
     */
    private fun runStartupDisplaySyncOnce() {
        if (!startupSync.compareAndSet(false, true)) {
            AppLog.i("MainActivity", "Sincronizzazione display già eseguita in questo processo")
            return
        }
        Thread {
            try {
                // Guarisce la luminosità: se un'esecuzione precedente era stata
                // uccisa con l'oscuramento attivo, lo schermo restava scuro per
                // sempre. Qui si rimette il valore originale (se memorizzato).
                runCatching { com.nos.motobitride.core.display.DimOverlay.hide(applicationContext) }

                // Igiene della bolla AVVIA: se non c'e' ne' una sessione ne' una
                // ricerca di connessione in corso, una bolla rimasta viva dalla
                // sessione precedente non ha senso e va chiusa (era quella che
                // "girava" da sola all'apertura dell'app).
                runCatching {
                    val nessunaSessione = com.nos.motobitride.core.hud.HudSession.stream == null
                    val nessunaRicerca =
                        !com.nos.motobitride.core.hud.HudSession.autoConnectForSession.value
                    if (nessunaSessione && nessunaRicerca) {
                        com.nos.motobitride.feature.stream.StartBubbleService
                            .hide(applicationContext)
                        AppLog.i("MainActivity", "Bolla AVVIA residua chiusa (nulla in corso)")
                    }
                }

                if (AppSettings.displayOverride.value) {
                    AppLog.w("MainActivity", "Display ancora modificato dall'avvio precedente: ripristino")
                    val result = DisplayMaster.restoreAll(applicationContext)
                    AppLog.i("MainActivity", "Ripristino all'avvio: $result")
                    // Interruzione anomala della sessione precedente: si passa alla
                    // modalità a bolla, che NON applica nulla da sola finché non
                    // tocchi la bolla. Il telefono resta in mano all'utente.
                    AppSettings.setDisplayMode(com.nos.motobitride.core.settings.DisplayMode.ALWAYS_ON)
                } else {
                    DisplayModeController.apply(applicationContext)
                }
            } catch (t: Throwable) {
                AppLog.e("MainActivity", "Sincronizzazione display fallita", t)
            }
        }.start()
    }

    private companion object {
        val startupSync = java.util.concurrent.atomic.AtomicBoolean(false)
    }
}

@Composable
fun DaemonApp() {
    MotobitRideTheme {
        Surface(color = MaterialTheme.colorScheme.background) {
            val navController = rememberNavController()
            AppNavGraph(navController = navController)
        }
    }
}
