package mce.phase.frontend

import mce.ast.Id
import mce.ast.core.Item
import mce.ast.core.Term
import mce.ast.core.VTerm
import mce.phase.Normalizer
import mce.phase.quoteTerm
import mce.util.run

/**
 * Performs zonking.
 */
@Suppress("NAME_SHADOWING")
class Zonk private constructor(
    private val normalizer: Normalizer
) : mce.phase.Map() {
    private val diagnostics: MutableList<Diagnostic> = mutableListOf()

    override fun mapTerm(term: Term): Term = when (term) {
        is Term.Meta -> when (val solution = normalizer.getSolution(term.index)) {
            null -> {
                diagnostics += Diagnostic.UnsolvedMeta(term.id!!)
                term
            }
            else -> quoteTerm(solution).run(normalizer)
        }
        else -> mapTermInternal(term)
    }

    data class Result(
        val item: Item,
        val types: Map<Id, VTerm>,
        val normalizer: Normalizer,
        val diagnostics: List<Diagnostic>
    )

    companion object {
        operator fun invoke(input: Elaborate.Result): Result = Zonk(input.normalizer).run {
            Result(mapItem(input.item), input.types, normalizer, input.diagnostics + diagnostics)
        }
    }
}
