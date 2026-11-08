package com.nos.motobitride

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.navigation.compose.rememberNavController
import kotlinx.coroutines.delay
import com.nos.motobitride.core.garage.DeadlineWorker
import com.nos.motobitride.core.garage.VehicleStore
import com.nos.motobitride.core.link.SavedHud
import com.nos.motobitride.core.log.AppLog
import com.nos.motobitride.core.settings.AppSettings
import com.nos.motobitride.navigation.AppNavGraph
import com.nos.motobitride.ui.theme.MotobitRideTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        // Il manifest assegna Theme.MotobitRide.Splash solo per la finestra di
        // avvio (startup window). Prima di creare la finestra si torna al tema
        // normale, cosi' lo splash non resta come sfondo dell'Activity.
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

            Box {
                AppNavGraph(navController = navController)
                SplashOverlay()
            }
        }
    }
}

/**
 * Splash mostrata DENTRO l'app.
 *
 * Perché non basta il `windowBackground` del tema: da Android 12 la splash di
 * sistema viene mostrata sopra la finestra e l'app disegna il primo frame
 * subito, quindi il windowBackground non si vede mai (risultato: schermo nero).
 *
 * Durata breve e dissolvenza in uscita; l'immagine è in `drawable-nodpi` e usa
 * `ContentScale.Fit`, cosi' non viene deformata e non viene tagliata.
 */
@Composable
private fun SplashOverlay() {
    var visible by remember { mutableStateOf(true) }

    LaunchedEffect(Unit) {
        delay(750)
        visible = false
    }

    AnimatedVisibility(
        visible = visible,
        enter = EnterTransition.None,
        exit = fadeOut(animationSpec = tween(250))
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color(0xFF0B0B0D)),
            contentAlignment = Alignment.Center
        ) {
            Image(
                painter = painterResource(R.drawable.ic_splash_strtup),
                contentDescription = null,
                contentScale = ContentScale.Fit,
                modifier = Modifier.fillMaxSize()
            )
        }
    }
}