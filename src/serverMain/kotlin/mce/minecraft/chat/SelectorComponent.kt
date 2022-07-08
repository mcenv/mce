package mce.minecraft.chat

import kotlinx.serialization.Serializable

@Serializable
data class SelectorComponent(
    val selector: String,
    val separator: Component? = null,
    override val extra: List<Component>? = null,
) : Component()
