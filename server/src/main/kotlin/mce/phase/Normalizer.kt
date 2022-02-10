package mce.phase

import mce.graph.Core

typealias Environment = List<Lazy<Core.Value>>

/**
 * Evaluates the [term] to a value under this environment.
 */
fun Environment.evaluate(metaState: MetaState, term: Core.Term): Core.Value {
    fun Environment.evaluate(term: Core.Term): Core.Value = when (term) {
        is Core.Term.Hole -> Core.Value.Hole
        is Core.Term.Meta -> metaState[term.index] ?: Core.Value.Meta(term.index)
        is Core.Term.Variable -> this[term.level].value
        is Core.Term.Definition -> Core.Value.Definition(term.name)
        is Core.Term.Let -> (this + lazyOf(evaluate(term.init))).evaluate(term.body)
        is Core.Term.Match -> Core.Value.Match(evaluate(term.scrutinee), term.clauses.map { it.first to lazy { evaluate(it.second) /* TODO: collect variables */ } })
        is Core.Term.BooleanOf -> Core.Value.BooleanOf(term.value)
        is Core.Term.ByteOf -> Core.Value.ByteOf(term.value)
        is Core.Term.ShortOf -> Core.Value.ShortOf(term.value)
        is Core.Term.IntOf -> Core.Value.IntOf(term.value)
        is Core.Term.LongOf -> Core.Value.LongOf(term.value)
        is Core.Term.FloatOf -> Core.Value.FloatOf(term.value)
        is Core.Term.DoubleOf -> Core.Value.DoubleOf(term.value)
        is Core.Term.StringOf -> Core.Value.StringOf(term.value)
        is Core.Term.ByteArrayOf -> Core.Value.ByteArrayOf(term.elements.map { lazy { evaluate(it) } })
        is Core.Term.IntArrayOf -> Core.Value.IntArrayOf(term.elements.map { lazy { evaluate(it) } })
        is Core.Term.LongArrayOf -> Core.Value.LongArrayOf(term.elements.map { lazy { evaluate(it) } })
        is Core.Term.ListOf -> Core.Value.ListOf(term.elements.map { lazy { evaluate(it) } })
        is Core.Term.CompoundOf -> Core.Value.CompoundOf(term.elements.map { lazy { evaluate(it) } })
        is Core.Term.ReferenceOf -> Core.Value.ReferenceOf(lazy { evaluate(term.element) })
        is Core.Term.FunctionOf -> Core.Value.FunctionOf(term.parameters, term.body)
        is Core.Term.Apply -> when (val function = evaluate(term.function)) {
            is Core.Value.FunctionOf -> term.arguments.map { lazy { evaluate(it) } }.evaluate(function.body)
            else -> Core.Value.Apply(function, term.arguments.map { lazy { evaluate(it) } })
        }
        is Core.Term.ThunkOf -> Core.Value.ThunkOf(lazy { evaluate(term.body) })
        is Core.Term.Force -> when (val thunk = evaluate(term.element)) {
            is Core.Value.ThunkOf -> thunk.body.value
            else -> Core.Value.Force(lazyOf(thunk))
        }
        is Core.Term.CodeOf -> Core.Value.CodeOf(lazy { evaluate(term.element) })
        is Core.Term.Splice -> when (val element = evaluate(term.element)) {
            is Core.Value.CodeOf -> element.element.value
            else -> Core.Value.Splice(lazyOf(element))
        }
        is Core.Term.Union -> Core.Value.Union(term.variants.map { lazy { evaluate(it) } })
        is Core.Term.Intersection -> Core.Value.Intersection(term.variants.map { lazy { evaluate(it) } })
        is Core.Term.Boolean -> Core.Value.Boolean
        is Core.Term.Byte -> Core.Value.Byte
        is Core.Term.Short -> Core.Value.Short
        is Core.Term.Int -> Core.Value.Int
        is Core.Term.Long -> Core.Value.Long
        is Core.Term.Float -> Core.Value.Float
        is Core.Term.Double -> Core.Value.Double
        is Core.Term.String -> Core.Value.String
        is Core.Term.ByteArray -> Core.Value.ByteArray
        is Core.Term.IntArray -> Core.Value.IntArray
        is Core.Term.LongArray -> Core.Value.LongArray
        is Core.Term.List -> Core.Value.List(lazy { evaluate(term.element) })
        is Core.Term.Compound -> Core.Value.Compound(term.elements)
        is Core.Term.Reference -> Core.Value.Reference(lazy { evaluate(term.element) })
        is Core.Term.Function -> Core.Value.Function(term.parameters, term.resultant)
        is Core.Term.Thunk -> Core.Value.Thunk(lazy { evaluate(term.element) }, term.effects)
        is Core.Term.Code -> Core.Value.Code(lazy { evaluate(term.element) })
        is Core.Term.Type -> Core.Value.Type
    }

    return evaluate(term)
}

