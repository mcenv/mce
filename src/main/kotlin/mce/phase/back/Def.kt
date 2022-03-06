package mce.phase.back

import mce.graph.Packed.NbtPath
import mce.graph.Packed.Objective
import mce.graph.Packed.ResourceLocation
import mce.graph.Packed.ScoreHolder
import mce.phase.back.Dsl.get

object Def {
    /**
     * A storage to store stacks.
     * @see [net.minecraft.resources.ResourceLocation.isAllowedInResourceLocation]
     */
    @Suppress("KDocUnresolvedReference")
    val STACKS = ResourceLocation("0")

    val BYTE = NbtPath()["a"]
    val SHORT = NbtPath()["b"]
    val INT = NbtPath()["c"]
    val LONG = NbtPath()["d"]
    val FLOAT = NbtPath()["e"]
    val DOUBLE = NbtPath()["f"]
    val BYTE_ARRAY = NbtPath()["g"]
    val STRING = NbtPath()["h"]
    val LIST = NbtPath()["i"]
    val COMPOUND = NbtPath()["j"]
    val INT_ARRAY = NbtPath()["k"]
    val LONG_ARRAY = NbtPath()["l"]

    /**
     * An objective to store registers.
     * @see [net.minecraft.commands.arguments.ObjectiveArgument.parse]
     */
    @Suppress("KDocUnresolvedReference")
    val REGISTERS = Objective("0")

    val REGISTER_0 = ScoreHolder("0")

    /**
     * A resource location of the apply function.
     */
    val APPLY = ResourceLocation("apply")
}
