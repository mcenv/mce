package mce.util.rcon

import java.io.InputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.concurrent.atomic.AtomicInteger

class Packet private constructor(
    val id: Int,
    val type: PacketType,
    val body: String,
) {
    val size: Int get() = SIZE_HEADER + body.toByteArray().size

    fun toByteArray(): ByteArray =
        ByteBuffer
            .allocate(size + Int.SIZE_BYTES)
            .order(ByteOrder.LITTLE_ENDIAN)
            .putInt(size)
            .putInt(id)
            .putInt(type)
            .put(body.toByteArray())
            .put(NULL)
            .put(NULL)
            .flip()
            .array()

    companion object {
        private const val NULL: Byte = 0
        private const val SIZE_HEADER: Int = Int.SIZE_BYTES + Int.SIZE_BYTES + Byte.SIZE_BYTES + Byte.SIZE_BYTES

        private val id: AtomicInteger = AtomicInteger()

        operator fun invoke(type: PacketType, body: String): Packet = Packet(id.getAndIncrement(), type, body)

        private fun InputStream.readInt(): Int =
            ByteBuffer
                .wrap(readNBytes(Int.SIZE_BYTES))
                .order(ByteOrder.LITTLE_ENDIAN)
                .int

        fun InputStream.readPacket(): Packet {
            val size = readInt()
            val id = readInt()
            val type = readInt()
            val body = String(readNBytes(size - 8))
            return Packet(id, type, body)
        }
    }
}
