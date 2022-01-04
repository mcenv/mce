package mce.server

import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import mce.graph.Id
import mce.phase.Elaborate
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

    @Suppress("UNCHECKED_CAST")
    private suspend fun <V> fetch(key: Key<V>): V = coroutineScope {
        when (key) {
            is Key.Item -> getValue(key)
            is Key.Elaborated -> getValue(key) ?: run {
                counter[key.name] = counter[key.name]!! + 1
                Elaborate(
                    dependencies[key.name]!!
                        .map { async { fetch(Key.Elaborated(it)).item } }
                        .awaitAll()
                        .associateBy { it.name },
                    fetch(Key.Item(key.name))
                ).also { setValue(key, it) }
            } as V
        } as V
    }

    fun edit(name: String): Nothing = TODO()

    suspend fun hover(name: String, id: Id): HoverItem = HoverItem(fetch(Key.Elaborated(name)).types[id]!!.value)

    suspend fun build(): Nothing = TODO()

    fun exit(): Nothing = TODO()

    fun getCount(name: String): Int? = counter[name]

    private inline fun <reified V> getValue(key: Key<V>): V? = values[key] as V?

    private inline fun <reified V> setValue(key: Key<V>, value: V) {
        values[key] = value!!
    }
}
