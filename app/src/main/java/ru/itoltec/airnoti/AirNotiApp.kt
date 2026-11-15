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
    enum class SendOrigin { MANUAL, NOTIFICATION }
    internal fun trace(message: String) {
        if (applicationInfo.flags and android.content.pm.ApplicationInfo.FLAG_DEBUGGABLE != 0) android.util.Log.d("AirNotiRelay", message)
    }
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
    private var choosing: Job? = null
    private val receiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            environment.value++
            if (!bluetoothReady()) cancel() else refreshDevice()
        }
    }
    override fun onCreate() {
        super.onCreate()
        preferences = SettingsRepository(this)
        settings = preferences.flow.catch { emit(Settings()) }.stateIn(scope, SharingStarted.Eagerly, Settings())
        transport = AirTransport(this)
        registerReceiver(receiver, IntentFilter(BluetoothAdapter.ACTION_STATE_CHANGED).apply { addAction(android.bluetooth.BluetoothDevice.ACTION_BOND_STATE_CHANGED) })
        scope.launch {
            var previous = Settings()
            settings.collect { current ->
                if (!current.enabled || current.address != previous.address) cancel()
                previous = current
            }
        }
    }
    @android.annotation.SuppressLint("MissingPermission")
    fun refreshDevice() {
        if (!bluetoothPermission() || choosing?.isActive == true) return
        choosing = scope.launch {
            val current = preferences.flow.first()
            val devices = runCatching {
                getSystemService(BluetoothManager::class.java).adapter?.bondedDevices?.map { BondedCandidate(it.address, it.name) }.orEmpty()
            }.getOrDefault(emptyList())
            val chosen = DeviceChoice.choose(current.address, devices)
            if (chosen != null && chosen != current.address) preferences.device(chosen)
        }
    }
    fun bluetoothPermission(): Boolean = Build.VERSION.SDK_INT < 31 || checkSelfPermission(android.Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED
    fun bluetoothReady(): Boolean = bluetoothPermission() && runCatching { getSystemService(BluetoothManager::class.java).adapter?.isEnabled == true }.getOrDefault(false)
    fun send(origin: SendOrigin = SendOrigin.MANUAL) {
        val config = settings.value
        if (stopped || !config.enabled || config.address.isBlank() || !bluetoothReady() || operation?.isActive == true) return
        operation = scope.launch {
            trace("send_start origin=$origin")
            sending.value = true; result.value = null
            try { result.value = transport.tap(config.address, config.strength); trace("send_result origin=$origin result=${result.value}") }
            finally { sending.value = false }
        }
    }
    fun cancel() { operation?.cancel(); operation = null; if (::transport.isInitialized) transport.cancel(); sending.value = false }
    fun enable() { scope.launch { preferences.enabled(true); stopped = false } }
    fun disable() { stopped = true; cancel(); scope.launch { preferences.enabled(false) } }
}

