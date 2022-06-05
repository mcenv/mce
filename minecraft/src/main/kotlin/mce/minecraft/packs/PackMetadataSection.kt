package mce.minecraft.packs

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import mce.minecraft.chat.Component

@Serializable
data class PackMetadataSection(
    val description: @Serializable(with = Component.Serializer::class) Component,
    @SerialName("pack_format") val packFormat: Int,
)
