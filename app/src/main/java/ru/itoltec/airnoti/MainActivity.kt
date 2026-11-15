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
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
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
@Composable internal fun AirTheme(content: @Composable () -> Unit) {
    val dark = isSystemInDarkTheme()
    val context = LocalContext.current
    val scheme = if (Build.VERSION.SDK_INT >= 31) {
        if (dark) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
    } else if (dark) darkColorScheme(primary = Color(0xFFA9C7FF)) else lightColorScheme(primary = Color(0xFF315DA8))
    MaterialTheme(colorScheme = scheme, content = content)
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
    var query by rememberSaveable { mutableStateOf("") }
    var showDevices by remember { mutableStateOf(false) }
    var strengthMenu by remember { mutableStateOf(false) }
    var showHelp by remember { mutableStateOf(false) }
    var packagesChanged by remember { mutableIntStateOf(0) }
    var permissionChanged by remember { mutableIntStateOf(0) }
    var apps by remember { mutableStateOf<List<AppEntry>>(emptyList()) }
    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { permissionChanged++ }
    val hasBluetooth = remember(resumeCount, environment, permissionChanged) { app.bluetoothPermission() }
    val bluetoothOn = remember(resumeCount, environment, permissionChanged) { app.bluetoothReady() }
    val access = remember(resumeCount, listener) { context.getSystemService(NotificationManager::class.java).isNotificationListenerAccessGranted(ComponentName(context, AirNotificationListener::class.java)) }
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
    val status = when {
        !hasBluetooth -> R.string.permission_needed
        !bluetoothOn -> R.string.bt_off
        settings.address.isBlank() -> R.string.device_missing
        !settings.enabled -> R.string.disabled
        sending -> R.string.sending
        !access -> R.string.notification_access
        !listener -> R.string.listener_wait
        result == AirTransport.Result.UNAVAILABLE -> R.string.unavailable
        result == AirTransport.Result.FAILED -> R.string.failed
        else -> R.string.ready
    }
    val filtered = remember(apps, query) { apps.filter { it.title.contains(query, true) || it.info.packageName.contains(query, true) } }
    LazyColumn(Modifier.fillMaxSize().safeDrawingPadding(), contentPadding = PaddingValues(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item {
            Text(stringResource(R.string.app_name), style = MaterialTheme.typography.headlineLarge)

        }
        item {
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    ToggleRow(stringResource(R.string.enabled), settings.enabled, true, switch = true) { enabled ->
                        if (!enabled) app.disable() else app.enable()
                    }
                    Text(stringResource(status), style = MaterialTheme.typography.bodyLarge)
                }
            }
        }
        item {
            if (!hasBluetooth) Button(onClick = { if (Build.VERSION.SDK_INT >= 31) permission.launch(Manifest.permission.BLUETOOTH_CONNECT) }) { Text(stringResource(R.string.grant_bluetooth)) }
            if (!access) {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    TextButton(onClick = { context.startActivity(Intent(AndroidSettings.ACTION_NOTIFICATION_LISTENER_SETTINGS)) }, modifier = Modifier.weight(1f)) { Text(stringResource(R.string.notification_access)) }
                    TextButton(onClick = { showHelp = true }) { Text(stringResource(R.string.help)) }
                }
            }
        }
        item {
            val deviceName = remember(settings.address, hasBluetooth) {
                if (!hasBluetooth || settings.address.isBlank()) null else runCatching {
                    context.getSystemService(BluetoothManager::class.java).adapter.bondedDevices.firstOrNull { it.address == settings.address }?.name
                }.getOrNull()
            }
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                TextButton(onClick = { showDevices = true }, enabled = hasBluetooth && bluetoothOn, modifier = Modifier.weight(1f)) {
                    Text(deviceName ?: stringResource(R.string.choose_device))
                }
                TextButton(onClick = { app.send() }, enabled = settings.enabled && bluetoothOn && settings.address.isNotBlank() && !sending) {
                    Text(stringResource(R.string.test))
                }
            }
            result?.let { value -> Text(stringResource(when (value) {
                AirTransport.Result.VERIFIED -> R.string.verified
                AirTransport.Result.UNAVAILABLE -> R.string.unavailable
                AirTransport.Result.FAILED -> R.string.failed
                AirTransport.Result.UNKNOWN -> R.string.unknown
                AirTransport.Result.BUSY -> R.string.busy
                AirTransport.Result.HANDOFF_UNCONFIRMED -> R.string.handoff
            }), style = MaterialTheme.typography.bodyMedium) }
        }
        item {
            Box {
                TextButton(onClick = { strengthMenu = true }, modifier = Modifier.fillMaxWidth()) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                        Text(stringResource(R.string.strength), color = MaterialTheme.colorScheme.onSurface, modifier = Modifier.weight(1f))
                        Text(stringResource(when (settings.strength) { 2 -> R.string.strength_low; 3 -> R.string.strength_high; else -> R.string.strength_current }), modifier = Modifier.weight(1f))
                    }
                }
                DropdownMenu(expanded = strengthMenu, onDismissRequest = { strengthMenu = false }) {
                    listOf(0 to R.string.strength_current, 2 to R.string.strength_low, 3 to R.string.strength_high).forEach { (value, label) ->
                        DropdownMenuItem(text = { Text(stringResource(label)) }, onClick = {
                            scope.launch { app.preferences.strength(value) }; strengthMenu = false
                        })
                    }
                }
            }
            if (settings.strength != 0) Text(stringResource(R.string.strength_note), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        item {
            HorizontalDivider()
            ToggleRow(stringResource(R.string.all_apps), settings.all, true) { scope.launch { app.preferences.all(it) } }
            if (!settings.all) Text(if (settings.packages.isEmpty()) stringResource(R.string.nothing_selected) else stringResource(R.string.selected_count, settings.packages.size), style = MaterialTheme.typography.bodyMedium)
            OutlinedTextField(value = query, onValueChange = { query = it }, label = { Text(stringResource(R.string.search)) }, singleLine = true, modifier = Modifier.fillMaxWidth())
        }
        if (filtered.isEmpty()) item { Text(stringResource(R.string.no_matches)) }
        items(filtered, key = { it.info.packageName }) { entry ->
            val icon = remember(entry.info.packageName, packagesChanged) { runCatching { entry.info.loadIcon(context.packageManager).toBitmap(48, 48).asImageBitmap() }.getOrNull() }
            Row(Modifier.fillMaxWidth().heightIn(min = 64.dp).toggleable(settings.all || entry.info.packageName in settings.packages, enabled = !settings.all, role = Role.Checkbox) {
                scope.launch { app.preferences.select(entry.info.packageName, it) }
            }, verticalAlignment = Alignment.CenterVertically) {
                if (icon != null) Image(icon, contentDescription = null, modifier = Modifier.size(40.dp))
                Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
                    Text(entry.title, style = MaterialTheme.typography.bodyLarge)

                }
                Checkbox(checked = settings.all || entry.info.packageName in settings.packages, enabled = !settings.all, onCheckedChange = null)
            }
        }

    }
    if (showHelp) AlertDialog(onDismissRequest = { showHelp = false }, title = { Text(stringResource(R.string.notification_access)) },
        text = { Text(stringResource(R.string.restricted_note)) },
        confirmButton = { TextButton(onClick = { context.startActivity(Intent(AndroidSettings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:${context.packageName}"))); showHelp = false }) { Text(stringResource(R.string.app_info)) } },
        dismissButton = { TextButton(onClick = { showHelp = false }) { Text(stringResource(R.string.close)) } })
    if (showDevices) {
        val devices = remember(environment, permissionChanged) { runCatching { context.getSystemService(BluetoothManager::class.java).adapter.bondedDevices.toList().sortedBy { it.name ?: it.address } }.getOrDefault(emptyList()) }
        AlertDialog(onDismissRequest = { showDevices = false }, title = { Text(stringResource(R.string.choose_device)) }, text = {
            Column {
                Text(stringResource(R.string.device_hint))
                if (devices.isEmpty()) Text(stringResource(R.string.no_devices))
                LazyColumn { items(devices, key = { it.address }) { device ->
                    TextButton(onClick = { app.cancel(); scope.launch { app.preferences.device(device.address) }; showDevices = false }) {
                        Text("${device.name ?: "Bluetooth"}\n${device.address}")
                    }
                } }
            }
        }, confirmButton = { TextButton(onClick = { showDevices = false }) { Text(stringResource(R.string.close)) } })
    }
}
@Composable private fun ToggleRow(label: String, checked: Boolean, enabled: Boolean, switch: Boolean = false, change: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth().heightIn(min = 56.dp).toggleable(checked, enabled = enabled, role = if (switch) Role.Switch else Role.Checkbox, onValueChange = change), verticalAlignment = Alignment.CenterVertically) {
        Text(label, Modifier.weight(1f).padding(end = 12.dp), style = MaterialTheme.typography.titleMedium)
        if (switch) Switch(checked, onCheckedChange = null, enabled = enabled) else Checkbox(checked, onCheckedChange = null, enabled = enabled)
    }
}
