package mce.pass.builtin.src.string

import mce.ast.core.VTerm
import mce.ast.pack.Command
import mce.pass.builtin.BuiltinDef2
import mce.pass.builtin.commuter

@Suppress("ClassName")
object `+` : BuiltinDef2("string/+") {
    override fun eval(a: VTerm, b: VTerm): VTerm = when {
        a is VTerm.StringOf && b is VTerm.StringOf -> VTerm.StringOf(a.value + b.value)
        // "" + b = b
        a is VTerm.StringOf && a.value.isEmpty() -> b
        // a + "" = a
        b is VTerm.StringOf && b.value.isEmpty() -> a
        else -> VTerm.Def(name, listOf(a, b).sortedWith(commuter).map { lazyOf(it) })
    }

    override fun pack(): List<Command> = TODO()
}
