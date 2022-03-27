package mce.phase

import kotlinx.serialization.KSerializer
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.descriptors.buildClassSerialDescriptor
import kotlinx.serialization.descriptors.element
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.encoding.decodeStructure
import kotlinx.serialization.encoding.encodeStructure

object IdSerializer : KSerializer<Id> {
    override val descriptor: SerialDescriptor = buildClassSerialDescriptor("Id") {
        element<Long>("most")
        element<Long>("least")
    }

    override fun serialize(encoder: Encoder, value: Id) {
        encoder.encodeStructure(descriptor) {
            encodeLongElement(descriptor, 0, value.most)
            encodeLongElement(descriptor, 1, value.least)
        }
    }

    override fun deserialize(decoder: Decoder): Id =
        decoder.decodeStructure(descriptor) {
            val most = decodeLongElement(descriptor, 0)
            val least = decodeLongElement(descriptor, 1)
            Id(most, least)
        }
}
