package mce.minecraft.packs

import kotlinx.serialization.Serializable

@Serializable
data class ResourceFilterSection(
    val block: List<ResourceLocationPattern>,
)
