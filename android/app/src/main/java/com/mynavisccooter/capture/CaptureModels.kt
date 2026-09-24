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
    val safety: Map<String, Any>
) {
    fun toJson(): String {
        fun esc(value: String): String = value
            .replace("\\", "\\\\")
            .replace("\"", "\\\"")
            .replace("\n", "\\n")

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

        return """
            {
              "schema":"mynavi-scooter-capture/v1",
              "capturedAtUtc":"${esc(capturedAtUtc)}",
              "appVersion":"${esc(appVersion)}",
              "androidVersion":"${esc(androidVersion)}",
              "deviceName":"${esc(deviceName)}",
              "deviceAddress":"${esc(deviceAddress)}",
              "rssi":${rssi ?: "null"},
              "manufacturerData":${mapJson(manufacturerData)},
              "model":$modelJson,
              "services":$serviceJson,
              "safety":{"readOnly":true,"writesPerformed":false,"firmwareFlashed":false,"note":"Initial BLE/GATT capture only; no scooter configuration was changed."}
            }
        """.trimIndent()
    }

    companion object {
        fun nowUtc(): String = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US).format(Date())
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
