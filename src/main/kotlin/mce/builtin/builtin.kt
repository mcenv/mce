package mce.builtin

import mce.ast.Core
import mce.phase.Normalizer
import mce.builtin.src.byte_array.size as byte_array_size
import mce.builtin.src.int.add as int_add
import mce.builtin.src.int.div as int_div
import mce.builtin.src.int.eq as int_eq
import mce.builtin.src.int.mod as int_mod
import mce.builtin.src.int.mul as int_mul
import mce.builtin.src.int.ne as int_ne
import mce.builtin.src.int.sub as int_sub
import mce.builtin.src.int_array.size as int_array_size
import mce.builtin.src.list.size as list_size
import mce.builtin.src.long_array.size as long_array_size

val builtins: Map<String, Normalizer.() -> Core.VTerm> = mapOf(
    "int/eq" to { int_eq() },
    "int/ne" to { int_ne() },
    "int/add" to { int_add() },
    "int/sub" to { int_sub() },
    "int/mul" to { int_mul() },
    "int/div" to { int_div() },
    "int/mod" to { int_mod() },
    "byte_array/size" to { byte_array_size() },
    "int_array/size" to { int_array_size() },
    "long_array/size" to { long_array_size() },
    "list/size" to { list_size() },
)

@Suppress("NAME_SHADOWING")
val commuter: Comparator<Core.VTerm> = Comparator { term1, term2 ->
    when {
        term1 is Core.VTerm.Var && term2 is Core.VTerm.Var -> term1.level.compareTo(term2.level)
        term1 is Core.VTerm.Var -> 1
        term2 is Core.VTerm.Var -> -1
        else -> 0
    }
}
