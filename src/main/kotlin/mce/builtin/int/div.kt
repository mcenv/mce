package mce.builtin.int

import mce.ast.Core
import mce.phase.frontend.Normalizer

fun Normalizer.div(): Core.VTerm {
    val a = lookup(size - 2)
    val b = lookup(size - 1)
    return when {
        a is Core.VTerm.IntOf && b is Core.VTerm.IntOf -> Core.VTerm.IntOf(Math.floorDiv(a.value, b.value))
        // a / 1 = a
        b is Core.VTerm.IntOf && b.value == 1 -> a
        // a / a = 1
        a is Core.VTerm.Var && b is Core.VTerm.Var && a.level == b.level -> Core.VTerm.IntOf(1)
        else -> Core.VTerm.Def("int/div", listOf(a, b).map { lazyOf(it) })
    }
}
