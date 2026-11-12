package com.nos.motobitride.ui.i18n

/**
 * Accesso ai testi dell'interfaccia.
 *
 * I testi **non stanno più nel codice**: stanno in `assets/texts/`, una cartella
 * per lingua, tre file per cartella (`ui`, `settings`, `hud`).
 *
 * ```
 * assets/texts/languages.xml      elenco delle lingue (una riga per lingua)
 * assets/texts/it-IT/ui.xml       comuni, Home, Garage, QR
 * assets/texts/it-IT/settings.xml Impostazioni
 * assets/texts/it-IT/hud.xml      connessione e streaming
 * assets/texts/en-GB/...          idem in inglese
 * ```
 *
 * - **Aggiungere una lingua**: una riga in `languages.xml` + una cartella con i
 *   tre file. Nessuna modifica al codice.
 * - **Cambiare un testo**: modifica all'XML. Nessuna ricompilazione.
 *
 * Il contesto serve per leggere gli assets: viene impostato una volta in
 * `MainActivity.onCreate` con [init].
 */
object AppTexts {

    private var appContext: android.content.Context? = null

    fun init(context: android.content.Context) {
        appContext = context.applicationContext
    }

    fun get(key: String, language: AppLanguage): String {
        val ctx = appContext ?: return "[$key]"
        return LanguageTexts.get(ctx, language.code, key)
    }

    fun get(key: String, language: AppLanguage, vararg args: Any): String {
        val ctx = appContext ?: return "[$key]"
        return LanguageTexts.get(ctx, language.code, key, *args)
    }
}
