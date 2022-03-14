package mce.builtin.src.int

import mce.ast.Core
import mce.builtin.commuter
import mce.phase.Normalizer

fun Normalizer.eq(): Core.VTerm {
    val a = lookup(size - 2)
    val b = lookup(size - 1)
    return when {
        a is Core.VTerm.IntOf && b is Core.VTerm.IntOf -> Core.VTerm.BoolOf(a.value == b.value)
        // a == a = true
        a is Core.VTerm.Var && b is Core.VTerm.Var && a.level == b.level -> Core.VTerm.BoolOf(true)
        else -> Core.VTerm.Def("int/eq", listOf(a, b).sortedWith(commuter).map { lazyOf(it) })
    }
}
