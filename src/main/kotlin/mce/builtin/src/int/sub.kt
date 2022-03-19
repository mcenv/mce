package mce.builtin.src.int

import mce.ast.core.VTerm
import mce.phase.Normalizer

fun Normalizer.sub(): VTerm {
    val a = lookup(size - 2)
    val b = lookup(size - 1)
    return when {
        a is VTerm.IntOf && b is VTerm.IntOf -> VTerm.IntOf(a.value - b.value)
        // a - 0 = a
        b is VTerm.IntOf && b.value == 0 -> a
        // a - a = 0
        a is VTerm.Var && b is VTerm.Var && a.level == b.level -> VTerm.IntOf(0)
        else -> VTerm.Def("int/sub", listOf(a, b).map { lazyOf(it) })
    }
}
