package ru.itoltec.airnoti

import android.app.Notification
import android.app.Person
import android.content.Context
import android.os.Bundle
import android.os.Process
import android.os.UserHandle
import android.service.notification.StatusBarNotification
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.*
import org.junit.Test

/** Real Android notification objects, synthetic content only; never sends a BLE command. */
class NotificationPipelineTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val settings = Settings(enabled = true, all = false, packages = setOf("org.telegram.messenger"))
    private fun sbn(n: Notification, time: Long = 200) = StatusBarNotification(
        "org.telegram.messenger", "org.telegram.messenger", 1, null, Process.myUid(), 0, 0, n, Process.myUserHandle(), time)
    private fun message(text: String, time: Long) = Notification.Builder(context, "test")
        .setSmallIcon(R.drawable.ic_ui_apps)
        .setStyle(Notification.MessagingStyle(Person.Builder().setName("Test user").build())
            .addMessage(text, time, Person.Builder().setName("Test sender").build())).build()
    @Test fun messagingNotificationReachesQueueAndNewMessageWithSameIdSurvives() {
        val listener = AirNotificationListener()
        val policy = NotificationPolicy(context.packageName)
        val queue = AlertQueue()
        val first = listener.extract(sbn(message("synthetic", 101)))
        assertFalse(first.service)
        assertTrue(policy.accept(first, settings, 100))
        queue.offer(QueuedNotice(first.key, first.pkg, first.group, first.summary, 0))
        assertTrue(queue.take(700, settings))
        assertFalse(policy.accept(first, settings, 100))
        val next = listener.extract(sbn(message("synthetic", 102), 201))
        assertTrue(policy.accept(next, settings, 100))
        policy.seed(next)
        assertFalse(policy.accept(next, settings, 100))
    }
    @Test fun zeroProgressMetadataIsNotAProgressNotification() {
        val notification = Notification.Builder(context, "test").setSmallIcon(R.drawable.ic_ui_apps)
            .setContentText("synthetic").addExtras(Bundle().apply { putInt(Notification.EXTRA_PROGRESS, 0); putInt(Notification.EXTRA_PROGRESS_MAX, 0) }).build()
        assertFalse(AirNotificationListener().extract(sbn(notification)).service)
    }
    @Test fun actualProgressAndOngoingAreForwarded() {
        val progress = Notification.Builder(context, "test").setSmallIcon(R.drawable.ic_ui_apps).setProgress(100, 10, false).build()
        val ongoing = Notification.Builder(context, "test").setSmallIcon(R.drawable.ic_ui_apps).setOngoing(true).build()
        val indefinite = Notification.Builder(context, "test").setSmallIcon(R.drawable.ic_ui_apps).setProgress(0, 0, true).build()
        val listener = AirNotificationListener()
        assertTrue(listener.extract(sbn(progress)).service)
        assertTrue(listener.extract(sbn(ongoing)).service)
        assertTrue(listener.extract(sbn(indefinite)).service)
        for (notification in listOf(progress, ongoing, indefinite)) {
            assertTrue(NotificationPolicy(context.packageName).accept(listener.extract(sbn(notification)), settings, 100))
        }
    }
}
