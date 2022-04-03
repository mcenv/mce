package mce.protocol

import mce.phase.frontend.decode.Term

data class HoverResponse(
    val type: Term,
    override val id: Int,
) : Response()
