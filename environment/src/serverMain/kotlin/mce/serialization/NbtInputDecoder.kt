package mce.serialization

import kotlinx.serialization.DeserializationStrategy
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.descriptors.StructureKind
import kotlinx.serialization.encoding.AbstractDecoder
import kotlinx.serialization.encoding.CompositeDecoder
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.modules.EmptySerializersModule
import kotlinx.serialization.modules.SerializersModule
import java.io.DataInput

@ExperimentalSerializationApi
class NbtInputDecoder(private val input: DataInput, private val elementsCount: Int = 0) : AbstractDecoder() {
    private var elementIndex: Int = 0

    override val serializersModule: SerializersModule = EmptySerializersModule

    override fun decodeNotNullMark(): Boolean = TODO()

    override fun decodeNull(): Nothing? = TODO()

    override fun decodeBoolean(): Boolean = input.readBoolean()

    override fun decodeByte(): Byte = input.readByte()

    override fun decodeShort(): Short = input.readShort()

    override fun decodeChar(): Char = input.readChar()

    override fun decodeInt(): Int = input.readInt()

    override fun decodeLong(): Long = input.readLong()

    override fun decodeFloat(): Float = input.readFloat()

    override fun decodeDouble(): Double = input.readDouble()

    override fun decodeString(): String = input.readUTF()

    override fun decodeEnum(enumDescriptor: SerialDescriptor): Int = input.readInt()

    override fun decodeInline(inlineDescriptor: SerialDescriptor): Decoder = this

    private fun decodeByteArray(): ByteArray {
        val size = decodeInt()
        return ByteArray(size).also { input.readFully(it) }
    }

    private fun decodeIntArray(): IntArray {
        val size = decodeInt()
        return IntArray(size) { decodeInt() }
    }

    private fun decodeLongArray(): LongArray {
        val size = decodeInt()
        return LongArray(size) { decodeLong() }
    }

    @Suppress("UNCHECKED_CAST")
    override fun <T> decodeSerializableValue(deserializer: DeserializationStrategy<T>): T =
        when (deserializer.descriptor) {
            byteArrayDescriptor -> decodeByteArray() as T
            intArrayDescriptor -> decodeIntArray() as T
            longArrayDescriptor -> decodeLongArray() as T
            else -> super.decodeSerializableValue(deserializer)
        }

    override fun beginStructure(descriptor: SerialDescriptor): CompositeDecoder {
        val elementsCount = when (descriptor.kind) {
            StructureKind.LIST -> {
                decodeByte()
                decodeInt()
            }
            StructureKind.OBJECT -> {
                decodeByte()
                descriptor.elementsCount
            }
            else -> descriptor.elementsCount
        }
        return NbtInputDecoder(input, elementsCount)
    }

    override fun endStructure(descriptor: SerialDescriptor) {
        when (descriptor.kind) {
            StructureKind.CLASS -> require(NbtType.END.ordinal == input.readByte().toInt())
            else -> Unit
        }
    }

    override fun decodeElementIndex(descriptor: SerialDescriptor): Int {
        return if (elementsCount == elementIndex) {
            CompositeDecoder.DECODE_DONE
        } else {
            when (descriptor.kind) {
                StructureKind.CLASS -> {
                    require(descriptor.getElementDescriptor(elementIndex).kind.toNbtType().ordinal == input.readByte().toInt())
                    require(descriptor.getElementName(elementIndex) == input.readUTF())
                }
                else -> Unit
            }
            elementIndex++
        }
    }
}
