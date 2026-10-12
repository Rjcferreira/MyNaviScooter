package com.mynavisccooter.capture

data class RegisterSpec(val device: Int, val register: Int, val name: String, val length: Int = 2)

object Zt3BackupPlan {
    val required = listOf(
        RegisterSpec(0x16, 0x1D, "vcu_bool"), RegisterSpec(0x16, 0x1E, "vcu_bool_2"),
        RegisterSpec(0x16, 0x1F, "vcu_bool_3"), RegisterSpec(0x16, 0x42, "start_speed"),
        RegisterSpec(0x16, 0x47, "max_speed_eco_drive"), RegisterSpec(0x16, 0x48, "max_speed_sports"),
        RegisterSpec(0x16, 0x49, "auto_off_time"), RegisterSpec(0x16, 0x4A, "custom_key"),
        RegisterSpec(0x16, 0x5D, "tail_light_mode"), RegisterSpec(0x16, 0x6E, "acc_level"),
        RegisterSpec(0x16, 0x70, "kers_level"), RegisterSpec(0x16, 0x74, "alarm_level"),
        RegisterSpec(0x16, 0x76, "voice_volume"), RegisterSpec(0x07, 0x82, "charge_threshold")
    )
}

/**
 * Read-only inventory derived from the public X3/ZT3 device profile.
 *
 * This is deliberately not a brute-force 0x00..0xFF sweep. Unknown offsets can
 * have undocumented behaviour and a BLE register view is not a memory dump.
 * Every item below is declared as readable by the known protocol profile.
 */
object Zt3DeepScanPlan {
    val documented = listOf(
        RegisterSpec(0x16, 0x10, "vcu_serial", 14),
        RegisterSpec(0x16, 0x17, "vcu_version"),
        RegisterSpec(0x16, 0x18, "mcu_version"),
        RegisterSpec(0x16, 0x19, "bms_version"),
        RegisterSpec(0x16, 0x1D, "vcu_bool"),
        RegisterSpec(0x16, 0x1E, "vcu_bool_2"),
        RegisterSpec(0x16, 0x1F, "vcu_bool_3"),
        RegisterSpec(0x16, 0x42, "start_speed"),
        RegisterSpec(0x16, 0x47, "max_speed_eco_drive"),
        RegisterSpec(0x16, 0x48, "max_speed_sports"),
        RegisterSpec(0x16, 0x49, "auto_off_time"),
        RegisterSpec(0x16, 0x4A, "custom_key"),
        RegisterSpec(0x16, 0x5D, "tail_light_mode"),
        RegisterSpec(0x16, 0x6E, "acc_level"),
        RegisterSpec(0x16, 0x70, "kers_level"),
        RegisterSpec(0x16, 0x74, "alarm_level"),
        RegisterSpec(0x16, 0x76, "voice_volume"),
        RegisterSpec(0x16, 0xC0, "mcu_uid", 12),
        RegisterSpec(0x16, 0xDA, "vcu_uid", 12),
        RegisterSpec(0x16, 0xE4, "vcu_rand", 6),
        RegisterSpec(0x16, 0xE7, "vcu_flag"),
        RegisterSpec(0x02, 0xE4, "mcu_rand", 6),
        RegisterSpec(0x02, 0xE7, "mcu_flag"),
        RegisterSpec(0x04, 0x01, "ble_version"),
        RegisterSpec(0x07, 0x82, "charge_threshold")
        ,RegisterSpec(0x16, 0x55, "battery_percent")
        ,RegisterSpec(0x16, 0x57, "vcu_speed")
        ,RegisterSpec(0x16, 0x58, "error_code")
        ,RegisterSpec(0x16, 0x59, "warning_code")
        ,RegisterSpec(0x16, 0x5A, "drive_mode")
        ,RegisterSpec(0x16, 0x5F, "remaining_range")
        ,RegisterSpec(0x16, 0x62, "odometer", 4)
        ,RegisterSpec(0x16, 0x64, "total_runtime", 4)
        ,RegisterSpec(0x16, 0x68, "trip_distance", 4)
        ,RegisterSpec(0x16, 0x6A, "trip_time", 4)
        ,RegisterSpec(0x16, 0x6B, "vcu_temperature")
        ,RegisterSpec(0x07, 0x59, "battery_cycles")
        ,RegisterSpec(0x07, 0x8C, "battery_voltage")
        ,RegisterSpec(0x07, 0x8D, "battery_current")
        ,RegisterSpec(0x07, 0x8E, "battery_health")
        ,RegisterSpec(0x07, 0x8F, "battery_soc")
        ,RegisterSpec(0x07, 0x92, "charging_state")
        ,RegisterSpec(0x07, 0x96, "battery_temperatures", 4)
        ,RegisterSpec(0x07, 0xA0, "cell_voltages", 26)
        ,RegisterSpec(0x07, 0xF9, "battery_temperature")
        ,RegisterSpec(0x02, 0x3E, "mcu_temperature")
        ,RegisterSpec(0x02, 0x48, "motor_temperature_a")
        ,RegisterSpec(0x02, 0x49, "motor_temperature_b")
        ,RegisterSpec(0x02, 0x86, "mcu_speed")
        ,RegisterSpec(0x02, 0x8F, "mcu_voltage")
    )
}

