package mce.minecraft.chat

import kotlinx.serialization.DeserializationStrategy
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonContentPolymorphicSerializer
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.jsonObject

@Serializable
sealed class Component {
    abstract val extra: List<Component>?

    object Serializer : JsonContentPolymorphicSerializer<Component>(Component::class) {
        override fun selectDeserializer(element: JsonElement): DeserializationStrategy<out Component> =
            when {
                "text" in element.jsonObject -> LiteralComponent.serializer()
                "translate" in element.jsonObject -> TranslatableComponent.serializer()
                "score" in element.jsonObject -> ScoreComponent.serializer()
                "selector" in element.jsonObject -> SelectorComponent.serializer()
                "keybind" in element.jsonObject -> KeybindComponent.serializer()
                "nbt" in element.jsonObject -> NbtComponent.serializer()
                else -> serializer()
            }
    }
}
