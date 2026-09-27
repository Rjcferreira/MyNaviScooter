package com.mynavisccooter.capture

import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothGattService
import android.os.Build
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

data class CharacteristicCapture(
    val uuid: String,
    val properties: String,
    val valueHex: String?,
    val valueText: String?
)

data class ServiceCapture(
    val uuid: String,
    val characteristics: List<CharacteristicCapture>
)

data class ProtocolProbeCapture(
    val attempted: Boolean,
    val notificationCharacteristics: List<String>,
    val requestHex: String?,
    val rawNotificationHex: List<String>,
    val preComm: PreCommResult?,
    val authenticationAttempted: Boolean,
    val authenticated: Boolean,
    val authenticationNote: String,
    val note: String
)

data class RegisterCapture(val device: Int, val register: Int, val name: String, val expectedBytes: Int, val valueHex: String)

data class DeepScanCapture(
    val plan: String = "zt3-documented-registers/v1",
    val readOnly: Boolean = true,
    val results: List<RegisterCapture> = emptyList(),
    val timedOut: List<String> = emptyList()
)

data class CaptureReport(
    val capturedAtUtc: String,
    val appVersion: String,
    val androidVersion: String,
    val deviceName: String,
    val deviceAddress: String,
    val rssi: Int?,
    val manufacturerData: Map<String, String>,
    val model: ModelProfile,
    val services: List<ServiceCapture>,
    val protocolProbe: ProtocolProbeCapture?,
    val initialState: List<RegisterCapture> = emptyList(),
    val deepScan: DeepScanCapture? = null,
    val safety: Map<String, Any>,
    val diagnosticEvents: List<String> = emptyList(),
    val outcome: String = "unknown"
) {
    fun toJson(): String {
        fun esc(value: String): String = value
            .replace("\\", "\\\\")
            .replace("\"", "\\\"")
            .replace("\n", "\\n")
            .replace("\r", "\\r")
            .replace("\t", "\\t")

        fun mapJson(map: Map<String, String>): String = map.entries.joinToString(",", "{", "}") {
            "\"${esc(it.key)}\":\"${esc(it.value)}\""
        }

        val modelJson = """
            {"name":"${esc(model.name)}","serverId":${model.serverId},"hardwareId":${model.hardwareId},"expectedCommandCount":${model.expectedCommandCount},"target":"${esc(model.target)}","confidence":"${esc(model.confidence)}"}
        """.trimIndent()

        val serviceJson = services.joinToString(",", "[", "]") { service ->
            val chars = service.characteristics.joinToString(",", "[", "]") { c ->
                "{" +
                    "\"uuid\":\"${esc(c.uuid)}\"," +
                    "\"properties\":\"${esc(c.properties)}\"," +
                    "\"valueHex\":${c.valueHex?.let { "\"${esc(it)}\"" } ?: "null"}," +
                    "\"valueText\":${c.valueText?.let { "\"${esc(it)}\"" } ?: "null"}" +
                    "}"
            }
            "{\"uuid\":\"${esc(service.uuid)}\",\"characteristics\":$chars}"
        }

        val probeJson = protocolProbe?.let { probe ->
            val preComm = probe.preComm?.let { p ->
                "{\"index\":${p.index},\"authParameterHex\":\"${esc(p.authParameterHex)}\",\"reportedSerial\":${p.reportedSerial?.let { "\"${esc(it)}\"" } ?: "null"},\"frameHex\":\"${esc(p.frameHex)}\"}"
            } ?: "null"
            "{\"attempted\":${probe.attempted},\"notificationCharacteristics\":${probe.notificationCharacteristics.joinToString(",", "[", "]") { "\"${esc(it)}\"" }},\"requestHex\":${probe.requestHex?.let { "\"${esc(it)}\"" } ?: "null"},\"rawNotificationHex\":${probe.rawNotificationHex.joinToString(",", "[", "]") { "\"${esc(it)}\"" }},\"preComm\":$preComm,\"authenticationAttempted\":${probe.authenticationAttempted},\"authenticated\":${probe.authenticated},\"authenticationNote\":\"${esc(probe.authenticationNote)}\",\"note\":\"${esc(probe.note)}\"}"
        } ?: "null"
        val stateJson = initialState.joinToString(",", "[", "]") { r ->
            "{\"device\":${r.device},\"register\":${r.register},\"name\":\"${esc(r.name)}\",\"expectedBytes\":${r.expectedBytes},\"valueHex\":\"${esc(r.valueHex)}\"}"
        }
        val deepScanJson = deepScan?.let { scan ->
            val rows = scan.results.joinToString(",", "[", "]") { r ->
                "{\"device\":${r.device},\"register\":${r.register},\"name\":\"${esc(r.name)}\",\"expectedBytes\":${r.expectedBytes},\"valueHex\":\"${esc(r.valueHex)}\"}"
            }
            "{\"plan\":\"${esc(scan.plan)}\",\"readOnly\":${scan.readOnly},\"captured\":${scan.results.size},\"requested\":${Zt3DeepScanPlan.documented.size},\"timedOut\":${scan.timedOut.joinToString(",", "[", "]") { "\"${esc(it)}\"" }},\"registers\":$rows}"
        } ?: "null"

        return """
            {
              "schema":"mynavi-scooter-capture/v1",
              "capturedAtUtc":"${esc(capturedAtUtc)}",
              "appVersion":"${esc(appVersion)}",
              "buildRevision":"${BuildConfig.REVISION}",
              "outcome":"${esc(outcome)}",
              "diagnosticEvents":${diagnosticEvents.joinToString(",", "[", "]") { "\"${esc(it)}\"" }},
              "androidVersion":"${esc(androidVersion)}",
              "deviceName":"${esc(deviceName)}",
              "deviceAddress":"${esc(deviceAddress)}",
              "rssi":${rssi ?: "null"},
              "manufacturerData":${mapJson(manufacturerData)},
              "model":$modelJson,
              "services":$serviceJson,
              "protocolProbe":$probeJson,
              "initialState":{"complete":${initialState.size == Zt3BackupPlan.required.size && initialState.all { it.valueHex.length == it.expectedBytes * 2 }},"captured":${initialState.size},"required":${Zt3BackupPlan.required.size},"registers":$stateJson},
              "deepScan":$deepScanJson,
              "safety":{"readOnly":${safety["readOnly"] == true},"configurationWritesPerformed":${safety["configurationWritesPerformed"] == true},"firmwareFlashed":${safety["firmwareFlashed"] == true},"pairingWriteAttempted":${safety["pairingWriteAttempted"] == true},"pairingAcceptedByScooter":${safety["pairingAcceptedByScooter"] == true},"note":"Pairing may change Bluetooth credentials. Configuration writes, when requested, are limited to speed registers 0x47 and 0x48. Firmware commands are never sent."}
            }
        """.trimIndent()
    }

    companion object {
        fun nowUtc(): String = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US).apply {
            timeZone = java.util.TimeZone.getTimeZone("UTC")
        }.format(Date())
    }
}

