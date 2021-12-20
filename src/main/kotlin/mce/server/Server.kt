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
    private val counter: MutableMap<String, Int> = mutableMapOf()

    fun register(surface: S.Item) {
        dependencies[surface.name] = surface.imports.toMutableList()
        surfaces[surface.name] = surface
        counter[surface.name] = 0
    }

    private suspend fun fetch(name: String): Elaborate.Output = cores.getOrPut(name) {
        counter[name] = counter[name]!! + 1
        Elaborate(dependencies[name]!!.associateWith { fetch(it).item }, surfaces[name]!!)
    }

    fun edit(name: String) {
        dependencies[name]!!.forEach { cores -= it }
    }

    suspend fun hover(name: String, id: UUID): HoverItem =
        HoverItem(emptyList<C.Value?>().pretty(fetch(name).types[id]!!))

    suspend fun build() {
        surfaces.keys.forEach { fetch(it) }
    }

    fun exit(): Nothing = TODO()

    fun getCount(name: String): Int? = counter[name]
}
