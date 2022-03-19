package mce.phase

import mce.ast.pack.*
import mce.ast.pack.Command.*
import mce.ast.pack.Consumer.RESULT
import mce.ast.pack.Execute.Run
import mce.ast.pack.Execute.StoreValue
import mce.ast.pack.Function
import mce.ast.pack.SourceProvider.Value
import mce.fetch
import mce.server.Key
import kotlin.test.Test
import kotlin.test.assertEquals

class PackTest {
    private fun pack(name: String): Datapack = fetch(Key.Datapack(name))

    @Test
    fun byte() {
        assertEquals(
            Datapack(
                listOf(
                    Function(
                        ResourceLocation("apply"),
                        listOf(
                            Command.Execute(StoreValue(RESULT, REGISTER_0, REGISTERS, Run(GetData(STACKS, INT[-1])))),
                            RemoveData(STACKS, INT[-1]),
                        ),
                    ),
                    Function(
                        ResourceLocation("const"),
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
