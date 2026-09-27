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
    )
}
