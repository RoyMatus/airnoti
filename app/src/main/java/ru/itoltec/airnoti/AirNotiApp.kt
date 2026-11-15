package ru.itoltec.airnoti

import android.app.Application
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothManager
import android.content.*
import android.content.pm.PackageManager
import android.os.Build
import android.os.SystemClock
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*

class AirNotiApp : Application() {
    val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    lateinit var preferences: SettingsRepository
    lateinit var settings: StateFlow<Settings>
    lateinit var transport: AirTransport
    val result = MutableStateFlow<AirTransport.Result?>(null)
    val sending = MutableStateFlow(false)
    val listenerConnected = MutableStateFlow(false)
    val environment = MutableStateFlow(0)
    private var operation: Job? = null
    private var stopped = false
    private val receiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            environment.value++
            if (!bluetoothReady()) cancel()
        }
    }
    override fun onCreate() {
        super.onCreate()
        preferences = SettingsRepository(this)
        settings = preferences.flow.catch { emit(Settings()) }.stateIn(scope, SharingStarted.Eagerly, Settings())
        transport = AirTransport(this)
        registerReceiver(receiver, IntentFilter(BluetoothAdapter.ACTION_STATE_CHANGED))
        scope.launch {
            var previous = Settings()
            settings.collect { current ->
                if (!current.enabled || current.address != previous.address) cancel()
                previous = current
            }
        }
    }
    fun bluetoothPermission(): Boolean = Build.VERSION.SDK_INT < 31 || checkSelfPermission(android.Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED
    fun bluetoothReady(): Boolean = bluetoothPermission() && runCatching { getSystemService(BluetoothManager::class.java).adapter?.isEnabled == true }.getOrDefault(false)
    fun send() {
        val config = settings.value
        if (stopped || !config.enabled || config.address.isBlank() || !bluetoothReady() || operation?.isActive == true) return
        operation = scope.launch {
            sending.value = true; result.value = null
            try { result.value = transport.tap(config.address, config.strength) }
            finally { sending.value = false }
        }
    }
    fun cancel() { operation?.cancel(); operation = null; if (::transport.isInitialized) transport.cancel(); sending.value = false }
    fun enable() { scope.launch { preferences.enabled(true); stopped = false } }
    fun disable() { stopped = true; cancel(); scope.launch { preferences.enabled(false) } }
}
