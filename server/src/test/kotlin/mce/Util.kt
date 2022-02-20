package mce

import kotlinx.coroutines.runBlocking
import mce.server.Key
import mce.server.Server

fun read(name: String): String = ::read::class.java.getResourceAsStream(name)!!.use {
    it.readAllBytes().toString(Charsets.UTF_8)
}

fun <V> fetch(key: Key<V>): V = runBlocking {
    Server { read("/$it.mce") }.fetch(key)
}
