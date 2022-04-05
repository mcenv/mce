package mce.pass.builtin.src.int

import mce.ast.core.VTerm
import mce.ast.pack.Command
import mce.ast.pack.Command.GetData
import mce.ast.pack.Command.PerformOperation
import mce.ast.pack.Consumer.RESULT
import mce.ast.pack.Execute.*
import mce.ast.pack.Operation.PLUS_ASSIGN
import mce.ast.pack.StoreType
import mce.pass.backend.*
import mce.pass.builtin.BuiltinDef2
import mce.pass.builtin.commuter

@Suppress("ClassName")
object add : BuiltinDef2("int/add") {
    override fun eval(a: VTerm, b: VTerm): VTerm = when {
        a is VTerm.IntOf && b is VTerm.IntOf -> VTerm.IntOf(a.value + b.value)
        // 0 + b = b
        a is VTerm.IntOf && a.value == 0 -> b
        // a + 0 = a
        b is VTerm.IntOf && b.value == 0 -> a
        else -> VTerm.Def(name, listOf(a, b).sortedWith(commuter).map { lazyOf(it) })
    }

    override fun pack(): List<Command> = listOf(
        Command.Execute(StoreValue(RESULT, R0, REG, Run(GetData(MAIN, INT[-1])))),
        Pop(MAIN, INT),
        Command.Execute(StoreValue(RESULT, R1, REG, Run(GetData(MAIN, INT[-1])))),
        Command.Execute(StoreData(RESULT, MAIN, INT[-1], StoreType.INT, 1.0, Run(PerformOperation(R1, REG, PLUS_ASSIGN, R0, REG))))
    )
}
