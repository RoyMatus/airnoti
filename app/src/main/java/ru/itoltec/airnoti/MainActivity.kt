package ru.itoltec.airnoti

import android.Manifest
import android.annotation.SuppressLint
import android.app.NotificationManager
import android.bluetooth.BluetoothManager
import android.content.*
import android.content.pm.ApplicationInfo
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings as AndroidSettings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.graphics.drawable.toBitmap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class MainActivity : ComponentActivity() {
    private val resumeCount = mutableIntStateOf(0)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent { AirTheme { Surface { AirScreen(application as AirNotiApp, resumeCount.intValue) } } }
    }
    override fun onResume() { super.onResume(); resumeCount.intValue++ }
}
private data class AppEntry(val info: ApplicationInfo, val title: String)
@SuppressLint("MissingPermission", "QueryPermissionsNeeded")
@Composable internal fun AirScreen(app: AirNotiApp, resumeCount: Int) {
    val context = LocalContext.current
    val settings by app.settings.collectAsState()
    val sending by app.sending.collectAsState()
    val result by app.result.collectAsState()
    val listener by app.listenerConnected.collectAsState()
    val environment by app.environment.collectAsState()
    val scope = rememberCoroutineScope()
    var packagesChanged by remember { mutableIntStateOf(0) }
    var permissionChanged by remember { mutableIntStateOf(0) }
    var apps by remember { mutableStateOf<List<AppEntry>>(emptyList()) }
    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        permissionChanged++
        if (granted) {
            app.refreshDevice()
            if (app.settings.value.enabled && !context.getSystemService(NotificationManager::class.java).isNotificationListenerAccessGranted(ComponentName(context, AirNotificationListener::class.java))) {
                context.startActivity(Intent(AndroidSettings.ACTION_NOTIFICATION_LISTENER_SETTINGS))
            }
        }
    }
    val hasBluetooth = remember(resumeCount, environment, permissionChanged) { app.bluetoothPermission() }
    val bluetoothOn = remember(resumeCount, environment, permissionChanged) { app.bluetoothReady() }
    val access = remember(resumeCount, listener) { context.getSystemService(NotificationManager::class.java).isNotificationListenerAccessGranted(ComponentName(context, AirNotificationListener::class.java)) }
    LaunchedEffect(resumeCount, hasBluetooth, environment) { app.refreshDevice() }
    DisposableEffect(Unit) {
        val receiver = object : BroadcastReceiver() { override fun onReceive(c: Context, i: Intent) { packagesChanged++ } }
        val filter = IntentFilter().apply { addAction(Intent.ACTION_PACKAGE_ADDED); addAction(Intent.ACTION_PACKAGE_REMOVED); addAction(Intent.ACTION_PACKAGE_CHANGED); addDataScheme("package") }
        context.registerReceiver(receiver, filter)
        onDispose { context.unregisterReceiver(receiver) }
    }
    LaunchedEffect(packagesChanged) {
        apps = withContext(Dispatchers.IO) {
            context.packageManager.queryIntentActivities(Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER), 0)
                .map { it.activityInfo.applicationInfo }.distinctBy { it.packageName }.filter { it.packageName != context.packageName && it.enabled }
                .map { AppEntry(it, it.loadLabel(context.packageManager).toString()) }.sortedBy { it.title.lowercase() }
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
            apps.map { UiApp(it.info.packageName, it.title) }, devices),
        actions = AirActions(
            enabled = { if (it) app.enable() else app.disable() },
            send = { app.send() },
            device = { app.cancel(); scope.launch { app.preferences.device(it) } },
            all = { scope.launch { app.preferences.all(it) } },
            select = { pkg, selected -> scope.launch { app.preferences.select(pkg, selected) } },
            strength = { scope.launch { app.preferences.strength(it) } },
            bluetooth = {
                if (!hasBluetooth && Build.VERSION.SDK_INT >= 31) permission.launch(Manifest.permission.BLUETOOTH_CONNECT)
                else if (!bluetoothOn) context.startActivity(Intent(android.bluetooth.BluetoothAdapter.ACTION_REQUEST_ENABLE))
            },
            notifications = { context.startActivity(Intent(AndroidSettings.ACTION_NOTIFICATION_LISTENER_SETTINGS)) },
            appInfo = { context.startActivity(Intent(AndroidSettings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:${context.packageName}"))) }
        ),
        appIcon = { pkg ->
            val icon = remember(pkg, packagesChanged) { runCatching {
                context.packageManager.getApplicationIcon(pkg).toBitmap(88, 88).asImageBitmap()
            }.getOrNull() }
            if (icon != null) Image(icon, null, Modifier.size(44.dp))
        }
    )
}