package mce.phase.backend.pack

val nbtPath: NbtPath = NbtPath()

/**
 * A storage to store stacks.
 * @see [net.minecraft.resources.ResourceLocation.isAllowedInResourceLocation]
 */
@Suppress("KDocUnresolvedReference")
val STACKS = ResourceLocation("0")

val BYTE = nbtPath["a"]
val SHORT = nbtPath["b"]
val INT = nbtPath["c"]
val LONG = nbtPath["d"]
val FLOAT = nbtPath["e"]
val DOUBLE = nbtPath["f"]
val BYTE_ARRAY = nbtPath["g"]
val STRING = nbtPath["h"]
val LIST = nbtPath["i"]
val COMPOUND = nbtPath["j"]
val INT_ARRAY = nbtPath["k"]
val LONG_ARRAY = nbtPath["l"]

/**
 * An objective to store registers.
 * @see [net.minecraft.commands.arguments.ObjectiveArgument.parse]
 */
@Suppress("KDocUnresolvedReference")
val REGISTERS = Objective("0")

val REGISTER_0 = ScoreHolder("0")
val REGISTER_1 = ScoreHolder("1")

/**
 * A resource location of the apply function.
 */
val APPLY = ResourceLocation("apply")
