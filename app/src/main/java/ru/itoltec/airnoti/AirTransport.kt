package ru.itoltec.airnoti

// Adapted from fitbit-air (MIT, Thore Koritzius, 2026). See third_party and NOTICE.
import android.annotation.SuppressLint
import android.bluetooth.*
import android.content.Context
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import com.fitbit.goldengate.bindings.GoldenGate
import com.fitbit.goldengate.bindings.coap.CoapEndpoint
import com.fitbit.goldengate.bindings.coap.data.Method
import com.fitbit.goldengate.bindings.coap.data.OutgoingRequestBuilder
import com.fitbit.goldengate.bindings.dtls.DtlsProtocolStatus.TlsProtocolState.*
import com.fitbit.goldengate.bindings.dtls.TlsKeyResolver
import com.fitbit.goldengate.bindings.node.NodeKey
import com.fitbit.goldengate.bindings.node.BluetoothAddressNodeKey
import com.fitbit.goldengate.bindings.io.RxSource
import com.fitbit.goldengate.bindings.io.TxSink
import com.fitbit.goldengate.bindings.stack.*
import io.reactivex.Single
import io.reactivex.disposables.CompositeDisposable
import kotlinx.coroutines.suspendCancellableCoroutine
import java.util.UUID
import java.util.concurrent.TimeUnit
import kotlin.coroutines.resume

/** One bounded session; no scanning, reconnect loop, warm link or background polling. Main-thread owned. */
@Suppress("DEPRECATION")
@SuppressLint("MissingPermission")
class AirTransport(private val context: Context) {
    enum class Result { VERIFIED, UNAVAILABLE, FAILED, UNKNOWN, BUSY, HANDOFF_UNCONFIRMED }
    private val handler = Handler(Looper.getMainLooper())
    private var gatt: BluetoothGatt? = null
    private var stack: Stack? = null
    private var endpoint: CoapEndpoint? = null
    private var sink: TxSink? = null
    private var source: RxSource? = null
    private val subscriptions = CompositeDisposable()
    private val packets = ArrayDeque<ByteArray>()
    private val traffic = ArrayDeque<Pair<Long, Int>>()
    private var writing = false
    private var started = false
    private var sent = false
    private var finishing = false
    @Volatile private var muted = false
    private var reply: ((Result) -> Unit)? = null
    private var strength = 0
    private var phase = "idle"
    private fun trace(message: String) { (context.applicationContext as? AirNotiApp)?.trace("transport $message") }

