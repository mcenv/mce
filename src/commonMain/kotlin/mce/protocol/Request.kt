package mce.protocol

import kotlinx.serialization.Serializable

@Serializable
sealed class Request {
    abstract val id: Int
}
