package com.nos.motobitride.core.garage

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.nos.motobitride.core.log.AppLog
import java.time.LocalDate
import java.time.ZoneId

/**
 * Azioni della notifica di scadenza.
 *
 *  - **Posponi**  -> snooze di 1 giorno (la notifica torna domani)
 *  - **Elimina**  -> sopprime la notifica per l'occorrenza indicata (anno);
 *                    si riarma da sola alla prossima scadenza.
 */
class DeadlineActionReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val kindName = intent.getStringExtra(EXTRA_KIND) ?: return
        val kind = runCatching { DeadlineKind.valueOf(kindName) }.getOrNull() ?: return

        VehicleStore.init(context)

        when (intent.action) {
            ACTION_SNOOZE -> {
                // Domani alle 00:05 (ora locale): da quel momento la notifica puo' ripartire.
                val tomorrow = LocalDate.now().plusDays(1)
                    .atTime(0, 5)
                    .atZone(ZoneId.systemDefault())
                    .toInstant()
                    .toEpochMilli()
                VehicleStore.snooze(kind, tomorrow)
                AppLog.i(TAG, "Posponi $kind -> domani")
            }

            ACTION_DISMISS -> {
                val year = intent.getIntExtra(EXTRA_YEAR, LocalDate.now().year)
                VehicleStore.dismiss(kind, year)
                AppLog.i(TAG, "Elimina $kind per il $year")
            }
        }

        DeadlineNotifications.cancel(context, kind)
    }

    companion object {
        private const val TAG = "DeadlineAction"

        const val ACTION_SNOOZE = "com.nos.motobitride.action.DEADLINE_SNOOZE"
        const val ACTION_DISMISS = "com.nos.motobitride.action.DEADLINE_DISMISS"

        const val EXTRA_KIND = "extra_kind"
        const val EXTRA_YEAR = "extra_year"
    }
}
