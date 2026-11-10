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
        Thread {
            runCatching { DisplayModeController.apply(applicationContext) }
        }.start()
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
