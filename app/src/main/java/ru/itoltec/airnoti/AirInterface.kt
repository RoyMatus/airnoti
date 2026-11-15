package ru.itoltec.airnoti

import android.os.Build
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.*
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

internal data class BandEntry(val address: String, val name: String)
internal data class AirUiState(
    val settings: Settings = Settings(), val sending: Boolean = false,
    val result: AirTransport.Result? = null, val hasBluetooth: Boolean = false,
    val bluetoothOn: Boolean = false, val access: Boolean = false, val listener: Boolean = false,
    val devices: List<BandEntry> = emptyList()
) {
    val canTest get() = settings.enabled && hasBluetooth && bluetoothOn && settings.address.isNotBlank() && !sending
    val status get() = when {
        !hasBluetooth -> R.string.permission_needed
        !bluetoothOn -> R.string.bt_off
        settings.address.isBlank() -> R.string.device_missing
        !settings.enabled -> R.string.ui_relay_off
        sending -> R.string.sending
        !access -> R.string.ui_relay_permission_missing
        !listener -> R.string.listener_wait
        result == AirTransport.Result.UNAVAILABLE -> R.string.unavailable
        result == AirTransport.Result.FAILED -> R.string.failed
        else -> R.string.ui_ready_to_send
    }
}
internal data class AirActions(
    val enabled: (Boolean) -> Unit = {}, val send: () -> Unit = {}, val device: (String) -> Unit = {},
    val strength: (Int) -> Unit = {}, val bluetooth: () -> Unit = {},
    val notifications: () -> Unit = {}, val appInfo: () -> Unit = {}
)
internal fun AirTransport.Result.message() = when (this) {
    AirTransport.Result.VERIFIED -> R.string.verified
    AirTransport.Result.UNAVAILABLE -> R.string.unavailable
    AirTransport.Result.FAILED -> R.string.failed
    AirTransport.Result.UNKNOWN -> R.string.unknown
    AirTransport.Result.BUSY -> R.string.busy
    AirTransport.Result.HANDOFF_UNCONFIRMED -> R.string.handoff
}

@Composable internal fun AirTheme(dynamic: Boolean = true, content: @Composable () -> Unit) {
    val dark = isSystemInDarkTheme()
    val context = LocalContext.current
    val scheme = if (dynamic && Build.VERSION.SDK_INT >= 31) {
        if (dark) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
    } else if (dark) darkColorScheme(
        primary = Color(0xFFA7D5A7), onPrimary = Color(0xFF123819),
        primaryContainer = Color(0xFF29432C), onPrimaryContainer = Color(0xFFE0E5DD),
        background = Color(0xFF111611), onBackground = Color(0xFFE0E5DD),
        surface = Color(0xFF111611), onSurface = Color(0xFFE0E5DD),
        surfaceContainer = Color(0xFF20271F), onSurfaceVariant = Color(0xFFB7C4B2), outline = Color(0xFF889581)
    ) else lightColorScheme(
        primary = Color(0xFF315E3A), onPrimary = Color.White,
        primaryContainer = Color(0xFFD8ECD1), onPrimaryContainer = Color(0xFF202722),
        background = Color(0xFFF8FAF4), onBackground = Color(0xFF202722),
        surface = Color(0xFFF8FAF4), onSurface = Color(0xFF202722),
        surfaceContainer = Color(0xFFEDF1E8), onSurfaceVariant = Color(0xFF536052), outline = Color(0xFF73806F)
    )
    MaterialTheme(colorScheme = scheme, typography = Typography(
        bodyLarge = MaterialTheme.typography.bodyLarge.copy(fontSize = 17.sp, lineHeight = 25.sp),
        bodyMedium = MaterialTheme.typography.bodyMedium.copy(fontSize = 15.sp, lineHeight = 22.sp)
    ), content = content)
}

