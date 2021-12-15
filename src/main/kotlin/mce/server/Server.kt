package mce.server

import mce.phase.Elaborate
import mce.pretty
import java.util.*
import mce.graph.Core as C
import mce.graph.Surface as S

class Server {
    private val dependencies: MutableMap<String, MutableList<String>> = mutableMapOf()
    private val surfaces: MutableMap<String, S.Item> = mutableMapOf()
    private val cores: MutableMap<String, Elaborate.Output> = mutableMapOf()

    fun register(surface: S.Item) {
        surface.imports.forEach {
            dependencies.computeIfAbsent(it) { mutableListOf() } += surface.name
        }
        surfaces[surface.name] = surface
    }

    suspend fun fetch(name: String): Elaborate.Output = cores.getOrElse(name) {
        Elaborate(dependencies[name]!!.associateWith { fetch(it).item }, surfaces[name]!!)
    }

    fun edit(name: String) {
        dependencies[name]!!.forEach {
            cores -= it
        }
    }

    suspend fun hover(name: String, id: UUID): HoverItem =
        HoverItem(emptyList<C.Value?>().pretty(fetch(name).types[id]!!))

    suspend fun build() {
        surfaces.keys.forEach {
            fetch(it)
        }
    }

    fun exit(): Nothing = TODO()
}
