package mce.phase

import mce.fetch
import mce.phase.Pack.Companion.BYTE
import mce.phase.Pack.Companion.STACK
import mce.server.Key
import kotlin.test.Test
import kotlin.test.assertEquals
import mce.graph.Packed as P

class PackTest {
    private fun pack(name: String): P.Datapack = fetch(Key.Datapack(name))

    @Test
    fun byte() {
        assertEquals(
            P.Datapack(
                listOf(
                    P.Function(
                        P.ResourceLocation("const"),
                        listOf(
                            P.Command.InsertAtIndex(
                                STACK, BYTE, -1, P.SourceProvider.Value(P.Nbt.Byte(0))
                            )
                        )
                    )
                )
            ),
            pack("const")
        )
    }
}
