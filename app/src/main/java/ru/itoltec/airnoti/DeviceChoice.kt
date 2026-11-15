package ru.itoltec.airnoti

internal data class BondedCandidate(val address: String, val name: String?)
internal object DeviceChoice {
    fun choose(saved: String, devices: List<BondedCandidate>): String? {
        if (devices.any { it.address == saved }) return saved
        // Exact product name evidence from fitbit-air; do not auto-pick arbitrary BLE devices.
        return devices.filter { it.name?.contains("Fitbit Air", ignoreCase = true) == true }.singleOrNull()?.address
    }
}
