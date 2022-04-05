package mce.protocol

import kotlinx.serialization.Serializable
import mce.ast.surface.Term

@Serializable
data class CompletionResponse(
    val name: String,
    val type: Term,
    override val id: Int,
) : Response()
