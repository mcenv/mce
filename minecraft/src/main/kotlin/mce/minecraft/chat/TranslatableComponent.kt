package mce.minecraft.chat

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class TranslatableComponent(
    @SerialName("translate") val key: String,
    @SerialName("with") val args: List<Component>? = null,
    override val extra: List<Component>? = null,
) : Component()
