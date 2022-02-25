package mce

import kotlinx.coroutines.runBlocking
import mce.server.Key
import mce.server.Server

fun <V> fetch(key: Key<V>): V = runBlocking {
    Server().fetch(key)
}
