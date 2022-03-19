package mce.builtin.src.int

import mce.ast.core.VTerm
import mce.builtin.BuiltinFunction2

object sub : BuiltinFunction2("int/sub") {
    override fun eval(a: VTerm, b: VTerm): VTerm? = when {
        a is VTerm.IntOf && b is VTerm.IntOf -> VTerm.IntOf(a.value - b.value)
        // a - 0 = a
        b is VTerm.IntOf && b.value == 0 -> a
        // a - a = 0
        a is VTerm.Var && b is VTerm.Var && a.level == b.level -> VTerm.IntOf(0)
        else -> null
    }
}
