package mce.protocol

import kotlinx.serialization.Serializable
import mce.phase.frontend.decode.Term

@Serializable
data class CompletionResponse(
    val name: String,
    val type: Term,
    override val id: Int,
) : Response()
