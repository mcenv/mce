package mce.builtin.src.int

import mce.ast.core.VTerm
import mce.builtin.BuiltinFunction2
import mce.builtin.commuter

object mul : BuiltinFunction2("int/mul") {
    override fun eval(a: VTerm, b: VTerm): VTerm = when {
        a is VTerm.IntOf && b is VTerm.IntOf -> VTerm.IntOf(a.value * b.value)
        // 0 * b = 0
        a is VTerm.IntOf && a.value == 0 -> VTerm.IntOf(0)
        // a * 0 = 0
        b is VTerm.IntOf && b.value == 0 -> VTerm.IntOf(0)
        // 1 * b = b
        a is VTerm.IntOf && a.value == 1 -> b
        // a * 1 = a
        b is VTerm.IntOf && b.value == 1 -> a
        else -> VTerm.Def(name, listOf(a, b).sortedWith(commuter).map { lazyOf(it) })
    }
}
