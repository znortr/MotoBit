package com.nos.motobitride.ui.shizuku

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import com.nos.motobitride.core.shizuku.ShizukuBridge
import com.nos.motobitride.core.shizuku.ShizukuStatus
import kotlinx.coroutines.delay

/**
 * Stato di Shizuku osservato dalla UI.
 *
 * Il ponte non espone flow (deve restare raggiungibile per riflessione senza
 * tipi condivisi), quindi si interroga a intervallo mentre la schermata è
 * visibile. Con [enabled] = false non viene **mai** interrogato.
 */
@Composable
fun rememberShizukuStatus(enabled: Boolean): ShizukuStatus {
    var status by remember(enabled) {
        mutableStateOf(
            if (enabled) ShizukuBridge.current.currentStatus() else ShizukuStatus.DISABLED
        )
    }

    LaunchedEffect(enabled) {
        if (!enabled) {
            status = ShizukuStatus.DISABLED
            return@LaunchedEffect
        }
        while (true) {
            status = ShizukuBridge.current.currentStatus()
            delay(1_500)
        }
    }

    return status
}

/** Chiave i18n dell'etichetta di stato. */
fun ShizukuStatus.labelKey(): String = when (this) {
    ShizukuStatus.NOT_INSTALLED -> "shizuku_status_not_installed"
    ShizukuStatus.NOT_RUNNING -> "shizuku_status_not_running"
    ShizukuStatus.NOT_AUTHORIZED -> "shizuku_status_not_authorized"
    ShizukuStatus.READY -> "shizuku_status_ready"
    ShizukuStatus.DISABLED -> "shizuku_status_disabled"
}

/** Chiave i18n dell'azione da proporre, null se non serve. */
fun ShizukuStatus.actionKey(): String? = when (this) {
    ShizukuStatus.NOT_INSTALLED -> "shizuku_action_install"
    ShizukuStatus.NOT_RUNNING -> "shizuku_action_open"
    ShizukuStatus.NOT_AUTHORIZED -> "shizuku_action_grant"
    ShizukuStatus.READY -> null
    ShizukuStatus.DISABLED -> null
}

/**
 * Esegue l'azione giusta per lo stato corrente.
 * @return true se è stata aperta una schermata esterna (Store / Shizuku).
 */
fun ShizukuStatus.performAction(): Boolean = when (this) {
    ShizukuStatus.NOT_INSTALLED -> ShizukuBridge.current.openInstallPage()
    ShizukuStatus.NOT_RUNNING -> ShizukuBridge.current.openShizukuApp()
    ShizukuStatus.NOT_AUTHORIZED -> {
        ShizukuBridge.current.requestPermission()
        false
    }
    ShizukuStatus.READY, ShizukuStatus.DISABLED -> false
}
