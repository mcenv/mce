package mce.pass.backend.pack

val nbtPath: NbtPath = NbtPath()

/**
 * A main storage.
 * @see [net.minecraft.resources.ResourceLocation.isAllowedInResourceLocation]
 */
@Suppress("KDocUnresolvedReference")
val MAIN = ResourceLocation("0")

const val BYTE_KEY = "a"
const val SHORT_KEY = "b"
const val INT_KEY = "c"
const val LONG_KEY = "d"
const val FLOAT_KEY = "e"
const val DOUBLE_KEY = "f"
const val BYTE_ARRAY_KEY = "g"
const val STRING_KEY = "h"
const val LIST_KEY = "i"
const val COMPOUND_KEY = "j"
const val INT_ARRAY_KEY = "k"
const val LONG_ARRAY_KEY = "l"
const val SCRUTINEE_KEY = "m"

val BYTE = nbtPath[BYTE_KEY]
val SHORT = nbtPath[SHORT_KEY]
val INT = nbtPath[INT_KEY]
val LONG = nbtPath[LONG_KEY]
val FLOAT = nbtPath[FLOAT_KEY]
val DOUBLE = nbtPath[DOUBLE_KEY]
val BYTE_ARRAY = nbtPath[BYTE_ARRAY_KEY]
val STRING = nbtPath[STRING_KEY]
val LIST = nbtPath[LIST_KEY]
val COMPOUND = nbtPath[COMPOUND_KEY]
val INT_ARRAY = nbtPath[INT_ARRAY_KEY]
val LONG_ARRAY = nbtPath[LONG_ARRAY_KEY]
val SCRUTINEE = nbtPath[SCRUTINEE_KEY]

/**
 * An objective to store registers.
 * @see [net.minecraft.commands.arguments.ObjectiveArgument.parse]
 */
@Suppress("KDocUnresolvedReference")
val REG = Objective("0")

val R0 = ScoreHolder("0")
val R1 = ScoreHolder("1")

/**
 * A resource location of the apply function.
 */
val APPLY = ResourceLocation("apply")
