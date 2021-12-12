package mce.phase

import mce.Diagnostic
import mce.graph.Core as C
import mce.graph.Surface as S

typealias Context = List<Pair<String, C.Value>>

typealias Environment = List<Lazy<C.Value>>

typealias Level = Int

class Elaborate : Phase<S.Item, C.Item> {
    private val diagnostics: MutableList<Diagnostic> = mutableListOf()

    override fun run(input: S.Item): Pair<C.Item, List<Diagnostic>> = when (input) {
        is S.Item.Definition -> C.Item.Definition(
            input.name, input.imports, emptyList<Pair<String, C.Value>>().checkTerm(
                input.body, emptyList<Lazy<C.Value>>().evaluate(
                    emptyList<Pair<String, C.Value>>().checkTerm(input.type, C.Value.Type)
                )
            )
        )
    } to diagnostics

    private fun Context.inferTerm(term: S.Term): C.Term = when (term) {
        is S.Term.Hole -> {
            diagnostics += Diagnostic.TermExpected(TODO(), term.id)
            C.Term.Hole(TODO())
        }
        is S.Term.Dummy -> C.Term.Dummy(TODO())
        is S.Term.Variable -> {
            val level = indexOfLast { it.first == term.name }
            if (level == -1) {
                diagnostics += Diagnostic.VariableNotFound(term.name, term.id)
                TODO()
            } else C.Term.Variable(term.name, level, this[level].second)
        }
        is S.Term.BooleanOf -> C.Term.BooleanOf(term.value)
        is S.Term.ByteOf -> C.Term.ByteOf(term.value)
        is S.Term.ShortOf -> C.Term.ShortOf(term.value)
        is S.Term.IntOf -> C.Term.IntOf(term.value)
        is S.Term.LongOf -> C.Term.LongOf(term.value)
        is S.Term.FloatOf -> C.Term.FloatOf(term.value)
        is S.Term.DoubleOf -> C.Term.DoubleOf(term.value)
        is S.Term.StringOf -> C.Term.StringOf(term.value)
        is S.Term.ByteArrayOf -> C.Term.ByteArrayOf(term.elements.map { checkTerm(it, C.Value.Byte) })
        is S.Term.IntArrayOf -> C.Term.IntArrayOf(term.elements.map { checkTerm(it, C.Value.Int) })
        is S.Term.LongArrayOf -> C.Term.LongArrayOf(term.elements.map { checkTerm(it, C.Value.Long) })
        is S.Term.ListOf -> if (term.elements.isEmpty()) {
            diagnostics += Diagnostic.InferenceFailed(term.id)
            TODO()
        } else {
            val first = inferTerm(term.elements.first())
            C.Term.ListOf(
                listOf(first) + term.elements.drop(1).map { checkTerm(it, first.type) },
                C.Value.List(lazyOf(first.type))
            )
        }
        is S.Term.CompoundOf -> {
            val elements = term.elements.map { inferTerm(it) }
            C.Term.CompoundOf(elements, C.Value.Compound(elements.map { lazyOf(it.type) }))
        }
        is S.Term.FunctionOf -> {
            diagnostics += Diagnostic.InferenceFailed(term.id)
            TODO()
        }
        is S.Term.Apply -> {
            val function = inferTerm(term.function)
            when (val type = function.type) {
                is C.Value.Function -> {
                    val arguments = (term.arguments zip type.parameters).map { (argument, parameter) ->
                        checkTerm(argument, parameter.second.value)
                    }
                    C.Term.Apply(function, arguments, mutableListOf<Lazy<C.Value>>().let { environment ->
                        arguments.map { argument -> lazy { environment.evaluate(argument) }.also { environment += it } }
                    }.evaluate(type.resultant))
                }
                else -> {
                    diagnostics += Diagnostic.FunctionExpected(term.function.id)
                    C.Term.Dummy(TODO())
                }
            }
        }
        is S.Term.Boolean -> C.Term.Boolean
        is S.Term.Byte -> C.Term.Byte
        is S.Term.Short -> C.Term.Short
        is S.Term.Int -> C.Term.Int
        is S.Term.Long -> C.Term.Long
        is S.Term.Float -> C.Term.Float
        is S.Term.Double -> C.Term.Double
        is S.Term.String -> C.Term.String
        is S.Term.ByteArray -> C.Term.ByteArray
        is S.Term.IntArray -> C.Term.IntArray
        is S.Term.LongArray -> C.Term.LongArray
        is S.Term.List -> C.Term.List(checkTerm(term.element, C.Value.Type))
        is S.Term.Compound -> C.Term.Compound(term.elements.map { checkTerm(it, C.Value.Type) })
        is S.Term.Function -> mutableListOf<Pair<String, C.Value>>().let { context ->
            C.Term.Function(
                mutableListOf<Lazy<C.Value>>().let { environment ->
                    term.parameters.map { (name, parameter) ->
                        name to context.checkTerm(parameter, C.Value.Type).also {
                            context += name to environment.evaluate(it)
                            environment += lazyOf(C.Value.Variable(name, environment.size))
                        }
                    }
                },
                context.checkTerm(term.resultant, C.Value.Type)
            )
        }
        is S.Term.Type -> C.Term.Type
    }