fun BluetoothGattCharacteristic.propertyLabel(): String {
    val p = properties
    return buildList {
        if (p and BluetoothGattCharacteristic.PROPERTY_READ != 0) add("read")
        if (p and BluetoothGattCharacteristic.PROPERTY_WRITE != 0) add("write")
        if (p and BluetoothGattCharacteristic.PROPERTY_WRITE_NO_RESPONSE != 0) add("writeNoResponse")
        if (p and BluetoothGattCharacteristic.PROPERTY_NOTIFY != 0) add("notify")
        if (p and BluetoothGattCharacteristic.PROPERTY_INDICATE != 0) add("indicate")
    }.joinToString("|").ifBlank { "none" }
}

fun BluetoothGattService.toCapture(): ServiceCapture = ServiceCapture(
    uuid = uuid.toString(),
    characteristics = characteristics.map { c ->
        CharacteristicCapture(c.uuid.toString(), c.propertyLabel(), null, null)
    }
)

fun bytesToHex(bytes: ByteArray?): String? = bytes?.joinToString("") { "%02X".format(it.toInt() and 0xFF) }

fun bytesToSafeText(bytes: ByteArray?): String? = bytes?.toString(Charsets.UTF_8)?.filter { it == '\n' || it == '\r' || it.code in 32..126 }
    ?.takeIf { it.isNotBlank() }
