package mce.phase

import java.util.concurrent.atomic.AtomicInteger
import mce.graph.Core as C
import mce.graph.Defunctionalized as D

class Defunctionalize private constructor() {
    private val functions: MutableMap<Int, D.Term> = mutableMapOf()

    private fun defunctionalizeItem(item: C.Item): D.Item = when (item) {
        is C.Item.Definition -> {
            val body = defunctionalizeTerm(item.body)
            D.Item.Definition(item.imports, item.name, body)
        }
    }

    private fun defunctionalizeTerm(term: C.Term): D.Term = when (term) {
        is C.Term.Hole -> throw InternalError()
        is C.Term.Meta -> throw InternalError()
        is C.Term.Variable -> D.Term.Variable(term.name, term.level)
        is C.Term.Definition -> D.Term.Definition(term.name)
        is C.Term.Let -> {
            val init = defunctionalizeTerm(term.init)
            val body = defunctionalizeTerm(term.body)
            D.Term.Let(term.name, init, body)
        }
        is C.Term.Match -> {
            val scrutinee = defunctionalizeTerm(term.scrutinee)
            val clauses = term.clauses.map { defunctionalizePattern(it.first) to defunctionalizeTerm(it.second) }
            D.Term.Match(scrutinee, clauses)
        }
        is C.Term.BooleanOf -> D.Term.BooleanOf(term.value)
        is C.Term.ByteOf -> D.Term.ByteOf(term.value)
        is C.Term.ShortOf -> D.Term.ShortOf(term.value)
        is C.Term.IntOf -> D.Term.IntOf(term.value)
        is C.Term.LongOf -> D.Term.LongOf(term.value)
        is C.Term.FloatOf -> D.Term.FloatOf(term.value)
        is C.Term.DoubleOf -> D.Term.DoubleOf(term.value)
        is C.Term.StringOf -> D.Term.StringOf(term.value)
        is C.Term.ByteArrayOf -> {
            val elements = term.elements.map { defunctionalizeTerm(it) }
            D.Term.ByteArrayOf(elements)
        }
        is C.Term.IntArrayOf -> {
            val elements = term.elements.map { defunctionalizeTerm(it) }
            D.Term.IntArrayOf(elements)
        }
        is C.Term.LongArrayOf -> {
            val elements = term.elements.map { defunctionalizeTerm(it) }
            D.Term.LongArrayOf(elements)
        }
        is C.Term.ListOf -> {
            val elements = term.elements.map { defunctionalizeTerm(it) }
            D.Term.ListOf(elements)
        }
        is C.Term.CompoundOf -> {
            val elements = term.elements.map { defunctionalizeTerm(it) }
            D.Term.CompoundOf(elements)
        }
        is C.Term.ReferenceOf -> {
            val element = defunctionalizeTerm(term.element)
            D.Term.ReferenceOf(element)
        }
        is C.Term.FunctionOf -> {
            val tag = freshTag()
            D.Term.FunctionOf(tag).also {
                val body = defunctionalizeTerm(term.body)
                functions[tag] = body
            }
        }
        is C.Term.Apply -> {
            val function = defunctionalizeTerm(term.function)
            val arguments = term.arguments.map { defunctionalizeTerm(it) }
            D.Term.Apply(function, arguments)
        }
        is C.Term.ThunkOf -> {
            val tag = freshTag()
            D.Term.FunctionOf(tag).also {
                val body = defunctionalizeTerm(term.body)
                functions[tag] = body
            }
        }
        is C.Term.Force -> {
            val element = defunctionalizeTerm(term.element)
            D.Term.Apply(element, emptyList())
        }
        is C.Term.CodeOf -> throw InternalError()
        is C.Term.Splice -> throw InternalError()
        is C.Term.Union -> {
            val variants = term.variants.map { defunctionalizeTerm(it) }
            D.Term.Union(variants)
        }
        is C.Term.Intersection -> {
            val variants = term.variants.map { defunctionalizeTerm(it) }
            D.Term.Intersection(variants)
        }
        is C.Term.Boolean -> D.Term.Boolean
        is C.Term.Byte -> D.Term.Byte
        is C.Term.Short -> D.Term.Short
        is C.Term.Int -> D.Term.Int
        is C.Term.Long -> D.Term.Long
        is C.Term.Float -> D.Term.Float
        is C.Term.Double -> D.Term.Double
        is C.Term.String -> D.Term.String
        is C.Term.ByteArray -> D.Term.ByteArray
        is C.Term.IntArray -> D.Term.IntArray
        is C.Term.LongArray -> D.Term.LongArray
        is C.Term.List -> {
            val element = defunctionalizeTerm(term.element)
            D.Term.List(element)
        }
        is C.Term.Compound -> {
            val elements = term.elements.map { it.first to defunctionalizeTerm(it.second) }
            D.Term.Compound(elements)
        }
        is C.Term.Reference -> {
            val element = defunctionalizeTerm(term.element)
            D.Term.Reference(element)
        }
        is C.Term.Function -> {
            val parameters = term.parameters.map { D.Parameter(it.name, defunctionalizeTerm(it.lower), defunctionalizeTerm(it.upper), defunctionalizeTerm(it.type)) }
            val resultant = defunctionalizeTerm(term.resultant)
            D.Term.Function(parameters, resultant)
        }
        is C.Term.Thunk -> {
            val element = defunctionalizeTerm(term.element)
            val effects = defunctionalizeEffects(term.effects)
            D.Term.Thunk(element, effects)
        }
        is C.Term.Code -> throw InternalError()
        is C.Term.Type -> D.Term.Type
    }

