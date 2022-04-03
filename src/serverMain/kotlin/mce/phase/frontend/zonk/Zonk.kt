package mce.phase.frontend.zonk

import mce.Id
import mce.phase.*
import mce.phase.frontend.Diagnostic
import mce.phase.frontend.elab.Elab
import mce.phase.frontend.elab.Item
import mce.phase.frontend.elab.Term
import mce.phase.frontend.elab.VTerm
import mce.util.run

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
            else -> quoteTerm(solution).run(normalizer)
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
            Result(transformItem(input.item), input.types, normalizer, input.diagnostics + diagnostics)
        }
    }
}
