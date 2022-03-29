package mce.util

class ByteArrayInputStream(
    private val buffer: ByteArray,
) {
    private var count: Int = 0

    private fun read(): Int = buffer[count++].toInt() and 0xff

    private fun read(size: Int): ByteArray = buffer.copyOfRange(count, count + size).also { count += size }

    fun readBoolean(): Boolean = read() != 0

    fun readByte(): Byte = read().toByte()

    fun readShort(): Short = ((read() shl 8) + (read() shl 0)).toShort()

    fun readChar(): Char = ((read() shl 8) + (read() shl 0)).toChar()

    fun readInt(): Int =
        (read() shl 24) +
                (read() shl 16) +
                (read() shl 8) +
                (read() shl 0)

    fun readLong(): Long =
        (read().toLong() shl 56) +
                (read().toLong() shl 48) +
                (read().toLong() shl 40) +
                (read().toLong() shl 32) +
                (read().toLong() shl 24) +
                (read().toLong() shl 16) +
                (read().toLong() shl 8) +
                (read().toLong() shl 0)

    fun readFloat(): Float = Float.fromBits(readInt())

    fun readDouble(): Double = Double.fromBits(readLong())

    fun readString(): String {
        val size = readInt()
        return read(size).decodeToString()
    }
}
