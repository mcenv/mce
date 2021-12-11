package mce

import mce.graph.Core.Term
import java.util.*

sealed class Diagnostic {
    abstract val id: UUID

    class VariableNotFound(val name: String, override val id: UUID) : Diagnostic()
    class InferenceFailed(override val id: UUID) : Diagnostic()
    class FunctionExpected(override val id: UUID) : Diagnostic()
    class TypeMismatch(val expected: Term, override val id: UUID) : Diagnostic()
}
