package mce

import mce.graph.Id
import mce.graph.Surface as S

sealed class Diagnostic {
    abstract val id: Id

    data class TermExpected(val type: S.Term, override val id: Id) : Diagnostic()
    data class VariableNotFound(val name: String, override val id: Id) : Diagnostic()
    data class DefinitionNotFound(val name: String, override val id: Id) : Diagnostic()
    data class FunctionExpected(override val id: Id) : Diagnostic()
    data class CodeExpected(override val id: Id) : Diagnostic()
    data class TypeMismatch(val expected: S.Term, val actual: S.Term, override val id: Id) : Diagnostic()
}
