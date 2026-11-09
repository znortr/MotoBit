package com.nos.motobitride.feature.home

import androidx.compose.foundation.Image
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.graphics.drawable.toBitmap
import com.nos.motobitride.core.apps.AppInfo

/**
 * Griglia delle app scelte, **due colonne**.
 *
 * Ogni app è un riquadro **quadrato con bordo** (come un pulsante) con icona
 * originale e nome centrati.
 */
@Composable
fun AppGrid(
    apps: List<AppInfo>,
    onAppClick: (AppInfo) -> Unit,
    onAddApp: () -> Unit,
    modifier: Modifier = Modifier,
    loading: Boolean = false,
    emptyText: String = "Nessuna app. Aggiungine una con AGGIUNGI APP.",
    addLabel: String = "AGGIUNGI APP"
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        // Evita il lampo di "Nessuna app" al primo frame, quando label e icone
        // delle app scelte stanno ancora arrivando (caricate su IO).
        if (loading && apps.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(72.dp),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator(
                    modifier = Modifier.size(24.dp),
                    strokeWidth = 2.dp
                )
            }
        } else if (apps.isEmpty()) {
            Text(
                text = emptyText,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 12.dp)
            )
        }

        apps.chunked(2).forEach { rowApps ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                rowApps.forEach { app ->
                    AppTile(
                        app = app,
                        modifier = Modifier.weight(1f),
                        onClick = { onAppClick(app) }
                    )
                }
                if (rowApps.size == 1) Spacer(Modifier.weight(1f))
            }
        }

        OutlinedButton(onClick = onAddApp, modifier = Modifier.fillMaxWidth()) {
            Text(addLabel)
        }
    }
}

@Composable
private fun AppTile(
    app: AppInfo,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    val image = remember(app.packageName) {
        runCatching { app.icon.toBitmap(96, 96).asImageBitmap() }.getOrNull()
    }

    Column(
        modifier = modifier
            .aspectRatio(1f)
            .clip(RoundedCornerShape(16.dp))
            .border(
                width = 1.dp,
                color = MaterialTheme.colorScheme.outline.copy(alpha = 0.55f),
                shape = RoundedCornerShape(16.dp)
            )
            .clickable(onClick = onClick)
            .padding(8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        if (image != null) {
            Image(
                bitmap = image,
                contentDescription = app.label,
                modifier = Modifier.size(40.dp)
            )
        }
        Spacer(Modifier.height(6.dp))
        Text(
            text = app.label,
            style = MaterialTheme.typography.labelSmall,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onSurface
        )
    }
}
