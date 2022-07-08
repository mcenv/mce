package mce.minecraft.packs

import kotlinx.serialization.Serializable

@Serializable
data class PackMetadata(
    val pack: PackMetadataSection,
    val filter: ResourceFilterSection? = null,
)
