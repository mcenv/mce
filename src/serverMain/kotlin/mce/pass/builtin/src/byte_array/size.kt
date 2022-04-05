package mce.pass.builtin.src.byte_array

import mce.ast.core.VTerm
import mce.ast.pack.Command
import mce.ast.pack.Command.Append
import mce.ast.pack.Command.CheckMatchingData
import mce.ast.pack.Consumer.RESULT
import mce.ast.pack.Execute.Run
import mce.ast.pack.Execute.StoreData
import mce.ast.pack.Nbt
import mce.ast.pack.SourceProvider.Value
import mce.ast.pack.StoreType
import mce.pass.backend.*
import mce.pass.builtin.BuiltinDef1

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
