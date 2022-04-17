package mce.serialization

import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.SerializationStrategy
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.descriptors.StructureKind
import kotlinx.serialization.encoding.AbstractEncoder
import kotlinx.serialization.encoding.CompositeEncoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.modules.EmptySerializersModule
import kotlinx.serialization.modules.SerializersModule
import java.io.DataOutput

@ExperimentalSerializationApi
class NbtOutputEncoder(private val output: DataOutput) : AbstractEncoder() {
    override val serializersModule: SerializersModule = EmptySerializersModule

    override fun encodeNull(): Nothing = TODO()

    override fun encodeBoolean(value: Boolean): Unit = encodeByte(if (value) 1 else 0)

    override fun encodeByte(value: Byte): Unit = output.writeByte(value.toInt())

    override fun encodeShort(value: Short): Unit = output.writeShort(value.toInt())

    override fun encodeChar(value: Char): Unit = output.writeChar(value.code)

    override fun encodeInt(value: Int): Unit = output.writeInt(value)

    override fun encodeLong(value: Long): Unit = output.writeLong(value)

    override fun encodeFloat(value: Float): Unit = output.writeFloat(value)

    override fun encodeDouble(value: Double): Unit = output.writeDouble(value)

    override fun encodeString(value: String): Unit = output.writeUTF(value)

    override fun encodeEnum(enumDescriptor: SerialDescriptor, index: Int): Unit = output.writeInt(index)

    override fun encodeInline(inlineDescriptor: SerialDescriptor): Encoder = this

    private fun encodeByteArray(value: ByteArray) {
        encodeInt(value.size)
        output.write(value)
    }

    private fun encodeIntArray(value: IntArray) {
        encodeInt(value.size)
        value.forEach { encodeInt(it) }
    }

    private fun encodeLongArray(value: LongArray) {
        encodeInt(value.size)
        value.forEach { encodeLong(it) }
    }

    override fun <T> encodeSerializableValue(serializer: SerializationStrategy<T>, value: T) {
        when (serializer.descriptor) {
            byteArrayDescriptor -> encodeByteArray(value as ByteArray)
            intArrayDescriptor -> encodeIntArray(value as IntArray)
            longArrayDescriptor -> encodeLongArray(value as LongArray)
            else -> super.encodeSerializableValue(serializer, value)
        }
    }

    override fun beginStructure(descriptor: SerialDescriptor): CompositeEncoder = this

    override fun beginCollection(descriptor: SerialDescriptor, collectionSize: Int): CompositeEncoder = apply {
        val type = when (collectionSize) {
            0 -> NbtType.END
            else -> descriptor.getElementDescriptor(0).kind.toNbtType()
        }
        encodeByte(type.ordinal.toByte())
        encodeInt(collectionSize)
    }

    override fun endStructure(descriptor: SerialDescriptor) {
        when (descriptor.kind) {
            StructureKind.CLASS -> encodeByte(NbtType.END.ordinal.toByte())
            else -> Unit
        }
    }

    override fun encodeElement(descriptor: SerialDescriptor, index: Int): Boolean {
        when (descriptor.kind) {
            StructureKind.CLASS -> {
                encodeByte(descriptor.getElementDescriptor(index).kind.toNbtType().ordinal.toByte())
                encodeString(descriptor.getElementName(index))
            }
            else -> Unit
        }
        return true
    }

    override fun <T> encodeSerializableElement(descriptor: SerialDescriptor, index: Int, serializer: SerializationStrategy<T>, value: T) {
        if (encodeElement(descriptor, index)) {
            when (descriptor.getElementDescriptor(index).kind) {
                StructureKind.OBJECT -> encodeBooleanElement(descriptor, index, false)
                else -> encodeSerializableValue(serializer, value)
            }
        }
    }

    override fun <T : Any> encodeNullableSerializableElement(descriptor: SerialDescriptor, index: Int, serializer: SerializationStrategy<T>, value: T?): Nothing = TODO()
}
