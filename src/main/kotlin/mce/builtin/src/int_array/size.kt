package mce.builtin.src.int_array

import mce.ast.core.VTerm
import mce.phase.Normalizer

fun Normalizer.size(): VTerm {
    return when (val `is` = lookup(size - 1)) {
        is VTerm.ListOf -> VTerm.IntOf(`is`.elements.size)
        else -> VTerm.Def("int_array/size", listOf(`is`).map { lazyOf(it) })
    }
}