    private fun defunctionalizePattern(pattern: C.Pattern): D.Pattern = when (pattern) {
        is C.Pattern.Variable -> D.Pattern.Variable(pattern.name)
        is C.Pattern.BooleanOf -> D.Pattern.BooleanOf(pattern.value)
        is C.Pattern.ByteOf -> D.Pattern.ByteOf(pattern.value)
        is C.Pattern.ShortOf -> D.Pattern.ShortOf(pattern.value)
        is C.Pattern.IntOf -> D.Pattern.IntOf(pattern.value)
        is C.Pattern.LongOf -> D.Pattern.LongOf(pattern.value)
        is C.Pattern.FloatOf -> D.Pattern.FloatOf(pattern.value)
        is C.Pattern.DoubleOf -> D.Pattern.DoubleOf(pattern.value)
        is C.Pattern.StringOf -> D.Pattern.StringOf(pattern.value)
        is C.Pattern.ByteArrayOf -> {
            val elements = pattern.elements.map { defunctionalizePattern(it) }
            D.Pattern.ByteArrayOf(elements)
        }
        is C.Pattern.IntArrayOf -> {
            val elements = pattern.elements.map { defunctionalizePattern(it) }
            D.Pattern.IntArrayOf(elements)
        }
        is C.Pattern.LongArrayOf -> {
            val elements = pattern.elements.map { defunctionalizePattern(it) }
            D.Pattern.LongArrayOf(elements)
        }
        is C.Pattern.ListOf -> {
            val elements = pattern.elements.map { defunctionalizePattern(it) }
            D.Pattern.ListOf(elements)
        }
        is C.Pattern.CompoundOf -> {
            val elements = pattern.elements.map { defunctionalizePattern(it) }
            D.Pattern.CompoundOf(elements)
        }
        is C.Pattern.ReferenceOf -> {
            val element = defunctionalizePattern(pattern.element)
            D.Pattern.ReferenceOf(element)
        }
    }

    private fun defunctionalizeEffect(effect: C.Effect): D.Effect = TODO()

    private fun defunctionalizeEffects(effects: C.Effects): D.Effects = when (effects) {
        is C.Effects.Any -> D.Effects.Any
        is C.Effects.Set -> D.Effects.Set(effects.effects.map { defunctionalizeEffect(it) }.toSet())
    }

    data class Output(
        val item: D.Item,
        val functions: Map<Int, D.Term>
    )

    companion object {
        private val tag: AtomicInteger = AtomicInteger(0)

        private fun freshTag(): Int = tag.getAndIncrement()

        operator fun invoke(item: C.Item): Output = Defunctionalize().run {
            Output(defunctionalizeItem(item), functions)
        }
    }
}
