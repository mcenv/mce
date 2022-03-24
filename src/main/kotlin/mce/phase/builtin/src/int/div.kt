package mce.phase.builtin.src.int

import mce.phase.backend.pack.*
import mce.phase.backend.pack.Command.GetData
import mce.phase.backend.pack.Command.PerformOperation
import mce.phase.backend.pack.Consumer.RESULT
import mce.phase.backend.pack.Execute.*
import mce.phase.backend.pack.Operation.DIV_ASSIGN
import mce.phase.builtin.BuiltinDef2
import mce.phase.frontend.elab.VTerm

@Suppress("ClassName")
object div : BuiltinDef2("int/div") {
    override fun eval(a: VTerm, b: VTerm): VTerm? = when {
        a is VTerm.IntOf && b is VTerm.IntOf -> VTerm.IntOf(Math.floorDiv(a.value, b.value))
        // a / 1 = a
        b is VTerm.IntOf && b.value == 1 -> a
        // a / a = 1
        a is VTerm.Var && b is VTerm.Var && a.level == b.level -> VTerm.IntOf(1)
        else -> null
    }

    override fun pack(): List<Command> = listOf(
        Command.Execute(StoreValue(RESULT, REGISTER_0, REGISTERS, Run(GetData(STACKS, INT[-1])))),
        Pop(STACKS, INT),
        Command.Execute(StoreValue(RESULT, REGISTER_1, REGISTERS, Run(GetData(STACKS, INT[-1])))),
        Command.Execute(StoreData(RESULT, STACKS, INT[-1], StoreType.INT, 1.0, Run(PerformOperation(REGISTER_1, REGISTERS, DIV_ASSIGN, REGISTER_0, REGISTERS))))
    )
}
