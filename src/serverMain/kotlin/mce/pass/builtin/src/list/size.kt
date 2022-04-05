package mce.pass.builtin.src.list

import mce.ast.core.VTerm
import mce.ast.pack.Command
import mce.ast.pack.Command.*
import mce.ast.pack.Consumer.RESULT
import mce.ast.pack.Execute.Run
import mce.ast.pack.Execute.StoreData
import mce.ast.pack.Nbt
import mce.ast.pack.SourceProvider.Value
import mce.ast.pack.StoreType
import mce.pass.backend.*
import mce.pass.builtin.BuiltinDef3

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
