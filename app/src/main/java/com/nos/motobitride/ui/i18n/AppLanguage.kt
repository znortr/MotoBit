package com.nos.motobitride.ui.i18n

enum class AppLanguage(
    val code: String,
    val nameNative: String,
    val flag: String
) {
    ITALIANO("it", "Italiano", "🇮🇹"),
    ENGLISH("en", "English", "🇬🇧");

    val labelWithFlag: String
        get() = "$flag  $nameNative"

    companion object {
        fun fromCode(code: String?): AppLanguage =
            entries.firstOrNull { it.code.equals(code, ignoreCase = true) } ?: ITALIANO
    }
}
