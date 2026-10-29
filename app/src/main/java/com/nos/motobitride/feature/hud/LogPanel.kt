package com.nos.motobitride.feature.hud

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.nos.motobitride.core.log.AppLog
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Finestra di log in-app: mostra gli eventi del protocollo HUD,
 * con copia negli appunti e salvataggio su file .txt.
 */
@Composable
fun LogPanel(
    modifier: Modifier = Modifier,
    onCopied: (Int) -> Unit = {},
    onSaved: (String) -> Unit = {},
    onError: (String) -> Unit = {}
) {
    val context = LocalContext.current
    val version by AppLog.version.collectAsState()
    val entries = remember(version) { AppLog.entries() }
    val listState = rememberLazyListState()

    LaunchedEffect(entries.size) {
        if (entries.isNotEmpty()) {
            listState.scrollToItem(entries.size - 1)
        }
    }

    Column(modifier = modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = "Log (${entries.size})",
                style = MaterialTheme.typography.labelLarge
            )

            Row {
                TextButton(onClick = {
                    try {
                        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE)
                                as ClipboardManager
                        clipboard.setPrimaryClip(
                            ClipData.newPlainText("MotoBit log", AppLog.asText())
                        )
                        onCopied(entries.size)
                    } catch (t: Throwable) {
                        onError("Copia fallita: ${t.message}")
                    }
                }) {
                    Text("Copia")
                }

                TextButton(onClick = {
                    try {
                        val dir = File(context.getExternalFilesDir(null), "logs")
                        if (!dir.exists()) dir.mkdirs()
                        val name = "motobit-log-" +
                                SimpleDateFormat("yyyyMMdd-HHmmss", Locale.US)
                                    .format(Date()) + ".txt"
                        val file = File(dir, name)
                        file.writeText(AppLog.asText())
                        onSaved(file.absolutePath)
                    } catch (t: Throwable) {
                        onError("Salvataggio fallito: ${t.message}")
                    }
                }) {
                    Text("Salva .txt")
                }

                TextButton(onClick = { AppLog.clear() }) {
                    Text("Pulisci")
                }
            }
        }

        LazyColumn(
            state = listState,
            modifier = Modifier
                .fillMaxWidth()
                .height(170.dp)
                .border(1.dp, Color(0xFF3A3A3A), RoundedCornerShape(6.dp))
                .background(Color(0xFF111111), RoundedCornerShape(6.dp))
                .padding(6.dp)
        ) {
            items(entries) { entry ->
                Text(
                    text = entry.format(),
                    fontFamily = FontFamily.Monospace,
                    fontSize = 9.sp,
                    lineHeight = 11.sp,
                    color = when (entry.level) {
                        "E" -> Color(0xFFFF6B6B)
                        "W" -> Color(0xFFFFC857)
                        else -> Color(0xFFB8E986)
                    }
                )
            }
        }
    }
}
