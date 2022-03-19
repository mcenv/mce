package mce.builtin.src.long_array

import mce.ast.core.VTerm
import mce.ast.pack.*
import mce.ast.pack.Command.CheckMatchingData
import mce.ast.pack.Consumer.RESULT
import mce.ast.pack.Execute.Run
import mce.ast.pack.Execute.StoreData
import mce.ast.pack.SourceProvider.Value
import mce.builtin.BuiltinDef1

@Suppress("ClassName")
object size : BuiltinDef1("long_array/size") {
    override fun eval(a: VTerm): VTerm? = when (a) {
        is VTerm.ListOf -> VTerm.IntOf(a.elements.size)
        else -> null
    }

    override fun pack(): List<Command> = listOf(
        Append(STACKS, INT, Value(Nbt.Int(0))),
        Command.Execute(StoreData(RESULT, STACKS, INT[-1], StoreType.INT, 1.0, Run(CheckMatchingData(true, STACKS, LONG_ARRAY[-1]())))),
    )
}
