package com.lumabeat.app.wiz

import android.content.Context
import android.net.wifi.WifiManager
import android.os.Build
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import java.net.SocketTimeoutException
import java.util.concurrent.ConcurrentHashMap

class WizLanController(context: Context) {
    private val appContext = context.applicationContext
    private val wifiManager = appContext.getSystemService(Context.WIFI_SERVICE) as WifiManager
    private val commandSocket = DatagramSocket()
    private val commandSocketLock = Any()
    private val addressCache = ConcurrentHashMap<String, InetAddress>()

    suspend fun discover(timeoutMillis: Int = DISCOVERY_TIMEOUT_MS): List<WizLight> =
        withContext(Dispatchers.IO) {
            val lock = wifiManager.createMulticastLock("lumabeat-wiz-discovery")
            lock.setReferenceCounted(false)
            lock.acquire()
            try {
                discoverOnLan(timeoutMillis).map { light ->
                    light.copy(isOn = queryPowerState(light))
                }
            } finally {
                if (lock.isHeld) lock.release()
            }
        }

    suspend fun setPower(lights: List<WizLight>, enabled: Boolean) =
        sendToAll(lights) {
            JSONObject()
                .put("method", "setPilot")
                .put("params", JSONObject().put("state", enabled))
        }

    suspend fun setBrightness(lights: List<WizLight>, brightnessPercent: Int) =
        sendToAll(lights) {
            JSONObject()
                .put("method", "setPilot")
                .put(
                    "params",
                    JSONObject()
                        .put("state", true)
                        .put("dimming", brightnessPercent.coerceIn(10, 100)),
                )
        }

    suspend fun setColor(
        lights: List<WizLight>,
        color: LightColor,
    ) = sendToAll(lights) {
        JSONObject()
            .put("method", "setPilot")
            .put(
                "params",
                JSONObject()
                    .put("state", true)
                    .put("r", color.red.coerceIn(0, 255))
                    .put("g", color.green.coerceIn(0, 255))
                    .put("b", color.blue.coerceIn(0, 255)),
            )
    }

    suspend fun setColorTemperature(
        lights: List<WizLight>,
        temperatureKelvin: Int,
        brightnessPercent: Int,
    ) = sendToAll(lights) {
        JSONObject()
            .put("method", "setPilot")
            .put(
                "params",
                JSONObject()
                    .put("state", true)
                    .put("temp", temperatureKelvin.coerceIn(2_200, 6_500))
                    .put("dimming", brightnessPercent.coerceIn(10, 100)),
            )
    }

    fun close() {
        commandSocket.close()
    }

    private fun discoverOnLan(timeoutMillis: Int): List<WizLight> {
        val request = JSONObject()
            .put("method", "getSystemConfig")
            .put("params", JSONObject())
            .toString()
            .encodeToByteArray()
        val deadline = System.currentTimeMillis() + timeoutMillis
        val found = linkedMapOf<String, WizLight>()

        DatagramSocket().use { socket ->
            socket.broadcast = true
            socket.soTimeout = RECEIVE_WINDOW_MS
            val broadcastPacket = DatagramPacket(
                request,
                request.size,
                InetAddress.getByName("255.255.255.255"),
                WIZ_PORT,
            )
            socket.send(broadcastPacket)
            if (isAndroidEmulator()) {
                val bridgeRequest = JSONObject()
                    .put("bridge", "discover")
                    .toString()
                    .encodeToByteArray()
                socket.send(
                    DatagramPacket(
                        bridgeRequest,
                        bridgeRequest.size,
                        InetAddress.getByName(EMULATOR_HOST),
                        BRIDGE_PORT,
                    ),
                )
            }
            receiveLightsUntil(socket, deadline, found)
        }

        return found.values.sortedBy(WizLight::displayName)
    }

