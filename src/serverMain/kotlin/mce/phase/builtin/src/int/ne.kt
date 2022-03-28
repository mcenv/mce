package mce.phase.builtin.src.int

import mce.phase.backend.pack.*
import mce.phase.backend.pack.Command.*
import mce.phase.backend.pack.Consumer.RESULT
import mce.phase.backend.pack.Execute.Run
import mce.phase.backend.pack.Execute.StoreValue
import mce.phase.backend.pack.SourceComparator.EqScore
import mce.phase.backend.pack.SourceProvider.Value
import mce.phase.builtin.BuiltinDef2
import mce.phase.builtin.commuter
import mce.phase.frontend.elab.VTerm
import mce.phase.backend.pack.Execute as E

@Suppress("ClassName")
object ne : BuiltinDef2("int/ne") {
    override fun eval(a: VTerm, b: VTerm): VTerm = when {
        a is VTerm.IntOf && b is VTerm.IntOf -> VTerm.BoolOf(a.value != b.value)
        else -> VTerm.Def(name, listOf(a, b).sortedWith(commuter).map { lazyOf(it) })
    }

    override fun pack(): List<Command> = listOf(
        Execute(StoreValue(RESULT, R0, REG, Run(GetData(MAIN, INT[-1])))),
        Pop(MAIN, INT),
        Execute(StoreValue(RESULT, R1, REG, Run(GetData(MAIN, INT[-1])))),
        Pop(MAIN, INT),
        Execute(E.CheckScore(false, R1, REG, EqScore(R0, REG), Run(Append(MAIN, BYTE, Value(Nbt.Byte(1)))))),
        Execute(E.CheckScore(true, R1, REG, EqScore(R0, REG), Run(Append(MAIN, BYTE, Value(Nbt.Byte(0)))))),
    )
}