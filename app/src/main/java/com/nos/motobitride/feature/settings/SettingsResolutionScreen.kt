package com.nos.motobitride.feature.settings

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.nos.motobitride.core.display.DisplayBridge
import com.nos.motobitride.core.display.DisplayMaster
import com.nos.motobitride.core.display.DisplayModeController
import com.nos.motobitride.core.display.DisplaySetup
import com.nos.motobitride.core.display.ProximityMode
import com.nos.motobitride.core.settings.AppSettings
import com.nos.motobitride.core.settings.DisplayMode
import com.nos.motobitride.ui.i18n.AppTexts
import com.nos.motobitride.ui.shizuku.actionKey
import com.nos.motobitride.ui.shizuku.labelKey
import com.nos.motobitride.ui.shizuku.performAction
import com.nos.motobitride.ui.shizuku.rememberShizukuStatus
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.roundToInt

/** Altezza reale della barra comandi del telefono, in pixel. */
private fun navBarHeightPx(ctx: android.content.Context): Int {
    val res = ctx.resources
    val id = res.getIdentifier("navigation_bar_height", "dimen", "android")
    return if (id > 0) {
        runCatching { res.getDimensionPixelSize(id) }.getOrDefault(0)
    } else {
        (48 * res.displayMetrics.density).toInt()
    }
}

/**
 * Pagina "Risoluzione".
 *
 * Ogni controllo fa **una cosa sola**:
 *  - percentuale, densità e orientamento sono parametri del piano → si applicano
 *    con «Applica» (devono essere coerenti fra loro);
 *  - oscuramento e barra comandi agiscono subito e **non toccano altro**;
 *  - la modalità sceglie chi comanda il display (in tasca / sempre acceso /
 *    Volume +) e le tre si escludono.
 *
 * I numeri mostrati sono quelli che verranno applicati davvero: se il piano non
 * è calcolabile in sicurezza, i comandi si disabilitano e non si tocca niente.
 */
