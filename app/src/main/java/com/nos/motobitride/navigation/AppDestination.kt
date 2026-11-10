package com.nos.motobitride.navigation

sealed class AppDestination(val route: String) {
    data object Hud: AppDestination("hud")
    data object Garage: AppDestination("garage")
    data object QrScan: AppDestination("qr_scan")
    data object Settings: AppDestination("settings")

    // Sottopagine delle Impostazioni
    data object SettingsGeneral: AppDestination("settings_general")
    data object SettingsConnections: AppDestination("settings_connections")
    data object SettingsResolution: AppDestination("settings_resolution")
    data object SettingsShizukuGuide: AppDestination("settings_shizuku_guide")
    data object SettingsPermissions: AppDestination("settings_permissions")
    data object SettingsDevelopment: AppDestination("settings_development")
}
