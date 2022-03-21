package mce.ast

import kotlinx.serialization.KSerializer
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import java.util.*

/**
 * A unique identifier for a node.
 */
@JvmInline
value class Id(val id: UUID)

/**
 * Creates a fresh [Id].
 */
fun freshId(): Id = Id(UUID.randomUUID())

object IdSerializer : KSerializer<Id> {
    override val descriptor: SerialDescriptor = PrimitiveSerialDescriptor("Id", PrimitiveKind.STRING)

    override fun serialize(encoder: Encoder, value: Id) {
        encoder.encodeString(value.toString())
    }

    override fun deserialize(decoder: Decoder): Id = Id(UUID.fromString(decoder.decodeString()))
}
