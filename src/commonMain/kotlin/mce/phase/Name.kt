package mce.phase

import kotlinx.serialization.Serializable
import mce.Id
import mce.IdSerializer

@Serializable
data class Name(
    val text: String,
    val id: @Serializable(with = IdSerializer::class) Id,
)
