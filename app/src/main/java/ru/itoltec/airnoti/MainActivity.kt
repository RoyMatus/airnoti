package ru.itoltec.airnoti

import android.Manifest
import android.annotation.SuppressLint
import android.app.NotificationManager
import android.bluetooth.BluetoothManager
import android.content.*
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings as AndroidSettings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.enableEdgeToEdge
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalContext
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    private val resumeCount = mutableIntStateOf(0)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent { AirTheme { Surface { AirScreen(application as AirNotiApp, resumeCount.intValue) } } }
    }
    override fun onResume() { super.onResume(); resumeCount.intValue++ }
}
@SuppressLint("MissingPermission")
@Composable internal fun AirScreen(app: AirNotiApp, resumeCount: Int) {
    val context = LocalContext.current
    val settings by app.settings.collectAsState()
    val sending by app.sending.collectAsState()
    val result by app.result.collectAsState()
    val listener by app.listenerConnected.collectAsState()
    val environment by app.environment.collectAsState()
    val scope = rememberCoroutineScope()
    var permissionChanged by remember { mutableIntStateOf(0) }
    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        permissionChanged++
        if (granted) {
            app.refreshDevice()
            if (app.settings.value.enabled && !context.getSystemService(NotificationManager::class.java).isNotificationListenerAccessGranted(ComponentName(context, AirNotificationListener::class.java))) {
                openNotificationAccess(context)
            }
        }
    }
    val hasBluetooth = remember(resumeCount, environment, permissionChanged) { app.bluetoothPermission() }
    val bluetoothOn = remember(resumeCount, environment, permissionChanged) { app.bluetoothReady() }
    val access = remember(resumeCount, listener) { context.getSystemService(NotificationManager::class.java).isNotificationListenerAccessGranted(ComponentName(context, AirNotificationListener::class.java)) }
    LaunchedEffect(resumeCount, hasBluetooth, environment) { app.refreshDevice() }
    LaunchedEffect(resumeCount, access) {
        if (access && !app.listenerConnected.value) {
            android.service.notification.NotificationListenerService.requestRebind(ComponentName(context, AirNotificationListener::class.java))
        }
    }
    val devices = remember(resumeCount, environment, permissionChanged, hasBluetooth, bluetoothOn) {
        if (!hasBluetooth || !bluetoothOn) emptyList() else runCatching {
            context.getSystemService(BluetoothManager::class.java).adapter.bondedDevices
                .map { BandEntry(it.address, it.name ?: "Bluetooth") }.sortedBy { it.name }
        }.getOrDefault(emptyList())
    }
    AirInterface(
        state = AirUiState(settings, sending, result, hasBluetooth, bluetoothOn, access, listener,
            devices = devices),
        actions = AirActions(
            enabled = { if (it) app.enable() else app.disable() },
            send = { app.send() },
            device = { app.cancel(); scope.launch { app.preferences.device(it) } },
            strength = { scope.launch { app.preferences.strength(it) } },
            bluetooth = {
                if (!hasBluetooth && Build.VERSION.SDK_INT >= 31) permission.launch(Manifest.permission.BLUETOOTH_CONNECT)
                else if (!bluetoothOn) context.startActivity(Intent(android.bluetooth.BluetoothAdapter.ACTION_REQUEST_ENABLE))
            },
            notifications = {
                if (access && !listener) android.service.notification.NotificationListenerService.requestRebind(ComponentName(context, AirNotificationListener::class.java))
                else openNotificationAccess(context)
            },
            appInfo = { context.startActivity(Intent(AndroidSettings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:${context.packageName}"))) }
        ),
    )
}

/** Open this app's permission, rather than asking people to find it in a system-wide list. */
private fun openNotificationAccess(context: Context) {
    val detail = Intent(AndroidSettings.ACTION_NOTIFICATION_LISTENER_DETAIL_SETTINGS).putExtra(
        AndroidSettings.EXTRA_NOTIFICATION_LISTENER_COMPONENT_NAME,
        ComponentName(context, AirNotificationListener::class.java).flattenToString()
    )
    try { context.startActivity(detail) }
    catch (_: ActivityNotFoundException) {
        try { context.startActivity(Intent(AndroidSettings.ACTION_NOTIFICATION_LISTENER_SETTINGS)) }
        catch (_: ActivityNotFoundException) { context.startActivity(Intent(AndroidSettings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:${context.packageName}"))) }
    }
}
