package com.instantgameclipper.ui

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.media.projection.MediaProjectionManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.instantgameclipper.service.ClipperForegroundService
import com.instantgameclipper.service.ServiceState

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    Phase1Screen()
                }
            }
        }
    }
}

@Composable
private fun Phase1Screen() {
    val context = LocalContext.current
    val running by ServiceState.isRunning.collectAsState()

    var notificationGranted by remember {
        mutableStateOf(NotificationManagerCompat.from(context).areNotificationsEnabled())
    }
    var overlayGranted by remember {
        mutableStateOf(Settings.canDrawOverlays(context))
    }
    var projectionGranted by remember { mutableStateOf(false) }

    val notificationLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) {
        notificationGranted = NotificationManagerCompat.from(context).areNotificationsEnabled()
    }

    val overlayLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult(),
    ) {
        overlayGranted = Settings.canDrawOverlays(context)
    }

    val projectionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult(),
    ) { result ->
        val data = result.data
        if (result.resultCode == Activity.RESULT_OK && data != null) {
            projectionGranted = true
            ClipperForegroundService.start(context, result.resultCode, data)
        } else {
            projectionGranted = false
        }
    }

    fun refresh() {
        notificationGranted = NotificationManagerCompat.from(context).areNotificationsEnabled()
        overlayGranted = Settings.canDrawOverlays(context)
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text("Phase 1", style = MaterialTheme.typography.headlineSmall)
        Text("権限を揃えてから Foreground Service を起動する")

        PermissionRow(
            title = "通知 (POST_NOTIFICATIONS)",
            granted = notificationGranted,
            buttonLabel = "許可する",
        ) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                notificationLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            } else {
                notificationGranted = true
            }
        }

        PermissionRow(
            title = "オーバーレイ (SYSTEM_ALERT_WINDOW)",
            granted = overlayGranted,
            buttonLabel = "設定を開く",
        ) {
            val intent = Intent(
                Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                Uri.parse("package:${context.packageName}"),
            )
            overlayLauncher.launch(intent)
        }

        PermissionRow(
            title = "画面収録 (MediaProjection)",
            granted = projectionGranted || running,
            buttonLabel = "許可する",
        ) {
            val manager = context.getSystemService(MediaProjectionManager::class.java)
            projectionLauncher.launch(manager.createScreenCaptureIntent())
        }

        val ready = notificationGranted && overlayGranted
        Text(
            if (running) "サービス: 稼働中"
            else if (ready) "サービス: 停止中（画面収録の許可で起動）"
            else "サービス: 先に通知とオーバーレイを許可",
        )

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Button(
                onClick = {
                    refresh()
                    if (!ready) return@Button
                    val manager = context.getSystemService(MediaProjectionManager::class.java)
                    projectionLauncher.launch(manager.createScreenCaptureIntent())
                },
                enabled = !running,
            ) {
                Text("開始")
            }
            OutlinedButton(
                onClick = { ClipperForegroundService.stop(context) },
                enabled = running,
            ) {
                Text("停止")
            }
        }

        OutlinedButton(onClick = { refresh() }) {
            Text("状態を再読み込み")
        }
    }
}

@Composable
private fun PermissionRow(
    title: String,
    granted: Boolean,
    buttonLabel: String,
    onClick: () -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(title, style = MaterialTheme.typography.titleMedium)
        Text(if (granted) "許可済み" else "未許可")
        if (!granted) {
            Button(onClick = onClick) { Text(buttonLabel) }
        }
    }
}
