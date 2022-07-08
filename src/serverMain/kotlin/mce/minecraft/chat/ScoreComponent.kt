package mce.minecraft.chat

import kotlinx.serialization.Serializable

@Serializable
data class ScoreComponent(
    val name: String,
    val objective: String,
    override val extra: List<Component>? = null,
) : Component()
