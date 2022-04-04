package mce.pass

import mce.fetch
import mce.pass.backend.gen.Gen
import mce.pass.backend.gen.Generator
import mce.pass.backend.pack.ResourceLocation
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
                ResourceLocation("apply") to """execute store result 0 0 run data get storage 0 c[-1]
                    |data remove storage 0 c[-1]
                """.trimMargin(),
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

        fun dump(): kotlin.collections.Map<ResourceLocation, String> = commands.mapValues { it.value.toString() }
    }
}