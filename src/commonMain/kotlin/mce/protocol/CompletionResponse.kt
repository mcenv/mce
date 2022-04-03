package mce.protocol

import mce.phase.frontend.decode.Term

data class CompletionResponse(
    val name: String,
    val type: Term,
    override val id: Int,
) : Response()
