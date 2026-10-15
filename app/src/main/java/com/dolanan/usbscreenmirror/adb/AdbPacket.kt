package com.dolanan.usbscreenmirror.adb

import java.io.ByteArrayOutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder

data class AdbPacket(val command: Int, val arg0: Int, val arg1: Int, val payload: ByteArray = byteArrayOf()) {
    fun encode(): ByteArray {
        val out = ByteBuffer.allocate(24 + payload.size).order(ByteOrder.LITTLE_ENDIAN)
        out.putInt(command); out.putInt(arg0); out.putInt(arg1); out.putInt(payload.size)
        out.putInt(checksum(payload)); out.putInt(command xor -1); out.put(payload)
        return out.array()
    }
    companion object {
        const val CNXN = 0x4e584e43
        const val AUTH = 0x48545541
        const val OPEN = 0x4e45504f
        const val OKAY = 0x59414b4f
        const val WRTE = 0x45545257
        const val CLSE = 0x45534c43
        const val AUTH_TOKEN = 1
        const val AUTH_SIGNATURE = 2
        const val AUTH_PUBLIC_KEY = 3
        const val VERSION = 0x01000001
        const val MAX_DATA = 256 * 1024
        fun checksum(bytes: ByteArray): Int { var sum = 0; for (b in bytes) sum += b.toInt() and 0xff; return sum }
        fun commandName(command: Int): String = when (command) { CNXN -> "CNXN"; AUTH -> "AUTH"; OPEN -> "OPEN"; OKAY -> "OKAY"; WRTE -> "WRTE"; CLSE -> "CLSE"; else -> "????" }
    }
}

/** Incremental ADB framing parser; USB reads are not assumed to align to packet boundaries. */
class AdbPacketParser(private val maxPayload: Int = AdbPacket.MAX_DATA) {
    private val pending = ByteArrayOutputStream()
    fun feed(chunk: ByteArray, length: Int = chunk.size): List<AdbPacket> {
        if (length <= 0) return emptyList()
        pending.write(chunk, 0, length)
        val bytes = pending.toByteArray()
        val packets = ArrayList<AdbPacket>()
        var offset = 0
        while (bytes.size - offset >= 24) {
            val h = ByteBuffer.wrap(bytes, offset, 24).order(ByteOrder.LITTLE_ENDIAN)
            val command = h.int; val a0 = h.int; val a1 = h.int; val len = h.int; val sum = h.int; val magic = h.int
            require(magic == (command xor -1)) { "ADB header magic mismatch" }
            require(len in 0..maxPayload) { "ADB payload length out of range: $len" }
            if (bytes.size - offset < 24 + len) break
            val data = bytes.copyOfRange(offset + 24, offset + 24 + len)
            require(AdbPacket.checksum(data) == sum) { "ADB checksum mismatch" }
            packets += AdbPacket(command, a0, a1, data)
            offset += 24 + len
        }
        pending.reset()
        if (offset < bytes.size) pending.write(bytes, offset, bytes.size - offset)
        return packets
    }
}
