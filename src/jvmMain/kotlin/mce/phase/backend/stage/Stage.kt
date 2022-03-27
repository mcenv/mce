package mce.phase.backend.stage

import mce.phase.Id
import mce.phase.Normalizer
import mce.phase.frontend.elab.Item
import mce.phase.frontend.elab.Term
import mce.phase.frontend.elab.VTerm
import mce.phase.frontend.zonk.Zonk
import mce.phase.normTerm
import mce.util.run

/**
 * Performs staging.
 */
@Suppress("NAME_SHADOWING")
class Stage private constructor(
    private val normalizer: Normalizer,
) : mce.phase.Map() {
    // TODO: use specialized normalizer for staging.
    override fun mapTerm(term: Term): Term = when (term) {
        is Term.Hole -> throw Error()
        is Term.Meta -> throw Error()
        is Term.CodeOf -> throw Error()
        is Term.Splice -> {
            val staged = normTerm(term).run(normalizer)
            mapTermInternal(staged)
        }
        is Term.Code -> throw Error()
        else -> mapTermInternal(term)
    }

    data class Result(
        val item: Item,
        val types: Map<Id, VTerm>,
    )

    companion object {
        operator fun invoke(input: Zonk.Result): Result = Stage(input.normalizer).run {
            Result(mapItem(input.item), input.types)
        }
    }
}
