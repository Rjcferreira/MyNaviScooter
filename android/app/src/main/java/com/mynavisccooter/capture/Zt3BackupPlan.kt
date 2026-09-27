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
