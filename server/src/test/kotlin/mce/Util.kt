package mce

import kotlinx.coroutines.runBlocking
import mce.server.Key
import mce.server.Server
import java.nio.charset.Charset

fun read(name: String): String = ::read::class.java.getResourceAsStream(name)!!.use {
    it.readAllBytes().toString(Charset.defaultCharset())
}

fun <V> fetch(key: Key<V>): V = runBlocking {
    Server { read("/$it.mce") }.fetch(key)
}
