package mce.phase.backend.gen

import mce.phase.backend.pack.ResourceLocation

interface Generator {
    fun entry(name: ResourceLocation, block: Generator.() -> Unit)

    fun write(char: Char)

    fun write(string: String)
}
