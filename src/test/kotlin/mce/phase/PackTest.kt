package mce.phase

import mce.ast.Packed.Command.*
import mce.ast.Packed.Consumer.RESULT
import mce.ast.Packed.Execute.Run
import mce.ast.Packed.Execute.StoreValue
import mce.ast.Packed.Nbt
import mce.ast.Packed.SourceProvider.Value
import mce.fetch
import mce.phase.backend.Pack.Companion.BYTE
import mce.phase.backend.Pack.Companion.INT
import mce.phase.backend.Pack.Companion.REGISTERS
import mce.phase.backend.Pack.Companion.REGISTER_0
import mce.phase.backend.Pack.Companion.STACKS
import mce.phase.backend.Pack.Companion.get
import mce.server.Key
import kotlin.test.Test
import kotlin.test.assertEquals
import mce.ast.Packed as P

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
