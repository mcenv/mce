package mce.phase.frontend

import mce.ast.Id
import mce.phase.Normalizer
import mce.phase.map
import mce.phase.quoteTerm
import mce.util.run
import mce.ast.Core as C

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
            val parameters = item.parameters.map { zonkParameter(it) }
            val body = zonkTerm(item.body)
            C.Item.Def(item.imports, item.exports, item.modifiers, item.name, parameters, item.resultant, item.effects, body, item.id)
        }
        is C.Item.Mod -> {
            val body = zonkModule(item.body)
            C.Item.Mod(item.imports, item.exports, item.modifiers, item.name, item.type, body, item.id)
        }
        is C.Item.Test -> {
            val body = zonkTerm(item.body)
            C.Item.Test(item.imports, item.exports, item.modifiers, item.name, body, item.id)
        }
    }

    private fun zonkParameter(parameter: C.Parameter): C.Parameter {
        val lower = parameter.lower?.let { zonkTerm(it) }
        val upper = parameter.upper?.let { zonkTerm(it) }
        val type = zonkTerm(parameter.type)
        return C.Parameter(parameter.termRelevant, parameter.name, lower, upper, parameter.typeRelevant, type, parameter.id)
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
            val parameters = signature.parameters.map { zonkParameter(it) }
            val resultant = zonkTerm(signature.resultant)
            C.Signature.Def(signature.name, parameters, resultant, signature.id)
        }
        is C.Signature.Mod -> {
            val type = zonkModule(signature.type)
            C.Signature.Mod(signature.name, type, signature.id)
        }
        is C.Signature.Test -> C.Signature.Test(signature.name, signature.id)
    }

    private fun zonkTerm(term: C.Term): C.Term = when (term) {
        is C.Term.Meta -> when (val solution = normalizer.getSolution(term.index)) {
            null -> {
                diagnostics += Diagnostic.UnsolvedMeta(term.id!!)
                term
            }
            else -> quoteTerm(solution).run(normalizer)
        }
        else -> term.map { zonkTerm(it) }
    }

    data class Result(
        val item: C.Item,
        val types: Map<Id, C.VTerm>,
        val normalizer: Normalizer,
        val diagnostics: List<Diagnostic>
    )

    companion object {
        operator fun invoke(input: Elaborate.Result): Result = Zonk(input.normalizer).run {
            Result(zonkItem(input.item), input.types, normalizer, input.diagnostics + diagnostics)
        }
    }
}
