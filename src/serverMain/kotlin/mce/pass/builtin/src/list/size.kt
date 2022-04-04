package mce.pass.builtin.src.list

import mce.pass.backend.pack.*
import mce.pass.backend.pack.Command.*
import mce.pass.backend.pack.Command.Execute
import mce.pass.backend.pack.Consumer.RESULT
import mce.pass.backend.pack.Execute.Run
import mce.pass.backend.pack.Execute.StoreData
import mce.pass.backend.pack.SourceProvider.Value
import mce.pass.builtin.BuiltinDef3
import mce.pass.frontend.elab.VTerm

@Suppress("ClassName")
object size : BuiltinDef3("list/size") {
    override fun eval(a: VTerm, b: VTerm, c: VTerm): VTerm? = when (c) {
        is VTerm.ListOf -> VTerm.IntOf(c.elements.size)
        else -> null
    }

    override fun pack(): List<Command> = listOf(
        Append(MAIN, INT, Value(Nbt.Int(0))),
        Execute(StoreData(RESULT, MAIN, INT[-1], StoreType.INT, 1.0, Run(CheckMatchingData(true, MAIN, LIST[-1]())))),
    )
}
