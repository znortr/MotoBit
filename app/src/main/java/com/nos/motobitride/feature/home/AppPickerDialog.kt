package com.nos.motobitride.feature.home

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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

private const val PAGE_SIZE = 9

/**
 * Selettore delle app: griglia **3×3** (9 per pagina), swipe orizzontale e
 * indicatore di pagina. Tap = seleziona/deseleziona (multi-selezione).
 *
 * La selezione è applicata subito (la Home si aggiorna sotto): la chiusura
 * avviene con "Fatto".
 */
@Composable
fun AppPickerDialog(
    allApps: List<AppInfo>,
    selectedPackages: Set<String>,
    onToggle: (String) -> Unit,
    onDismiss: () -> Unit,
    title: String = "Aggiungi app",
    doneLabel: String = "Fatto",
    emptyLabel: String = "Nessuna app avviabile trovata."
) {
    val pages = remember(allApps) {
        if (allApps.isEmpty()) emptyList() else allApps.chunked(PAGE_SIZE)
    }
    val pagerState = rememberPagerState(pageCount = { pages.size.coerceAtLeast(1) })

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            if (pages.isEmpty()) {
                Text(emptyLabel, style = MaterialTheme.typography.bodyMedium)
            } else {
                Column(Modifier.fillMaxWidth()) {
                    HorizontalPager(
                        state = pagerState,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(330.dp)
                    ) { pageIndex ->
                        val pageApps = pages.getOrNull(pageIndex).orEmpty()
                        Column(
                            modifier = Modifier.fillMaxSize(),
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            pageApps.chunked(3).forEach { rowApps ->
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                                ) {
                                    rowApps.forEach { app ->
                                        PickerTile(
                                            app = app,
                                            selected = selectedPackages.contains(app.packageName),
                                            modifier = Modifier.weight(1f),
                                            onClick = { onToggle(app.packageName) }
                                        )
                                    }
                                    repeat(3 - rowApps.size) { Spacer(Modifier.weight(1f)) }
                                }
                            }
                        }
                    }

                    // Indicatore di pagina
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 10.dp),
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        repeat(pages.size) { index ->
                            val current = index == pagerState.currentPage
                            Box(
                                modifier = Modifier
                                    .padding(horizontal = 3.dp)
                                    .size(if (current) 9.dp else 6.dp)
                                    .clip(CircleShape)
                                    .background(
                                        if (current) MaterialTheme.colorScheme.primary
                                        else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f)
                                    )
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text(doneLabel) }
        }
    )
}

@Composable
private fun PickerTile(
    app: AppInfo,
    selected: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    val image = remember(app.packageName) {
        runCatching { app.icon.toBitmap(96, 96).asImageBitmap() }.getOrNull()
    }
    val borderColor = if (selected) MaterialTheme.colorScheme.primary
    else MaterialTheme.colorScheme.outline.copy(alpha = 0.45f)

    Column(
        modifier = modifier
            .aspectRatio(1f)
            .clip(RoundedCornerShape(14.dp))
            .border(
                width = if (selected) 2.dp else 1.dp,
                color = borderColor,
                shape = RoundedCornerShape(14.dp)
            )
            .background(
                if (selected) MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)
                else MaterialTheme.colorScheme.surface.copy(alpha = 0f)
            )
            .clickable(onClick = onClick)
            .padding(6.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        if (image != null) {
            Image(
                bitmap = image,
                contentDescription = app.label,
                modifier = Modifier.size(32.dp)
            )
        }
        Spacer(Modifier.height(4.dp))
        Text(
            text = app.label,
            style = MaterialTheme.typography.labelSmall,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center
        )
    }
}
