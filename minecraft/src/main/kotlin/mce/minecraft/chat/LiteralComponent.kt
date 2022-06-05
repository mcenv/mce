package mce.minecraft.chat

import kotlinx.serialization.Serializable

@Serializable
data class LiteralComponent(
    val text: String,
    override val extra: List<Component>? = null,
) : Component()
