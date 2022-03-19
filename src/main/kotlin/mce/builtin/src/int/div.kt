package mce.builtin.src.int

import mce.ast.core.VTerm
import mce.builtin.BuiltinFunction2

object div : BuiltinFunction2("int/div") {
    override fun eval(a: VTerm, b: VTerm): VTerm? = when {
        a is VTerm.IntOf && b is VTerm.IntOf -> VTerm.IntOf(Math.floorDiv(a.value, b.value))
        // a / 1 = a
        b is VTerm.IntOf && b.value == 1 -> a
        // a / a = 1
        a is VTerm.Var && b is VTerm.Var && a.level == b.level -> VTerm.IntOf(1)
        else -> null
    }
}
