package com.mynavisccooter.capture

/** Read-only rendering of captured settings. Does not construct or send commands. */
object X3SettingsReadout {
    fun lines(rows: List<RegisterCapture>): List<String> {
        fun word(device: Int, register: Int): Int? {
            val row = rows.singleOrNull { it.device == device && it.register == register } ?: return null
            if (row.expectedBytes != 2 || !row.valueHex.matches(Regex("[0-9A-Fa-f]{4}"))) return null
            return row.valueHex.take(2).toInt(16) or (row.valueHex.takeLast(2).toInt(16) shl 8)
        }
        fun version(raw: Int): String = raw.toString(16).padStart(3, '0').map { it.digitToInt(16) }.joinToString(".")
        return buildList {
            word(0x16, 0x17)?.let { add("VCU: ${version(it)}") }
            // Keep other firmware encodings raw until their format is confirmed per model.
            word(0x16, 0x18)?.let { add("Motor/MCU · versão codificada: 0x%04X".format(it)) }
            word(0x16, 0x19)?.let { add("BMS · versão codificada: 0x%04X".format(it)) }
            word(0x04, 0x01)?.let { add("Bluetooth · versão codificada: 0x%04X".format(it)) }
            word(0x16, 0x42)?.takeIf { it in 0..5 }?.let {
                add(if (it == 0) "Arranque sem impulso: ativo (0 km/h)" else "Arranque com impulso: $it km/h")
            }
            word(0x16, 0x49)?.takeIf { it in 1..30 }?.let { add("Desligar automaticamente: $it min") }
            word(0x07, 0x82)?.takeIf { it in 1..100 }?.let { add("Limite de carga: $it %") }
            // Keep disputed speed masks raw rather than asserting an unsupported interpretation.
            word(0x16, 0x47)?.let { add("Eco/Drive · registo 0x47: %04X (hex)".format(it)) }
            word(0x16, 0x48)?.let { add("Sport/Race · registo 0x48: %04X (hex)".format(it)) }
        }
    }
}
