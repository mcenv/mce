package mce.minecraft

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class PackMetadata(
    val pack: PackMetadataSection,
    val filter: ResourceFilterSection? = null,
)

@Serializable
data class PackMetadataSection(
    val description: String, // TODO: use [Component]
    @SerialName("pack_format") val packFormat: Int,
)

@Serializable
data class ResourceFilterSection(
    val block: List<ResourceLocationPattern>,
)

@Serializable
data class ResourceLocationPattern(
    val namespace: String? = null,
    val path: String? = null,
)
