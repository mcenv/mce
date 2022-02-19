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
            C.Item.Def(item.imports, item.exports, item.modifiers, item.name, item.type, body)
        }
    }

    private fun stageTerm(term: C.Term): C.Term = when (term) {
        is C.Term.Hole -> throw Error()
        is C.Term.Meta -> throw Error()
        is C.Term.Var -> term
        is C.Term.Def -> term
        is C.Term.Let -> {
            val init = stageTerm(term.init)
            val body = stageTerm(term.body)
            C.Term.Let(term.name, init, body, term.id)
        }
        is C.Term.Match -> {
            val scrutinee = stageTerm(term.scrutinee)
            val clauses = term.clauses.map { it.first to stageTerm(it.second) }
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
            val elements = term.elements.map { stageTerm(it) }
            C.Term.ByteArrayOf(elements, term.id)
        }
        is C.Term.IntArrayOf -> {
            val elements = term.elements.map { stageTerm(it) }
            C.Term.IntArrayOf(elements, term.id)
        }
        is C.Term.LongArrayOf -> {
            val elements = term.elements.map { stageTerm(it) }
            C.Term.LongArrayOf(elements, term.id)
        }
        is C.Term.ListOf -> {
            val elements = term.elements.map { stageTerm(it) }
            C.Term.ListOf(elements, term.id)
        }
        is C.Term.CompoundOf -> {
            val elements = term.elements.map { stageTerm(it) }
            C.Term.CompoundOf(elements, term.id)
        }
        is C.Term.BoxOf -> {
            val content = stageTerm(term.content)
            C.Term.BoxOf(content, term.id)
        }
        is C.Term.RefOf -> {
            val element = stageTerm(term.element)
            C.Term.RefOf(element, term.id)
        }
        is C.Term.Refl -> term
        is C.Term.FunOf -> {
            val body = stageTerm(term.body)
            C.Term.FunOf(term.parameters, body, term.id)
        }
        is C.Term.Apply -> {
            val function = stageTerm(term.function)
            val arguments = term.arguments.map { stageTerm(it) }
            C.Term.Apply(function, arguments, term.id)
        }
        is C.Term.CodeOf -> throw Error()
        is C.Term.Splice -> {
            val staged = normalizer.norm(term)
            stageTerm(staged)
        }
        is C.Term.Union -> {
            val variants = term.variants.map { stageTerm(it) }
            C.Term.Union(variants, term.id)
        }
        is C.Term.Intersection -> {
            val variants = term.variants.map { stageTerm(it) }
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
            val element = stageTerm(term.element)
            C.Term.List(element, term.id)
        }
        is C.Term.Compound -> {
            val elements = term.elements.map { it.first to stageTerm(it.second) }
            C.Term.Compound(elements, term.id)
        }
        is C.Term.Box -> {
            val content = stageTerm(term.content)
            C.Term.Box(content, term.id)
        }
        is C.Term.Ref -> {
            val element = stageTerm(term.element)
            C.Term.Ref(element, term.id)
        }
        is C.Term.Eq -> {
            val left = stageTerm(term.left)
            val right = stageTerm(term.right)
            C.Term.Eq(left, right, term.id)
        }
        is C.Term.Fun -> {
            val parameters = term.parameters.map { (name, lower, upper, type) ->
                val lower = lower?.let { stageTerm(it) }
                val upper = upper?.let { stageTerm(it) }
                val type = stageTerm(type)
                C.Parameter(name, lower, upper, type)
            }
            val resultant = stageTerm(term.resultant)
            C.Term.Fun(parameters, resultant, term.effects, term.id)
        }
        is C.Term.Code -> throw Error()
        is C.Term.Type -> term
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
