package com.nos.motobitride.feature.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.nos.motobitride.R
import com.nos.motobitride.core.settings.AppSettings
import com.nos.motobitride.ui.components.MotobitTopAppBar
import com.nos.motobitride.ui.i18n.AppTexts

/**
 * Menu Impostazioni: cinque categorie, ognuna apre una sottopagina.
 * Ogni voce mostra icona, titolo, sottotitolo e chevron.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onNavigateHome: () -> Unit = {},
    onNavigateGarage: () -> Unit = {},
    onNavigateSettings: () -> Unit = {},
    onOpenGeneral: () -> Unit = {},
    onOpenConnections: () -> Unit = {},
    onOpenResolution: () -> Unit = {},
    onOpenPermissions: () -> Unit = {},
    onOpenDevelopment: () -> Unit = {}
) {
    val currentLang by AppSettings.language.collectAsState()

    Scaffold(
        topBar = {
            MotobitTopAppBar(
                onNavigateHome = onNavigateHome,
                onNavigateGarage = onNavigateGarage,
                onNavigateSettings = onNavigateSettings
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .padding(innerPadding)
                .fillMaxSize()
                .padding(horizontal = 16.dp)
                .verticalScroll(rememberScrollState())
        ) {
            Spacer(Modifier.height(8.dp))
            Text(
                text = AppTexts.get("settings_menu_title", currentLang),
                style = MaterialTheme.typography.headlineSmall
            )
            Spacer(Modifier.height(4.dp))
            Text(
                text = AppTexts.get("settings_menu_sub", currentLang),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(18.dp))

            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                SettingsCategoryCard(
                    title = AppTexts.get("settings_cat_general_title", currentLang),
                    subtitle = AppTexts.get("settings_cat_general_sub", currentLang),
                    icon = Icons.Default.Settings,
                    accent = Color(0xFF4C8DFF),
                    onClick = onOpenGeneral
                )

                SettingsCategoryCard(
                    title = AppTexts.get("settings_cat_connections_title", currentLang),
                    subtitle = AppTexts.get("settings_cat_connections_sub", currentLang),
                    icon = Icons.Default.Refresh,
                    accent = Color(0xFF2ECC71),
                    onClick = onOpenConnections
                )

                SettingsCategoryCard(
                    title = AppTexts.get("settings_cat_resolution_title", currentLang),
                    subtitle = AppTexts.get("settings_cat_resolution_sub", currentLang),
                    painterRes = R.drawable.ic_cat_resolution,
                    accent = Color(0xFFFF9F43),
                    onClick = onOpenResolution
                )

                SettingsCategoryCard(
                    title = AppTexts.get("settings_cat_permissions_title", currentLang),
                    subtitle = AppTexts.get("settings_cat_permissions_sub", currentLang),
                    icon = Icons.Default.Lock,
                    accent = Color(0xFFE74C3C),
                    onClick = onOpenPermissions
                )

                SettingsCategoryCard(
                    title = AppTexts.get("settings_cat_development_title", currentLang),
                    subtitle = AppTexts.get("settings_cat_development_sub", currentLang),
                    icon = Icons.Default.Build,
                    accent = Color(0xFF9B59B6),
                    onClick = onOpenDevelopment
                )
            }

            Spacer(Modifier.height(28.dp))
        }
    }
}
