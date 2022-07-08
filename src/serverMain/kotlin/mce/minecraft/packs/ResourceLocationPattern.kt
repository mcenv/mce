package mce.minecraft.packs

import kotlinx.serialization.Serializable

@Serializable
data class ResourceLocationPattern(
    val namespace: String? = null,
    val path: String? = null,
)
