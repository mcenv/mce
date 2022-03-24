package mce.phase.builtin.src

import mce.phase.backend.pack.Command
import mce.phase.builtin.BuiltinDef1
import mce.phase.frontend.elab.VTerm

@Suppress("ClassName")
object identity : BuiltinDef1("identity") {
    override fun eval(a: VTerm): VTerm = a

    override fun pack(): List<Command> = emptyList()
}
