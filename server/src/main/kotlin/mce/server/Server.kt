package mce.server

import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import mce.BUILTINS
import mce.Diagnostic.Companion.serializeTerm
import mce.graph.Id
import mce.phase.*
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
                is Key.SurfaceItem -> Parse(key.name, fetch(Key.Source(key.name))) as V
                is Key.ElaborateResult -> {
                    val surfaceItem = fetch(Key.SurfaceItem(key.name))
                    val items = surfaceItem.imports
                        .filter { fetch(Key.SurfaceItem(it)).let { item -> item.modifiers.contains(S.Modifier.BUILTIN) || item.exports.contains(surfaceItem.name) } }
                        .map { async { fetch(Key.ElaborateResult(it)).item } }
                        .awaitAll()
                        .associateBy { it.name }
                    Elaborate(surfaceItem, items) as V
                }
                is Key.ZonkResult -> Zonk(fetch(Key.ElaborateResult(key.name))) as V
                is Key.StageResult -> Stage(fetch(Key.ZonkResult(key.name))) as V
                is Key.DefunctionalizeResult -> Defunctionalize(fetch(Key.StageResult(key.name))) as V
                is Key.Datapack -> Pack(fetch(Key.DefunctionalizeResult(key.name))) as V
            }.also {
                setValue(key, it)
            }
        }
    }

    private fun read(name: String): String = Server::class.java.getResourceAsStream(name)!!.use {
        it.readAllBytes().toString(Charsets.UTF_8)
    }

    fun edit(name: String): Nothing = TODO()

    suspend fun hover(name: String, id: Id): HoverItem {
        val output = fetch(Key.ElaborateResult(name))
        val type = serializeTerm(output.normalizer.quote(output.types[id]!!))
        return HoverItem(type)
    }

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
