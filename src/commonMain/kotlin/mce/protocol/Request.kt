package mce.protocol

import kotlinx.serialization.Serializable
import mce.Id

@Serializable
sealed class Request {
    abstract val id: Int

    @Serializable
    object Exit : Request() {
        override val id: Int = 0
    }

    @Serializable
    data class Hover(
        val name: String,
        val target: Id,
        override val id: Int,
    ) : Request()

    @Serializable
    data class Completion(
        val name: String,
        val target: Id,
        override val id: Int,
    ) : Request()
}
