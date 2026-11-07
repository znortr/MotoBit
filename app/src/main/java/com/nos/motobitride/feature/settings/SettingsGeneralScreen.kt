package com.nos.motobitride.feature.settings

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import com.nos.motobitride.core.settings.AppSettings
import com.nos.motobitride.ui.i18n.AppLanguage
import com.nos.motobitride.ui.i18n.AppTexts
import com.nos.motobitride.ui.i18n.AppThemeMode

/**
 * Pagina "Generale": lingua e tema.
 *
 * Niente titoli di sezione: il nome dell'impostazione sta nella label del
 * select box, la spiegazione va SOTTO il box.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsGeneralScreen(
    onBack: () -> Unit = {}
) {
    val currentLang by AppSettings.language.collectAsState()
    val currentThemeMode by AppSettings.themeMode.collectAsState()

    SettingsSubPage(
        title = AppTexts.get("settings_cat_general_title", currentLang),
        subtitle = AppTexts.get("settings_cat_general_sub", currentLang),
        backLabel = AppTexts.get("common_back", currentLang),
        onBack = onBack
    ) {
        // ------------------------------------------------------------
        // Lingua
        // ------------------------------------------------------------
        var langExpanded by remember { mutableStateOf(false) }

        ExposedDropdownMenuBox(
            expanded = langExpanded,
            onExpandedChange = { langExpanded = !langExpanded },
            modifier = Modifier.fillMaxWidth()
        ) {
            OutlinedTextField(
                value = currentLang.labelWithFlag,
                onValueChange = {},
                readOnly = true,
                label = { Text(AppTexts.get("settings_language_title", currentLang)) },
                trailingIcon = {
                    ExposedDropdownMenuDefaults.TrailingIcon(expanded = langExpanded)
                },
                colors = ExposedDropdownMenuDefaults.outlinedTextFieldColors(),
                modifier = Modifier
                    .menuAnchor()
                    .fillMaxWidth()
            )
            ExposedDropdownMenu(
                expanded = langExpanded,
                onDismissRequest = { langExpanded = false }
            ) {
                AppLanguage.entries.forEach { lang ->
                    DropdownMenuItem(
                        text = {
                            Text(
                                lang.labelWithFlag,
                                style = MaterialTheme.typography.bodyLarge
                            )
                        },
                        onClick = {
                            AppSettings.setLanguage(lang)
                            langExpanded = false
                        }
                    )
                }
            }
        }
        Text(
            text = AppTexts.get("settings_language_desc", currentLang),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        // ------------------------------------------------------------
        // Tema
        // ------------------------------------------------------------
        var themeExpanded by remember { mutableStateOf(false) }

        ExposedDropdownMenuBox(
            expanded = themeExpanded,
            onExpandedChange = { themeExpanded = !themeExpanded },
            modifier = Modifier.fillMaxWidth()
        ) {
            OutlinedTextField(
                value = AppTexts.get(currentThemeMode.labelKey, currentLang),
                onValueChange = {},
                readOnly = true,
                label = { Text(AppTexts.get("settings_theme_title", currentLang)) },
                trailingIcon = {
                    ExposedDropdownMenuDefaults.TrailingIcon(expanded = themeExpanded)
                },
                colors = ExposedDropdownMenuDefaults.outlinedTextFieldColors(),
                modifier = Modifier
                    .menuAnchor()
                    .fillMaxWidth()
            )
            ExposedDropdownMenu(
                expanded = themeExpanded,
                onDismissRequest = { themeExpanded = false }
            ) {
                AppThemeMode.entries.forEach { mode ->
                    DropdownMenuItem(
                        text = {
                            Text(
                                AppTexts.get(mode.labelKey, currentLang),
                                style = MaterialTheme.typography.bodyLarge
                            )
                        },
                        onClick = {
                            AppSettings.setThemeMode(mode)
                            themeExpanded = false
                        }
                    )
                }
            }
        }
        Text(
            text = AppTexts.get("settings_theme_desc", currentLang),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}
