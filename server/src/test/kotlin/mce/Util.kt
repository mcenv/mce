package mce

import kotlinx.coroutines.runBlocking
import mce.server.Key
import mce.server.Server
import java.nio.charset.Charset

fun read(name: String): String = ::read::class.java.getResourceAsStream(name)!!.use {
    it.readAllBytes().toString(Charset.defaultCharset())
}

fun <V> fetch(key: Key<V>, vararg imports: String): V = runBlocking {
    Server().run {
        register(key.name, read("/${key.name}.mce"))
        imports.forEach {
            register(it, read("/$it.mce"))
        }
        fetch(key)
    }
}
