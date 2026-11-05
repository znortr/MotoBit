package com.nos.motobitride.ui.i18n

object AppTexts {
    private val itMap = mapOf(
        // Common
        "app_name" to "Motobit Ride",
        "common_back" to "Indietro",
        "common_close" to "Chiudi",
        "common_start" to "Avvia",
        "common_stop" to "Stop",
        "common_retry" to "Riprova",
        "common_clear" to "Cancella",
        "common_name" to "Nome",

        // Theme
        "theme_dark" to "🌙 Scuro (Default)",
        "theme_light" to "☀️ Chiaro",
        "theme_system" to "📱 Sistema",

        // HUD Screen
        "hud_saved_card" to "HUD Memorizzato",
        "hud_connection_title" to "Connessione HUD",
        "hud_status" to "Stato link: ",
        "hud_ip" to "Indirizzo IP: ",
        "hud_scan_qr" to "Scansiona QR",
        "hud_no_saved" to "Nessun HUD salvato. Scansiona il QR Code visualizzato sul display della moto.",
        "hud_battery_warning" to "Ottimizzazione batteria attiva",
        "hud_battery_desc" to "L'app potrebbe essere fermata in background durante la guida. Disattiva l'ottimizzazione per Motobit Ride.",
        "hud_disable" to "Disattiva",

        // Settings Screen
        "settings_title" to "Impostazioni",
        "settings_language_title" to "Lingua App",
        "settings_language_desc" to "Seleziona la lingua dell'interfaccia (Italiano / English)",
        "settings_theme_title" to "Tema Grafico",
        "settings_theme_desc" to "Seleziona l'aspetto dell'applicazione (Default: Scuro)",
        "settings_startup_title" to "Comportamento all'avvio",
        "settings_startup_manual" to "Solo credenziali salvate",
        "settings_startup_manual_desc" to "Mostra il nome dell'HUD salvato. Link e video partono solo premendo Avvia.",
        "settings_startup_autolink" to "Collega l'HUD",
        "settings_startup_autolink_desc" to "Ricostruisce il Wi-Fi Direct in automatico e mostra il nome. Il video parte solo premendo Avvia.",
        "settings_startup_autolink_stream" to "Collega e trasmetti",
        "settings_startup_autolink_stream_desc" to "Ricostruisce il link e avvia anche il video. Android chiede comunque il consenso alla cattura schermo.",
        "settings_encoder_idle_title" to "Sospensione encoder H.264",
        "settings_encoder_idle_desc" to "Sospende la codifica video dopo N minuti di attesa per risparmiare batteria e calore.",
        "settings_encoder_idle_label" to "Sospendi encoder dopo: %d min (0 = mai)",
        "settings_clock_sync_title" to "Sincronizzazione orologio TFT",
        "settings_clock_sync_desc" to "Il TFT imposta l'orario solo ricevendo una raffica di pacchetti ora all'apertura della connessione (uno ogni 250 ms). Questo valore e' il numero di pacchetti inviati (minimo 3).",
        "settings_clock_sync_label" to "Push orologio: %d pacchetti (raffica handshake)",
        "settings_diagnostics_entry" to "Log e diagnostica",
        "settings_diagnostics_entry_desc" to "Visualizza eventi in tempo reale e configura il salvataggio log su file.",

        // Diagnostics Screen
        "diag_title" to "Diagnostica",
        "diag_section_title" to "Log diagnostica",
        "diag_switch_title" to "Salvataggio file log completo (canali, sessione, protocollo)",
        "diag_switch_desc" to "Se attivo, il log completo viene salvato in automatico nella cartella Documenti dell'app.",
        "diag_footer" to "Con l'interruttore attivo il log completo viene scritto anche su file in Documenti; con l'interruttore spento la scrittura su file e' disattivata.",
        "diag_clear_button" to "Pulisci",
        "diag_toast_cleared" to "Log pulito",
        "diag_keepalive_title" to "Keepalive orologio periodico PXC (5 min)",
        "diag_keepalive_desc" to "Push dell'ora NON richiesto dal TFT sulla socket di controllo. Misurato che il TFT non lo legge: resta in coda e blocca gli ACK, facendogli chiudere la sessione. Lasciare OFF.",

        // QR Scan Screen
        "qr_scan_title" to "Scansiona QR Code",
        "qr_scan_instruction" to "Inquadra il QR Code mostrato sul display TFT della moto per salvare l'HUD."
    )

