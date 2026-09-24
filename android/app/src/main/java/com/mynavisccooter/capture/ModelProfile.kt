package com.mynavisccooter.capture

data class ModelProfile(
    val name: String,
    val serverId: Int,
    val hardwareId: Int,
    val expectedCommandCount: Int,
    val target: String,
    val confidence: String
)

object ModelProfiles {
    val ZT3_PRO = ModelProfile("Segway ZT3 Pro", 10256, 256, 191, "X3 / VCU 0x16", "high")
    val F3_PRO = ModelProfile("Segway F3 Pro", 10259, 259, 192, "X3 / VCU 0x16", "high")
    val GT3 = ModelProfile("Segway GT3", 10257, 257, 192, "X3 / VCU 0x16", "high")
    val UNKNOWN = ModelProfile("Segway / Ninebot desconhecida", -1, -1, -1, "unknown", "low")

    fun fromAdvertisement(name: String?, manufacturerHex: String): ModelProfile {
        val n = (name ?: "").lowercase()
        return when {
            n.contains("zt3") -> ZT3_PRO
            n.contains("f3") -> F3_PRO
            n.contains("gt3") -> GT3
            // The X3 manufacturer signature is useful even when Android hides the name.
            manufacturerHex.contains("4E430100020000FC") -> UNKNOWN
            else -> UNKNOWN
        }
    }
}
