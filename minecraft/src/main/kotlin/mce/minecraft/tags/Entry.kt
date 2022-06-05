package mce.minecraft.tags

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.*
import mce.minecraft.ResourceLocation

@Serializable
data class Entry(
    val id: @Serializable(with = ResourceLocation.Serializer::class) ResourceLocation,
    val required: Boolean = true,
) {
    object Serializer : JsonTransformingSerializer<Entry>(serializer()) {
        override fun transformSerialize(element: JsonElement): JsonElement =
            if (element.jsonObject["required"]?.jsonPrimitive?.boolean != false) {
                element.jsonObject["id"]!!
            } else {
                element
            }

        override fun transformDeserialize(element: JsonElement): JsonElement =
            when (element) {
                is JsonPrimitive -> buildJsonObject {
                    put("id", element.content)
                    put("required", true)
                }
                else -> element
            }
    }
}
