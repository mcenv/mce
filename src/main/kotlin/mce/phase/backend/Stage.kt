package mce.phase.backend

import mce.ast.Id
import mce.ast.core.*
import mce.phase.Normalizer
import mce.phase.frontend.Zonk
import mce.phase.map
import mce.phase.normTerm
import mce.util.run

/**
 * Performs staging.
 */
@Suppress("NAME_SHADOWING")
class Stage private constructor(
    private val normalizer: Normalizer,
) {
    private fun stageItem(item: Item): Item = when (item) {
        is Item.Def -> {
            val parameters = item.parameters.map { stageParameter(it) }
            val body = stageTerm(item.body)
            Item.Def(item.imports, item.exports, item.modifiers, item.name, parameters, item.resultant, item.effects, body, item.id)
        }
        is Item.Mod -> {
            val body = stageModule(item.body)
            Item.Mod(item.imports, item.exports, item.modifiers, item.name, item.type, body, item.id)
        }
        is Item.Test -> {
            val body = stageTerm(item.body)
            Item.Test(item.imports, item.exports, item.modifiers, item.name, body, item.id)
        }
    }

    private fun stageParameter(parameter: Parameter): Parameter {
        val lower = parameter.lower?.let { stageTerm(it) }
        val upper = parameter.upper?.let { stageTerm(it) }
        val type = stageTerm(parameter.type)
        return Parameter(parameter.termRelevant, parameter.name, lower, upper, parameter.typeRelevant, type, parameter.id)
    }

    private fun stageModule(module: Module): Module = when (module) {
        is Module.Var -> module
        is Module.Str -> {
            val items = module.items.map { stageItem(it) }
            Module.Str(items, module.id!!)
        }
        is Module.Sig -> {
            val signatures = module.signatures.map { stageSignature(it) }
            Module.Sig(signatures, module.id!!)
        }
        is Module.Type -> module
    }

    private fun stageSignature(signature: Signature): Signature = when (signature) {
        is Signature.Def -> {
            val parameters = signature.parameters.map { stageParameter(it) }
            val resultant = stageTerm(signature.resultant)
            Signature.Def(signature.name, parameters, resultant, signature.id)
        }
        is Signature.Mod -> {
            val type = stageModule(signature.type)
            Signature.Mod(signature.name, type, signature.id)
        }
        is Signature.Test -> Signature.Test(signature.name, signature.id)
    }

    private fun stageTerm(term: Term): Term = when (term) {
        is Term.Hole -> throw Error()
        is Term.Meta -> throw Error()
        is Term.CodeOf -> throw Error()
        is Term.Splice -> {
            val staged = normTerm(term).run(normalizer)
            stageTerm(staged)
        }
        is Term.Code -> throw Error()
        else -> term.map { stageTerm(it) }
    }

    data class Result(
        val item: Item,
        val types: Map<Id, VTerm>
    )

    companion object {
        operator fun invoke(input: Zonk.Result): Result = Stage(input.normalizer).run {
            Result(stageItem(input.item), input.types)
        }
    }
}
