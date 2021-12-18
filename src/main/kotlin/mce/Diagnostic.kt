package mce

import java.util.*
import mce.graph.Surface as S

sealed class Diagnostic {
    abstract val id: UUID

    data class TermExpected(val type: S.Term, override val id: UUID) : Diagnostic()
    data class NameNotFound(val name: String, override val id: UUID) : Diagnostic()
    data class FunctionExpected(override val id: UUID) : Diagnostic()
    data class TypeMismatch(val expected: S.Term, val actual: S.Term, override val id: UUID) : Diagnostic()
}
