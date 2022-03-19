package mce.builtin.src.int

import mce.ast.core.VTerm
import mce.ast.pack.*
import mce.ast.pack.Command.GetData
import mce.ast.pack.Command.RemoveData
import mce.ast.pack.Consumer.RESULT
import mce.ast.pack.Execute.*
import mce.ast.pack.SourceComparator.Eq
import mce.ast.pack.SourceProvider.Value
import mce.builtin.BuiltinDef2
import mce.builtin.commuter

object eq : BuiltinDef2("int/eq") {
    override fun eval(a: VTerm, b: VTerm): VTerm = when {
        a is VTerm.IntOf && b is VTerm.IntOf -> VTerm.BoolOf(a.value == b.value)
        // a == a = true
        a is VTerm.Var && b is VTerm.Var && a.level == b.level -> VTerm.BoolOf(true)
        else -> VTerm.Def(name, listOf(a, b).sortedWith(commuter).map { lazyOf(it) })
    }

    override fun pack(): List<Command> = listOf(
        Command.Execute(StoreValue(RESULT, REGISTER_0, REGISTERS, Run(GetData(STACKS, INT[-1])))),
        RemoveData(STACKS, INT[-1]),
        Command.Execute(StoreValue(RESULT, REGISTER_1, REGISTERS, Run(GetData(STACKS, INT[-1])))),
        RemoveData(STACKS, INT[-1]),
        Command.Execute(CheckScore(false, REGISTER_1, REGISTERS, Eq(REGISTER_0, REGISTERS), Run(Append(STACKS, BYTE, Value(Nbt.Byte(0)))))),
        Command.Execute(CheckScore(true, REGISTER_1, REGISTERS, Eq(REGISTER_0, REGISTERS), Run(Append(STACKS, BYTE, Value(Nbt.Byte(1)))))),
    )
}
