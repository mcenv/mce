package mce.minecraft.chat

import kotlinx.serialization.Serializable

@Serializable
class KeybindComponent(
    val name: String,
    override val extra: List<Component>? = null,
) : Component()
