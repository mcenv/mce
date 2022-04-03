package mce.protocol

import kotlinx.serialization.Serializable
import mce.Id

@Serializable
data class CompletionRequest(
    val name: String,
    val target: Id,
    override val id: Int,
) : Request()
