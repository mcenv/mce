package mce.emulator

import mce.ast.pack.Command
import mce.ast.pack.Nbt
import mce.ast.pack.NbtType
import mce.ast.pack.ResourceLocation
import mce.ast.pack.SourceProvider.Value
import mce.phase.backend.Pack.Companion.get
import mce.phase.backend.Pack.Companion.nbtPath
import kotlin.test.Test
import kotlin.test.assertEquals
import mce.ast.pack.Function as PFunction

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
