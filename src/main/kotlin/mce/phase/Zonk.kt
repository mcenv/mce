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
        is C.Item.Mod -> {
            val type = zonkModule(item.type)
            val body = zonkModule(item.body)
            C.Item.Mod(item.imports, item.exports, item.modifiers, item.name, type, body)
        }
    }

    private fun zonkModule(module: C.Module): C.Module = when (module) {
        is C.Module.Var -> module
        is C.Module.Str -> {
            val items = module.items.map { zonkItem(it) }
            C.Module.Str(items, module.id)
        }
        is C.Module.Sig -> {
            val signatures = module.signatures.map { zonkSignature(it) }
            C.Module.Sig(signatures, module.id)
        }
        is C.Module.Type -> module
    }

    private fun zonkSignature(signature: C.Signature): C.Signature = when (signature) {
        is C.Signature.Def -> {
            val type = zonkTerm(signature.type)
            C.Signature.Def(signature.name, type, signature.id)
        }
        is C.Signature.Mod -> {
            val type = zonkModule(signature.type)
            C.Signature.Mod(signature.name, type, signature.id)
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
