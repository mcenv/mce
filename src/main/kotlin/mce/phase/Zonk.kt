package mce.phase

import mce.Diagnostic
import mce.graph.Id
import mce.graph.Core as C

/**
 * Performs zonking.
 */
@Suppress("NAME_SHADOWING")
class Zonk private constructor(
    private val normalizer: Normalizer
) {
    private val diagnostics: MutableList<Diagnostic> = mutableListOf()

    private fun zonkItem(item: C.Item): C.Item = when (item) {
        is C.Item.Def -> {
            val body = zonkTerm(item.body)
            C.Item.Def(item.imports, item.exports, item.modifiers, item.name, item.parameters, item.resultant, item.effects, body)
        }
    }

    private fun zonkTerm(term: C.Term): C.Term = when (term) {
        is C.Term.Meta -> when (val solution = normalizer.getSolution(term.index)) {
            null -> {
                diagnostics += Diagnostic.UnsolvedMeta(term.id!!)
                term
            }
            else -> normalizer.quote(solution)
        }
        else -> term.map { zonkTerm(it) }
    }

    data class Result(
        val item: C.Item,
        val types: Map<Id, C.Value>,
        val normalizer: Normalizer,
        val diagnostics: List<Diagnostic>
    )

    companion object {
        operator fun invoke(input: Elaborate.Result): Result = Zonk(input.normalizer).run {
            Result(zonkItem(input.item), input.types, normalizer, input.diagnostics + diagnostics)
        }
    }
}
