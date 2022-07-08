package mce.minecraft.advancements

import kotlinx.serialization.Serializable
import mce.minecraft.ResourceLocation
import mce.minecraft.chat.Component

@Serializable
data class DisplayInfo(
    val title: Component,
    val description: Component,
    // TODO: icon
    val background: @Serializable(with = ResourceLocation.Serializer::class) ResourceLocation? = null,
    val frame: FrameType = FrameType.TASK,
    val showToast: Boolean = true,
    val announceChat: Boolean = true,
    val hidden: Boolean = false,
)