    private val enMap = mapOf(
        // Common
        "app_name" to "Motobit Ride",
        "common_back" to "Back",
        "common_close" to "Close",
        "common_start" to "Start",
        "common_stop" to "Stop",
        "common_retry" to "Retry",
        "common_clear" to "Clear",
        "common_name" to "Name",

        // Theme
        "theme_dark" to "🌙 Dark (Default)",
        "theme_light" to "☀️ Light",
        "theme_system" to "📱 System",

        // HUD Screen
        "hud_saved_card" to "Saved HUD",
        "hud_connection_title" to "HUD Connection",
        "hud_status" to "Link status: ",
        "hud_ip" to "IP Address: ",
        "hud_scan_qr" to "Scan QR",
        "hud_no_saved" to "No saved HUD. Scan the QR Code displayed on the motorcycle display.",
        "hud_battery_warning" to "Battery optimization active",
        "hud_battery_desc" to "The app might be stopped in background while riding. Disable optimization for Motobit Ride.",
        "hud_disable" to "Disable",

        // Settings Screen
        "settings_title" to "Settings",
        "settings_language_title" to "App Language",
        "settings_language_desc" to "Select interface language (Italian / English)",
        "settings_theme_title" to "Graphic Theme",
        "settings_theme_desc" to "Select application appearance (Default: Dark)",
        "settings_startup_title" to "Startup Behavior",
        "settings_startup_manual" to "Saved credentials only",
        "settings_startup_manual_desc" to "Shows saved HUD name. Link and video start only by pressing Start.",
        "settings_startup_autolink" to "Connect HUD",
        "settings_startup_autolink_desc" to "Rebuilds Wi-Fi Direct automatically and shows name. Video starts only by pressing Start.",
        "settings_startup_autolink_stream" to "Connect and stream",
        "settings_startup_autolink_stream_desc" to "Rebuilds link and also starts video. Android still requests screen capture consent.",
        "settings_encoder_idle_title" to "H.264 Encoder Suspension",
        "settings_encoder_idle_desc" to "Suspends video encoding after N minutes of waiting to save battery and heat.",
        "settings_encoder_idle_label" to "Suspend encoder after: %d min (0 = never)",
        "settings_clock_sync_title" to "TFT Clock Synchronization",
        "settings_clock_sync_desc" to "The TFT sets time by receiving a burst of clock packets at connection startup (one every 250 ms). This value is the number of sent packets (minimum 3).",
        "settings_clock_sync_label" to "Clock push: %d packets (handshake burst)",
        "settings_diagnostics_entry" to "Log & Diagnostics",
        "settings_diagnostics_entry_desc" to "View live events and configure file log saving.",

        // Diagnostics Screen
        "diag_title" to "Diagnostics",
        "diag_section_title" to "Diagnostics Log",
        "diag_switch_title" to "Save full log file (channels, session, protocol)",
        "diag_switch_desc" to "If active, full log is automatically saved in the app's Documents folder.",
        "diag_footer" to "With switch active, full log is written to file in Documents; with switch off, file writing is disabled.",
        "diag_clear_button" to "Clear",
        "diag_toast_cleared" to "Log cleared",
        "diag_keepalive_title" to "PXC periodic clock keepalive (5 min)",
        "diag_keepalive_desc" to "Clock push NOT requested by the TFT on the control socket. Measured that the TFT does not read it: it stays queued and blocks ACKs, making the TFT close the session. Leave OFF.",

        // QR Scan Screen
        "qr_scan_title" to "Scan QR Code",
        "qr_scan_instruction" to "Frame the QR Code shown on the motorcycle TFT display to save the HUD."
    )

    fun get(key: String, language: AppLanguage): String {
        val map = if (language == AppLanguage.ENGLISH) enMap else itMap
        return map[key] ?: itMap[key] ?: "[$key]"
    }

    fun get(key: String, language: AppLanguage, vararg args: Any): String {
        val raw = get(key, language)
        return try {
            String.format(raw, *args)
        } catch (_: Exception) {
            raw
        }
    }
}
