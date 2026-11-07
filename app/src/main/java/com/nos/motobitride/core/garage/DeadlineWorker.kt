package com.nos.motobitride.core.garage

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.nos.motobitride.core.link.SavedHud
import com.nos.motobitride.core.log.AppLog
import com.nos.motobitride.core.settings.AppSettings
import java.time.Duration
import java.time.LocalDate
import java.time.LocalDateTime
import java.util.concurrent.TimeUnit

/**
 * Controllo giornaliero delle scadenze.
 *
 * Perche' WorkManager e non AlarmManager: sopravvive al riavvio del telefono
 * senza dover scrivere un `BOOT_COMPLETED`, e non richiede il permesso
 * `SCHEDULE_EXACT_ALARM` (ristretto su Android 12+). Per un avviso a 7 giorni
 * la precisione al minuto non serve.
 */
class DeadlineWorker(
    context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val ctx = applicationContext
        AppSettings.init(ctx)
        VehicleStore.init(ctx)
        SavedHud.init(ctx)

        if (!AppSettings.notificationsEnabled.value) {
            AppLog.i(TAG, "Notifiche disattivate: nessun controllo")
            return Result.success()
        }

        val profile = VehicleStore.current.value
        val hudName = SavedHud.current.value?.displayName
        val vehicleName = vehicleDisplayName(profile, hudName)
        val today = LocalDate.now()
        val daysBefore = AppSettings.daysBefore.value

        DeadlineKind.entries.forEach { kind ->
            val deadline = profile.deadline(kind)
            if (!DeadlineReminder.shouldNotify(deadline, today, daysBefore)) return@forEach

            val due = DeadlineReminder.nextOccurrence(deadline, today) ?: return@forEach
            val days = DeadlineReminder.daysUntil(deadline, today) ?: return@forEach

            DeadlineNotifications.show(ctx, kind, vehicleName, due, days)
        }

        return Result.success()
    }

    companion object {
        private const val TAG = "DeadlineWorker"
        private const val UNIQUE_NAME = "motobit_deadline_check"

        /**
         * Pianifica il controllo giornaliero, allineato alle ~09:00 locali
         * (per non svegliare di notte). Idempotente: `KEEP` non duplica.
         */
        fun schedule(context: Context) {
            val now = LocalDateTime.now()
            var next = now.toLocalDate().atTime(9, 0)
            if (!next.isAfter(now)) next = next.plusDays(1)
            val initialDelay = Duration.between(now, next).toMillis().coerceAtLeast(0L)

            val request = PeriodicWorkRequestBuilder<DeadlineWorker>(1, TimeUnit.DAYS)
                .setInitialDelay(initialDelay, TimeUnit.MILLISECONDS)
                .build()

            runCatching {
                WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                    UNIQUE_NAME,
                    ExistingPeriodicWorkPolicy.KEEP,
                    request
                )
                AppLog.i(TAG, "Controllo scadenze pianificato (primo giro tra ${initialDelay / 60_000} min)")
            }.onFailure {
                AppLog.e(TAG, "pianificazione scadenze fallita", it)
            }
        }
    }
}
