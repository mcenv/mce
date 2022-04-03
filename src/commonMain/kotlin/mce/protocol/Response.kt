package mce.protocol

import kotlinx.serialization.Serializable

@Serializable
sealed class Response {
    abstract val id: Int
}
