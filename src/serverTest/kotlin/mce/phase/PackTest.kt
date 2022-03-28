package mce.phase

import mce.emulator.*
import mce.fetch
import mce.phase.backend.pack.*
import mce.phase.backend.pack.Command.Append
import mce.phase.backend.pack.Command.GetData
import mce.phase.backend.pack.Consumer.RESULT
import mce.phase.backend.pack.Execute.Run
import mce.phase.backend.pack.Execute.StoreValue
import mce.phase.backend.pack.Function
import mce.phase.backend.pack.SourceProvider.Value
import mce.server.build.Key
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
                            Command.Execute(StoreValue(RESULT, R0, REG, Run(GetData(MAIN, INT[-1])))),
                            Pop(MAIN, INT),
                        ),
                    ),
                    Function(
                        ResourceLocation("const"),
                        listOf(
                            Append(MAIN, BYTE, Value(Nbt.Byte(0))),
                        ),
                    ),
                ),
            ),
            pack("const"),
        )
    }

    @Test
    fun match_boolean() {
        val result = pack("match_boolean")

        val storage = NbtStorage()
        Executor(
            functions = result.functions.associateBy { it.name },
            scoreboard = Scoreboard(mutableMapOf(REG.name to REG)),
            storage = storage,
        ).runTopFunction(ResourceLocation("match_boolean"))

        assertEquals(
            CompoundNbt(
                mutableMapOf(
                    BYTE_KEY to ListNbt(
                        mutableListOf(
                            ByteNbt(1),
                        ),
                        NbtType.BYTE,
                    ),
                ),
            ),
            storage[MAIN],
        )
    }

    @Test
    fun match_variable() {
        val result = pack("match_variable")

        val storage = NbtStorage()
        Executor(
            functions = result.functions.associateBy { it.name },
            scoreboard = Scoreboard(mutableMapOf(REG.name to REG)),
            storage = storage,
        ).runTopFunction(ResourceLocation("match_variable"))

        assertEquals(
            CompoundNbt(
                mutableMapOf(
                    BYTE_KEY to ListNbt(
                        mutableListOf(
                            ByteNbt(0),
                        ),
                        NbtType.BYTE,
                    ),
                ),
            ),
            storage[MAIN],
        )
    }

    @Test
    fun pack_match() {
        val result = pack("pack_match")

        val storage = NbtStorage()
        Executor(
            functions = result.functions.associateBy { it.name },
            scoreboard = Scoreboard(mutableMapOf(REG.name to REG)),
            storage = storage,
        ).runTopFunction(ResourceLocation("pack_match"))

        assertEquals(
            CompoundNbt(
                mutableMapOf(
                    INT_KEY to ListNbt(
                        mutableListOf(),
                        NbtType.INT,
                    ),
                    STRING_KEY to ListNbt(
                        mutableListOf(
                            StringNbt("else"),
                        ),
                        NbtType.STRING,
                    ),
                ),
            ),
            storage[MAIN],
        )
    }
}
