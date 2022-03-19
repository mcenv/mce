package mce.builtin.src.int

import mce.ast.core.VTerm
import mce.phase.Normalizer

fun Normalizer.div(): VTerm {
    val a = lookup(size - 2)
    val b = lookup(size - 1)
    return when {
        a is VTerm.IntOf && b is VTerm.IntOf -> VTerm.IntOf(Math.floorDiv(a.value, b.value))
        // a / 1 = a
        b is VTerm.IntOf && b.value == 1 -> a
        // a / a = 1
        a is VTerm.Var && b is VTerm.Var && a.level == b.level -> VTerm.IntOf(1)
        else -> VTerm.Def("int/div", listOf(a, b).map { lazyOf(it) })
    }
}
