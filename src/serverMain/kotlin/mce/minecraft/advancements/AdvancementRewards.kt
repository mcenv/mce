package mce.minecraft.advancements

import kotlinx.serialization.Serializable
import mce.minecraft.ResourceLocation

@Serializable
data class AdvancementRewards(
    val experience: Int = 0,
    val loot: List<@Serializable(with = ResourceLocation.Serializer::class) ResourceLocation> = emptyList(),
    val recipes: List<@Serializable(with = ResourceLocation.Serializer::class) ResourceLocation> = emptyList(),
    val function: @Serializable(with = ResourceLocation.Serializer::class) ResourceLocation? = null,
)
