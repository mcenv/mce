package mce.server

import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import mce.BUILTINS
import mce.graph.Id
import mce.phase.*
import java.nio.charset.Charset
import mce.graph.Surface as S

class Server(
    private val sources: (String) -> String
) {
    private val values: MutableMap<Key<*>, Any> = mutableMapOf()
    private val counter: MutableMap<Key<*>, Int> = mutableMapOf()

    @Suppress("UNCHECKED_CAST")
    suspend fun <V> fetch(key: Key<V>): V = coroutineScope {
        getValue(key) ?: run {
            incrementCount(key)
            when (key) {
                is Key.Source -> (if (BUILTINS.containsKey(key.name)) read("/${key.name}.mce") else sources(key.name)) as V
                is Key.SurfaceItem -> {
                    val source = fetch(Key.Source(key.name))
                    Parse(key.name, source) as V
                }
                is Key.ElaboratedOutput -> {
                    val surfaceItem = fetch(Key.SurfaceItem(key.name))
                    val items = surfaceItem.imports
                        .filter { fetch(Key.SurfaceItem(it)).let { item -> item.modifiers.contains(S.Modifier.BUILTIN) || item.exports.contains(surfaceItem.name) } }
                        .map { async { fetch(Key.ElaboratedOutput(it)).item } }
                        .awaitAll()
                        .associateBy { it.name }
                    Elaborate(items, surfaceItem) as V
                }
                is Key.ZonkedOutput -> {
                    val elaboratedOutput = fetch(Key.ElaboratedOutput(key.name))
                    Zonk(elaboratedOutput.normalizer, elaboratedOutput.item) as V
                }
                is Key.StagedItem -> {
                    val zonkedOutput = fetch(Key.ZonkedOutput(key.name))
                    Stage(zonkedOutput.normalizer, zonkedOutput.item) as V
                }
                is Key.DefunctionalizedItem -> {
                    val item = fetch(Key.StagedItem(key.name))
                    Defunctionalize(item) as V
                }
            }.also {
                setValue(key, it)
            }
        }
    }

    private fun read(name: String): String = Server::class.java.getResourceAsStream(name)!!.use {
        it.readAllBytes().toString(Charset.defaultCharset())
    }

    fun edit(name: String): Nothing = TODO()

    suspend fun hover(name: String, id: Id): HoverItem = HoverItem(fetch(Key.ElaboratedOutput(name)).types[id]!!.value)

    suspend fun build(): Nothing = TODO()

    fun exit(): Nothing = TODO()

    fun getCount(key: Key<*>): Int = counter[key] ?: 0

    private fun incrementCount(key: Key<*>) {
        counter[key] = (counter[key] ?: 0) + 1
    }

    @Suppress("UNCHECKED_CAST")
    private fun <V> getValue(key: Key<V>): V? = values[key] as V?

    private fun <V> setValue(key: Key<V>, value: V) {
        values[key] = value!!
    }
}
