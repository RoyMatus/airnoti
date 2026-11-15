package ru.itoltec.airnoti

/** Notification payloads never enter this class: only ephemeral fingerprints and routing metadata. */
internal data class Notice(val key: String, val pkg: String, val fingerprint: String, val group: String?, val summary: Boolean, val service: Boolean, val posted: Long)
internal class NotificationPolicy(private val ownPackage: String) {
    private val seen = LinkedHashMap<String, String>()
    fun seed(notice: Notice) { remember(notice) }
    fun accept(n: Notice, settings: Settings, connectedAt: Long): Boolean {
        val previous = seen[n.key]
        remember(n)
        return settings.accepts(n.pkg) && n.pkg != ownPackage && n.posted > connectedAt && previous != n.fingerprint
    }
    private fun remember(n: Notice) {
        seen.remove(n.key); seen[n.key] = n.fingerprint
        while (seen.size > 512) seen.remove(seen.keys.first())
    }
    fun clear() { seen.clear() }
}
internal data class QueuedNotice(val key: String, val pkg: String, val group: String?, val summary: Boolean, val at: Long)
/** Bounded conflation, child/summary suppression, expiry, and a 3 second minimum send interval. */
internal class AlertQueue {
    private val pending = ArrayDeque<QueuedNotice>()
    private val childGroups = LinkedHashMap<String, Long>()
    private var lastSent = -3_000L
    val size get() = pending.size
    fun offer(event: QueuedNotice) {
        if (!event.summary && event.group != null) {
            childGroups[event.group] = event.at
            pending.removeAll { it.summary && it.group == event.group }
            while (childGroups.size > 128) childGroups.remove(childGroups.keys.first())
        }
        if (event.summary && event.group != null && event.at - (childGroups[event.group] ?: -100_000) < 30_000) return
        pending.removeAll { it.key == event.key }
        while (pending.size >= 16) pending.removeFirst()
        pending.addLast(event)
    }
    fun take(now: Long, settings: Settings): Boolean {
        pending.removeAll { now - it.at > 10_000 || !settings.accepts(it.pkg) }
        if (!settings.enabled) { clear(); return false }
        if (pending.isEmpty() || now - pending.first().at < 700 || now - lastSent < 3_000) return false
        pending.clear(); lastSent = now; return true
    }
    fun clear() { pending.clear(); childGroups.clear() }
}
