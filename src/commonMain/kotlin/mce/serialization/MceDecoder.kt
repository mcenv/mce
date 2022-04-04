package mce.serialization

import kotlinx.serialization.DeserializationStrategy
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.CompositeDecoder
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.modules.EmptySerializersModule
import kotlinx.serialization.modules.SerializersModule
import mce.util.ByteArrayInputStream

@OptIn(ExperimentalSerializationApi::class)
class MceDecoder(private val input: ByteArrayInputStream) : Decoder, CompositeDecoder {
    override val serializersModule: SerializersModule = EmptySerializersModule

    override fun decodeNotNullMark(): Boolean = throw NotImplementedError()

    override fun decodeNull(): Nothing = throw NotImplementedError()

    override fun decodeBoolean(): Boolean = input.readBoolean()

    override fun decodeByte(): Byte = input.readByte()

    override fun decodeShort(): Short = input.readShort()

    override fun decodeChar(): Char = input.readChar()

    override fun decodeInt(): Int = input.readInt()

    override fun decodeLong(): Long = input.readLong()

    override fun decodeFloat(): Float = input.readFloat()

    override fun decodeDouble(): Double = input.readDouble()

    override fun decodeString(): String = input.readString()

    override fun decodeEnum(enumDescriptor: SerialDescriptor): Int = input.readInt()

    override fun decodeInline(inlineDescriptor: SerialDescriptor): Decoder = this

    override fun beginStructure(descriptor: SerialDescriptor): CompositeDecoder = this

    override fun endStructure(descriptor: SerialDescriptor): Unit = Unit

    override fun decodeSequentially(): Boolean = true

    override fun decodeElementIndex(descriptor: SerialDescriptor): Int = input.readInt()

    override fun decodeCollectionSize(descriptor: SerialDescriptor): Int = input.readInt()

    override fun decodeBooleanElement(descriptor: SerialDescriptor, index: Int): Boolean = decodeBoolean()

    override fun decodeByteElement(descriptor: SerialDescriptor, index: Int): Byte = decodeByte()

    override fun decodeCharElement(descriptor: SerialDescriptor, index: Int): Char = decodeChar()

    override fun decodeShortElement(descriptor: SerialDescriptor, index: Int): Short = decodeShort()

    override fun decodeIntElement(descriptor: SerialDescriptor, index: Int): Int = decodeInt()

    override fun decodeLongElement(descriptor: SerialDescriptor, index: Int): Long = decodeLong()

    override fun decodeFloatElement(descriptor: SerialDescriptor, index: Int): Float = decodeFloat()

    override fun decodeDoubleElement(descriptor: SerialDescriptor, index: Int): Double = decodeDouble()

    override fun decodeStringElement(descriptor: SerialDescriptor, index: Int): String = decodeString()

    override fun decodeInlineElement(descriptor: SerialDescriptor, index: Int): Decoder = this

    override fun <T> decodeSerializableElement(descriptor: SerialDescriptor, index: Int, deserializer: DeserializationStrategy<T>, previousValue: T?): T = deserializer.deserialize(this)

    override fun <T : Any> decodeNullableSerializableElement(descriptor: SerialDescriptor, index: Int, deserializer: DeserializationStrategy<T?>, previousValue: T?): T = throw NotImplementedError()
}
