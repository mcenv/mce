package mce.pass.backend.gen

import mce.pass.backend.pack.ResourceLocation

interface Generator {
    fun entry(name: ResourceLocation, block: Generator.() -> Unit)

    fun write(char: Char)

    fun write(string: String)
}
