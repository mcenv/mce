package mce.phase.frontend

import mce.ast.Id
import mce.ast.core.*
import mce.phase.Normalizer
import mce.phase.map
import mce.phase.quoteTerm
import mce.util.run

/**
 * Performs zonking.
 */
@Suppress("NAME_SHADOWING")
class Zonk private constructor(
    private val normalizer: Normalizer
) {
    private val diagnostics: MutableList<Diagnostic> = mutableListOf()

    private fun zonkItem(item: Item): Item = when (item) {
        is Item.Def -> {
            val parameters = item.parameters.map { zonkParameter(it) }
            val body = zonkTerm(item.body)
            Item.Def(item.imports, item.exports, item.modifiers, item.name, parameters, item.resultant, item.effects, body, item.id)
        }
        is Item.Mod -> {
            val body = zonkModule(item.body)
            Item.Mod(item.imports, item.exports, item.modifiers, item.name, item.type, body, item.id)
        }
        is Item.Test -> {
            val body = zonkTerm(item.body)
            Item.Test(item.imports, item.exports, item.modifiers, item.name, body, item.id)
        }
    }

    private fun zonkParameter(parameter: Parameter): Parameter {
        val lower = parameter.lower?.let { zonkTerm(it) }
        val upper = parameter.upper?.let { zonkTerm(it) }
        val type = zonkTerm(parameter.type)
        return Parameter(parameter.termRelevant, parameter.name, lower, upper, parameter.typeRelevant, type, parameter.id)
    }

    private fun zonkModule(module: Module): Module = when (module) {
        is Module.Var -> module
        is Module.Str -> {
            val items = module.items.map { zonkItem(it) }
            Module.Str(items, module.id)
        }
        is Module.Sig -> {
            val signatures = module.signatures.map { zonkSignature(it) }
            Module.Sig(signatures, module.id)
        }
        is Module.Type -> module
    }

    private fun zonkSignature(signature: Signature): Signature = when (signature) {
        is Signature.Def -> {
            val parameters = signature.parameters.map { zonkParameter(it) }
            val resultant = zonkTerm(signature.resultant)
            Signature.Def(signature.name, parameters, resultant, signature.id)
        }
        is Signature.Mod -> {
            val type = zonkModule(signature.type)
            Signature.Mod(signature.name, type, signature.id)
        }
        is Signature.Test -> Signature.Test(signature.name, signature.id)
    }

    private fun zonkTerm(term: Term): Term = when (term) {
        is Term.Meta -> when (val solution = normalizer.getSolution(term.index)) {
            null -> {
                diagnostics += Diagnostic.UnsolvedMeta(term.id!!)
                term
            }
            else -> quoteTerm(solution).run(normalizer)
        }
        else -> term.map { zonkTerm(it) }
    }

    data class Result(
        val item: Item,
        val types: Map<Id, VTerm>,
        val normalizer: Normalizer,
        val diagnostics: List<Diagnostic>
    )

    companion object {
        operator fun invoke(input: Elaborate.Result): Result = Zonk(input.normalizer).run {
            Result(zonkItem(input.item), input.types, normalizer, input.diagnostics + diagnostics)
        }
    }
}
