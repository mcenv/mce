package mce.minecraft.chat

import kotlinx.serialization.Serializable

@Serializable
data class StorageNbtComponent(
    override val nbt: String,
    val storage: String,
    override val extra: List<Component>? = null,
) : NbtComponent()
