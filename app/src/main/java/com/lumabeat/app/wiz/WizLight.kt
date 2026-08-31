package com.lumabeat.app.wiz

data class WizLight(
    val ipAddress: String,
    val macAddress: String,
    val model: String,
    val firmwareVersion: String,
    val bridgeHost: String? = null,
    val isOn: Boolean? = null,
) {
    val displayName: String
        get() = model.ifBlank { "WiZ ${macAddress.takeLast(6).uppercase()}" }

    val isBridged: Boolean
        get() = bridgeHost != null
}

data class LightColor(
    val red: Int,
    val green: Int,
    val blue: Int,
)
