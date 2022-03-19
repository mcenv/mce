package mce.builtin.src.byte_array

import mce.ast.core.VTerm
import mce.phase.Normalizer

fun Normalizer.size(): VTerm {
    return when (val bs = lookup(size - 1)) {
        is VTerm.ListOf -> VTerm.IntOf(bs.elements.size)
        else -> VTerm.Def("byte_array/size", listOf(bs).map { lazyOf(it) })
    }
}
