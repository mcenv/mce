package mce.builtin.src.int

import mce.ast.core.VTerm
import mce.builtin.BuiltinFunction2
import mce.builtin.commuter

object ne : BuiltinFunction2("int/ne") {
    override fun eval(a: VTerm, b: VTerm): VTerm = when {
        a is VTerm.IntOf && b is VTerm.IntOf -> VTerm.BoolOf(a.value != b.value)
        else -> VTerm.Def(name, listOf(a, b).sortedWith(commuter).map { lazyOf(it) })
    }
}
