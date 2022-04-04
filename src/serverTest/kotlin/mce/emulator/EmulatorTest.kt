package mce.emulator

import mce.pass.backend.pack.*
import mce.pass.backend.pack.SourceProvider.Value
import kotlin.test.Test
import kotlin.test.assertEquals
import mce.pass.backend.pack.Function as PFunction

class EmulatorTest {
    @Test
    fun commandOrder() {
        val a = ResourceLocation("a")
        val storage = NbtStorage()
        val executor = Executor(
            functions = mapOf(
                a to PFunction(
                    a,
                    listOf(
                        Command.InsertAtIndex(a, nbtPath["a"], -1, Value(Nbt.Int(0))),
                        Command.InsertAtIndex(a, nbtPath["a"], -1, Value(Nbt.Int(1))),
                    ),
                ),
            ),
            storage = storage,
        )
        executor.runTopFunction(a)

        assertEquals(
            CompoundNbt(
                mutableMapOf(
                    "a" to ListNbt(
                        mutableListOf(
                            IntNbt(0),
                            IntNbt(1),
                        ),
                        NbtType.INT,
                    ),
                ),
            ),
            storage[a],
        )
    }

    @Test
    fun commandOrderNested() {
        val a = ResourceLocation("a")
        val b = ResourceLocation("b")
        val storage = NbtStorage()
        val executor = Executor(
            functions = mapOf(
                a to PFunction(
                    a,
                    listOf(
                        Command.InsertAtIndex(a, nbtPath["a"], -1, Value(Nbt.Int(0))),
                        Command.InsertAtIndex(a, nbtPath["a"], -1, Value(Nbt.Int(1))),
                        Command.RunFunction(b),
                    ),
                ),
                b to PFunction(
                    b,
                    listOf(
                        Command.InsertAtIndex(a, nbtPath["a"], -1, Value(Nbt.Int(2))),
                        Command.InsertAtIndex(a, nbtPath["a"], -1, Value(Nbt.Int(3))),
                    )
                )
            ),
            storage = storage,
        )
        executor.runTopFunction(a)

        assertEquals(
            CompoundNbt(
                mutableMapOf(
                    "a" to ListNbt(
                        mutableListOf(
                            IntNbt(0),
                            IntNbt(1),
                            IntNbt(2),
                            IntNbt(3),
                        ),
                        NbtType.INT,
                    ),
                ),
            ),
            storage[a],
        )
    }
}
