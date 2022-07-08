package mce.minecraft.chat

import kotlinx.serialization.Serializable

@Serializable
data class BlockNbtComponent(
    override val nbt: String,
    val block: String,
    override val extra: List<Component>? = null,
) : NbtComponent()
