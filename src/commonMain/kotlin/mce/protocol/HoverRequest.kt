package mce.protocol

import kotlinx.serialization.Serializable
import mce.Id

@Serializable
data class HoverRequest(
    val name: String,
    val target: Id,
    override val id: Int,
) : Request()
