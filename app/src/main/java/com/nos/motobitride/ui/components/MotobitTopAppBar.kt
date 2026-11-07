package com.nos.motobitride.ui.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import com.nos.motobitride.R

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MotobitTopAppBar(
    onNavigateHome: () -> Unit = {},
    onNavigateGarage: () -> Unit = {},
    onNavigateSettings: () -> Unit = {}
) {
    val ctx = LocalContext.current
    val versionName = remember(ctx) {
        runCatching {
            ctx.packageManager.getPackageInfo(ctx.packageName, 0).versionName
        }.getOrNull() ?: "1.1.5"
    }

    TopAppBar(
        title = {
            Column {
                val motoColor = MaterialTheme.colorScheme.onBackground
                val bitColor = Color(0xFFFF3B30)
                val titleText = remember(motoColor) {
                    buildAnnotatedString {
                        withStyle(style = SpanStyle(color = motoColor, fontWeight = FontWeight.Bold)) {
                            append("Moto")
                        }
                        withStyle(style = SpanStyle(color = bitColor, fontWeight = FontWeight.Bold)) {
                            append("Bit")
                        }
                    }
                }
                Text(
                    text = titleText,
                    style = MaterialTheme.typography.titleLarge
                )
                Text(
                    text = "v$versionName",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f)
                )
            }
        },
        actions = {
            Row {
                // 1. Home Icon
                IconButton(onClick = onNavigateHome) {
                    Icon(
                        imageVector = Icons.Default.Home,
                        contentDescription = "Home"
                    )
                }
                // 2. Moto / Garage Icon (vettoriale locale: niente
                // material-icons-extended, che pesava ~50 MB di dex)
                IconButton(onClick = onNavigateGarage) {
                    Icon(
                        painter = painterResource(R.drawable.ic_moto),
                        contentDescription = "Garage"
                    )
                }
                // 3. Cog / Settings Icon
                IconButton(onClick = onNavigateSettings) {
                    Icon(
                        imageVector = Icons.Default.Settings,
                        contentDescription = "Settings"
                    )
                }
            }
        }
    )
}
