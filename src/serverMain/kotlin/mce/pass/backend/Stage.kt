package mce.pass.backend

import mce.Id
import mce.ast.core.Item
import mce.ast.core.Term
import mce.ast.core.VTerm
import mce.pass.*
import mce.pass.frontend.Zonk
import mce.util.Store

/**
 * Performs staging.
 */
@Suppress("NAME_SHADOWING")
class Stage private constructor(
    private val normalizer: Normalizer,
) : Transform() {
    // TODO: do not normalize runtime code
    override fun transformTerm(term: Term): Term = when (term) {
        is Term.Hole -> throw Error()
        is Term.Meta -> throw Error()
        is Term.CodeOf -> throw Error()
        is Term.Splice -> {
            val staged = Store(normalizer).normTerm(term)
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
