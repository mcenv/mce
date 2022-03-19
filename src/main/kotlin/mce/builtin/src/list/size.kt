package mce.builtin.src.list

import mce.ast.core.VTerm
import mce.builtin.BuiltinFunction3

object size : BuiltinFunction3("list/size") {
    override fun eval(a: VTerm, b: VTerm, c: VTerm): VTerm? = when (c) {
        is VTerm.ListOf -> VTerm.IntOf(c.elements.size)
        else -> null
    }
}
