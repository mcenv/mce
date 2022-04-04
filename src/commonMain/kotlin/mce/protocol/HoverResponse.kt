package mce.protocol

import kotlinx.serialization.Serializable
import mce.pass.frontend.decode.Term

@Serializable
data class HoverResponse(
    val type: Term,
    override val id: Int,
) : Response()
