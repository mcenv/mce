package mce.pass

import mce.ast.pack.ResourceLocation
import mce.fetch
import mce.pass.backend.Gen
import mce.pass.backend.Generator
import mce.server.build.Key
import kotlin.test.Test
import kotlin.test.assertEquals

class GenTest {
    private fun gen(name: String): Gen.Result = fetch(Key.GenResult(name))

    @Test
    fun const() {
        val generator = StringGenerator()
        gen("const").generate(generator)
        assertEquals(
            mapOf(
                ResourceLocation("const") to """data modify storage 0 a append value 0b"""
            ),
            generator.dump(),
        )
    }

    class StringGenerator : Generator {
        private var name: ResourceLocation? = null
        private val commands: MutableMap<ResourceLocation, StringBuilder> = mutableMapOf()

        override fun entry(name: ResourceLocation, block: Generator.() -> Unit) {
            this.name = name
            commands[name] = StringBuilder()
            block()
            this.name = null
        }

        override fun write(char: Char) {
            commands[name]!!.append(char)
        }

        override fun write(string: String) {
            commands[name]!!.append(string)
        }

        fun dump(): Map<ResourceLocation, String> = commands.mapValues { it.value.toString() }
    }
}
