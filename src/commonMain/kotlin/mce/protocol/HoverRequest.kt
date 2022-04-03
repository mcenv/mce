package mce.protocol

import mce.Id

data class HoverRequest(
    val name: String,
    val target: Id,
    override val id: Int,
) : Request()
