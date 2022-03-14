package mce.builtin.src.long_array

import mce.ast.Core
import mce.phase.Normalizer

fun Normalizer.size(): Core.VTerm {
    return when (val ls = lookup(size - 1)) {
        is Core.VTerm.ListOf -> Core.VTerm.IntOf(ls.elements.size)
        else -> Core.VTerm.Def("long_array/size", listOf(ls).map { lazyOf(it) })
    }
}