data class TelemetrySnapshot(
    val speedKmh: Float? = null, val batteryPercent: Int? = null,
    val voltageV: Float? = null, val currentA: Float? = null,
    val powerW: Float? = null, val rangeKm: Float? = null,
    val vcuTempC: Float? = null, val batteryTempC: Float? = null,
    val motorTempC: Float? = null, val odometerKm: Float? = null,
    val tripKm: Float? = null, val errorCode: Int? = null,
    val warningCode: Int? = null, val cellVoltagesMv: List<Int> = emptyList()
)

object Zt3TelemetryDecoder {
    private fun bytes(hex: String): ByteArray = runCatching {
        hex.chunked(2).map { it.toInt(16).toByte() }.toByteArray()
    }.getOrDefault(byteArrayOf())
    private fun u16(b: ByteArray) = if (b.size >= 2) (b[0].toInt() and 255) or ((b[1].toInt() and 255) shl 8) else 0
    private fun s16(b: ByteArray): Int = u16(b).toShort().toInt()
    private fun find(rows: List<RegisterCapture>, name: String) = rows.firstOrNull { it.name == name }?.let { bytes(it.valueHex) }
    fun decode(rows: List<RegisterCapture>): TelemetrySnapshot {
        val volts = find(rows, "battery_voltage")?.let { u16(it) / 100f }
        val amps = find(rows, "battery_current")?.let { s16(it) / 100f }
        val cells = find(rows, "cell_voltages")?.toList()?.chunked(2)?.filter { it.size == 2 }
            ?.map { (it[0].toInt() and 255) or ((it[1].toInt() and 255) shl 8) }.orEmpty()
        return TelemetrySnapshot(
            speedKmh = (find(rows, "mcu_speed") ?: find(rows, "vcu_speed"))?.let { u16(it) / 10f },
            batteryPercent = (find(rows, "battery_soc") ?: find(rows, "battery_percent"))?.firstOrNull()?.toInt()?.and(255),
            voltageV = volts, currentA = amps, powerW = if (volts != null && amps != null) volts * amps else null,
            rangeKm = find(rows, "remaining_range")?.let { u16(it) / 100f },
            vcuTempC = find(rows, "vcu_temperature")?.let { s16(it) / 10f },
            batteryTempC = find(rows, "battery_temperatures")?.let { maxOf(it[0].toInt() and 255, it[1].toInt() and 255).toFloat() },
            motorTempC = find(rows, "motor_temperature_a")?.let { s16(it) / 10f },
            // This ZT3 reports 0x313F for an odometer close to 126 km. Treat
            // the low word as hundredths of a kilometre; retain the raw bytes
            // in the exported register list so this scale remains auditable.
            odometerKm = find(rows, "odometer")?.let { u16(it) / 100f },
            tripKm = find(rows, "trip_distance")?.let { u16(it).toFloat() },
            errorCode = find(rows, "error_code")?.let(::u16), warningCode = find(rows, "warning_code")?.let(::u16),
            cellVoltagesMv = cells
        )
    }
}