/** UI-only navigation and callbacks; no Bluetooth or persistence operations in preview/test content. */
@Composable internal fun AirInterface(state: AirUiState, actions: AirActions) {
    var path by rememberSaveable { mutableStateOf("home") }
    val page = path.substringAfterLast('/')
    fun go(next: String) { path += "/$next" }
    fun back() { path = path.substringBeforeLast('/', "home") }
    BackHandler(page != "home") { back() }
    val configuration = LocalConfiguration.current
    val compact = configuration.screenWidthDp < 380
    val large = configuration.fontScale >= 1.5f || compact
    val recovery: @Composable () -> Unit = {
        when {
            !state.hasBluetooth -> ActionButton(R.string.grant_bluetooth, actions.bluetooth)
            !state.bluetoothOn -> ActionButton(R.string.ui_enable_bluetooth, actions.bluetooth)
            state.settings.address.isBlank() -> ActionButton(R.string.choose_device, { go("devices") })
            !state.access -> Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(stringResource(R.string.ui_permission_explanation), style = MaterialTheme.typography.bodyMedium)
                ActionButton(R.string.ui_grant_notification_access, actions.notifications)
            }
            !state.listener -> ActionButton(R.string.ui_reconnect_listener, actions.notifications)
            state.result == AirTransport.Result.UNAVAILABLE || state.result == AirTransport.Result.FAILED -> ActionButton(R.string.ui_no_vibration, { go("trouble") })
        }
    }
    key(page) {
        LazyColumn(
            Modifier.fillMaxSize().safeDrawingPadding().imePadding().testTag("screen"),
            contentPadding = PaddingValues(horizontal = if (compact) 16.dp else 22.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            item {
                if (page == "home") Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text(stringResource(R.string.app_name), Modifier.weight(1f), fontSize = 25.sp, fontWeight = FontWeight.Medium)
                    TextButton(onClick = { go("help") }) { Text(stringResource(R.string.help)) }
                } else Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    TextButton(onClick = { back() }) { Text(stringResource(R.string.ui_back)) }
                    Text(stringResource(when (page) {
                        "devices" -> R.string.ui_your_band
                        "strength" -> R.string.strength
                        "setup" -> R.string.ui_setup_help
                        "trouble" -> R.string.ui_check_band
                        "test" -> R.string.ui_test_vibration
                        else -> R.string.help
                    }), fontSize = 30.sp, lineHeight = 36.sp, fontWeight = FontWeight.Medium)
                }
            }
            when (page) {
                "home" -> {
                    item {
                        Card(shape = RoundedCornerShape(28.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)) {
                            Column(Modifier.fillMaxWidth().padding(if (compact) 20.dp else 24.dp), verticalArrangement = Arrangement.spacedBy(18.dp)) {
                                val toggleModifier = Modifier.fillMaxWidth().testTag("relay").toggleable(state.settings.enabled, role = Role.Switch, onValueChange = actions.enabled)
                                if (large) Column(toggleModifier) {
                                    HeroTitle(compact)
                                    Switch(state.settings.enabled, null, Modifier.align(Alignment.End))
                                } else Row(toggleModifier, verticalAlignment = Alignment.CenterVertically) {
                                    Box(Modifier.weight(1f).padding(end = 14.dp)) { HeroTitle(compact) }
                                    Switch(state.settings.enabled, null)
                                }
                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    Icon(painterResource(if (state.status == R.string.ui_ready_to_send) R.drawable.ic_ui_ready else R.drawable.ic_ui_status), null, Modifier.size(20.dp))
                                    Text(stringResource(state.status), Modifier.semantics { liveRegion = LiveRegionMode.Polite }, style = MaterialTheme.typography.bodyMedium)
                                }
                                recovery()
                            }
                        }
                    }
                    item {
                        LinkRow(state.devices.firstOrNull { it.address == state.settings.address }?.name
                            ?: state.settings.address.ifBlank { stringResource(R.string.choose_device) },
                            stringResource(R.string.ui_your_band), R.drawable.ic_ui_band, { go("devices") }, colored = false)
                    }
                    item { ActionButton(if (state.sending) R.string.sending else R.string.ui_test_vibration, { actions.send(); go("test") }, state.canTest, "test") }
                    if (!state.access) item { Note(R.string.ui_manual_test_only) }
                    state.result?.let { result -> item { ResultText(result) } }
                    item { Note(R.string.ui_signal_hint) }
                }
                "devices" -> {
                    item { Note(R.string.device_hint) }
                    if (!state.hasBluetooth || !state.bluetoothOn) item { recovery() }
                    if (state.devices.isEmpty()) item { Note(R.string.no_devices) }
                    items(state.devices, key = { it.address }) { band ->
                        Row(Modifier.fillMaxWidth().heightIn(min = 76.dp).selectable(state.settings.address == band.address, role = Role.RadioButton,
                            onClick = { actions.device(band.address); back() }), verticalAlignment = Alignment.CenterVertically) {
                            RadioButton(state.settings.address == band.address, null)
                            Column(Modifier.weight(1f).padding(12.dp)) { Text(band.name); Text(band.address, style = MaterialTheme.typography.bodyMedium) }
                        }
                    }
                }
                "help" -> {
                    item { LinkRow(stringResource(R.string.ui_no_vibration), onClick = { go("trouble") }) }
                    item { LinkRow(stringResource(R.string.strength), onClick = { go("strength") }) }
                    item { LinkRow(stringResource(R.string.ui_setup_help), onClick = { go("setup") }) }
                    item { Column(verticalArrangement = Arrangement.spacedBy(16.dp)) { Note(R.string.restricted_note); ActionButton(R.string.app_info, actions.appInfo) } }
                    item { Note(R.string.ui_privacy_note) }
                    item { Note(R.string.alpha_note) }
                }
                "strength" -> {
                    items(listOf(0 to R.string.ui_strength_health, 2 to R.string.strength_low, 3 to R.string.strength_high)) { (value, label) ->
                        Row(Modifier.fillMaxWidth().heightIn(min = 56.dp).selectable(state.settings.strength == value, role = Role.RadioButton, onClick = { actions.strength(value) }), verticalAlignment = Alignment.CenterVertically) {
                            RadioButton(state.settings.strength == value, null)
                            Text(stringResource(label), Modifier.weight(1f).padding(12.dp))
                        }
                    }
                    item { Note(if (state.settings.strength == 0) R.string.ui_strength_default_note else R.string.ui_strength_change_note) }
                    item { ActionButton(R.string.ui_done, { back() }) }
                }
                "setup" -> {
                    item {
                        Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                        // Steps derive from real state. Clicking a system action never marks a step complete.
                        when {
                            !state.hasBluetooth || !state.bluetoothOn -> { Note(R.string.ui_bluetooth_reason); recovery() }
                            !state.access -> { Note(R.string.ui_notification_reason); ActionButton(R.string.ui_grant_notification_access, actions.notifications); Note(R.string.restricted_note); ActionButton(R.string.app_info, actions.appInfo) }
                            state.settings.address.isBlank() -> { Note(R.string.ui_pair_first); ActionButton(R.string.choose_device, { go("devices") }) }
                            else -> { Note(R.string.ui_setup_complete); ActionButton(R.string.ui_home, { path = "home" }) }
                        }
                        }
                    }
                    item { Note(R.string.setup_note) }
                }
                "test" -> {
                    item {
                        Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                        if (state.sending) { LinearProgressIndicator(Modifier.fillMaxWidth()); Text(stringResource(R.string.sending)) }
                        else state.result?.let { ResultText(it) } ?: Note(R.string.ui_test_cancelled)
                        if (!state.access) Note(R.string.ui_manual_test_only)
                        }
                    }
                    if (!state.sending && state.result != null) {
                        item { ActionButton(R.string.ui_yes_felt, { back() }) }
                        item { OutlinedButton(onClick = { go("trouble") }, modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp)) { Text(stringResource(R.string.ui_no)) } }
                    }
                    item { recovery() }
                }
                "trouble" -> {
                    item { Note(R.string.ui_move_closer) }
                    item { Note(R.string.ui_check_bluetooth) }
                    item { Note(R.string.ui_wait_sync) }
                    item { Note(R.string.ui_check_strength) }
                    item { recovery() }
                    item { ActionButton(R.string.ui_test_again, { actions.send(); go("test") }, state.canTest) }
                }
            }
        }
    }
}