    suspend fun tap(address: String, requestedStrength: Int): Result = suspendCancellableCoroutine { continuation ->
        check(Looper.myLooper() == Looper.getMainLooper())
        if (reply != null) { continuation.resume(Result.BUSY); return@suspendCancellableCoroutine }
        reply = { if (continuation.isActive) continuation.resume(it) }
        continuation.invokeOnCancellation { handler.post { cancel() } }
        strength = requestedStrength
        phase = "connect"
        sent = false; started = false; finishing = false; muted = false
        try {
            val adapter = context.getSystemService(BluetoothManager::class.java).adapter
            val device = adapter?.bondedDevices?.firstOrNull { it.address == address }
            if (adapter?.isEnabled != true || device == null) finish(Result.UNAVAILABLE)
            else {
                handler.postDelayed({ finish(if (sent) Result.UNKNOWN else Result.FAILED) }, 25_000)
                gatt = device.connectGatt(context, false, callback, BluetoothDevice.TRANSPORT_LE)
                if (gatt == null) finish(Result.UNAVAILABLE)
            }
        } catch (_: SecurityException) { finish(Result.UNAVAILABLE) }
          catch (_: IllegalArgumentException) { finish(Result.UNAVAILABLE) }
    }
    fun cancel() { val done = reply; reply = null; close(); done?.invoke(Result.UNKNOWN) }
    private fun dispatch(g: BluetoothGatt, action: () -> Unit) { handler.post {
        if (gatt === g && reply != null) try { action() } catch (_: Exception) { finish(if (sent) Result.UNKNOWN else Result.FAILED) }
    } }
    private val callback = object : BluetoothGattCallback() {
        override fun onConnectionStateChange(g: BluetoothGatt, status: Int, newState: Int) = dispatch(g) {
            trace("connection status=$status state=$newState")
            if (status != GATT_SUCCESS || newState == BluetoothProfile.STATE_DISCONNECTED) finish(if (sent) Result.UNKNOWN else Result.UNAVAILABLE)
            else if (newState == BluetoothProfile.STATE_CONNECTED && !g.discoverServices()) finish(Result.FAILED)
        }
        override fun onServicesDiscovered(g: BluetoothGatt, status: Int) = dispatch(g) {
            phase = "subscribe"
            trace("services status=$status")
            val rx = g.getService(SERVICE)?.getCharacteristic(RX)
            val descriptor = rx?.getDescriptor(CCCD)
            if (status != GATT_SUCCESS || rx == null || descriptor == null || !g.setCharacteristicNotification(rx, true)) finish(Result.FAILED)
            else {
                val accepted = if (Build.VERSION.SDK_INT >= 33) g.writeDescriptor(descriptor, BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE) == BluetoothStatusCodes.SUCCESS
                else { descriptor.value = BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE; g.writeDescriptor(descriptor) }
                if (!accepted) finish(Result.FAILED)
            }
        }
        override fun onDescriptorWrite(g: BluetoothGatt, descriptor: BluetoothGattDescriptor, status: Int) = dispatch(g) {
            trace("subscription status=$status")
            if (status != GATT_SUCCESS) finish(Result.FAILED) else waitForQuiet(g, SystemClock.elapsedRealtime())
        }
        override fun onCharacteristicChanged(g: BluetoothGatt, c: BluetoothGattCharacteristic, value: ByteArray) = dispatch(g) { receive(c, value) }
        @Deprecated("Legacy callback") override fun onCharacteristicChanged(g: BluetoothGatt, c: BluetoothGattCharacteristic) {
            @Suppress("DEPRECATION") val bytes = c.value?.copyOf() ?: return
            if (Build.VERSION.SDK_INT < 33) dispatch(g) { receive(c, bytes) }
        }
        override fun onCharacteristicWrite(g: BluetoothGatt, c: BluetoothGattCharacteristic, status: Int) = dispatch(g) {
            writing = false
            if (status != GATT_SUCCESS) finish(if (sent) Result.UNKNOWN else Result.FAILED) else writeNext()
        }
    }
    private fun receive(c: BluetoothGattCharacteristic, bytes: ByteArray) {
        if (c.uuid != RX) return
        if (stack == null) {
            val now = SystemClock.elapsedRealtime()
            traffic.addLast(now to bytes.size)
            while (traffic.size > 128) traffic.removeFirst()
        }
        source?.receiveData(bytes)?.subscribe({}, { handler.post { finish(if (sent) Result.UNKNOWN else Result.FAILED) } })?.let(subscriptions::add)
    }
    private fun waitForQuiet(g: BluetoothGatt, since: Long) {
        handler.postDelayed({
            if (gatt !== g || finishing) return@postDelayed
            val now = SystemClock.elapsedRealtime()
            while (traffic.isNotEmpty() && now - traffic.first().first > 1_200) traffic.removeFirst()
            if (traffic.sumOf { it.second } >= 400) {
                if (now - since >= 5_000) finish(Result.BUSY) else waitForQuiet(g, since)
            } else startStack(g)
        }, 700)
    }
    private fun startStack(g: BluetoothGatt, handoff: Boolean = false, outcome: Result = Result.FAILED) {
        phase = if (handoff) "handoff" else "handshake"
        try {
            Native.init()
            val tx = TxSink().also { sink = it }
            val rx = RxSource().also { source = it }
            subscriptions.add(tx.dataObservable.subscribe({ bytes ->
                if (!muted) dispatch(g) {
                    if (sink !== tx || muted) return@dispatch
                    if (packets.size >= 64) { finish(Result.FAILED); return@dispatch }
                    packets.addLast(bytes); writeNext()
                }
            }, { handler.post { finish(Result.FAILED) } }))
            val newStack = if (handoff) Stack(BluetoothAddressNodeKey(g.device.address), tx.thisPointer, rx.thisPointer, DtlsSocketNetifGattlink(), false, RefuseKeys)
                else Stack(BluetoothAddressNodeKey(g.device.address), tx.thisPointer, rx.thisPointer, DtlsSocketNetifGattlink(), false)
            stack = newStack
            if (handoff) {
                subscriptions.add(newStack.stackEventObservable.subscribe({ event ->
                    if (event is StackEvent.GattlinkSessionResent) {
                        muted = true
                        handler.post { complete(outcome) }
                    }
                }, { handler.post { complete(Result.HANDOFF_UNCONFIRMED) } }))
            } else {
                val coap = CoapEndpoint().also { endpoint = it; it.attach(newStack) }
                subscriptions.add(newStack.dtlsEventObservable.subscribe({ state -> dispatch(g) {
                    trace("dtls state=${state.state}")
                    if (stack !== newStack || finishing) return@dispatch
                    if (state.state == TLS_STATE_SESSION && !started) {
                        started = true
                        g.requestConnectionPriority(BluetoothGatt.CONNECTION_PRIORITY_HIGH)
                        command(coap)
                    } else if (state.state == TLS_STATE_ERROR) finish(Result.FAILED)
                } }, { handler.post { finish(Result.FAILED) } }))
            }
            newStack.start()
        } catch (_: LinkageError) { if (handoff) complete(Result.HANDOFF_UNCONFIRMED) else finish(Result.FAILED) }
          catch (_: Exception) { if (handoff) complete(Result.HANDOFF_UNCONFIRMED) else finish(Result.FAILED) }
    }
    private fun read(coap: CoapEndpoint): Single<Int> = coap.responseFor(
        OutgoingRequestBuilder("/settings", Method.PUT).expectSuccess(false).forceNonBlockwise(true).maxResendCount(0)
            .body(byteArrayOf(10, 1, 1)).build()
    ).timeout(3, TimeUnit.SECONDS).flatMap { response -> response.body.asData().map { bytes ->
        trace("read response=${response.responseCode} length=${bytes.size}")
        check(response.responseCode.responseClass.toInt() == 2)
        check(bytes.contentEquals(HapticProtocol.write(2)) || bytes.contentEquals(HapticProtocol.write(3)))
        bytes.last().toInt()
    } }
    private fun command(coap: CoapEndpoint) {
        phase = "read_initial"
        subscriptions.add(read(coap).observeOn(io.reactivex.android.schedulers.AndroidSchedulers.mainThread()).flatMap { original ->
            check(endpoint === coap && !finishing)
            val target = if (strength == 0) original else strength
            val body = if (original == target) HapticProtocol.tap(target) else HapticProtocol.write(target)
            // No resend: even a missing response may mean the command reached the motor.
            sent = true
            phase = "post"
            coap.responseFor(OutgoingRequestBuilder("/settings", Method.POST).expectSuccess(false).forceNonBlockwise(true)
                .maxResendCount(0).body(body).build()).timeout(3, TimeUnit.SECONDS).flatMap { response ->
                check(response.responseCode.responseClass.toInt() == 2)
                phase = "verify"
                read(coap).map { verified -> check(verified == target); true }
            }
        }.subscribe({ handler.post { if (endpoint === coap) finish(Result.VERIFIED) } }, { error ->
            trace("command_error phase=$phase type=${error.javaClass.simpleName}")
            handler.post { if (endpoint === coap) finish(if (sent) Result.UNKNOWN else Result.FAILED) }
        }))
    }
    private fun writeNext() {
        val g = gatt ?: return
        if (writing || muted || packets.isEmpty()) return
        val c = g.getService(SERVICE)?.getCharacteristic(TX) ?: return finish(Result.FAILED)
        val bytes = packets.removeFirst(); writing = true
        val accepted = if (Build.VERSION.SDK_INT >= 33) g.writeCharacteristic(c, bytes, BluetoothGattCharacteristic.WRITE_TYPE_NO_RESPONSE) == BluetoothStatusCodes.SUCCESS
        else { c.writeType = BluetoothGattCharacteristic.WRITE_TYPE_NO_RESPONSE; c.value = bytes; g.writeCharacteristic(c) }
        if (!accepted) { writing = false; finish(if (sent) Result.UNKNOWN else Result.FAILED) }
    }
    private fun finish(requestedResult: Result) {
        trace("finish phase=$phase sent=$sent result=$requestedResult")
        val result = if (sent && requestedResult in setOf(Result.FAILED, Result.UNAVAILABLE, Result.BUSY)) Result.UNKNOWN else requestedResult
        if (reply == null) return
        if (finishing) { complete(if (result == Result.UNKNOWN) result else Result.HANDOFF_UNCONFIRMED); return }
        finishing = true
        handler.removeCallbacksAndMessages(null)
        val g = gatt
        if (g == null || stack == null) { complete(result); return }
        disposeStack()
        handler.postDelayed({ complete(if (result == Result.VERIFIED) Result.HANDOFF_UNCONFIRMED else result) }, 4_000)
        startStack(g, true, result)
    }
    private fun complete(result: Result) { val done = reply; reply = null; close(); done?.invoke(result) }
    private fun disposeStack() {
        subscriptions.clear()
        runCatching { endpoint?.close() }; endpoint = null
        runCatching { stack?.close() }; stack = null
        runCatching { source?.close() }; source = null
        runCatching { sink?.close() }; sink = null
        packets.clear(); writing = false
    }
    private fun close() {
        muted = true
        handler.removeCallbacksAndMessages(null)
        disposeStack()
        val closing = gatt; gatt = null
        runCatching { if (started) closing?.requestConnectionPriority(BluetoothGatt.CONNECTION_PRIORITY_LOW_POWER) }
        runCatching { closing?.disconnect() }; runCatching { closing?.close() }
        traffic.clear()
    }
    private object Native {
        private var ready = false
        @Synchronized fun init() { if (!ready) { System.loadLibrary("gg"); GoldenGate.init(loggingEnabled = false); ready = true } }
    }
    private object RefuseKeys : TlsKeyResolver() { override fun resolveKey(nodeKey: NodeKey<*>, keyId: ByteArray): ByteArray? = null }
    companion object {
        private const val GATT_SUCCESS = BluetoothGatt.GATT_SUCCESS
        private val SERVICE = UUID.fromString("abbaff00-e56a-484c-b832-8b17cf6cbfe8")
        private val TX = UUID.fromString("abbaff01-e56a-484c-b832-8b17cf6cbfe8")
        private val RX = UUID.fromString("abbaff02-e56a-484c-b832-8b17cf6cbfe8")
        private val CCCD = UUID.fromString("00002902-0000-1000-8000-00805f9b34fb")
    }
}

/** Wire encoding traced to fitbit-air AirHapticSettings; never arbitrary motor waveforms. */
internal object HapticProtocol {
    fun write(value: Int): ByteArray { require(value == 2 || value == 3); return byteArrayOf(10, 4, 10, 2, 8, value.toByte()) }
    fun tap(value: Int): ByteArray = write(if (value == 2) 3 else 2) + write(value)
}

