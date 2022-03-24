package mce.builtin.src.int

import mce.ast.core.VTerm
import mce.ast.pack.*
import mce.ast.pack.Command.*
import mce.ast.pack.Consumer.RESULT
import mce.ast.pack.Execute.Run
import mce.ast.pack.Execute.StoreValue
import mce.ast.pack.SourceComparator.Eq
import mce.ast.pack.SourceProvider.Value
import mce.builtin.BuiltinDef2
import mce.builtin.commuter
import mce.ast.pack.Execute as E

@Suppress("ClassName")
object ne : BuiltinDef2("int/ne") {
    override fun eval(a: VTerm, b: VTerm): VTerm = when {
        a is VTerm.IntOf && b is VTerm.IntOf -> VTerm.BoolOf(a.value != b.value)
        else -> VTerm.Def(name, listOf(a, b).sortedWith(commuter).map { lazyOf(it) })
    }

    override fun pack(): List<Command> = listOf(
        Execute(StoreValue(RESULT, REGISTER_0, REGISTERS, Run(GetData(STACKS, INT[-1])))),
        Pop(STACKS, INT),
        Execute(StoreValue(RESULT, REGISTER_1, REGISTERS, Run(GetData(STACKS, INT[-1])))),
        Pop(STACKS, INT),
        Execute(E.CheckScore(false, REGISTER_1, REGISTERS, Eq(REGISTER_0, REGISTERS), Run(Append(STACKS, BYTE, Value(Nbt.Byte(1)))))),
        Execute(E.CheckScore(true, REGISTER_1, REGISTERS, Eq(REGISTER_0, REGISTERS), Run(Append(STACKS, BYTE, Value(Nbt.Byte(0)))))),
    )
}
