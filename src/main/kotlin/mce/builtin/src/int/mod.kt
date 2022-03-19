package mce.builtin.src.int

import mce.ast.core.VTerm
import mce.builtin.BuiltinFunction2

object mod : BuiltinFunction2("int/mod") {
    override fun eval(a: VTerm, b: VTerm): VTerm? = when {
        a is VTerm.IntOf && b is VTerm.IntOf -> VTerm.IntOf(Math.floorMod(a.value, b.value))
        // a % 1 = 0
        b is VTerm.IntOf && b.value == 1 -> a
        // a % a = 0
        a is VTerm.Var && b is VTerm.Var && a.level == b.level -> VTerm.IntOf(0)
        else -> null
    }
}
