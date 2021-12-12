package mce.server

import mce.phase.Elaborate
import java.util.*
import mce.graph.Core as C
import mce.graph.Surface as S

class Server {
    private val dependencies: MutableMap<String, MutableList<String>> = mutableMapOf()
    private val surfaces: MutableMap<String, S.Item> = mutableMapOf()
    private val cores: MutableMap<String, C.Item> = mutableMapOf()

    fun register(surface: S.Item) {
        surface.imports.forEach {
            dependencies.computeIfAbsent(it) { mutableListOf() } += surface.name
        }
        surfaces[surface.name] = surface
    }

    suspend fun fetch(name: String): C.Item = cores.getOrElse(name) {
        val (core, diagnostics) = Elaborate().run(surfaces[name]!!)
        core
    }

    fun edit(name: String) {
        dependencies[name]!!.forEach {
            cores -= it
        }
    }

    suspend fun hover(name: String, id: UUID): HoverItem = TODO()

    suspend fun build() {
        surfaces.keys.forEach {
            fetch(it)
        }
    }

    fun exit(): Nothing = TODO()
}
