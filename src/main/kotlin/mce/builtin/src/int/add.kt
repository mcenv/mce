package mce.builtin.src.int

import mce.ast.core.VTerm
import mce.builtin.BuiltinFunction2
import mce.builtin.commuter

object add : BuiltinFunction2("int/add") {
    override fun eval(a: VTerm, b: VTerm): VTerm = when {
        a is VTerm.IntOf && b is VTerm.IntOf -> VTerm.IntOf(a.value + b.value)
        // 0 + b = b
        a is VTerm.IntOf && a.value == 0 -> b
        // a + 0 = a
        b is VTerm.IntOf && b.value == 0 -> a
        else -> VTerm.Def(name, listOf(a, b).sortedWith(commuter).map { lazyOf(it) })
    }
}
