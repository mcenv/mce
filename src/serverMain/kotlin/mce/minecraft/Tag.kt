package mce.minecraft

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.*
import mce.ast.pack.ResourceLocation

@Serializable
data class Tag(
    val values: List<@Serializable(with = Entry.Serializer::class) Entry>,
    val replace: Boolean = false,
)

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
