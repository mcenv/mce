package mce

import mce.graph.Id
import mce.graph.Surface.Term

sealed class Diagnostic {
    abstract val id: Id

    data class TermExpected(val type: Term, override val id: Id) : Diagnostic()
    data class VariableNotFound(val name: String, override val id: Id) : Diagnostic()
    data class DefinitionNotFound(val name: String, override val id: Id) : Diagnostic()
    data class FunctionExpected(override val id: Id) : Diagnostic()
    data class CodeExpected(override val id: Id) : Diagnostic()
    data class TypeMismatch(val expected: Term, val actual: Term, override val id: Id) : Diagnostic()
    data class PhaseMismatch(override val id: Id) : Diagnostic()
    data class StageMismatch(val expected: Int, val actual: Int, override val id: Id) : Diagnostic()
}
