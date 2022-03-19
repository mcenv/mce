package mce.builtin.src.long_array

import mce.ast.core.VTerm
import mce.phase.Normalizer

fun Normalizer.size(): VTerm {
    return when (val ls = lookup(size - 1)) {
        is VTerm.ListOf -> VTerm.IntOf(ls.elements.size)
        else -> VTerm.Def("long_array/size", listOf(ls).map { lazyOf(it) })
    }
}
