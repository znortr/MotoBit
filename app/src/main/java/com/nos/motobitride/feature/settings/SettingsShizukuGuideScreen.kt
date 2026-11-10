package com.nos.motobitride.feature.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import com.nos.motobitride.core.settings.AppSettings
import com.nos.motobitride.ui.i18n.AppTexts

/**
 * Guida passo-passo per attivare Shizuku.
 *
 * Scritta per chi non ha dimestichezza con la tecnologia: frasi brevi, nessun
 * termine tecnico, numeri grandi. Include i due casi che bloccano tutti:
 * il riavvio del telefono e la perdita del debug wireless.
 */
@Composable
fun SettingsShizukuGuideScreen(
    onBack: () -> Unit = {}
) {
    val currentLang by AppSettings.language.collectAsState()

    SettingsSubPage(
        title = AppTexts.get("guide_title", currentLang),
        subtitle = AppTexts.get("guide_intro", currentLang),
        backLabel = AppTexts.get("common_back", currentLang),
        onBack = onBack
    ) {
        // Nota importante in evidenza
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(MaterialTheme.shapes.medium)
                .background(MaterialTheme.colorScheme.secondaryContainer)
                .padding(12.dp)
        ) {
            Text(
                text = AppTexts.get("guide_note_reboot", currentLang),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSecondaryContainer
            )
        }

        Hint(AppTexts.get("guide_step1_title", currentLang), AppTexts.get("guide_step1_desc", currentLang), 1)
        Hint(AppTexts.get("guide_step2_title", currentLang), AppTexts.get("guide_step2_desc", currentLang), 2)
        Hint(AppTexts.get("guide_step3_title", currentLang), AppTexts.get("guide_step3_desc", currentLang), 3)
        Hint(AppTexts.get("guide_step4_title", currentLang), AppTexts.get("guide_step4_desc", currentLang), 4)
        Hint(AppTexts.get("guide_step5_title", currentLang), AppTexts.get("guide_step5_desc", currentLang), 5)
        Hint(AppTexts.get("guide_step6_title", currentLang), AppTexts.get("guide_step6_desc", currentLang), 6)
        Hint(AppTexts.get("guide_step7_title", currentLang), AppTexts.get("guide_step7_desc", currentLang), 7)

        HorizontalDivider()

        // Come si usa: la parte che serve davvero tutti i giorni.
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(
                text = AppTexts.get("guide_res_title", currentLang),
                style = MaterialTheme.typography.titleSmall
            )
            Bullet(AppTexts.get("guide_res_1", currentLang))
            Bullet(AppTexts.get("guide_res_2", currentLang))
            Bullet(AppTexts.get("guide_res_3", currentLang))
            Bullet(AppTexts.get("guide_res_4", currentLang))
        }

        HorizontalDivider()

        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(
                text = AppTexts.get("guide_trouble_title", currentLang),
                style = MaterialTheme.typography.titleSmall
            )
            Bullet(AppTexts.get("guide_trouble_1", currentLang))
            Bullet(AppTexts.get("guide_trouble_2", currentLang))
            Bullet(AppTexts.get("guide_trouble_3", currentLang))
            Bullet(AppTexts.get("guide_trouble_4", currentLang))
        }
    }
}

@Composable
private fun Hint(title: String, desc: String, number: Int) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.Top
    ) {
        Box(
            modifier = Modifier
                .size(28.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.primary),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = "$number",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onPrimary
            )
        }
        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(text = title, style = MaterialTheme.typography.titleSmall)
            Text(
                text = desc,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun Bullet(text: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.Top
    ) {
        Text(text = "•", style = MaterialTheme.typography.bodyMedium)
        Text(
            text = text,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}
