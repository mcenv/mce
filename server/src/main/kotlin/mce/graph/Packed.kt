package mce.graph

object Packed {
    data class Datapack(
        val functions: Map<String, Function>
    )

    data class Function(
        val name: String,
        val commands: List<Command>
    )

    sealed class Command
}
