package mce

import java.nio.charset.Charset

fun read(name: String): String = ::read::class.java.getResourceAsStream(name)!!.use {
    it.readAllBytes().toString(Charset.defaultCharset())
}
