package mce.phase

import mce.graph.Id
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
            val body = stageTerm(item.body)
            C.Item.Def(item.imports, item.exports, item.modifiers, item.name, item.parameters, item.resultant, item.effects, body)
        }
        is C.Item.Mod -> {
            val type = stageModule(item.type)
            val body = stageModule(item.body)
            C.Item.Mod(item.imports, item.exports, item.modifiers, item.name, type, body)
        }
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
            val type = stageTerm(signature.type)
            C.Signature.Def(signature.name, type, signature.id)
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
            val staged = normalizer.norm(term)
            stageTerm(staged)
        }
        is C.Term.Code -> throw Error()
        else -> term.map { stageTerm(it) }
    }

    data class Result(
        val item: C.Item,
        val types: Map<Id, C.Value>
    )

    companion object {
        operator fun invoke(input: Zonk.Result): Result = Stage(input.normalizer).run {
            Result(stageItem(input.item), input.types)
        }
    }
}