@Composable private fun HeroTitle(compact: Boolean) { Text(stringResource(R.string.ui_notifications_on_band), fontSize = if (compact) 24.sp else 27.sp, lineHeight = if (compact) 30.sp else 33.sp, fontWeight = FontWeight.Medium) }
@Composable private fun Note(id: Int) { Text(stringResource(id), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant) }
@Composable private fun ResultText(result: AirTransport.Result) {
    Text(stringResource(result.message()), Modifier.testTag("result").semantics { liveRegion = LiveRegionMode.Polite }, style = MaterialTheme.typography.bodyMedium)
}
@Composable private fun ActionButton(label: Int, action: () -> Unit, enabled: Boolean = true, tag: String = "action") {
    Button(onClick = action, enabled = enabled, modifier = Modifier.fillMaxWidth().heightIn(min = 58.dp).testTag(tag), shape = RoundedCornerShape(28.dp), contentPadding = PaddingValues(18.dp, 14.dp)) { Text(stringResource(label)) }
}
@Composable private fun LinkRow(title: String, subtitle: String? = null, icon: Int? = null, onClick: () -> Unit, colored: Boolean = true, tag: String = "link") {
    val large = LocalConfiguration.current.fontScale >= 1.5f
    Surface(onClick = onClick, modifier = Modifier.fillMaxWidth().heightIn(min = 76.dp).testTag(tag), shape = RoundedCornerShape(22.dp), color = if (colored) MaterialTheme.colorScheme.surfaceContainer else MaterialTheme.colorScheme.surface) {
        Row(Modifier.padding(18.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            if (!large) icon?.let { Icon(painterResource(it), null, Modifier.size(28.dp)) }
            Column(Modifier.weight(1f)) {
                Text(title, fontWeight = FontWeight.Medium)
                subtitle?.let { Text(it, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant) }
            }
            if (!large) Icon(painterResource(R.drawable.ic_ui_next), null, Modifier.size(20.dp))
        }
    }
}
