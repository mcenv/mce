package mce.protocol

import mce.Id

data class CompletionRequest(
    val name: String,
    val target: Id,
    override val id: Int,
) : Request()
