package mce.builtin.src.int

import mce.ast.Core
import mce.builtin.commuter
import mce.phase.Normalizer

fun Normalizer.ne(): Core.VTerm {
    val a = lookup(size - 2)
    val b = lookup(size - 1)
    return when {
        a is Core.VTerm.IntOf && b is Core.VTerm.IntOf -> Core.VTerm.BoolOf(a.value != b.value)
        else -> Core.VTerm.Def("int/ne", listOf(a, b).sortedWith(commuter).map { lazyOf(it) })
    }
}
