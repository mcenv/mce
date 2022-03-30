package mce.serialization

import kotlinx.serialization.BinaryFormat
import kotlinx.serialization.DeserializationStrategy
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.SerializationStrategy
import kotlinx.serialization.modules.EmptySerializersModule
import kotlinx.serialization.modules.SerializersModule
import mce.util.ByteArrayInputStream
import mce.util.ByteArrayOutputStream

@OptIn(ExperimentalSerializationApi::class)
object Mce : BinaryFormat {
    override val serializersModule: SerializersModule = EmptySerializersModule

    override fun <T> encodeToByteArray(serializer: SerializationStrategy<T>, value: T): ByteArray {
        val output = ByteArrayOutputStream()
        serializer.serialize(MceEncoder(output), value)
        return output.toByteArray()
    }

    override fun <T> decodeFromByteArray(deserializer: DeserializationStrategy<T>, bytes: ByteArray): T {
        val input = ByteArrayInputStream(bytes)
        return deserializer.deserialize(MceDecoder(input))
    }
}
