package ru.itoltec.airnoti

import org.junit.Assert.*
import org.junit.Test

class NotificationPolicyTest {
    private val selected = Settings(enabled = true, packages = setOf("messages"))
    private fun n(key: String = "1", pkg: String = "messages", hash: String = "a", service: Boolean = false, time: Long = 101) = Notice(key, pkg, hash, null, false, service, time)
    @Test fun selectionAndAllRetainIndividualChoice() {
        val all = selected.copy(all = true)
        assertTrue(all.accepts("newly.installed"))
        assertEquals(setOf("messages"), all.copy(all = false).packages)
        assertFalse(all.copy(all = false).accepts("newly.installed"))
    }
    @Test fun disabledNeverAccepts() { assertFalse(NotificationPolicy("self").accept(n(), selected.copy(enabled = false, all = true), 100)) }
    @Test fun ownAndServiceAndUnselectedAreRejected() {
        assertFalse(NotificationPolicy("self").accept(n(pkg = "self"), selected.copy(all = true), 100))
        assertFalse(NotificationPolicy("self").accept(n(service = true), selected, 100))
        assertFalse(NotificationPolicy("self").accept(n(pkg = "other"), selected, 100))
    }
    @Test fun reusedIdNewContentWorksButExactRepostDoesNot() {
        val p = NotificationPolicy("self")
        assertTrue(p.accept(n(), selected, 100))
        assertFalse(p.accept(n(time = 102), selected, 100))
        assertTrue(p.accept(n(hash = "new-message", time = 103), selected, 100))
    }
    @Test fun existingNotificationsAreSeededWithoutReplay() {
        val p = NotificationPolicy("self")
        p.seed(n())
        assertFalse(p.accept(n(), selected, 100))
        assertFalse(p.accept(n(key = "old", time = 99), selected, 100))
        assertTrue(p.accept(n(hash = "new"), selected, 100))
    }
    @Test fun boundedQueueExpiresAndNeverBackfillsOldAlerts() {
        val q = AlertQueue()
        repeat(100) { q.offer(QueuedNotice("$it", "messages", null, false, 0)) }
        assertEquals(16, q.size)
        assertFalse(q.take(10_001, selected)); assertEquals(0, q.size)
    }
    @Test fun burstsConflateAndRateLimit() {
        val q = AlertQueue()
        repeat(10) { q.offer(QueuedNotice("$it", "messages", null, false, 0)) }
        assertFalse(q.take(699, selected)); assertTrue(q.take(700, selected)); assertEquals(0, q.size)
        q.offer(QueuedNotice("next", "messages", null, false, 800))
        assertFalse(q.take(1500, selected)); assertTrue(q.take(3700, selected))
    }
    @Test fun summariesSuppressedForChildInEitherOrder() {
        val q = AlertQueue()
        q.offer(QueuedNotice("summary", "messages", "g", true, 0))
        q.offer(QueuedNotice("child", "messages", "g", false, 10))
        assertEquals(1, q.size)
        assertTrue(q.take(710, selected))
        q.offer(QueuedNotice("summary2", "messages", "g", true, 800))
        assertEquals(0, q.size)
    }
    @Test fun disableAndSelectionChangeDiscardPending() {
        val q = AlertQueue(); q.offer(QueuedNotice("1", "messages", null, false, 0))
        assertFalse(q.take(1000, selected.copy(enabled = false))); assertEquals(0, q.size)
        q.offer(QueuedNotice("2", "other", null, false, 1000))
        assertFalse(q.take(2000, selected)); assertEquals(0, q.size)
    }
    @Test fun sameKeyQueueIsReplaced() {
        val q = AlertQueue()
        repeat(10) { q.offer(QueuedNotice("1", "messages", null, false, it.toLong())) }
        assertEquals(1, q.size)
    }
    @Test fun protocolProducesOneAtomicAlternateRestoreMessage() {
        assertArrayEquals(byteArrayOf(10,4,10,2,8,3,10,4,10,2,8,2), HapticProtocol.tap(2))
        assertArrayEquals(byteArrayOf(10,4,10,2,8,2,10,4,10,2,8,3), HapticProtocol.tap(3))
    }
    @Test(expected = IllegalArgumentException::class) fun unsupportedStrengthCannotBeWritten() { HapticProtocol.write(1) }
}