@Composable
fun SettingsResolutionScreen(
    onBack: () -> Unit = {},
    onOpenShizukuGuide: () -> Unit = {}
) {
    val currentLang by AppSettings.language.collectAsState()
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()

    val shizukuEnabled by AppSettings.shizukuEnabled.collectAsState()
    val shizukuStatus = rememberShizukuStatus(shizukuEnabled)

    val scale by AppSettings.displayScale.collectAsState()
    val densityOn by AppSettings.displayDensityScale.collectAsState()
    val rotation by AppSettings.displayRotation.collectAsState()
    val hideNavBar by AppSettings.displayHideNavBar.collectAsState()
    val dimPercent by AppSettings.displayDimPercent.collectAsState()
    val overrideActive by AppSettings.displayOverride.collectAsState()
    val displayMode by AppSettings.displayMode.collectAsState()
    val bubbleDim by AppSettings.displayBubbleDim.collectAsState()
    val cropPx by AppSettings.displayCropPx.collectAsState()
    val cropOn = cropPx > 0

    var prepared by remember { mutableStateOf(DisplaySetup.isReady(ctx)) }
    var busy by remember { mutableStateOf(false) }
    var failure by remember { mutableStateOf(false) }
    val pocketSupported = remember { ProximityMode.isSupported(ctx) }

    val plan by produceState<DisplayMaster.Plan?>(
        initialValue = null, prepared, scale, densityOn
    ) {
        value = if (!prepared) null else withContext(Dispatchers.IO) {
            runCatching { DisplayMaster.plan(ctx) }.getOrNull()
        }
    }

    fun act(block: () -> DisplayMaster.Result) {
        if (busy) return
        busy = true
        scope.launch {
            val r = withContext(Dispatchers.IO) {
                runCatching(block).getOrElse { DisplayMaster.Result.FAILED }
            }
            failure = r == DisplayMaster.Result.UNSAFE ||
                    r == DisplayMaster.Result.FAILED ||
                    r == DisplayMaster.Result.NOT_READY
            prepared = DisplaySetup.isReady(ctx)
            busy = false
        }
    }

    val canAct = prepared && plan != null && !busy

    SettingsSubPage(
        title = AppTexts.get("settings_cat_resolution_title", currentLang),
        subtitle = AppTexts.get("settings_cat_resolution_sub", currentLang),
        backLabel = AppTexts.get("common_back", currentLang),
        onBack = onBack
    ) {
        // ---------------- Shizuku + preparazione una tantum ----------------
        Row(
            modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(Modifier.weight(1f)) {
                Text(
                    AppTexts.get("shizuku_switch_title", currentLang),
                    style = MaterialTheme.typography.bodyLarge
                )
                Text(
                    AppTexts.get("shizuku_switch_desc", currentLang),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Switch(
                checked = shizukuEnabled,
                onCheckedChange = {
                    AppSettings.setShizukuEnabled(it)
                    DisplayBridge.syncShizukuAllowed()
                }
            )
        }

        Row(
            modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                AppTexts.get(shizukuStatus.labelKey(), currentLang),
                style = MaterialTheme.typography.bodyMedium,
                color = if (shizukuStatus.isReady) MaterialTheme.colorScheme.primary
                else MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.weight(1f)
            )
            shizukuStatus.actionKey()?.let { key ->
                TextButton(onClick = { shizukuStatus.performAction() }) {
                    Text(AppTexts.get(key, currentLang))
                }
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                AppTexts.get(if (prepared) "setup_ready" else "setup_needed", currentLang),
                style = MaterialTheme.typography.bodyMedium,
                color = if (prepared) MaterialTheme.colorScheme.primary
                else MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.weight(1f)
            )
            if (!prepared) {
                TextButton(
                    onClick = {
                        if (!shizukuStatus.isReady) {
                            shizukuStatus.performAction()
                            return@TextButton
                        }
                        if (busy) return@TextButton
                        busy = true
                        scope.launch {
                            prepared = withContext(Dispatchers.IO) { DisplaySetup.run(ctx) }
                            busy = false
                        }
                    }
                ) {
                    Text(
                        AppTexts.get(if (busy) "setup_working" else "setup_action", currentLang)
                    )
                }
            }
        }

        TextButton(onClick = onOpenShizukuGuide, modifier = Modifier.fillMaxWidth()) {
            Text(AppTexts.get("guide_link", currentLang))
        }

        HorizontalDivider()

        if (!prepared) {
            Text(
                AppTexts.get("res_not_ready", currentLang),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            return@SettingsSubPage
        }

        // ---------------- Modalità: tre, si escludono ----------------
        Text(AppTexts.get("mode_title", currentLang), style = MaterialTheme.typography.titleSmall)
        listOf(
            DisplayMode.POCKET to "mode_pocket",
            DisplayMode.ALWAYS_ON to "mode_always_on"
        ).forEach { (mode, key) ->
            val enabled = if (mode == DisplayMode.POCKET) pocketSupported else true
            OutlinedButton(
                onClick = { DisplayModeController.select(ctx, mode) },
                enabled = enabled && !busy,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    AppTexts.get(key, currentLang),
                    color = if (displayMode == mode) MaterialTheme.colorScheme.primary
                    else MaterialTheme.colorScheme.onSurface
                )
            }
        }
        if (!pocketSupported) {
            Text(
                AppTexts.get("mode_pocket_unsupported", currentLang),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        // La bolla, quando cambia la risoluzione, oscura anche?
        if (displayMode == DisplayMode.ALWAYS_ON) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    AppTexts.get("bubble_dim", currentLang),
                    style = MaterialTheme.typography.bodyLarge,
                    modifier = Modifier.weight(1f)
                )
                Switch(
                    checked = bubbleDim,
                    enabled = !busy,
                    onCheckedChange = { AppSettings.setDisplayBubbleDim(it) }
                )
            }
        }

        HorizontalDivider()

        // ---------------- Percentuale del formato TFT ----------------
        Text(
            "${AppTexts.get("res_percent", currentLang)}: $scale%",
            style = MaterialTheme.typography.titleSmall
        )
        Slider(
            value = scale.toFloat(),
            onValueChange = { AppSettings.setDisplayScale(it.roundToInt()) },
            enabled = !busy,
            valueRange = 100f..200f,
            steps = 19
        )

        val p = plan
        if (p == null) {
            Text(
                AppTexts.get("res_unsafe", currentLang),
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.error
            )
        } else {
            Text(
                "${p.width} × ${p.height}",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.primary
            )
            Text(
                if (p.density > 0) "${p.dpWidth.roundToInt()} dp" else "densità non modificata",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        if (failure) {
            Text(
                AppTexts.get("res_unsafe", currentLang),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error
            )
        }

        // ---------------- Densità (opzionale) ----------------
        Row(
            modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                AppTexts.get("res_density", currentLang),
                style = MaterialTheme.typography.bodyLarge,
                modifier = Modifier.weight(1f)
            )
            Switch(
                checked = densityOn,
                enabled = !busy,
                onCheckedChange = { AppSettings.setDisplayDensityScale(it) }
            )
        }

        // ---------------- Orientamento: un solo switch ----------------
        Row(
            modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                AppTexts.get("res_orient", currentLang) + ": " +
                        AppTexts.get(
                            if (rotation == "landscape") "res_rot_landscape" else "res_rot_portrait",
                            currentLang
                        ),
                style = MaterialTheme.typography.bodyLarge,
                modifier = Modifier.weight(1f)
            )
            Switch(
                checked = rotation == "landscape",
                enabled = !busy,
                onCheckedChange = {
                    val mode = if (it) "landscape" else "portrait"
                    scope.launch {
                        busy = true
                        withContext(Dispatchers.IO) { DisplayMaster.setOrientation(ctx, mode) }
                        busy = false
                    }
                }
            )
        }

        // ---------------- Barra comandi ----------------
        Row(
            modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                AppTexts.get("res_navbar", currentLang),
                style = MaterialTheme.typography.bodyLarge,
                modifier = Modifier.weight(1f)
            )
            Switch(
                checked = hideNavBar,
                enabled = !busy,
                onCheckedChange = {
                    scope.launch {
                        busy = true
                        withContext(Dispatchers.IO) { DisplayMaster.setNavBar(ctx, it) }
                        busy = false
                    }
                }
            )
        }

        // NOTA: il "taglio del bordo inferiore" è stato rimosso — vedi TODO §16.

        HorizontalDivider()

        // ---------------- Oscuramento: 0–50%, solo overlay ----------------
        Text(
            "${AppTexts.get("res_dim", currentLang)}: $dimPercent%",
            style = MaterialTheme.typography.titleSmall
        )
        Slider(
            value = dimPercent.toFloat(),
            onValueChange = { AppSettings.setDisplayDimPercent(it.roundToInt()) },
            onValueChangeFinished = {
                val value = AppSettings.displayDimPercent.value
                scope.launch {
                    withContext(Dispatchers.IO) { DisplayMaster.applyDimOnly(ctx, value) }
                }
            },
            enabled = !busy,
            valueRange = 0f..50f,
            steps = 9
        )

        HorizontalDivider()

        // ---------------- Azioni ----------------
        // Niente pulsante «Applica»: la risoluzione si applica **con la bolla**
        // (modalità manubrio) o **coprendo il sensore** (modalità tasca).
        // Qui si sceglie solo *come* deve diventare.
        Text(
            AppTexts.get("res_apply_hint", currentLang),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        OutlinedButton(
            onClick = { act { DisplayMaster.restoreAll(ctx) } },
            enabled = !busy,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(AppTexts.get("res_restore", currentLang))
        }

        if (overrideActive) {
            Text(
                AppTexts.get("res_active", currentLang).format(DisplayBridge.current.lastVia),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}
