package mce.minecraft.advancements

import kotlinx.serialization.Serializable

@Serializable
enum class FrameType {
    TASK,
    CHALLENGE,
    GOAL,
}
