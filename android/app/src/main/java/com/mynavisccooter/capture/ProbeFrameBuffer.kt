package com.mynavisccooter.capture

class ProbeFrameBuffer {
    private var buffer = byteArrayOf()
    fun clear() { buffer = byteArrayOf() }
    fun append(value: ByteArray): List<ByteArray> {
        buffer += value
        val frames = mutableListOf<ByteArray>()
        while (buffer.size >= 2) {
            val start = (0 until buffer.size - 1).firstOrNull {
                buffer[it] == 0x5A.toByte() && buffer[it + 1] == 0xA5.toByte()
            }
            if (start == null) { buffer = buffer.takeLast(1).toByteArray(); break }
            buffer = buffer.copyOfRange(start, buffer.size)
            if (buffer.size < 3) break
            val total = (buffer[2].toInt() and 0xFF) + 13
            if (buffer.size < total) break
            frames += buffer.copyOfRange(0, total)
            buffer = buffer.copyOfRange(total, buffer.size)
        }
        return frames
    }
}