    private fun Context.checkTerm(term: S.Term, type: C.Value): C.Term = when {
        term is S.Term.Hole -> {
            diagnostics += Diagnostic.TermExpected(Diagnostic.pretty(type), term.id)
            C.Term.Hole(type)
        }
        term is S.Term.ListOf && type is C.Value.List ->
            C.Term.ListOf(term.elements.map { checkTerm(it, type.element.value) }, type)
        term is S.Term.CompoundOf && type is C.Value.Compound ->
            C.Term.CompoundOf((term.elements zip type.elements).map { checkTerm(it.first, it.second.value) }, type)
        else -> {
            val inferred = inferTerm(term)
            if (size.convertible(inferred.type, type)) inferred else {
                diagnostics +=
                    Diagnostic.TypeMismatch(Diagnostic.pretty(type), Diagnostic.pretty(inferred.type), term.id)
                C.Term.Dummy(type)
            }
        }
    }

    private fun Environment.evaluate(term: C.Term): C.Value = when (term) {
        is C.Term.Hole -> C.Value.Hole
        is C.Term.Dummy -> C.Value.Dummy
        is C.Term.Variable -> this[term.level].value
        is C.Term.BooleanOf -> C.Value.BooleanOf(term.value)
        is C.Term.ByteOf -> C.Value.ByteOf(term.value)
        is C.Term.ShortOf -> C.Value.ShortOf(term.value)
        is C.Term.IntOf -> C.Value.IntOf(term.value)
        is C.Term.LongOf -> C.Value.LongOf(term.value)
        is C.Term.FloatOf -> C.Value.FloatOf(term.value)
        is C.Term.DoubleOf -> C.Value.DoubleOf(term.value)
        is C.Term.StringOf -> C.Value.StringOf(term.value)
        is C.Term.ByteArrayOf -> C.Value.ByteArrayOf(term.elements.map { lazy { evaluate(it) } })
        is C.Term.IntArrayOf -> C.Value.IntArrayOf(term.elements.map { lazy { evaluate(it) } })
        is C.Term.LongArrayOf -> C.Value.LongArrayOf(term.elements.map { lazy { evaluate(it) } })
        is C.Term.ListOf -> C.Value.ListOf(term.elements.map { lazy { evaluate(it) } })
        is C.Term.CompoundOf -> C.Value.CompoundOf(term.elements.map { lazy { evaluate(it) } })
        is C.Term.FunctionOf -> C.Value.FunctionOf(term.parameters, term.body)
        is C.Term.Apply -> when (val function = evaluate(term.function)) {
            is C.Value.FunctionOf -> term.arguments.map { lazy { evaluate(it) } }.evaluate(function.body)
            else -> C.Value.Apply(function, term.arguments.map { lazy { evaluate(it) } })
        }
        is C.Term.Boolean -> C.Value.Boolean
        is C.Term.Byte -> C.Value.Byte
        is C.Term.Short -> C.Value.Short
        is C.Term.Int -> C.Value.Int
        is C.Term.Long -> C.Value.Long
        is C.Term.Float -> C.Value.Float
        is C.Term.Double -> C.Value.Double
        is C.Term.String -> C.Value.String
        is C.Term.ByteArray -> C.Value.ByteArray
        is C.Term.IntArray -> C.Value.IntArray
        is C.Term.LongArray -> C.Value.LongArray
        is C.Term.List -> C.Value.List(lazy { evaluate(term.element) })
        is C.Term.Compound -> C.Value.Compound(term.elements.map { lazy { evaluate(it) } })
        is C.Term.Function -> C.Value.Function(
            term.parameters.map { (name, parameter) -> name to lazy { evaluate(parameter) } }, term.resultant
        )
        is C.Term.Type -> C.Value.Type
    }

