package mce.pass.builtin.src

import mce.ast.core.VTerm
import mce.ast.pack.Command
import mce.pass.builtin.BuiltinDef1

@Suppress("ClassName")
object identity : BuiltinDef1("identity") {
    override fun eval(a: VTerm): VTerm = a

    override fun pack(): List<Command> = emptyList()
}
