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
            return JsonPrimitive("${if (DEFAULT_NAMESPACE == namespace) "" else "${normalize(namespace)}:"}${normalize(path)}")
        }

        override fun transformDeserialize(element: JsonElement): JsonElement =
            buildJsonObject {
                val parts = element.jsonPrimitive.content.split(':')
                when (parts.size) {
                    1 -> {
                        put("namespace", DEFAULT_NAMESPACE)
                        put("path", denormalize(parts[0]))
                    }
                    else -> {
                        put("namespace", denormalize(parts[0]))
                        put("path", denormalize(parts[1]))
                    }
                }
            }
    }

    companion object {
        const val DEFAULT_NAMESPACE = "minecraft"

        fun normalize(string: String): String =
            string.map {
                when (it) {
                    '-' -> "--"
                    in 'a'..'z', in '0'..'9', '/', '.', '_' -> it.toString()
                    else -> "-${it.code.toString(16)}"
                }
            }.joinToString("")

        fun denormalize(string: String): String = string // TODO
    }
}
