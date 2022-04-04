package mce.pass.builtin.src.byte_array

import mce.pass.backend.pack.*
import mce.pass.backend.pack.Command.Append
import mce.pass.backend.pack.Command.CheckMatchingData
import mce.pass.backend.pack.Consumer.RESULT
import mce.pass.backend.pack.Execute.Run
import mce.pass.backend.pack.Execute.StoreData
import mce.pass.backend.pack.SourceProvider.Value
import mce.pass.builtin.BuiltinDef1
import mce.pass.frontend.elab.VTerm

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
