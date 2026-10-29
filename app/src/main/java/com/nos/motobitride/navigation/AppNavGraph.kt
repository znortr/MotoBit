package com.nos.motobitride.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.navigation.compose.NavHost
import androidx.navigation.NavHostController
import androidx.navigation.compose.composable
import com.nos.motobitride.core.qr.QrContent
import com.nos.motobitride.feature.hud.HudScreen
import com.nos.motobitride.feature.login.LoginScreen
import com.nos.motobitride.feature.settings.SettingsScreen
import com.nos.motobitride.feature.ap.QrScanScreen

@Composable
fun AppNavGraph (
    navController: NavHostController,
    startDestination: String = AppDestination.Hud.route
) {
    NavHost (
        navController = navController,
        startDestination = startDestination
    ) {
        composable(AppDestination.Hud.route) { backStackEntry ->
            // Observe qrResult as State so Hud recomposes when it changes
            val qrResultState = backStackEntry
                .savedStateHandle
                .getStateFlow<QrContent?>("qrResult", QrContent.Unknown)
                .collectAsState()

            HudScreen(
                qrResultState.value,
                onShowQrScanner = {
                    navController.navigate(AppDestination.QrScan.route)
                },
                onOpenSettings = {
                    navController.navigate(AppDestination.Settings.route)
                }
            )
        }

        composable(AppDestination.QrScan.route) {
            QrScanScreen(
                onQrScanned = { qrValue ->
                    // send result back to the previous destination
                    navController.previousBackStackEntry?.savedStateHandle?.set("qrResult", qrValue)
                    if (navController.currentDestination?.route == AppDestination.QrScan.route) {
                        navController.popBackStack()
                    }
                },
                onBack = {
                    if (navController.currentDestination?.route == AppDestination.QrScan.route) {
                        navController.popBackStack()
                    }
                }
            )
        }

        composable(AppDestination.Settings.route) {
            SettingsScreen(
                onBack = {
                    if (navController.currentDestination?.route == AppDestination.Settings.route) {
                        navController.popBackStack()
                    }
                }
            )
        }

        composable(AppDestination.Login.route) {
            LoginScreen(
                onLoginSuccess = {
                    navController.navigate(AppDestination.Hud.route) {
                        popUpTo(AppDestination.Login.route) { inclusive = true }
                    }
                }
            )
        }
    }
}