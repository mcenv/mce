package mce.server.build

import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import mce.ast.pack.Advancement
import mce.ast.pack.ResourceLocation
import mce.ast.surface.Item
import mce.ast.surface.Modifier
import mce.pass.Config
import mce.pass.backend.Defun
import mce.pass.backend.Gen
import mce.pass.backend.Pack
import mce.pass.backend.Stage
import mce.pass.frontend.Elab
import mce.pass.frontend.Parse
import mce.pass.frontend.Zonk
import mce.server.pack.Packs
import mce.ast.pack.Function as PFunction

/**
 * A build system with constructive traces rebuilder and suspending scheduler.
 */
class Build(
    private val config: Config,
    private val packs: Packs,
) {
    private val values: MutableMap<Key<*>, Any> = mutableMapOf()
    private val counter: MutableMap<Key<*>, Int> = mutableMapOf()

    @Suppress("UNCHECKED_CAST")
    suspend fun <V> fetch(key: Key<V>): V = coroutineScope {
        values[key] as V? ?: run {
            counter[key] = (counter[key] ?: 0) + 1
            when (key) {
                is Key.Source -> packs.fetch(key.name)!! /* TODO */ as V
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
                    val results = packs.list()
                        .map { async { fetch(Key.PackResult(it)) } }
                        .awaitAll()
                    val functions = results.fold(mutableMapOf<ResourceLocation, PFunction>()) { functions, result -> functions.also { it.putAll(result.functions) } }
                    val advancements = results.fold(mutableMapOf<ResourceLocation, Advancement>()) { advancements, result -> advancements.also { it.putAll(result.advancements) } }
                    val defunctions = results.fold(mutableMapOf<Int, PFunction>()) { defunctions, result -> defunctions.also { it.putAll(result.defunctions) } }
                    Gen(config, Pack.Result(functions, advancements, defunctions)) as V
                }
            }.also {
                values[key] = it!!
            }
        }
    }

    private fun visible(item: Item, name: String): Boolean =
        item.modifiers.contains(Modifier.BUILTIN) ||
                item.exports.contains("*") ||
                item.exports.contains(name)

    fun getCount(key: Key<*>): Int = counter[key] ?: 0
}
