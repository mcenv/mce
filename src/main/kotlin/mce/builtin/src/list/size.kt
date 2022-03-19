package mce.builtin.src.list

import mce.ast.core.VTerm
import mce.phase.Normalizer

fun Normalizer.size(): VTerm {
    return when (val `as` = lookup(size - 1)) {
        is VTerm.ListOf -> VTerm.IntOf(`as`.elements.size)
        else -> {
            val α = lookup(size - 3)
            val n = lookup(size - 2)
            VTerm.Def("list/size", listOf(α, n, `as`).map(::lazyOf))
        }
    }
}
