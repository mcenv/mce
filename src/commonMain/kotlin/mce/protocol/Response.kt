package mce.protocol

import kotlinx.serialization.Serializable
import mce.ast.surface.Term

@Serializable
sealed class Response {
    abstract val id: Int

    @Serializable
    data class Hover(
        val type: Term,
        override val id: Int,
    ) : Response()

    @Serializable
    data class Completion(
        val items: List<Item>,
        override val id: Int,
    ) : Response() {
        @Serializable
        data class Item(
            val name: String,
            val type: Term,
        )
    }
}
