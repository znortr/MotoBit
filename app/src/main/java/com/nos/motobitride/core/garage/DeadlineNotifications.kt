package com.nos.motobitride.core.garage

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.nos.motobitride.R
import com.nos.motobitride.core.log.AppLog
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * Canale e costruzione delle notifiche di scadenza.
 *
 * Canale separato da quello del mirroring: qui l'importanza e' DEFAULT, quindi
 * la notifica suona/vibra (le scadenze devono farsi notare).
 */
object DeadlineNotifications {

    private const val TAG = "DeadlineNotif"
    const val CHANNEL_ID = "deadline_channel"

    private val DATE_FORMAT = DateTimeFormatter.ofPattern("d MMMM yyyy", Locale.ITALIAN)

    /** ID stabile per tipo: cosi' le tre scadenze non si sovrascrivono. */
    fun notificationId(kind: DeadlineKind): Int = when (kind) {
        DeadlineKind.REVISION -> 2001
        DeadlineKind.TAX -> 2002
        DeadlineKind.INSURANCE -> 2003
    }

    fun ensureChannel(context: Context) {
        val nm = context.getSystemService(NotificationManager::class.java) ?: return
        val channel = NotificationChannel(
            CHANNEL_ID,
            "Scadenze veicolo",
            NotificationManager.IMPORTANCE_DEFAULT
        ).apply {
            description = "Promemoria per revisione, tassa e assicurazione"
            setShowBadge(true)
        }
        nm.createNotificationChannel(channel)
    }

    fun show(
        context: Context,
        kind: DeadlineKind,
        vehicleName: String,
        due: LocalDate,
        days: Long
    ) {
        ensureChannel(context)

        val title = when (days) {
            0L -> "${label(kind)} — scade oggi"
            1L -> "${label(kind)} — scade domani"
            else -> "${label(kind)} — tra $days giorni"
        }
        val body = "$vehicleName · scadenza ${DATE_FORMAT.format(due)}"

        val snoozeIntent = PendingIntent.getBroadcast(
            context,
            notificationId(kind) * 10 + 1,
            Intent(context, DeadlineActionReceiver::class.java).apply {
                action = DeadlineActionReceiver.ACTION_SNOOZE
                putExtra(DeadlineActionReceiver.EXTRA_KIND, kind.name)
            },
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val dismissIntent = PendingIntent.getBroadcast(
            context,
            notificationId(kind) * 10 + 2,
            Intent(context, DeadlineActionReceiver::class.java).apply {
                action = DeadlineActionReceiver.ACTION_DISMISS
                putExtra(DeadlineActionReceiver.EXTRA_KIND, kind.name)
                putExtra(DeadlineActionReceiver.EXTRA_YEAR, due.year)
            },
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle(title)
            .setContentText(body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setAutoCancel(true)
            .setGroup("deadline_group")
            .addAction(0, "Posponi", snoozeIntent)
            .addAction(0, "Elimina", dismissIntent)
            .build()

        try {
            NotificationManagerCompat.from(context)
                .notify(notificationId(kind), notification)
            AppLog.i(TAG, "Notifica $kind: $title ($body)")
        } catch (t: Throwable) {
            AppLog.w(TAG, "notifica non mostrata: ${t.message}")
        }
    }

    fun cancel(context: Context, kind: DeadlineKind) {
        runCatching {
            NotificationManagerCompat.from(context).cancel(notificationId(kind))
        }
    }

    private fun label(kind: DeadlineKind): String = when (kind) {
        DeadlineKind.REVISION -> "Revisione tecnica"
        DeadlineKind.TAX -> "Tassa di circolazione"
        DeadlineKind.INSURANCE -> "Assicurazione"
    }
}
