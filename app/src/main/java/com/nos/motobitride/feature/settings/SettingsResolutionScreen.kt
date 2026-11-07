package com.nos.motobitride.feature.settings

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import com.nos.motobitride.core.settings.AppSettings
import com.nos.motobitride.ui.i18n.AppTexts

/**
 * Pagina "Risoluzione": per ora solo titolo e sottotitolo.
 * Le impostazioni di risoluzione verranno aggiunte in una revisione successiva.
 */
@Composable
fun SettingsResolutionScreen(
    onBack: () -> Unit = {}
) {
    val currentLang by AppSettings.language.collectAsState()

    SettingsSubPage(
        title = AppTexts.get("settings_cat_resolution_title", currentLang),
        subtitle = AppTexts.get("settings_cat_resolution_sub", currentLang),
        backLabel = AppTexts.get("common_back", currentLang),
        onBack = onBack
    ) {
        // Intenzionalmente vuota: solo titolo e sottotitolo.
    }
}
