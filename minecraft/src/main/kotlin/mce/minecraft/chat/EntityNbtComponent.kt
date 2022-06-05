package mce.minecraft.chat

import kotlinx.serialization.Serializable

@Serializable
data class EntityNbtComponent(
    override val nbt: String,
    val entity: String,
    override val extra: List<Component>? = null,
) : NbtComponent()
