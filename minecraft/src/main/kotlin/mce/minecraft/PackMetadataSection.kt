package mce.minecraft

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class PackMetadataSection(
    val description: String, // TODO: use [Component]
    @SerialName("pack_format") val packFormat: Int,
)
