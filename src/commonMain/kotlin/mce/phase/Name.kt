package mce.phase

import kotlinx.serialization.Serializable
import mce.Id

@Serializable
data class Name(
    val text: String,
    val id: Id,
)
