package mce.pass

import mce.ast.pack.ResourceLocation
import mce.fetch
import mce.pass.backend.Gen
import mce.pass.backend.Generator
import mce.pass.backend.ResourceType
import mce.server.build.Key
import org.junit.jupiter.api.Disabled
import kotlin.test.Test
import kotlin.test.assertEquals

class GenTest {
    private fun gen(name: String): Gen.Result = fetch(Key.GenResult)

    @Test
    @Disabled
    fun const() {
        val generator = StringGenerator()
        gen("const").generate(generator)
        assertEquals(
            mapOf(
                ResourceLocation("apply") to """execute store result score 0 0 run data get storage 0 c[-1]
                    |data remove storage 0 c[-1]""".trimMargin(),
                ResourceLocation("const") to """data modify storage 0 a append value 0b""",
            ),
            generator.dump(),
        )
    }

    @Test
    @Disabled
    fun defun_context() {
        val generator = StringGenerator()
        gen("defun_context").generate(generator)
        assertEquals(
            mapOf(
                ResourceLocation("0") to """data modify storage 0 i append value []
                    |data modify storage 0 c append from storage 0 c[-2]
                    |data modify storage 0 i[-1] append from storage 0 c[-1]
                    |data remove storage 0 c[-1]
                    |data modify storage 0 c append from storage 0 c[-2]
                    |data modify storage 0 i[-1] append from storage 0 c[-1]
                    |data remove storage 0 c[-1]
                    |scoreboard players set 0 0 0""".trimMargin(),
                ResourceLocation("apply") to """execute store result score 0 0 run data get storage 0 c[-1]
                    |data remove storage 0 c[-1]
                    |execute if score 0 0 matches 0..0 run function 0""".trimMargin(),
                ResourceLocation("defun_context") to """data modify storage 0 c append value 0""",
            ),
            generator.dump(),
        )
    }

    class StringGenerator : Generator {
        private var name: ResourceLocation? = null
        private val commands: MutableMap<ResourceLocation, StringBuilder> = mutableMapOf()

        override fun entry(type: ResourceType, name: ResourceLocation, block: Generator.() -> Unit) {
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