    private fun receiveLightsUntil(
        socket: DatagramSocket,
        deadline: Long,
        found: MutableMap<String, WizLight>,
    ) {
        val buffer = ByteArray(MAX_PACKET_SIZE)
        while (System.currentTimeMillis() < deadline) {
            val response = DatagramPacket(buffer, buffer.size)
            try {
                socket.receive(response)
                parseLight(response)?.let { light -> found[light.ipAddress] = light }
            } catch (_: SocketTimeoutException) {
                // A short timeout lets us re-check the overall discovery deadline.
            }
        }
    }

    private fun parseLight(packet: DatagramPacket): WizLight? = runCatching {
        val body = packet.data.decodeToString(packet.offset, packet.offset + packet.length)
        val result = JSONObject(body).optJSONObject("result") ?: return null
        val bridgedAddress = result.optString("bridgeIp").takeIf(String::isNotBlank)
        WizLight(
            ipAddress = bridgedAddress ?: packet.address.hostAddress.orEmpty(),
            macAddress = result.optString("mac"),
            model = result.optString("moduleName"),
            firmwareVersion = result.optString("fwVersion"),
            bridgeHost = bridgedAddress?.let { packet.address.hostAddress },
        )
    }.getOrNull()

    private fun queryPowerState(light: WizLight): Boolean? = runCatching {
        val command = JSONObject()
            .put("method", "getPilot")
            .put("params", JSONObject())
        val bridged = light.bridgeHost != null
        val request = if (bridged) {
            JSONObject()
                .put("bridge", "query")
                .put("target", light.ipAddress)
                .put("payload", command)
        } else {
            command
        }
        val data = request.toString().encodeToByteArray()

        DatagramSocket().use { socket ->
            socket.soTimeout = QUERY_TIMEOUT_MS
            socket.send(
                DatagramPacket(
                    data,
                    data.size,
                    InetAddress.getByName(light.bridgeHost ?: light.ipAddress),
                    if (bridged) BRIDGE_PORT else WIZ_PORT,
                ),
            )
            val buffer = ByteArray(MAX_PACKET_SIZE)
            val response = DatagramPacket(buffer, buffer.size)
            socket.receive(response)
            val body = response.data.decodeToString(
                response.offset,
                response.offset + response.length,
            )
            JSONObject(body).optJSONObject("result")?.optBoolean("state")
        }
    }.getOrNull()

    private suspend fun sendToAll(
        lights: List<WizLight>,
        payload: () -> JSONObject,
    ) = withContext(Dispatchers.IO) {
        val command = payload()
        synchronized(commandSocketLock) {
            lights.forEach { light ->
                val bridged = light.bridgeHost != null
                val data = if (bridged) {
                    JSONObject()
                        .put("bridge", "send")
                        .put("target", light.ipAddress)
                        .put("payload", command)
                        .toString()
                        .encodeToByteArray()
                } else {
                    command.toString().encodeToByteArray()
                }
                val packet = DatagramPacket(
                    data,
                    data.size,
                    resolveAddress(light.bridgeHost ?: light.ipAddress),
                    if (bridged) BRIDGE_PORT else WIZ_PORT,
                )
                commandSocket.send(packet)
            }
        }
    }

    private fun resolveAddress(host: String): InetAddress =
        addressCache.getOrPut(host) { InetAddress.getByName(host) }

    private fun isAndroidEmulator(): Boolean =
        Build.FINGERPRINT.startsWith("generic") ||
            Build.FINGERPRINT.contains("emulator") ||
            Build.MODEL.contains("sdk_gphone")

    private companion object {
        const val WIZ_PORT = 38_899
        const val DISCOVERY_TIMEOUT_MS = 2_500
        const val RECEIVE_WINDOW_MS = 200
        const val QUERY_TIMEOUT_MS = 1_200
        const val MAX_PACKET_SIZE = 2_048
        const val BRIDGE_PORT = 38_900
        const val EMULATOR_HOST = "10.0.2.2"
    }
}