    private fun Level.convertible(left: C.Value, right: C.Value): Boolean = when {
        left is C.Value.Variable && right is C.Value.Variable -> left.level == right.level
        left is C.Value.BooleanOf && right is C.Value.BooleanOf -> left.value == right.value
        left is C.Value.ByteOf && right is C.Value.ByteOf -> left.value == right.value
        left is C.Value.ShortOf && right is C.Value.ShortOf -> left.value == right.value
        left is C.Value.IntOf && right is C.Value.IntOf -> left.value == right.value
        left is C.Value.LongOf && right is C.Value.LongOf -> left.value == right.value
        left is C.Value.FloatOf && right is C.Value.FloatOf -> left.value == right.value
        left is C.Value.DoubleOf && right is C.Value.DoubleOf -> left.value == right.value
        left is C.Value.StringOf && right is C.Value.StringOf -> left.value == right.value
        left is C.Value.ByteArrayOf && right is C.Value.ByteArrayOf -> left.elements.size == right.elements.size &&
                (left.elements zip right.elements).all { convertible(it.first.value, it.second.value) }
        left is C.Value.IntArrayOf && right is C.Value.IntArrayOf -> left.elements.size == right.elements.size &&
                (left.elements zip right.elements).all { convertible(it.first.value, it.second.value) }
        left is C.Value.LongArrayOf && right is C.Value.LongArrayOf -> left.elements.size == right.elements.size &&
                (left.elements zip right.elements).all { convertible(it.first.value, it.second.value) }
        left is C.Value.ListOf && right is C.Value.ListOf -> left.elements.size == right.elements.size &&
                (left.elements zip right.elements).all { convertible(it.first.value, it.second.value) }
        left is C.Value.CompoundOf && right is C.Value.CompoundOf -> left.elements.size == right.elements.size &&
                (left.elements zip right.elements).all { convertible(it.first.value, it.second.value) }
        left is C.Value.FunctionOf && right is C.Value.FunctionOf -> left.parameters.size == right.parameters.size &&
                left.parameters.mapIndexed { index, parameter ->
                    lazyOf(C.Value.Variable(parameter, this + index))
                }.let {
                    (this + left.parameters.size).convertible(it.evaluate(left.body), it.evaluate(right.body))
                }
        left is C.Value.Apply && right is C.Value.Apply -> convertible(left.function, right.function) &&
                (left.arguments zip right.arguments).all { convertible(it.first.value, it.second.value) }
        left is C.Value.Boolean && right is C.Value.Boolean -> true
        left is C.Value.Byte && right is C.Value.Byte -> true
        left is C.Value.Short && right is C.Value.Short -> true
        left is C.Value.Int && right is C.Value.Int -> true
        left is C.Value.Long && right is C.Value.Long -> true
        left is C.Value.Float && right is C.Value.Float -> true
        left is C.Value.Double && right is C.Value.Double -> true
        left is C.Value.String && right is C.Value.String -> true
        left is C.Value.ByteArray && right is C.Value.ByteArray -> true
        left is C.Value.IntArray && right is C.Value.IntArray -> true
        left is C.Value.LongArray && right is C.Value.LongArray -> true
        left is C.Value.List && right is C.Value.List -> convertible(left.element.value, right.element.value)
        left is C.Value.Compound && right is C.Value.Compound -> left.elements.size == right.elements.size &&
                (left.elements zip right.elements).all { convertible(it.first.value, it.second.value) }
        left is C.Value.Function && right is C.Value.Function -> left.parameters.size == right.parameters.size &&
                (left.parameters zip right.parameters).withIndex().all { (index, parameter) ->
                    val (leftParameter, rightParameter) = parameter
                    (this + index).convertible(leftParameter.second.value, rightParameter.second.value)
                } &&
                left.parameters.mapIndexed { index, parameter ->
                    lazyOf(C.Value.Variable(parameter.first, this + index))
                }.let {
                    (this + left.parameters.size).convertible(it.evaluate(left.resultant), it.evaluate(right.resultant))
                }
        left is C.Value.Type && right is C.Value.Type -> true
        else -> false
    }
}
