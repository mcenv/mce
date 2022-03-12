package mce.builtin.int_array

import mce.ast.Core
import mce.phase.Normalizer

fun Normalizer.size(): Core.VTerm {
    return when (val `is` = lookup(size - 1)) {
        is Core.VTerm.ListOf -> Core.VTerm.IntOf(`is`.elements.size)
        else -> Core.VTerm.Def("int_array/size", listOf(`is`).map { lazyOf(it) })
    }
}
