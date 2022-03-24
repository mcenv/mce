package mce.phase

import mce.fetch
import mce.phase.backend.pack.*
import mce.phase.backend.pack.Command.Append
import mce.phase.backend.pack.Command.GetData
import mce.phase.backend.pack.Consumer.RESULT
import mce.phase.backend.pack.Execute.Run
import mce.phase.backend.pack.Execute.StoreValue
import mce.phase.backend.pack.Function
import mce.phase.backend.pack.SourceProvider.Value
import mce.server.Key
import kotlin.test.Test
import kotlin.test.assertEquals

class PackTest {
    private fun pack(name: String): Pack.Result = fetch(Key.PackResult(name))

    @Test
    fun byte() {
        assertEquals(
            Pack.Result(
                listOf(
                    Function(
                        ResourceLocation("apply"),
                        listOf(
                            Command.Execute(StoreValue(RESULT, REGISTER_0, REGISTERS, Run(GetData(STACKS, INT[-1])))),
                            Pop(STACKS, INT),
                        ),
                    ),
                    Function(
                        ResourceLocation("const"),
                        listOf(
                            Append(STACKS, BYTE, Value(Nbt.Byte(0))),
                        ),
                    ),
                ),
            ),
            pack("const"),
        )
    }
}
