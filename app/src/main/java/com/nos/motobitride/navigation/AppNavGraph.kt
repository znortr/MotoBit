package com.nos.motobitride.navigation

import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import com.nos.motobitride.core.qr.QrContent
import com.nos.motobitride.feature.ap.QrScanScreen
import com.nos.motobitride.feature.garage.GarageScreen
import com.nos.motobitride.feature.hud.HudScreen
import com.nos.motobitride.feature.settings.SettingsConnectionsScreen
import com.nos.motobitride.feature.settings.SettingsDevelopmentScreen
import com.nos.motobitride.feature.settings.SettingsGeneralScreen
import com.nos.motobitride.feature.settings.SettingsPermissionsScreen
import com.nos.motobitride.feature.settings.SettingsResolutionScreen
import com.nos.motobitride.feature.settings.SettingsScreen

@Composable
fun AppNavGraph(
    navController: NavHostController,
    startDestination: String = AppDestination.Hud.route
) {
    fun navigateTopLevel(route: String) {
        if (navController.currentDestination?.route != route) {
            navController.navigate(route) {
                popUpTo(AppDestination.Hud.route) {
                    saveState = true
                }
                launchSingleTop = true
                restoreState = true
            }
        }
    }

    fun goBackFrom(route: String) {
        if (navController.currentDestination?.route == route) {
            navController.popBackStack()
        }
    }

    NavHost(
        navController = navController,
        startDestination = startDestination,
        enterTransition = { slideInHorizontally(initialOffsetX = { it }) + fadeIn() },
        exitTransition = { slideOutHorizontally(targetOffsetX = { -it }) + fadeOut() },
        popEnterTransition = { slideInHorizontally(initialOffsetX = { -it }) + fadeIn() },
        popExitTransition = { slideOutHorizontally(targetOffsetX = { it }) + fadeOut() }
    ) {
        composable(AppDestination.Hud.route) { backStackEntry ->
            val qrResultState = backStackEntry
                .savedStateHandle
                .getStateFlow<QrContent?>("qrResult", QrContent.Unknown)
                .collectAsState()

            HudScreen(
                hudQrValue = qrResultState.value,
                onShowQrScanner = {
                    navController.navigate(AppDestination.QrScan.route)
                },
                onNavigateHome = { navigateTopLevel(AppDestination.Hud.route) },
                onNavigateGarage = { navigateTopLevel(AppDestination.Garage.route) },
                onNavigateSettings = { navigateTopLevel(AppDestination.Settings.route) }
            )
        }

        composable(AppDestination.Garage.route) { backStackEntry ->
            val qrResultState = backStackEntry
                .savedStateHandle
                .getStateFlow<QrContent?>("qrResult", QrContent.Unknown)
                .collectAsState()

            GarageScreen(
                hudQrValue = qrResultState.value,
                onNavigateHome = { navigateTopLevel(AppDestination.Hud.route) },
                onNavigateGarage = { navigateTopLevel(AppDestination.Garage.route) },
                onNavigateSettings = { navigateTopLevel(AppDestination.Settings.route) },
                onScanQr = { navController.navigate(AppDestination.QrScan.route) }
            )
        }

        composable(AppDestination.QrScan.route) {
            QrScanScreen(
                onQrScanned = { qrValue ->
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

        // ------------------------------------------------------------------
        // Impostazioni: menu + sottopagine
        // ------------------------------------------------------------------
        composable(AppDestination.Settings.route) {
            SettingsScreen(
                onNavigateHome = { navigateTopLevel(AppDestination.Hud.route) },
                onNavigateGarage = { navigateTopLevel(AppDestination.Garage.route) },
                onNavigateSettings = { navigateTopLevel(AppDestination.Settings.route) },
                onOpenGeneral = { navController.navigate(AppDestination.SettingsGeneral.route) },
                onOpenConnections = { navController.navigate(AppDestination.SettingsConnections.route) },
                onOpenResolution = { navController.navigate(AppDestination.SettingsResolution.route) },
                onOpenPermissions = { navController.navigate(AppDestination.SettingsPermissions.route) },
                onOpenDevelopment = { navController.navigate(AppDestination.SettingsDevelopment.route) }
            )
        }

        composable(AppDestination.SettingsGeneral.route) {
            SettingsGeneralScreen(
                onBack = { goBackFrom(AppDestination.SettingsGeneral.route) }
            )
        }

        composable(AppDestination.SettingsConnections.route) {
            SettingsConnectionsScreen(
                onBack = { goBackFrom(AppDestination.SettingsConnections.route) }
            )
        }

        composable(AppDestination.SettingsResolution.route) {
            SettingsResolutionScreen(
                onBack = { goBackFrom(AppDestination.SettingsResolution.route) }
            )
        }

        composable(AppDestination.SettingsPermissions.route) {
            SettingsPermissionsScreen(
                onBack = { goBackFrom(AppDestination.SettingsPermissions.route) }
            )
        }

        composable(AppDestination.SettingsDevelopment.route) {
            SettingsDevelopmentScreen(
                onBack = { goBackFrom(AppDestination.SettingsDevelopment.route) }
            )
        }
    }
}
