package mce.server

import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import mce.graph.Id
import mce.phase.Elaborate
import mce.pretty
import mce.graph.Core as C
import mce.graph.Surface as S

class Server {
    private val dependencies: MutableMap<String, MutableList<String>> = mutableMapOf()
    private val values: MutableMap<Key<*>, Any> = mutableMapOf()
    private val counter: MutableMap<String, Int> = mutableMapOf()

    fun register(surface: S.Item) {
        dependencies[surface.name] = surface.imports.toMutableList()
        setValue(Key.Item(surface.name), surface)
        counter[surface.name] = 0
    }

    @Suppress("IMPLICIT_CAST_TO_ANY", "UNCHECKED_CAST")
    private suspend fun <T> fetch(key: Key<T>): T = coroutineScope {
        when (key) {
            is Key.Item -> getValue(key)
            is Key.Elaborated -> getValue(Key.Elaborated(key.name)) ?: run {
                counter[key.name] = counter[key.name]!! + 1
                Elaborate(
                    dependencies[key.name]!!
                        .map { async { fetch(Key.Elaborated(it)).item } }
                        .awaitAll()
                        .associateBy { it.name },
                    fetch(Key.Item(key.name))
                ).also { setValue(key, it) }
            }
        } as T
    }

    fun edit(name: String): Nothing = TODO()

    suspend fun hover(name: String, id: Id): HoverItem =
        HoverItem(emptyList<C.Value?>().pretty(fetch(Key.Elaborated(name)).types[id]!!))

    suspend fun build(): Nothing = TODO()

    fun exit(): Nothing = TODO()

    fun getCount(name: String): Int? = counter[name]

    private inline fun <reified T> getValue(key: Key<T>): T? = values[key] as T?

    private inline fun <reified T> setValue(key: Key<T>, value: T) {
        values[key] = value!!
    }
}
