package mce.pass.builtin.src.int

import mce.pass.backend.pack.*
import mce.pass.backend.pack.Command.*
import mce.pass.backend.pack.Consumer.RESULT
import mce.pass.backend.pack.Execute.Run
import mce.pass.backend.pack.Execute.StoreValue
import mce.pass.backend.pack.SourceComparator.EqScore
import mce.pass.backend.pack.SourceProvider.Value
import mce.pass.builtin.BuiltinDef2
import mce.pass.builtin.commuter
import mce.pass.frontend.elab.VTerm
import mce.pass.backend.pack.Execute as E

@Suppress("ClassName")
object eq : BuiltinDef2("int/eq") {
    override fun eval(a: VTerm, b: VTerm): VTerm = when {
        a is VTerm.IntOf && b is VTerm.IntOf -> VTerm.BoolOf(a.value == b.value)
        // a == a = true
        a is VTerm.Var && b is VTerm.Var && a.level == b.level -> VTerm.BoolOf(true)
        else -> VTerm.Def(name, listOf(a, b).sortedWith(commuter).map { lazyOf(it) })
    }

    override fun pack(): List<Command> = listOf(
        Execute(StoreValue(RESULT, R0, REG, Run(GetData(MAIN, INT[-1])))),
        Pop(MAIN, INT),
        Execute(StoreValue(RESULT, R1, REG, Run(GetData(MAIN, INT[-1])))),
        Pop(MAIN, INT),
        Execute(E.CheckScore(false, R1, REG, EqScore(R0, REG), Run(Append(MAIN, BYTE, Value(Nbt.Byte(0)))))),
        Execute(E.CheckScore(true, R1, REG, EqScore(R0, REG), Run(Append(MAIN, BYTE, Value(Nbt.Byte(1)))))),
    )
}
