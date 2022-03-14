package mce.builtin.src.int

import mce.ast.Core
import mce.builtin.commuter
import mce.phase.Normalizer

fun Normalizer.mul(): Core.VTerm {
    val a = lookup(size - 2)
    val b = lookup(size - 1)
    return when {
        a is Core.VTerm.IntOf && b is Core.VTerm.IntOf -> Core.VTerm.IntOf(a.value * b.value)
        // 0 * b = 0
        a is Core.VTerm.IntOf && a.value == 0 -> Core.VTerm.IntOf(0)
        // a * 0 = 0
        b is Core.VTerm.IntOf && b.value == 0 -> Core.VTerm.IntOf(0)
        // 1 * b = b
        a is Core.VTerm.IntOf && a.value == 1 -> b
        // a * 1 = a
        b is Core.VTerm.IntOf && b.value == 1 -> a
        else -> Core.VTerm.Def("int/mul", listOf(a, b).sortedWith(commuter).map { lazyOf(it) })
    }
}
