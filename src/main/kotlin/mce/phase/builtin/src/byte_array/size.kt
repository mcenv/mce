package mce.phase.builtin.src.byte_array

import mce.phase.backend.pack.*
import mce.phase.backend.pack.Command.Append
import mce.phase.backend.pack.Command.CheckMatchingData
import mce.phase.backend.pack.Consumer.RESULT
import mce.phase.backend.pack.Execute.Run
import mce.phase.backend.pack.Execute.StoreData
import mce.phase.backend.pack.SourceProvider.Value
import mce.phase.builtin.BuiltinDef1
import mce.phase.frontend.elab.VTerm

@Suppress("ClassName")
object size : BuiltinDef1("byte_array/size") {
    override fun eval(a: VTerm): VTerm? = when (a) {
        is VTerm.ListOf -> VTerm.IntOf(a.elements.size)
        else -> null
    }

    override fun pack(): List<Command> = listOf(
        Append(MAIN, INT, Value(Nbt.Int(0))),
        Command.Execute(StoreData(RESULT, MAIN, INT[-1], StoreType.INT, 1.0, Run(CheckMatchingData(true, MAIN, BYTE_ARRAY[-1]())))),
    )
}
