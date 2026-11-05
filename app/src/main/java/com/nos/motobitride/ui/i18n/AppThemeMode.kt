package com.nos.motobitride.ui.i18n

enum class AppThemeMode(
    val code: String,
    val icon: String,
    val labelKey: String
) {
    DARK("dark", "🌙", "theme_dark"),
    LIGHT("light", "☀️", "theme_light"),
    SYSTEM("system", "📱", "theme_system");

    companion object {
        fun fromCode(code: String?): AppThemeMode =
            entries.firstOrNull { it.code.equals(code, ignoreCase = true) } ?: DARK
    }
}
