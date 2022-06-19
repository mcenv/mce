package mce.protocol

import kotlinx.serialization.Serializable
import mce.Id
import mce.ast.surface.Term

@Serializable
sealed class Request {
    abstract val id: Int

    @Serializable
    object Init : Request() {
        override val id: Int = -1
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

    @Serializable
    data class Replace(
        val target: Id,
        val term: Term,
        override val id: Int,
    ) : Request()

    @Serializable
    data class Move(
        val target: Id,
        val source: Id,
        override val id: Int,
    ) : Request()
}
