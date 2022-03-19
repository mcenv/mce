package mce.server

import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import mce.ast.Id
import mce.ast.surface.Item
import mce.ast.surface.Modifier
import mce.phase.backend.Defun
import mce.phase.backend.Pack
import mce.phase.backend.Stage
import mce.phase.frontend.Elaborate
import mce.phase.frontend.Parse
import mce.phase.frontend.Zonk
import mce.phase.frontend.serializeTerm
import mce.phase.quoteTerm
import mce.util.run

class Server {
    private val values: MutableMap<Key<*>, Any> = mutableMapOf()
    private val counter: MutableMap<Key<*>, Int> = mutableMapOf()

    @Suppress("UNCHECKED_CAST")
    suspend fun <V> fetch(key: Key<V>): V = coroutineScope {
        getValue(key) ?: run {
            incrementCount(key)
            when (key) {
                is Key.Source -> read(key.name) as V
                is Key.SurfaceItem -> Parse(key.name, fetch(Key.Source(key.name))) as V
                is Key.ElaborateResult -> {
                    val surfaceItem = fetch(Key.SurfaceItem(key.name))
                    val items = surfaceItem.imports
                        .filter { visible(fetch(Key.SurfaceItem(it)), surfaceItem.name) }
                        .map { async { fetch(Key.ElaborateResult(it)).item } }
                        .awaitAll()
                        .associateBy { it.name }
                    Elaborate(surfaceItem, items) as V
                }
                is Key.ZonkResult -> Zonk(fetch(Key.ElaborateResult(key.name))) as V
                is Key.StageResult -> Stage(fetch(Key.ZonkResult(key.name))) as V
                is Key.DefunctionalizeResult -> Defun(fetch(Key.StageResult(key.name))) as V
                is Key.Datapack -> Pack(fetch(Key.DefunctionalizeResult(key.name))) as V
            }.also {
                setValue(key, it)
            }
        }
    }

    private fun read(name: String): String = Server::class.java.getResourceAsStream("/std/src/$name.mce")!!.use { // TODO: pack registration
        it.readAllBytes().toString(Charsets.UTF_8)
    }

    private fun visible(item: Item, name: String): Boolean = item.modifiers.contains(Modifier.BUILTIN) || item.exports.contains("*") || item.exports.contains(name)

    suspend fun hover(name: String, id: Id): HoverItem {
        val output = fetch(Key.ElaborateResult(name))
        val type = serializeTerm(quoteTerm(output.types[id]!!).run(output.normalizer))
        return HoverItem(type)
    }

    suspend fun completion(name: String, id: Id): List<CompletionItem> {
        val output = fetch(Key.ElaborateResult(name))
        return output.completions[id]?.let { completions ->
            completions.map { (name, type) -> CompletionItem(name, serializeTerm(quoteTerm(type).run(output.normalizer))) }
        } ?: emptyList()
    }

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
