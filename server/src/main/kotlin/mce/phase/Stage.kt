package mce.phase

import mce.graph.Core as C
import mce.graph.Staged as S

/**
 * Performs staging and zonking.
 */
class Stage private constructor(
    private val metaState: MetaState,
    private val items: Map<String, S.Item>
) {
    private fun stageItem(item: C.Item): S.Item = when (item) {
        is C.Item.Definition -> {
            val body = stageTerm(item.body)
            S.Item.Definition(item.imports, item.name, body)
        }
    }

    private fun stageTerm(term: C.Term): S.Term = when (term) {
        is C.Term.Hole -> TODO()
        is C.Term.Meta -> stageTerm(emptyEnvironment().normalize(metaState, term))
        is C.Term.Variable -> S.Term.Variable(term.name, term.level)
        is C.Term.Definition -> S.Term.Definition(term.name)
        is C.Term.Let -> {
            val init = stageTerm(term.init)
            val body = stageTerm(term.body)
            S.Term.Let(term.name, init, body)
        }
        is C.Term.Match -> {
            val scrutinee = stageTerm(term.scrutinee)
            val clauses = term.clauses.map { stagePattern(it.first) to stageTerm(it.second) }
            S.Term.Match(scrutinee, clauses)
        }
        is C.Term.BooleanOf -> S.Term.BooleanOf(term.value)
        is C.Term.ByteOf -> S.Term.ByteOf(term.value)
        is C.Term.ShortOf -> S.Term.ShortOf(term.value)
        is C.Term.IntOf -> S.Term.IntOf(term.value)
        is C.Term.LongOf -> S.Term.LongOf(term.value)
        is C.Term.FloatOf -> S.Term.FloatOf(term.value)
        is C.Term.DoubleOf -> S.Term.DoubleOf(term.value)
        is C.Term.StringOf -> S.Term.StringOf(term.value)
        is C.Term.ByteArrayOf -> {
            val elements = term.elements.map { stageTerm(it) }
            S.Term.ByteArrayOf(elements)
        }
        is C.Term.IntArrayOf -> {
            val elements = term.elements.map { stageTerm(it) }
            S.Term.IntArrayOf(elements)
        }
        is C.Term.LongArrayOf -> {
            val elements = term.elements.map { stageTerm(it) }
            S.Term.LongArrayOf(elements)
        }
        is C.Term.ListOf -> {
            val elements = term.elements.map { stageTerm(it) }
            S.Term.ListOf(elements)
        }
        is C.Term.CompoundOf -> {
            val elements = term.elements.map { stageTerm(it) }
            S.Term.CompoundOf(elements)
        }
        is C.Term.ReferenceOf -> {
            val element = stageTerm(term.element)
            S.Term.ReferenceOf(element)
        }
        is C.Term.FunctionOf -> {
            val body = stageTerm(term.body)
            S.Term.FunctionOf(term.parameters, body)
        }
        is C.Term.Apply -> {
            val function = stageTerm(term.function)
            val arguments = term.arguments.map { stageTerm(it) }
            S.Term.Apply(function, arguments)
        }
        is C.Term.ThunkOf -> {
            val body = stageTerm(term.body)
            S.Term.ThunkOf(body)
        }
        is C.Term.Force -> {
            val element = stageTerm(term.element)
            S.Term.Force(element)
        }
        is C.Term.CodeOf -> TODO()
        is C.Term.Splice -> {
            val staged = emptyEnvironment().normalize(metaState, term)
            stageTerm(staged)
        }
        is C.Term.Union -> {
            val variants = term.variants.map { stageTerm(it) }
            S.Term.Union(variants)
        }
        is C.Term.Intersection -> {
            val variants = term.variants.map { stageTerm(it) }
            S.Term.Intersection(variants)
        }
        is C.Term.Boolean -> S.Term.Boolean
        is C.Term.Byte -> S.Term.Byte
        is C.Term.Short -> S.Term.Short
        is C.Term.Int -> S.Term.Int
        is C.Term.Long -> S.Term.Long
        is C.Term.Float -> S.Term.Float
        is C.Term.Double -> S.Term.Double
        is C.Term.String -> S.Term.String
        is C.Term.ByteArray -> S.Term.ByteArray
        is C.Term.IntArray -> S.Term.IntArray
        is C.Term.LongArray -> S.Term.LongArray
        is C.Term.List -> {
            val element = stageTerm(term.element)
            S.Term.List(element)
        }
        is C.Term.Compound -> {
            val elements = term.elements.map { it.first to stageTerm(it.second) }
            S.Term.Compound(elements)
        }
        is C.Term.Reference -> {
            val element = stageTerm(term.element)
            S.Term.Reference(element)
        }
        is C.Term.Function -> {
            val parameters = term.parameters.map { stageTerm(it.type) }
            val resultant = stageTerm(term.resultant)
            S.Term.Function(parameters, resultant)
        }
        is C.Term.Thunk -> {
            val element = stageTerm(term.element)
            val effects = stageEffects(term.effects)
            S.Term.Thunk(element, effects)
        }
        is C.Term.Code -> TODO()
        is C.Term.Type -> S.Term.Type
    }

    private fun stagePattern(pattern: C.Pattern): S.Pattern = when (pattern) {
        is C.Pattern.Variable -> S.Pattern.Variable(pattern.name)
        is C.Pattern.BooleanOf -> S.Pattern.BooleanOf(pattern.value)
        is C.Pattern.ByteOf -> S.Pattern.ByteOf(pattern.value)
        is C.Pattern.ShortOf -> S.Pattern.ShortOf(pattern.value)
        is C.Pattern.IntOf -> S.Pattern.IntOf(pattern.value)
        is C.Pattern.LongOf -> S.Pattern.LongOf(pattern.value)
        is C.Pattern.FloatOf -> S.Pattern.FloatOf(pattern.value)
        is C.Pattern.DoubleOf -> S.Pattern.DoubleOf(pattern.value)
        is C.Pattern.StringOf -> S.Pattern.StringOf(pattern.value)
        is C.Pattern.ByteArrayOf -> {
            val elements = pattern.elements.map { stagePattern(it) }
            S.Pattern.ByteArrayOf(elements)
        }
        is C.Pattern.IntArrayOf -> {
            val elements = pattern.elements.map { stagePattern(it) }
            S.Pattern.IntArrayOf(elements)
        }
        is C.Pattern.LongArrayOf -> {
            val elements = pattern.elements.map { stagePattern(it) }
            S.Pattern.LongArrayOf(elements)
        }
        is C.Pattern.ListOf -> {
            val elements = pattern.elements.map { stagePattern(it) }
            S.Pattern.ListOf(elements)
        }
        is C.Pattern.CompoundOf -> {
            val elements = pattern.elements.map { stagePattern(it) }
            S.Pattern.CompoundOf(elements)
        }
        is C.Pattern.ReferenceOf -> {
            val element = stagePattern(pattern.element)
            S.Pattern.ReferenceOf(element)
        }
    }

    private fun stageEffect(effect: C.Effect): S.Effect = TODO()

    private fun stageEffects(effects: C.Effects): S.Effects = when (effects) {
        is C.Effects.Any -> S.Effects.Any
        is C.Effects.Set -> S.Effects.Set(effects.effects.map { stageEffect(it) }.toSet())
    }

    companion object {
        operator fun invoke(metaState: MetaState, items: Map<String, S.Item>, item: C.Item): S.Item = Stage(metaState, items).stageItem(item)
    }
}
