package com.nos.motobitride.navigation

sealed class AppDestination(val route: String) {
    data object Hud: AppDestination("hud")
    data object QrScan: AppDestination("qr_scan")
    data object Settings: AppDestination("settings")
    data object Diagnostics: AppDestination("diagnostics")
    data object Login: AppDestination("login")
}