package mce.pass.builtin.src.int

import mce.pass.backend.pack.*
import mce.pass.backend.pack.Command.GetData
import mce.pass.backend.pack.Command.PerformOperation
import mce.pass.backend.pack.Consumer.RESULT
import mce.pass.backend.pack.Execute.*
import mce.pass.backend.pack.Operation.MINUS_ASSIGN
import mce.pass.builtin.BuiltinDef2
import mce.pass.frontend.elab.VTerm

@Suppress("ClassName")
object sub : BuiltinDef2("int/sub") {
    override fun eval(a: VTerm, b: VTerm): VTerm? = when {
        a is VTerm.IntOf && b is VTerm.IntOf -> VTerm.IntOf(a.value - b.value)
        // a - 0 = a
        b is VTerm.IntOf && b.value == 0 -> a
        // a - a = 0
        a is VTerm.Var && b is VTerm.Var && a.level == b.level -> VTerm.IntOf(0)
        else -> null
    }

    override fun pack(): List<Command> = listOf(
        Command.Execute(StoreValue(RESULT, R0, REG, Run(GetData(MAIN, INT[-1])))),
        Pop(MAIN, INT),
        Command.Execute(StoreValue(RESULT, R1, REG, Run(GetData(MAIN, INT[-1])))),
        Command.Execute(StoreData(RESULT, MAIN, INT[-1], StoreType.INT, 1.0, Run(PerformOperation(R1, REG, MINUS_ASSIGN, R0, REG))))
    )
}