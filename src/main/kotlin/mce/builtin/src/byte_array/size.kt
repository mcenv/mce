package mce.builtin.src.byte_array

import mce.ast.core.VTerm
import mce.builtin.BuiltinFunction1

object size : BuiltinFunction1("byte_array/size") {
    override fun eval(a: VTerm): VTerm? = when (a) {
        is VTerm.ListOf -> VTerm.IntOf(a.elements.size)
        else -> null
    }
}
