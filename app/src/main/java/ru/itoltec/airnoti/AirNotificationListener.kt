package ru.itoltec.airnoti

import android.app.Notification
import android.os.SystemClock
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import kotlinx.coroutines.*
import java.security.MessageDigest
import java.security.SecureRandom

class AirNotificationListener : NotificationListenerService() {
    private val app get() = application as AirNotiApp
    private val policy = NotificationPolicy("ru.itoltec.airnoti")
    private val queue = AlertQueue()
    private val salt = ByteArray(32).also { SecureRandom().nextBytes(it) }
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var drain: Job? = null
    private var connectedAt = Long.MAX_VALUE
    override fun onCreate() {
        super.onCreate()
        scope.launch { app.settings.collect { if (!it.enabled) { queue.clear(); drain?.cancel() } } }
    }
    override fun onListenerConnected() {
        connectedAt = System.currentTimeMillis()
        policy.clear(); queue.clear()
        activeNotifications?.forEach { policy.seed(extract(it)) }
        app.listenerConnected.value = true
    }
    override fun onListenerDisconnected() {
        connectedAt = Long.MAX_VALUE; queue.clear(); drain?.cancel(); app.cancel()
        app.listenerConnected.value = false
    }
    override fun onNotificationPosted(sbn: StatusBarNotification) {
        if (connectedAt == Long.MAX_VALUE) return
        if (packageManager.getLaunchIntentForPackage(sbn.packageName) == null) return
        val notice = extract(sbn)
        if (!policy.accept(notice, app.settings.value, connectedAt)) return
        queue.offer(QueuedNotice(notice.key, notice.pkg, notice.group, notice.summary, SystemClock.elapsedRealtime()))
        if (drain?.isActive != true) drain = scope.launch {
            while (queue.size > 0) {
                delay(700)
                if (!app.sending.value && queue.take(SystemClock.elapsedRealtime(), app.settings.value)) app.send()
                // Expiry also applies while an operation is running; take will run once it ends.
            }
        }
    }
    private fun extract(sbn: StatusBarNotification): Notice {
        val n = sbn.notification
        val digest = MessageDigest.getInstance("SHA-256").apply { update(salt) }
        fun add(value: CharSequence?) {
            val bytes = value?.toString()?.toByteArray() ?: byteArrayOf()
            digest.update(java.nio.ByteBuffer.allocate(4).putInt(bytes.size).array()); digest.update(bytes)
        }
        val messages = androidx.core.app.NotificationCompat.MessagingStyle.extractMessagingStyleFromNotification(n)?.messages
        if (!messages.isNullOrEmpty()) {
            // The latest message timestamp distinguishes repeated text within the same notification ID.
            messages.takeLast(25).forEach { m -> add(m.text); add(m.timestamp.toString()); add(m.person?.name); add(m.dataUri?.toString()) }
        } else {
            add(n.extras.getCharSequence(Notification.EXTRA_TITLE))
            add(n.extras.getCharSequence(Notification.EXTRA_TEXT))
            add(n.extras.getCharSequence(Notification.EXTRA_BIG_TEXT))
            n.extras.getCharSequenceArray(Notification.EXTRA_TEXT_LINES)?.forEach(::add)
        }
        val service = n.flags and (Notification.FLAG_ONGOING_EVENT or Notification.FLAG_FOREGROUND_SERVICE) != 0 ||
            n.extras.containsKey(Notification.EXTRA_PROGRESS) || n.category in setOf(Notification.CATEGORY_SERVICE, Notification.CATEGORY_TRANSPORT)
        return Notice(sbn.key, sbn.packageName, digest.digest().joinToString("") { "%02x".format(it) },
            if (sbn.isGroup) sbn.groupKey else null, n.flags and Notification.FLAG_GROUP_SUMMARY != 0, service, sbn.postTime)
    }
    override fun onDestroy() { scope.cancel(); queue.clear(); app.listenerConnected.value = false; app.cancel(); super.onDestroy() }
}
