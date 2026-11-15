package ru.itoltec.airnoti
import org.junit.Assert.*
import org.junit.Test
class DeviceChoiceTest {
    @Test fun selectsTheOnlyAirAutomatically() {
        assertEquals("a", DeviceChoice.choose("", listOf(BondedCandidate("x", "Headphones"), BondedCandidate("a", "Fitbit Air"))))
    }
    @Test fun preservesExplicitChoiceEvenIfRenamed() {
        assertEquals("x", DeviceChoice.choose("x", listOf(BondedCandidate("x", "My band"), BondedCandidate("a", "Fitbit Air"))))
    }
    @Test fun neverGuessesBetweenMultipleAirDevices() {
        assertNull(DeviceChoice.choose("", listOf(BondedCandidate("a", "Fitbit Air"), BondedCandidate("b", "Fitbit Air"))))
    }
    @Test fun doesNotPickUnrelatedDevicesOrMissingNames() {
        assertNull(DeviceChoice.choose("gone", listOf(BondedCandidate("x", "AirPods"), BondedCandidate("b", null))))
    }
}
