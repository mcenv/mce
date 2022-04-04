package mce.pass.builtin.src

import mce.pass.backend.pack.Command
import mce.pass.builtin.BuiltinDef1
import mce.pass.frontend.elab.VTerm

@Suppress("ClassName")
object identity : BuiltinDef1("identity") {
    override fun eval(a: VTerm): VTerm = a

    override fun pack(): List<Command> = emptyList()
}
