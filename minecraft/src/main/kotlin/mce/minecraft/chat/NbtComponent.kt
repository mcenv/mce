package mce.minecraft.chat

import kotlinx.serialization.DeserializationStrategy
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonContentPolymorphicSerializer
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.jsonObject

@Serializable
sealed class NbtComponent : Component() {
    abstract val nbt: String

    object Serializer : JsonContentPolymorphicSerializer<NbtComponent>(NbtComponent::class) {
        override fun selectDeserializer(element: JsonElement): DeserializationStrategy<out NbtComponent> =
            when {
                "block" in element.jsonObject -> BlockNbtComponent.serializer()
                "entity" in element.jsonObject -> EntityNbtComponent.serializer()
                "storage" in element.jsonObject -> StorageNbtComponent.serializer()
                else -> serializer()
            }
    }
}
