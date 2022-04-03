package mce.phase.backend.stage

import mce.Id
import mce.phase.*
import mce.phase.frontend.elab.Item
import mce.phase.frontend.elab.Term
import mce.phase.frontend.elab.VTerm
import mce.phase.frontend.zonk.Zonk
import mce.util.run

/**
 * Performs staging.
 */
@Suppress("NAME_SHADOWING")
class Stage private constructor(
    private val normalizer: Normalizer,
) : Transform() {
    // TODO: use specialized normalizer for staging.
    override fun transformTerm(term: Term): Term = when (term) {
        is Term.Hole -> throw Error()
        is Term.Meta -> throw Error()
        is Term.CodeOf -> throw Error()
        is Term.Splice -> {
            val staged = normTerm(term).run(normalizer)
            transformTermInternal(staged)
        }
        is Term.Code -> throw Error()
        else -> transformTermInternal(term)
    }

    data class Result(
        val item: Item,
        val types: Map<Id, VTerm>,
    )

    companion object : Pass<Zonk.Result, Result> {
        override operator fun invoke(config: Config, input: Zonk.Result): Result = Stage(input.normalizer).run {
            Result(transformItem(input.item), input.types)
        }
    }
}
