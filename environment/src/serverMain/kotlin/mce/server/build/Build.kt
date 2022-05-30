package mce.server.build

import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import mce.ast.Modifier
import mce.ast.pack.Advancement
import mce.ast.surface.Item
import mce.minecraft.ResourceLocation
import mce.minecraft.Tag
import mce.pass.Config
import mce.pass.backend.Defun
import mce.pass.backend.Gen
import mce.pass.backend.Pack
import mce.pass.backend.Stage
import mce.pass.frontend.Elab
import mce.pass.frontend.Parse
import mce.pass.frontend.Zonk
import mce.server.pack.Packs
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ConcurrentMap
import java.util.concurrent.atomic.AtomicInteger
import mce.ast.pack.Function as PFunction

/**
 * A build system with constructive traces rebuilder and suspending scheduler.
 */
class Build(
    private val config: Config,
    private val packs: Packs,
) {
    private val mutexes: ConcurrentMap<Key<*>, Mutex> = ConcurrentHashMap()
    private val values: ConcurrentMap<Key<*>, Any> = ConcurrentHashMap()
    private val counter: ConcurrentMap<Key<*>, AtomicInteger> = ConcurrentHashMap()

    @Suppress("UNCHECKED_CAST")
    suspend fun <V> fetch(key: Key<V>): V = coroutineScope {
        mutexes.computeIfAbsent(key) { Mutex() }.withLock {
            when (val value = values[key]) {
                null -> {
                    counter.computeIfAbsent(key) { AtomicInteger(0) }.addAndGet(1)
                    when (key) {
                        is Key.Source -> packs.fetch(key.name)!! /* TODO */ as V
                        is Key.SurfaceItem -> Parse(key.name, fetch(Key.Source(key.name))) as V
                        is Key.ElabResult -> {
                            val surfaceItem = fetch(Key.SurfaceItem(key.name))
                            val items = surfaceItem.imports
                                .filter { visible(fetch(Key.SurfaceItem(it)), surfaceItem.name) } // filter by visibility
                                .flatMap { fetch(Key.SurfaceItem(it)).imports + it }              // import dependencies recursively
                                .map { async { fetch(Key.ElabResult(it)).item } }                 // elab it
                                .awaitAll()
                                .associateBy { it.name }
                            Elab(config, surfaceItem to items) as V
                        }
                        is Key.ZonkResult -> {
                            Zonk(config, fetch(Key.ElabResult(key.name))).also { result ->
                                result.diagnostics.forEach { println(it) }
                            } as V
                            // TODO: cancel compilation here if diagnostics are not empty
                        }
                        is Key.StageResult -> Stage(config, fetch(Key.ZonkResult(key.name))) as V
                        is Key.DefunResult -> Defun(config, fetch(Key.StageResult(key.name))) as V
                        is Key.PackResult -> Pack(config, fetch(Key.DefunResult(key.name))) as V
                        is Key.GenResult -> {
                            val results = packs.list()
                                .flatMap { fetch(Key.SurfaceItem(it)).imports + it }                        // import dependencies recursively
                                .toSet()                                                                    // deduplicate dependencies
                                .filter { !fetch(Key.SurfaceItem(it)).modifiers.contains(Modifier.STATIC) } // filter out static dependencies
                                .map { async { fetch(Key.PackResult(it)) } }                                // pack it
                                .awaitAll()
                            val tags = results.fold(mutableMapOf<Pair<String, ResourceLocation>, Tag>()) { tags, result -> tags.also { it.putAll(result.tags) } }
                            val advancements = results.fold(mutableMapOf<ResourceLocation, Advancement>()) { advancements, result -> advancements.also { it.putAll(result.advancements) } }
                            val functions = results.fold(mutableMapOf<ResourceLocation, PFunction>()) { functions, result -> functions.also { it.putAll(result.functions) } }
                            val defunctions = results.fold(mutableMapOf<Int, PFunction>()) { defunctions, result -> defunctions.also { it.putAll(result.defunctions) } }
                            Gen(config, Pack.Result(tags, advancements, functions, defunctions)) as V
                        }
                    }.also {
                        values[key] = it!!
                    }
                }
                else -> value as V
            }
        }
    }

    private fun visible(item: Item, name: String): Boolean =
        item.modifiers.contains(Modifier.BUILTIN) ||
                item.exports.contains("*") ||
                item.exports.contains(name)

    fun getCount(key: Key<*>): Int = counter[key]?.get() ?: 0
}
