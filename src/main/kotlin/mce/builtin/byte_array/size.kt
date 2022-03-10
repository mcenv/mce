package mce.builtin.byte_array

import mce.ast.Core
import mce.phase.frontend.Normalizer

fun Normalizer.size(): Core.VTerm {
    return when (val bs = lookup(size - 1)) {
        is Core.VTerm.ListOf -> Core.VTerm.IntOf(bs.elements.size)
        else -> Core.VTerm.Def("byte_array/size", listOf(bs).map { lazyOf(it) })
    }
}
