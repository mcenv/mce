package mce

import java.util.*
import mce.graph.Surface as S

sealed class Diagnostic {
    abstract val id: UUID

    class TermExpected(val type: S.Term, override val id: UUID) : Diagnostic()
    class VariableNotFound(val name: String, override val id: UUID) : Diagnostic()
    class FunctionExpected(override val id: UUID) : Diagnostic()
    class TypeMismatch(val expected: S.Term, val actual: S.Term, override val id: UUID) : Diagnostic()
}
