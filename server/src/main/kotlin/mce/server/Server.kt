package mce.server

import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import mce.graph.Id
import mce.phase.Elaborate
import mce.phase.Parse

class Server {
    private val dependencies: MutableMap<String, List<String>> = mutableMapOf()
    private val values: MutableMap<Key<*>, Any> = mutableMapOf()
    private val counter: MutableMap<Key<*>, Int> = mutableMapOf()

    fun register(name: String, source: String) {
        setValue(Key.Source(name), source)
    }

    @Suppress("UNCHECKED_CAST")
    private suspend fun <V> fetch(key: Key<V>): V = coroutineScope {
        when (key) {
            is Key.Source -> getValue(key)
            is Key.Parsed -> getValue(key) ?: run {
                incrementCount(key)
                val source = fetch(Key.Source(key.name))
                Parse(key.name, source).also {
                    dependencies[key.name] = it.imports.toMutableList()
                    setValue(key, it)
                }
            }
            is Key.Elaborated -> getValue(key) ?: run {
                incrementCount(key)
                val parsed = fetch(Key.Parsed(key.name))
                val items = dependencies[key.name]!!
                    .map { async { fetch(Key.Elaborated(it)).item } }
                    .awaitAll()
                    .associateBy { it.name }
                Elaborate(items, parsed).also { setValue(key, it) }
            } as V
        } as V
    }

    fun edit(name: String): Nothing = TODO()

    suspend fun hover(name: String, id: Id): HoverItem = HoverItem(fetch(Key.Elaborated(name)).types[id]!!.value)

    suspend fun build(): Nothing = TODO()

    fun exit(): Nothing = TODO()

    fun getCount(key: Key<*>): Int = counter[key] ?: 0

    private fun incrementCount(key: Key<*>) {
        counter[key] = (counter[key] ?: 0) + 1
    }

    private inline fun <reified V> getValue(key: Key<V>): V? = values[key] as V?

    private inline fun <reified V> setValue(key: Key<V>, value: V) {
        values[key] = value!!
    }
}
