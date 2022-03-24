package mce.phase.builtin.src.int

import mce.phase.backend.pack.*
import mce.phase.backend.pack.Command.GetData
import mce.phase.backend.pack.Command.PerformOperation
import mce.phase.backend.pack.Consumer.RESULT
import mce.phase.backend.pack.Execute.*
import mce.phase.backend.pack.Operation.MINUS_ASSIGN
import mce.phase.builtin.BuiltinDef2
import mce.phase.frontend.elab.VTerm

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
        Command.Execute(StoreValue(RESULT, REGISTER_0, REGISTERS, Run(GetData(STACKS, INT[-1])))),
        Pop(STACKS, INT),
        Command.Execute(StoreValue(RESULT, REGISTER_1, REGISTERS, Run(GetData(STACKS, INT[-1])))),
        Command.Execute(StoreData(RESULT, STACKS, INT[-1], StoreType.INT, 1.0, Run(PerformOperation(REGISTER_1, REGISTERS, MINUS_ASSIGN, REGISTER_0, REGISTERS))))
    )
}
