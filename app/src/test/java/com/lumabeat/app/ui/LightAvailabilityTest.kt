package com.lumabeat.app.ui

import com.lumabeat.app.wiz.WizLight
import org.junit.Assert.assertEquals
import org.junit.Test

class LightAvailabilityTest {
    @Test
    fun onlyPoweredIncludedLightsAreActive() {
        val powered = light("192.168.1.10", "on", isOn = true)
        val off = light("192.168.1.20", "off", isOn = false)
        val unreachable = light("192.168.1.30", "unknown", isOn = null)
        val excluded = light("192.168.1.40", "excluded", isOn = true)
        val state = LumaBeatUiState(
            lights = listOf(powered, off, unreachable, excluded),
            includedLightKeys = setOf("on", "off", "unknown"),
        )

        assertEquals(1, state.activeLightCount())
    }

    private fun light(ip: String, mac: String, isOn: Boolean?) = WizLight(
        ipAddress = ip,
        macAddress = mac,
        model = "WiZ",
        firmwareVersion = "test",
        isOn = isOn,
    )
}
