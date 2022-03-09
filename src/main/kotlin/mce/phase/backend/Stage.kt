package mce.phase.backend

import mce.graph.Id
import mce.phase.frontend.Normalizer
import mce.phase.frontend.Zonk
import mce.phase.map
import mce.graph.Core as C

/**
 * Performs staging.
 */
@Suppress("NAME_SHADOWING")
class Stage private constructor(
    private val normalizer: Normalizer
) {
    private fun stageItem(item: C.Item): C.Item = when (item) {
        is C.Item.Def -> {
            val parameters = item.parameters.map { stageParameter(it) }
            val body = stageTerm(item.body)
            C.Item.Def(item.imports, item.exports, item.modifiers, item.name, parameters, item.resultant, item.effects, body, item.id)
        }
        is C.Item.Mod -> {
            val body = stageModule(item.body)
            C.Item.Mod(item.imports, item.exports, item.modifiers, item.name, item.type, body, item.id)
        }
    }

    private fun stageParameter(parameter: C.Parameter): C.Parameter {
        val lower = parameter.lower?.let { stageTerm(it) }
        val upper = parameter.upper?.let { stageTerm(it) }
        val type = stageTerm(parameter.type)
        return C.Parameter(parameter.relevant, parameter.name, lower, upper, type, parameter.id)
    }

    private fun stageModule(module: C.Module): C.Module = when (module) {
        is C.Module.Var -> module
        is C.Module.Str -> {
            val items = module.items.map { stageItem(it) }
            C.Module.Str(items, module.id!!)
        }
        is C.Module.Sig -> {
            val signatures = module.signatures.map { stageSignature(it) }
            C.Module.Sig(signatures, module.id!!)
        }
        is C.Module.Type -> module
    }

    private fun stageSignature(signature: C.Signature): C.Signature = when (signature) {
        is C.Signature.Def -> {
            val parameters = signature.parameters.map { stageParameter(it) }
            val resultant = stageTerm(signature.resultant)
            C.Signature.Def(signature.name, parameters, resultant, signature.id)
        }
        is C.Signature.Mod -> {
            val type = stageModule(signature.type)
            C.Signature.Mod(signature.name, type, signature.id)
        }
    }

    private fun stageTerm(term: C.Term): C.Term = when (term) {
        is C.Term.Hole -> throw Error()
        is C.Term.Meta -> throw Error()
        is C.Term.CodeOf -> throw Error()
        is C.Term.Splice -> {
            val staged = normalizer.normTerm(term)
            stageTerm(staged)
        }
        is C.Term.Code -> throw Error()
        else -> term.map { stageTerm(it) }
    }

    data class Result(
        val item: C.Item,
        val types: Map<Id, C.VTerm>
    )

    companion object {
        operator fun invoke(input: Zonk.Result): Result = Stage(input.normalizer).run {
            Result(stageItem(input.item), input.types)
        }
    }
}
