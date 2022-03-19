package mce.builtin.src.int

import mce.ast.core.VTerm
import mce.builtin.commuter
import mce.phase.Normalizer

fun Normalizer.ne(): VTerm {
    val a = lookup(size - 2)
    val b = lookup(size - 1)
    return when {
        a is VTerm.IntOf && b is VTerm.IntOf -> VTerm.BoolOf(a.value != b.value)
        else -> VTerm.Def("int/ne", listOf(a, b).sortedWith(commuter).map { lazyOf(it) })
    }
}
