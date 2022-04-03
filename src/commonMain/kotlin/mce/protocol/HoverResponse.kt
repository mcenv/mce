package mce.protocol

import kotlinx.serialization.Serializable
import mce.phase.frontend.decode.Term

@Serializable
data class HoverResponse(
    val type: Term,
    override val id: Int,
) : Response()