/**
 * Quotes the [value] to a term.
 */
@Suppress("NAME_SHADOWING")
fun quote(metaState: MetaState, value: Core.Value): Core.Term {
    fun quote(value: Core.Value): Core.Term = when (val value = metaState.force(value)) {
        is Core.Value.Hole -> Core.Term.Hole
        is Core.Value.Meta -> metaState[value.index]?.let { quote(it) } ?: Core.Term.Meta(value.index)
        is Core.Value.Variable -> Core.Term.Variable(value.name, value.level)
        is Core.Value.Definition -> Core.Term.Definition(value.name)
        is Core.Value.Match -> Core.Term.Match(quote(value.scrutinee), value.clauses.map { it.first to quote(it.second.value) })
        is Core.Value.BooleanOf -> Core.Term.BooleanOf(value.value)
        is Core.Value.ByteOf -> Core.Term.ByteOf(value.value)
        is Core.Value.ShortOf -> Core.Term.ShortOf(value.value)
        is Core.Value.IntOf -> Core.Term.IntOf(value.value)
        is Core.Value.LongOf -> Core.Term.LongOf(value.value)
        is Core.Value.FloatOf -> Core.Term.FloatOf(value.value)
        is Core.Value.DoubleOf -> Core.Term.DoubleOf(value.value)
        is Core.Value.StringOf -> Core.Term.StringOf(value.value)
        is Core.Value.ByteArrayOf -> Core.Term.ByteArrayOf(value.elements.map { quote(it.value) })
        is Core.Value.IntArrayOf -> Core.Term.IntArrayOf(value.elements.map { quote(it.value) })
        is Core.Value.LongArrayOf -> Core.Term.LongArrayOf(value.elements.map { quote(it.value) })
        is Core.Value.ListOf -> Core.Term.ListOf(value.elements.map { quote(it.value) })
        is Core.Value.CompoundOf -> Core.Term.CompoundOf(value.elements.map { quote(it.value) })
        is Core.Value.ReferenceOf -> Core.Term.ReferenceOf(quote(value.element.value))
        is Core.Value.FunctionOf -> Core.Term.FunctionOf(
            value.parameters,
            quote(
                value.parameters
                    .mapIndexed { index, parameter -> lazyOf(Core.Value.Variable(parameter, index)) }
                    .evaluate(metaState, value.body)
            )
        )
        is Core.Value.Apply -> Core.Term.Apply(quote(value.function), value.arguments.map { quote(it.value) })
        is Core.Value.ThunkOf -> Core.Term.ThunkOf(quote(value.body.value))
        is Core.Value.Force -> Core.Term.Force(quote(value.element.value))
        is Core.Value.CodeOf -> Core.Term.CodeOf(quote(value.element.value))
        is Core.Value.Splice -> Core.Term.Splice(quote(value.element.value))
        is Core.Value.Union -> Core.Term.Union(value.variants.map { quote(it.value) })
        is Core.Value.Intersection -> Core.Term.Intersection(value.variants.map { quote(it.value) })
        is Core.Value.Boolean -> Core.Term.Boolean
        is Core.Value.Byte -> Core.Term.Byte
        is Core.Value.Short -> Core.Term.Short
        is Core.Value.Int -> Core.Term.Int
        is Core.Value.Long -> Core.Term.Long
        is Core.Value.Float -> Core.Term.Float
        is Core.Value.Double -> Core.Term.Double
        is Core.Value.String -> Core.Term.String
        is Core.Value.ByteArray -> Core.Term.ByteArray
        is Core.Value.IntArray -> Core.Term.IntArray
        is Core.Value.LongArray -> Core.Term.LongArray
        is Core.Value.List -> Core.Term.List(quote(value.element.value))
        is Core.Value.Compound -> Core.Term.Compound(value.elements)
        is Core.Value.Reference -> Core.Term.Reference(quote(value.element.value))
        is Core.Value.Function -> Core.Term.Function(
            value.parameters,
            quote(
                value.parameters
                    .mapIndexed { index, (name, _) -> lazyOf(Core.Value.Variable(name, index)) }
                    .evaluate(metaState, value.resultant)
            )
        )
        is Core.Value.Thunk -> Core.Term.Thunk(quote(value.element.value), value.effects)
        is Core.Value.Code -> Core.Term.Code(quote(value.element.value))
        is Core.Value.Type -> Core.Term.Type
    }

    return quote(value)
}
