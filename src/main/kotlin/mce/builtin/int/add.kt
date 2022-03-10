package mce.builtin.int

import mce.ast.Core
import mce.builtin.commuter
import mce.phase.frontend.Normalizer

fun Normalizer.add(): Core.VTerm {
    val a = lookup(size - 2)
    val b = lookup(size - 1)
    return when {
        a is Core.VTerm.IntOf && b is Core.VTerm.IntOf -> Core.VTerm.IntOf(a.value + b.value)
        // 0 + b = b
        a is Core.VTerm.IntOf && a.value == 0 -> b
        // a + 0 = a
        b is Core.VTerm.IntOf && b.value == 0 -> a
        else -> Core.VTerm.Def("int/add", listOf(a, b).sortedWith(commuter).map { lazyOf(it) })
    }
}
