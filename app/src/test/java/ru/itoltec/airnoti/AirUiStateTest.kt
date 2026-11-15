package ru.itoltec.airnoti

import org.junit.Assert.*
import org.junit.Test

class AirUiStateTest {
    private val ready = AirUiState(Settings(enabled = true, address = "band"), hasBluetooth = true, bluetoothOn = true, access = true, listener = true)
    @Test fun emptySelectionDoesNotBlockManualTest() {
        assertEquals(R.string.ui_choose_apps, ready.status)
        assertTrue(ready.canTest)
        assertTrue(ready.copy(access = false, listener = false).canTest)
    }
    @Test fun unavailablePrerequisitesAndSendingBlockTest() {
        assertFalse(ready.copy(hasBluetooth = false).canTest)
        assertFalse(ready.copy(bluetoothOn = false).canTest)
        assertFalse(ready.copy(sending = true).canTest)
        assertFalse(ready.copy(settings = ready.settings.copy(enabled = false)).canTest)
        assertFalse(ready.copy(settings = ready.settings.copy(address = "")).canTest)
    }
    @Test fun statusPriorityAndResultMessagesRemainDistinct() {
        assertEquals(R.string.permission_needed, ready.copy(hasBluetooth = false, bluetoothOn = false, sending = true).status)
        assertEquals(R.string.bt_off, ready.copy(bluetoothOn = false, sending = true).status)
        assertEquals(R.string.sending, ready.copy(sending = true, access = false).status)
        assertEquals(R.string.failed, ready.copy(result = AirTransport.Result.FAILED).status)
        assertEquals(6, AirTransport.Result.entries.map { it.message() }.distinct().size)
    }
    @Test fun freshInstallDoesNotEnableOrSelectAnything() {
        val fresh = Settings()
        assertFalse(fresh.enabled); assertFalse(fresh.all); assertTrue(fresh.packages.isEmpty())
    }
}
