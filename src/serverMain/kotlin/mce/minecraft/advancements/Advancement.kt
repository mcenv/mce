package mce.minecraft.advancements

import kotlinx.serialization.Serializable
import mce.minecraft.ResourceLocation

@Serializable
data class Advancement(
    val parent: @Serializable(with = ResourceLocation.Serializer::class) ResourceLocation? = null,
    val display: DisplayInfo? = null,
    val rewards: AdvancementRewards? = null,
    val criteria: Map<String, Criterion> = emptyMap(),
    val requirements: List<List<String>> = emptyList(),
)
