package mce.minecraft.tags

import kotlinx.serialization.Serializable

@Serializable
data class Tag(
    val values: List<@Serializable(with = Entry.Serializer::class) Entry>,
    val replace: Boolean = false,
)
