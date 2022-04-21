package mce.minecraft

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.*

@Serializable
data class ResourceLocation(
    val namespace: String,
    val path: String,
) {
    constructor(path: String) : this(DEFAULT_NAMESPACE, path)

    object Serializer : JsonTransformingSerializer<ResourceLocation>(serializer()) {
        override fun transformSerialize(element: JsonElement): JsonElement {
            val namespace = element.jsonObject["namespace"]!!.jsonPrimitive.content
            val path = element.jsonObject["path"]!!.jsonPrimitive.content
            return JsonPrimitive("${if (DEFAULT_NAMESPACE == namespace) "" else "$namespace:"}$path")
        }

        override fun transformDeserialize(element: JsonElement): JsonElement =
            buildJsonObject {
                val parts = element.jsonPrimitive.content.split(':')
                when (parts.size) {
                    1 -> {
                        put("namespace", DEFAULT_NAMESPACE)
                        put("path", parts[0])
                    }
                    else -> {
                        put("namespace", parts[0])
                        put("path", parts[1])
                    }
                }
            }
    }

    companion object {
        const val DEFAULT_NAMESPACE = "minecraft"
    }
}
