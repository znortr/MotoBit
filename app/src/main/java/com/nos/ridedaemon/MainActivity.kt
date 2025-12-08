package com.nos.ridedaemon

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.pm.PackageManager
import android.net.wifi.WifiManager
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.nos.ridedaemon.hud.HudStreamController

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContent {
            val ctx = LocalContext.current.applicationContext

            val hudStreamController = remember {
                HudStreamController(ctx)
            }

            // make sure we stop streaming if this composable leaves the composition
            DisposableEffect(Unit) {
                onDispose {
                    hudStreamController.stop()
                }
            }

            HudAppScreen(
                hudStreamController = hudStreamController
            )
        }
    }

    @OptIn(ExperimentalMaterial3Api::class)
    @Composable
    fun HudAppScreen(
        hudStreamController: HudStreamController
    ) {
        val ctx = LocalContext.current

        var isStreaming by remember { mutableStateOf(false) }

        fun Context.getActivity(): ComponentActivity? = when (this) {
            is ComponentActivity -> this
            is ContextWrapper -> baseContext.getActivity()
            else -> null
        }

        fun streamSessionStart() {
            try {
                hudStreamController.start()
                isStreaming = true
            } catch (e: IllegalStateException) {
                Log.e("HudStream", "start() called in invalid state", e)
                Toast.makeText(ctx, e.message ?: "Unable to start HUD stream", Toast.LENGTH_SHORT).show()
            }
        }

        // Simple material layout
        Scaffold(
            topBar = {
                TopAppBar(
                    title = { Text("Moto HUD Dev") }
                )
            }
        ) { innerPadding ->
            Column(
                modifier = Modifier
                    .padding(innerPadding)
                    .fillMaxSize()
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp, Alignment.Top),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {

                Text(
                    text = "Streaming status: ${if (isStreaming) "ON" else "OFF"}",
                    style = MaterialTheme.typography.titleMedium
                )

                Row(
                    horizontalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    Button(
                        onClick = {
                            ctx.getActivity()?.let { act ->
                                when {
                                    ContextCompat.checkSelfPermission(
                                        ctx, Manifest.permission.NEARBY_WIFI_DEVICES
                                    ) == PackageManager.PERMISSION_GRANTED -> {
                                        streamSessionStart()
                                    }
                                    ActivityCompat.shouldShowRequestPermissionRationale(
                                        act, Manifest.permission.NEARBY_WIFI_DEVICES
                                    ) -> {
                                        Toast.makeText(ctx, "nearby wifi devices needed to find your bike", Toast.LENGTH_LONG).show()
                                    }
                                    else -> {
                                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                                            ActivityCompat.requestPermissions(
                                                act,
                                                arrayOf(Manifest.permission.NEARBY_WIFI_DEVICES),
                                                0
                                            )
                                        }
                                    }
                                }
                            }
                        },
                        enabled = !isStreaming
                    ) {
                        Text("Start")
                    }

                    OutlinedButton(
                        onClick = {
                            hudStreamController.stop()
                            isStreaming = false
                        },
                        enabled = isStreaming
                    ) {
                        Text("Stop")
                    }
                }
            }
        }
    }
}