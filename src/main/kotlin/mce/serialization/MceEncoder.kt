package mce.serialization

import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.SerializationStrategy
import kotlinx.serialization.descriptors.PolymorphicKind
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.CompositeEncoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.modules.EmptySerializersModule
import kotlinx.serialization.modules.SerializersModule
import java.io.DataOutput

@OptIn(ExperimentalSerializationApi::class)
class MceEncoder(private val output: DataOutput) : Encoder, CompositeEncoder {
    override val serializersModule: SerializersModule = EmptySerializersModule

    override fun encodeNotNullMark(): Nothing = throw NotImplementedError()

    override fun encodeNull(): Nothing = throw NotImplementedError()

    override fun encodeBoolean(value: Boolean): Unit = output.writeBoolean(value)

    override fun encodeByte(value: Byte): Unit = output.writeByte(value.toInt())

    override fun encodeShort(value: Short): Unit = output.writeShort(value.toInt())

    override fun encodeChar(value: Char): Unit = output.writeChar(value.code)

    override fun encodeInt(value: Int): Unit = output.writeInt(value)

    override fun encodeLong(value: Long): Unit = output.writeLong(value)

    override fun encodeFloat(value: Float): Unit = output.writeFloat(value)

    override fun encodeDouble(value: Double): Unit = output.writeDouble(value)

    override fun encodeString(value: String) {
        val bytes = value.encodeToByteArray()
        output.writeInt(bytes.size)
        output.write(bytes)
    }

    override fun encodeEnum(enumDescriptor: SerialDescriptor, index: Int): Unit = output.writeInt(index)

    override fun encodeInline(inlineDescriptor: SerialDescriptor): Encoder = this

    override fun beginStructure(descriptor: SerialDescriptor): CompositeEncoder = this

    override fun beginCollection(descriptor: SerialDescriptor, collectionSize: Int): CompositeEncoder = apply {
        output.writeInt(collectionSize)
    }

    override fun endStructure(descriptor: SerialDescriptor): Unit = Unit

    override fun shouldEncodeElementDefault(descriptor: SerialDescriptor, index: Int): Boolean = true

    override fun encodeBooleanElement(descriptor: SerialDescriptor, index: Int, value: Boolean): Unit = encodeBoolean(value)

    override fun encodeByteElement(descriptor: SerialDescriptor, index: Int, value: Byte): Unit = encodeByte(value)

    override fun encodeShortElement(descriptor: SerialDescriptor, index: Int, value: Short): Unit = encodeShort(value)

    override fun encodeCharElement(descriptor: SerialDescriptor, index: Int, value: Char): Unit = encodeChar(value)

    override fun encodeIntElement(descriptor: SerialDescriptor, index: Int, value: Int): Unit = encodeInt(value)

    override fun encodeLongElement(descriptor: SerialDescriptor, index: Int, value: Long): Unit = encodeLong(value)

    override fun encodeFloatElement(descriptor: SerialDescriptor, index: Int, value: Float): Unit = encodeFloat(value)

    override fun encodeDoubleElement(descriptor: SerialDescriptor, index: Int, value: Double): Unit = encodeDouble(value)

    override fun encodeStringElement(descriptor: SerialDescriptor, index: Int, value: String) {
        if (descriptor.kind !is PolymorphicKind) {
            encodeString(value)
        }
    }

    override fun encodeInlineElement(descriptor: SerialDescriptor, index: Int): Encoder = this

    override fun <T> encodeSerializableElement(descriptor: SerialDescriptor, index: Int, serializer: SerializationStrategy<T>, value: T) {
        if (descriptor.kind is PolymorphicKind) {
            val ordinal = serializer.descriptor.serialName.toInt()
            encodeInt(ordinal)
        }
        serializer.serialize(this, value)
    }

    override fun <T : Any> encodeNullableSerializableElement(descriptor: SerialDescriptor, index: Int, serializer: SerializationStrategy<T>, value: T?): Nothing = throw NotImplementedError()
}
