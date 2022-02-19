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
            C.Item.Def(item.imports, item.exports, item.modifiers, item.name, item.type, body)
        }
    }

    private fun zonkTerm(term: C.Term): C.Term = when (term) {
        is C.Term.Hole -> term
        is C.Term.Meta -> when (val solution = normalizer.getSolution(term.index)) {
            null -> {
                diagnostics += Diagnostic.UnsolvedMeta(term.id!!)
                term
            }
            else -> normalizer.quote(solution)
        }
        is C.Term.Var -> term
        is C.Term.Def -> term
        is C.Term.Let -> {
            val init = zonkTerm(term.init)
            val body = zonkTerm(term.body)
            C.Term.Let(term.name, init, body, term.id)
        }
        is C.Term.Match -> {
            val scrutinee = zonkTerm(term.scrutinee)
            val clauses = term.clauses.map { it.first to zonkTerm(it.second) }
            C.Term.Match(scrutinee, clauses, term.id)
        }
        is C.Term.BoolOf -> term
        is C.Term.ByteOf -> term
        is C.Term.ShortOf -> term
        is C.Term.IntOf -> term
        is C.Term.LongOf -> term
        is C.Term.FloatOf -> term
        is C.Term.DoubleOf -> term
        is C.Term.StringOf -> term
        is C.Term.ByteArrayOf -> {
            val elements = term.elements.map { zonkTerm(it) }
            C.Term.ByteArrayOf(elements, term.id)
        }
        is C.Term.IntArrayOf -> {
            val elements = term.elements.map { zonkTerm(it) }
            C.Term.IntArrayOf(elements, term.id)
        }
        is C.Term.LongArrayOf -> {
            val elements = term.elements.map { zonkTerm(it) }
            C.Term.LongArrayOf(elements, term.id)
        }
        is C.Term.ListOf -> {
            val elements = term.elements.map { zonkTerm(it) }
            C.Term.ListOf(elements, term.id)
        }
        is C.Term.CompoundOf -> {
            val elements = term.elements.map { zonkTerm(it) }
            C.Term.CompoundOf(elements, term.id)
        }
        is C.Term.BoxOf -> {
            val content = zonkTerm(term.content)
            C.Term.BoxOf(content, term.id)
        }
        is C.Term.RefOf -> {
            val element = zonkTerm(term.element)
            C.Term.RefOf(element, term.id)
        }
        is C.Term.Refl -> term
        is C.Term.FunOf -> {
            val body = zonkTerm(term.body)
            C.Term.FunOf(term.parameters, body, term.id)
        }
        is C.Term.Apply -> {
            val function = zonkTerm(term.function)
            val arguments = term.arguments.map { zonkTerm(it) }
            C.Term.Apply(function, arguments, term.id)
        }
        is C.Term.CodeOf -> {
            val element = zonkTerm(term.element)
            C.Term.CodeOf(element, term.id)
        }
        is C.Term.Splice -> {
            val element = zonkTerm(term.element)
            C.Term.Splice(element, term.id)
        }
        is C.Term.Union -> {
            val variants = term.variants.map { zonkTerm(it) }
            C.Term.Union(variants, term.id)
        }
        is C.Term.Intersection -> {
            val variants = term.variants.map { zonkTerm(it) }
            C.Term.Intersection(variants, term.id)
        }
        is C.Term.Bool -> term
        is C.Term.Byte -> term
        is C.Term.Short -> term
        is C.Term.Int -> term
        is C.Term.Long -> term
        is C.Term.Float -> term
        is C.Term.Double -> term
        is C.Term.String -> term
        is C.Term.ByteArray -> term
        is C.Term.IntArray -> term
        is C.Term.LongArray -> term
        is C.Term.List -> {
            val element = zonkTerm(term.element)
            C.Term.List(element, term.id)
        }
        is C.Term.Compound -> {
            val elements = term.elements.map { it.first to zonkTerm(it.second) }
            C.Term.Compound(elements, term.id)
        }
        is C.Term.Box -> {
            val content = zonkTerm(term.content)
            C.Term.Box(content, term.id)
        }
        is C.Term.Ref -> {
            val element = zonkTerm(term.element)
            C.Term.Ref(element, term.id)
        }
        is C.Term.Eq -> {
            val left = zonkTerm(term.left)
            val right = zonkTerm(term.right)
            C.Term.Eq(left, right, term.id)
        }
        is C.Term.Fun -> {
            val parameters = term.parameters.map { (name, lower, upper, type) ->
                val lower = lower?.let { zonkTerm(it) }
                val upper = upper?.let { zonkTerm(it) }
                val type = zonkTerm(type)
                C.Parameter(name, lower, upper, type)
            }
            val resultant = zonkTerm(term.resultant)
            C.Term.Fun(parameters, resultant, term.effects, term.id)
        }
        is C.Term.Code -> {
            val element = zonkTerm(term.element)
            C.Term.Code(element, term.id)
        }
        is C.Term.Type -> term
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
