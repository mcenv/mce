package mce.builtin.src.list

import mce.ast.core.VTerm
import mce.ast.pack.*
import mce.ast.pack.Command.CheckMatchingData
import mce.ast.pack.Consumer.RESULT
import mce.ast.pack.Execute.Run
import mce.ast.pack.Execute.StoreData
import mce.ast.pack.SourceProvider.Value
import mce.builtin.BuiltinFunction3

object size : BuiltinFunction3("list/size") {
    override fun eval(a: VTerm, b: VTerm, c: VTerm): VTerm? = when (c) {
        is VTerm.ListOf -> VTerm.IntOf(c.elements.size)
        else -> null
    }

    override fun pack(): List<Command> = listOf(
        Append(STACKS, INT, Value(Nbt.Int(0))),
        Command.Execute(StoreData(RESULT, STACKS, INT[-1], StoreType.INT, 1.0, Run(CheckMatchingData(true, STACKS, LIST[-1]())))),
    )
}
