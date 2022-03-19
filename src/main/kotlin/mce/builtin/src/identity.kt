package mce.builtin.src

import mce.ast.core.VTerm
import mce.builtin.BuiltinFunction1

object identity : BuiltinFunction1("identity") {
    override fun eval(a: VTerm): VTerm = a
}
