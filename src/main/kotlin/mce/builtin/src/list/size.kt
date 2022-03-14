package mce.builtin.src.list

import mce.ast.Core
import mce.phase.Normalizer

fun Normalizer.size(): Core.VTerm {
    return when (val `as` = lookup(size - 1)) {
        is Core.VTerm.ListOf -> Core.VTerm.IntOf(`as`.elements.size)
        else -> {
            val α = lookup(size - 3)
            val n = lookup(size - 2)
            Core.VTerm.Def("list/size", listOf(α, n, `as`).map(::lazyOf))
        }
    }
}
