package com.nos.motobitride.core.garage

import java.time.LocalDate
import java.time.YearMonth
import java.time.temporal.ChronoUnit

/**
 * Calcolo delle scadenze annuali. Pura aritmetica sulle date: **nessun permesso**.
 *
 * Regola del giorno:
 *  - `day = 0`  -> "entro il mese": la scadenza e' l'**ultimo giorno** del mese
 *                  (es. "tassa entro marzo" = 31 marzo);
 *  - `day = 1..31` -> giorno preciso, ridotto all'ultimo giorno se il mese e' piu' corto
 *                  (es. 31 febbraio -> 28/29).
 */
object DeadlineReminder {

    /** Data di scadenza per l'anno indicato, oppure null se il mese non e' impostato. */
    fun dueDate(deadline: VehicleDeadline, year: Int): LocalDate? {
        if (!deadline.isSet) return null
        val lastDay = YearMonth.of(year, deadline.month).lengthOfMonth()
        val day = if (deadline.day in 1..31) deadline.day.coerceAtMost(lastDay) else lastDay
        return LocalDate.of(year, deadline.month, day)
    }

    /** Prossima occorrenza: quest'anno se non e' ancora passata, altrimenti l'anno dopo. */
    fun nextOccurrence(deadline: VehicleDeadline, today: LocalDate): LocalDate? {
        val thisYear = dueDate(deadline, today.year) ?: return null
        return if (thisYear.isBefore(today)) dueDate(deadline, today.year + 1) else thisYear
    }

    /** Giorni che mancano alla prossima occorrenza (0 = oggi). */
    fun daysUntil(deadline: VehicleDeadline, today: LocalDate): Long? {
        val due = nextOccurrence(deadline, today) ?: return null
        return ChronoUnit.DAYS.between(today, due)
    }

    /**
     * La notifica va mostrata oggi?
     *
     * @param daysBefore quanti giorni prima avvisare (Impostazioni, default 7)
     */
    fun shouldNotify(
        deadline: VehicleDeadline,
        today: LocalDate,
        daysBefore: Int,
        nowMillis: Long = System.currentTimeMillis()
    ): Boolean {
        if (!deadline.isSet) return false
        if (deadline.snoozedUntil > nowMillis) return false

        val due = nextOccurrence(deadline, today) ?: return false
        if (deadline.dismissedForYear == due.year) return false

        val days = ChronoUnit.DAYS.between(today, due)
        return days in 0..daysBefore.toLong()
    }
}
