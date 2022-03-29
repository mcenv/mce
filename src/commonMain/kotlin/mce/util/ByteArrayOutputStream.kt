package mce.util

import kotlin.math.max

class ByteArrayOutputStream(
    capacity: Int = 32,
) {
    private var buffer: ByteArray = ByteArray(capacity)
    private var count: Int = 0
    private val writeBuffer: ByteArray = ByteArray(Long.SIZE_BYTES)

    private fun ensureCapacity(minCapacity: Int) {
        val oldCapacity = buffer.size
        val minGrowth = minCapacity - oldCapacity
        if (minGrowth > 0) {
            buffer = buffer.copyOf(oldCapacity + max(minGrowth, oldCapacity))
        }
    }

    private fun write(byte: Int) {
        ensureCapacity(count + Byte.SIZE_BYTES)
        buffer[count++] = byte.toByte()
    }

    private fun write(buffer: ByteArray, size: Int) {
        ensureCapacity(count + buffer.size)
        buffer.copyInto(this.buffer, count, 0, size)
        count += size
    }

    fun writeBoolean(value: Boolean) {
        write(if (value) 1 else 0)
    }

    fun writeByte(value: Byte) {
        write(value.toInt())
    }

    fun writeShort(value: Short) {
        write(writeBuffer.also {
            it[0] = (value.toInt() ushr 8).toByte()
            it[1] = (value.toInt() ushr 0).toByte()
        }, Short.SIZE_BYTES)
    }

    fun writeChar(value: Char) {
        write(writeBuffer.also {
            it[0] = (value.code ushr 8).toByte()
            it[1] = (value.code ushr 0).toByte()
        }, Char.SIZE_BYTES)
    }

    fun writeInt(value: Int) {
        write(writeBuffer.also {
            it[0] = (value ushr 24).toByte()
            it[1] = (value ushr 16).toByte()
            it[2] = (value ushr 8).toByte()
            it[3] = (value ushr 0).toByte()
        }, Int.SIZE_BYTES)
    }

    fun writeLong(value: Long) {
        write(writeBuffer.also {
            it[0] = (value ushr 56).toByte()
            it[1] = (value ushr 48).toByte()
            it[2] = (value ushr 40).toByte()
            it[3] = (value ushr 32).toByte()
            it[4] = (value ushr 24).toByte()
            it[5] = (value ushr 16).toByte()
            it[6] = (value ushr 8).toByte()
            it[7] = (value ushr 0).toByte()
        }, Long.SIZE_BYTES)
    }

    fun writeFloat(value: Float): Unit = writeInt(value.toBits())

    fun writeDouble(value: Double): Unit = writeLong(value.toBits())

    fun writeString(value: String) {
        val buffer = value.encodeToByteArray()
        writeInt(buffer.size)
        write(buffer, buffer.size)
    }

    fun toByteArray(): ByteArray = buffer.copyOf()
}
