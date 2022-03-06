package mce.phase

import mce.fetch
import mce.graph.Packed.Command.*
import mce.graph.Packed.Consumer.RESULT
import mce.graph.Packed.Execute.Run
import mce.graph.Packed.Execute.StoreValue
import mce.graph.Packed.Nbt
import mce.graph.Packed.SourceProvider.Value
import mce.phase.pack.Def.BYTE
import mce.phase.pack.Def.INT
import mce.phase.pack.Def.REGISTERS
import mce.phase.pack.Def.REGISTER_0
import mce.phase.pack.Def.STACKS
import mce.phase.pack.Dsl.get
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
                        P.ResourceLocation("apply"),
                        listOf(
                            Execute(StoreValue(RESULT, REGISTER_0, REGISTERS, Run(GetData(STACKS, INT[-1])))),
                            RemoveData(STACKS, INT[-1]),
                        ),
                    ),
                    P.Function(
                        P.ResourceLocation("const"),
                        listOf(
                            InsertAtIndex(STACKS, BYTE, -1, Value(Nbt.Byte(0))),
                        ),
                    ),
                ),
            ),
            pack("const"),
        )
    }
}
