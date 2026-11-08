package com.nos.motobitride

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.navigation.compose.rememberNavController
import com.nos.motobitride.core.garage.DeadlineWorker
import com.nos.motobitride.core.garage.VehicleStore
import com.nos.motobitride.core.link.SavedHud
import com.nos.motobitride.core.log.AppLog
import com.nos.motobitride.core.settings.AppSettings
import com.nos.motobitride.navigation.AppNavGraph
import com.nos.motobitride.ui.theme.MotobitRideTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        // Il manifest assegna Theme.MotobitRide.Splash solo alla finestra di
        // avvio. Prima di creare la finestra si torna al tema normale: lo
        // splash e' gestito dal sistema (SplashScreen API su Android 12+) e
        // non resta come sfondo dell'Activity.
        setTheme(R.style.Theme_MotobitRide)

        super.onCreate(savedInstanceState)

        // Impostazioni, HUD memorizzato e profilo veicolo vanno caricati prima della UI
        AppSettings.init(applicationContext)
        SavedHud.init(applicationContext)
        VehicleStore.init(applicationContext)

        // Controllo giornaliero delle scadenze (revisione/tassa/assicurazione).
        // Idempotente: WorkManager non duplica il lavoro gia' pianificato.
        DeadlineWorker.schedule(applicationContext)

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
