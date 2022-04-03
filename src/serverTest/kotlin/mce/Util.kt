package mce

import kotlinx.coroutines.runBlocking
import mce.phase.Config
import mce.server.Server
import mce.server.build.Key

fun <V> fetch(key: Key<V>): V = runBlocking {
    Server(Config).build.fetch(key)
}
