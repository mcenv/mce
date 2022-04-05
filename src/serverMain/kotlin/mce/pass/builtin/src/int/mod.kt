package mce.pass.builtin.src.int

import mce.ast.core.VTerm
import mce.ast.pack.Command
import mce.ast.pack.Command.GetData
import mce.ast.pack.Command.PerformOperation
import mce.ast.pack.Consumer.RESULT
import mce.ast.pack.Execute.*
import mce.ast.pack.Operation.MOD_ASSIGN
import mce.ast.pack.StoreType
import mce.pass.backend.*
import mce.pass.builtin.BuiltinDef2

@Suppress("ClassName")
object mod : BuiltinDef2("int/mod") {
    override fun eval(a: VTerm, b: VTerm): VTerm? = when {
        a is VTerm.IntOf && b is VTerm.IntOf -> VTerm.IntOf(Math.floorMod(a.value, b.value))
        // a % 1 = 0
        b is VTerm.IntOf && b.value == 1 -> a
        // a % a = 0
        a is VTerm.Var && b is VTerm.Var && a.level == b.level -> VTerm.IntOf(0)
        else -> null
    }

    override fun pack(): List<Command> = listOf(
        Command.Execute(StoreValue(RESULT, R0, REG, Run(GetData(MAIN, INT[-1])))),
        Pop(MAIN, INT),
        Command.Execute(StoreValue(RESULT, R1, REG, Run(GetData(MAIN, INT[-1])))),
        Command.Execute(StoreData(RESULT, MAIN, INT[-1], StoreType.INT, 1.0, Run(PerformOperation(R1, REG, MOD_ASSIGN, R0, REG))))
    )
}
