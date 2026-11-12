package com.nos.motobitride.ui.i18n

import android.content.Context
import android.util.Xml
import org.xmlpull.v1.XmlPullParser

/**
 * Lettore dei testi da `assets/texts/` — stesso metodo dell'app di riferimento
 * RideLink, semplificato per il nostro caso.
 *
 * ```
 * assets/texts/languages.xml          elenco delle lingue (una riga per lingua)
 * assets/texts/it-IT/ui.xml           testi comuni, Home, Garage, QR
 * assets/texts/it-IT/settings.xml     testi delle Impostazioni
 * assets/texts/it-IT/hud.xml          testi di connessione e streaming
 * assets/texts/en-GB/...              idem per l'inglese
 * ```
 *
 * Aggiungere una lingua = **una riga** in `languages.xml` + una cartella con i
 * tre file. Cambiare un testo = modificare l'XML, senza ricompilare il codice.
 *
 * Fallback: lingua richiesta → inglese → `[chiave]`.
 */
object LanguageTexts {

    private const val FALLBACK = "en-GB"
    private val CATEGORIES = listOf("ui", "settings", "hud")

    private val cache = mutableMapOf<String, Map<String, String>>()

    /** Normalizza "it" → "it-IT", "en" → "en-GB". */
    fun normalize(language: String): String = when (language.lowercase()) {
        "it", "it-it" -> "it-IT"
        "en", "en-gb", "en-us" -> "en-GB"
        else -> language
    }

    @Synchronized
    private fun load(context: Context, language: String, category: String): Map<String, String> {
        val cacheKey = "$language/$category"
        cache[cacheKey]?.let { return it }

        val map = mutableMapOf<String, String>()
        try {
            context.assets.open("texts/$language/$category.xml").use { input ->
                val parser = Xml.newPullParser()
                parser.setInput(input, "UTF-8")
                var event = parser.eventType
                var currentId: String? = null
                while (event != XmlPullParser.END_DOCUMENT) {
                    if (event == XmlPullParser.START_TAG && parser.name == "text") {
                        currentId = parser.getAttributeValue(null, "id")
                    } else if (event == XmlPullParser.TEXT && currentId != null) {
                        val id = currentId!!
                        map[id] = (map[id] ?: "") + parser.text
                    } else if (event == XmlPullParser.END_TAG && parser.name == "text") {
                        currentId = null
                    }
                    event = parser.next()
                }
            }
        } catch (_: Throwable) {
            // File assente: si usa il fallback, non è un errore fatale.
        }

        cache[cacheKey] = map
        return map
    }

    /**
     * Testo per [key]. Cerca nelle tre categorie, così chi chiama non deve
     * sapere in quale file si trova la chiave.
     */
    fun get(context: Context, language: String, key: String): String {
        val lang = normalize(language)
        for (category in CATEGORIES) {
            load(context, lang, category)[key]?.let { return it }
        }
        for (category in CATEGORIES) {
            load(context, FALLBACK, category)[key]?.let { return it }
        }
        return "[$key]"
    }

    fun get(context: Context, language: String, key: String, vararg args: Any): String {
        val raw = get(context, language, key)
        return try {
            String.format(raw, *args)
        } catch (_: Exception) {
            raw
        }
    }

    /** Gli id delle lingue presenti in `languages.xml` (nessun elenco cablato). */
    fun languageIds(context: Context): List<String> {
        val ids = mutableListOf<String>()
        try {
            context.assets.open("texts/languages.xml").use { input ->
                val parser = Xml.newPullParser()
                parser.setInput(input, "UTF-8")
                var event = parser.eventType
                while (event != XmlPullParser.END_DOCUMENT) {
                    if (event == XmlPullParser.START_TAG && parser.name == "language") {
                        val id = parser.getAttributeValue(null, "id").orEmpty()
                        if (id.isNotBlank()) ids.add(id)
                    }
                    event = parser.next()
                }
            }
        } catch (_: Throwable) {
            // Nessun elenco: si resta sulle lingue note all'enum.
        }
        return ids
    }
}
