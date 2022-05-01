package mce.pass.frontend

import mce.Id
import mce.ast.core.Item
import mce.ast.core.Term
import mce.ast.core.VTerm
import mce.pass.*
import mce.util.Store

/**
 * Performs zonking.
 */
@Suppress("NAME_SHADOWING")
class Zonk private constructor(
    private val normalizer: Normalizer
) : Transform() {
    private val diagnostics: MutableList<Diagnostic> = mutableListOf()

    override fun transformTerm(term: Term): Term = when (term) {
        is Term.Meta -> when (val solution = normalizer.getSolution(term.index)) {
            null -> {
                diagnostics += Diagnostic.UnsolvedMeta(term.id!!)
                term
            }
            else -> Store(normalizer).quoteTerm(solution)
        }
        else -> transformTermInternal(term)
    }

    data class Result(
        val item: Item,
        val types: Map<Id, VTerm>,
        val normalizer: Normalizer,
        val diagnostics: List<Diagnostic>,
    )

    companion object : Pass<Elab.Result, Result> {
        override operator fun invoke(config: Config, input: Elab.Result): Result = Zonk(input.normalizer).run {
            Result(transformItem(input.item), input.types, input.normalizer, input.diagnostics + diagnostics)
        }
    }
}
