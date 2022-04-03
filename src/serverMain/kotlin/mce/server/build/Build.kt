package mce.server.build

import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import mce.phase.Config
import mce.phase.backend.defun.Defun
import mce.phase.backend.gen.Gen
import mce.phase.backend.pack.Pack
import mce.phase.backend.stage.Stage
import mce.phase.frontend.decode.Item
import mce.phase.frontend.decode.Modifier
import mce.phase.frontend.decode.Parse
import mce.phase.frontend.elab.Elab
import mce.phase.frontend.zonk.Zonk
import mce.server.Server

/**
 * A build system with constructive traces rebuilder and suspending scheduler.
 */
class Build(
    private val config: Config,
) {
    private val values: MutableMap<Key<*>, Any> = mutableMapOf()
    private val counter: MutableMap<Key<*>, Int> = mutableMapOf()

    @Suppress("UNCHECKED_CAST")
    suspend fun <V> fetch(key: Key<V>): V = coroutineScope {
        getValue(key) ?: run {
            incrementCount(key)
            when (key) {
                is Key.Source -> read(key.name) as V
                is Key.SurfaceItem -> Parse(key.name, fetch(Key.Source(key.name))) as V
                is Key.ElabResult -> {
                    val surfaceItem = fetch(Key.SurfaceItem(key.name))
                    val items = surfaceItem.imports
                        .filter { visible(fetch(Key.SurfaceItem(it)), surfaceItem.name) }
                        .map { async { fetch(Key.ElabResult(it)).item } }
                        .awaitAll()
                        .associateBy { it.name }
                    Elab(config, surfaceItem to items) as V
                }
                is Key.ZonkResult -> Zonk(config, fetch(Key.ElabResult(key.name))) as V
                is Key.StageResult -> Stage(config, fetch(Key.ZonkResult(key.name))) as V
                is Key.DefunResult -> Defun(config, fetch(Key.StageResult(key.name))) as V
                is Key.PackResult -> Pack(config, fetch(Key.DefunResult(key.name))) as V
                is Key.GenResult -> {
                    val surfaceItem = fetch(Key.SurfaceItem(key.name))
                    val functions = (surfaceItem.imports + key.name)
                        .map { async { fetch(Key.PackResult(it)).functions } }
                        .awaitAll()
                        .flatten()
                    Gen(config, functions) as V
                }
            }.also {
                setValue(key, it)
            }
        }
    }

    private fun read(name: String): String =
        Server::class.java.getResourceAsStream("/std/src/$name.mce")!!.use { // TODO: pack registration
            it.readAllBytes().toString(Charsets.UTF_8)
        }

    private fun visible(item: Item, name: String): Boolean =
        item.modifiers.contains(Modifier.BUILTIN) ||
                item.exports.contains("*") ||
                item.exports.contains(name)

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
