package mce.phase.frontend

import mce.ast.Id
import mce.ast.surface.Eff
import mce.ast.surface.Term

sealed class Diagnostic {
    abstract val id: Id

    data class TermExpected(val type: Term, override val id: Id) : Diagnostic()
    data class VarNotFound(val name: String, override val id: Id) : Diagnostic()
    data class DefNotFound(val name: String, override val id: Id) : Diagnostic()
    data class ModNotFound(val name: String, override val id: Id) : Diagnostic()
    data class SizeMismatch(val expected: Int, val actual: Int, override val id: Id) : Diagnostic()
    data class TermMismatch(val expected: Term, val actual: Term, override val id: Id) : Diagnostic()
    data class ModMismatch(override val id: Id) : Diagnostic()
    data class SigMismatch(override val id: Id) : Diagnostic()
    data class EffMismatch(val expected: List<Eff>, val actual: List<Eff>, override val id: Id) : Diagnostic()
    data class PhaseMismatch(override val id: Id) : Diagnostic()
    data class StageMismatch(val expected: Int, val actual: Int, override val id: Id) : Diagnostic()
    data class RelevanceMismatch(override val id: Id) : Diagnostic()
    data class PolyRepr(override val id: Id) : Diagnostic()
    data class UnsolvedMeta(override val id: Id) : Diagnostic()
}
