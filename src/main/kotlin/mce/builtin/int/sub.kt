package mce.builtin.int

import mce.ast.Core
import mce.phase.Normalizer

fun Normalizer.sub(): Core.VTerm {
    val a = lookup(size - 2)
    val b = lookup(size - 1)
    return when {
        a is Core.VTerm.IntOf && b is Core.VTerm.IntOf -> Core.VTerm.IntOf(a.value - b.value)
        // a - 0 = a
        b is Core.VTerm.IntOf && b.value == 0 -> a
        // a - a = 0
        a is Core.VTerm.Var && b is Core.VTerm.Var && a.level == b.level -> Core.VTerm.IntOf(0)
        else -> Core.VTerm.Def("int/sub", listOf(a, b).map { lazyOf(it) })
    }
}
