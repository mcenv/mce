package mce.builtin.src.int

import mce.ast.core.VTerm
import mce.builtin.commuter
import mce.phase.Normalizer

fun Normalizer.eq(): VTerm {
    val a = lookup(size - 2)
    val b = lookup(size - 1)
    return when {
        a is VTerm.IntOf && b is VTerm.IntOf -> VTerm.BoolOf(a.value == b.value)
        // a == a = true
        a is VTerm.Var && b is VTerm.Var && a.level == b.level -> VTerm.BoolOf(true)
        else -> VTerm.Def("int/eq", listOf(a, b).sortedWith(commuter).map { lazyOf(it) })
    }
}
