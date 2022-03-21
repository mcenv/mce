package mce.ast

import kotlinx.serialization.Serializable

@Serializable
data class Name(
    val text: String,
    val id: @Serializable(with = IdSerializer::class) Id,
)
