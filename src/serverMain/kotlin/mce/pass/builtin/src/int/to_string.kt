package mce.pass.builtin.src.int

import mce.ast.core.VTerm
import mce.ast.pack.Command
import mce.pass.builtin.BuiltinDef1

@Suppress("ClassName")
object to_string : BuiltinDef1("int/to_string") {
    override fun eval(a: VTerm): VTerm? = when (a) {
        is VTerm.IntOf -> VTerm.StringOf(a.value.toString())
        else -> null
    }

    override fun pack(): List<Command> = TODO()
}
