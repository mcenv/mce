package mce.phase

import mce.Diagnostic
import mce.pretty
import java.util.*
import mce.graph.Core as C
import mce.graph.Surface as S

typealias Context = List<Pair<String, C.Value>>

typealias Environment = List<Lazy<C.Value>>

typealias Level = Int

class Elaborate : Phase<S.Item, Elaborate.Output> {
    data class Output(
        val item: C.Item,
        val diagnostics: List<Diagnostic>,
        val types: Map<UUID, C.Value>
    )

    data class Typing(
        val term: C.Term,
        val type: C.Value
    )

    private val diagnostics: MutableList<Diagnostic> = mutableListOf()
    private val types: MutableMap<UUID, C.Value> = mutableMapOf()
    private val metas: MutableList<C.Value?> = mutableListOf()

    override fun run(input: S.Item): Output = Output(
        when (input) {
            is S.Item.Definition -> C.Item.Definition(
                input.name, input.imports, emptyList<Pair<String, C.Value>>().checkTerm(
                    input.body, emptyList<Lazy<C.Value>>().evaluate(
                        emptyList<Pair<String, C.Value>>().checkTerm(input.type, C.Value.Type)
                    )
                )
            )
        }, diagnostics, types
    )

    private fun Context.inferTerm(term: S.Term): Typing = when (term) {
        is S.Term.Hole -> {
            val type = fresh()
            diagnostics += Diagnostic.TermExpected(metas.pretty(type), term.id)
            Typing(C.Term.Hole, type)
        }
        is S.Term.Dummy -> TODO()
        is S.Term.Meta -> TODO()
        is S.Term.Variable -> {
            val level = indexOfLast { it.first == term.name }
            if (level == -1) {
                diagnostics += Diagnostic.VariableNotFound(term.name, term.id)
                Typing(C.Term.Dummy, fresh())
            } else Typing(C.Term.Variable(term.name, level), this[level].second)
        }
        is S.Term.BooleanOf -> Typing(C.Term.BooleanOf(term.value), C.Value.Boolean)
        is S.Term.ByteOf -> Typing(C.Term.ByteOf(term.value), C.Value.Byte)
        is S.Term.ShortOf -> Typing(C.Term.ShortOf(term.value), C.Value.Short)
        is S.Term.IntOf -> Typing(C.Term.IntOf(term.value), C.Value.Int)
        is S.Term.LongOf -> Typing(C.Term.LongOf(term.value), C.Value.Long)
        is S.Term.FloatOf -> Typing(C.Term.FloatOf(term.value), C.Value.Float)
        is S.Term.DoubleOf -> Typing(C.Term.DoubleOf(term.value), C.Value.Double)
        is S.Term.StringOf -> Typing(C.Term.StringOf(term.value), C.Value.String)
        is S.Term.ByteArrayOf ->
            Typing(C.Term.ByteArrayOf(term.elements.map { checkTerm(it, C.Value.Byte) }), C.Value.ByteArray)
        is S.Term.IntArrayOf -> Typing(C.Term.IntArrayOf(term.elements.map {
            checkTerm(it, C.Value.Int)
        }), C.Value.IntArray)
        is S.Term.LongArrayOf ->
            Typing(C.Term.LongArrayOf(term.elements.map { checkTerm(it, C.Value.Long) }), C.Value.LongArray)
        is S.Term.ListOf -> if (term.elements.isEmpty()) Typing(C.Term.ListOf(emptyList()), fresh()) else {
            val first = inferTerm(term.elements.first())
            Typing(C.Term.ListOf(
                listOf(first.term) + term.elements.drop(1).map { checkTerm(it, first.type) }
            ), C.Value.List(lazyOf(first.type)))
        }
        is S.Term.CompoundOf -> {
            val elements = term.elements.map { inferTerm(it) }
            Typing(C.Term.CompoundOf(elements.map { it.term }), C.Value.Compound(elements.map { lazyOf(it.type) }))
        }
        is S.Term.FunctionOf -> {
            val types = term.parameters.map { fresh() }
            val body = (term.parameters zip types).inferTerm(term.body)
            Typing(
                C.Term.FunctionOf(term.parameters, body.term),
                C.Value.Function((term.parameters zip types.map(::lazyOf)), quote(body.type))
            )
        }
        is S.Term.Apply -> {
            val function = inferTerm(term.function)
            when (val forced = force(function.type)) {
                is C.Value.Function -> {
                    val arguments = (term.arguments zip forced.parameters).map { (argument, parameter) ->
                        checkTerm(argument, parameter.second.value)
                    }
                    Typing(C.Term.Apply(function.term, arguments), mutableListOf<Lazy<C.Value>>().let { environment ->
                        arguments.map { argument -> lazy { environment.evaluate(argument) }.also { environment += it } }
                    }.evaluate(forced.resultant))
                }
                else -> {
                    diagnostics += Diagnostic.FunctionExpected(term.function.id)
                    Typing(C.Term.Dummy, fresh())
                }
            }
        }
        is S.Term.Boolean -> Typing(C.Term.Boolean, C.Value.Type)
        is S.Term.Byte -> Typing(C.Term.Byte, C.Value.Type)
        is S.Term.Short -> Typing(C.Term.Short, C.Value.Type)
        is S.Term.Int -> Typing(C.Term.Int, C.Value.Type)
        is S.Term.Long -> Typing(C.Term.Long, C.Value.Type)
        is S.Term.Float -> Typing(C.Term.Float, C.Value.Type)
        is S.Term.Double -> Typing(C.Term.Double, C.Value.Type)
        is S.Term.String -> Typing(C.Term.String, C.Value.Type)
        is S.Term.ByteArray -> Typing(C.Term.ByteArray, C.Value.Type)
        is S.Term.IntArray -> Typing(C.Term.IntArray, C.Value.Type)
        is S.Term.LongArray -> Typing(C.Term.LongArray, C.Value.Type)
        is S.Term.List -> Typing(C.Term.List(checkTerm(term.element, C.Value.Type)), C.Value.Type)
        is S.Term.Compound -> Typing(
            C.Term.Compound(term.elements.map { checkTerm(it, C.Value.Type) }),
            C.Value.Type
        )
        is S.Term.Function -> Typing(mutableListOf<Pair<String, C.Value>>().let { context ->
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
        }, C.Value.Type)
        is S.Term.Type -> Typing(C.Term.Type, C.Value.Type)
    }.also { types[term.id] = it.type }

    private fun Context.checkTerm(term: S.Term, type: C.Value): C.Term {
        val forced = force(type)
        types[term.id] = forced
        return when {
            term is S.Term.Hole -> {
                diagnostics += Diagnostic.TermExpected(metas.pretty(forced), term.id)
                C.Term.Hole
            }
            term is S.Term.ListOf && forced is C.Value.List ->
                C.Term.ListOf(term.elements.map { checkTerm(it, forced.element.value) })
            term is S.Term.CompoundOf && forced is C.Value.Compound ->
                C.Term.CompoundOf((term.elements zip forced.elements).map { checkTerm(it.first, it.second.value) })
            term is S.Term.FunctionOf && forced is C.Value.Function -> C.Term.FunctionOf(
                term.parameters,
                (term.parameters zip forced.parameters.map { it.second.value }).checkTerm(
                    term.body,
                    term.parameters.mapIndexed { index, parameter ->
                        lazyOf(C.Value.Variable(parameter, index))
                    }.evaluate(forced.resultant)
                )
            )
            term is S.Term.Apply -> {
                val arguments = term.arguments.map { inferTerm(it) }
                C.Term.Apply(
                    checkTerm(term.function, C.Value.Function(arguments.map { "" to lazyOf(it.type) }, quote(forced))),
                    arguments.map { it.term }
                )
            }
            else -> {
                val inferred = inferTerm(term)
                if (size.unify(inferred.type, forced)) inferred.term else {
                    diagnostics +=
                        Diagnostic.TypeMismatch(metas.pretty(forced), metas.pretty(inferred.type), term.id)
                    C.Term.Dummy
                }
            }
        }
    }

    private fun Environment.evaluate(term: C.Term): C.Value = when (term) {
        is C.Term.Hole -> C.Value.Hole
        is C.Term.Dummy -> C.Value.Dummy
        is C.Term.Meta -> metas[term.index] ?: C.Value.Meta(term.index)
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

    private fun quote(value: C.Value): C.Term = when (val forced = force(value)) {
        is C.Value.Hole -> C.Term.Hole
        is C.Value.Dummy -> C.Term.Dummy
        is C.Value.Meta -> metas[forced.index]?.let(::quote) ?: C.Term.Meta(forced.index)
        is C.Value.Variable -> C.Term.Variable(forced.name, forced.level)
        is C.Value.BooleanOf -> C.Term.BooleanOf(forced.value)
        is C.Value.ByteOf -> C.Term.ByteOf(forced.value)
        is C.Value.ShortOf -> C.Term.ShortOf(forced.value)
        is C.Value.IntOf -> C.Term.IntOf(forced.value)
        is C.Value.LongOf -> C.Term.LongOf(forced.value)
        is C.Value.FloatOf -> C.Term.FloatOf(forced.value)
        is C.Value.DoubleOf -> C.Term.DoubleOf(forced.value)
        is C.Value.StringOf -> C.Term.StringOf(forced.value)
        is C.Value.ByteArrayOf -> C.Term.ByteArrayOf(forced.elements.map { quote(it.value) })
        is C.Value.IntArrayOf -> C.Term.IntArrayOf(forced.elements.map { quote(it.value) })
        is C.Value.LongArrayOf -> C.Term.LongArrayOf(forced.elements.map { quote(it.value) })
        is C.Value.ListOf -> C.Term.ListOf(forced.elements.map { quote(it.value) })
        is C.Value.CompoundOf -> C.Term.CompoundOf(forced.elements.map { quote(it.value) })
        is C.Value.FunctionOf -> TODO()
        is C.Value.Apply -> TODO()
        is C.Value.Boolean -> C.Term.Boolean
        is C.Value.Byte -> C.Term.Byte
        is C.Value.Short -> C.Term.Short
        is C.Value.Int -> C.Term.Int
        is C.Value.Long -> C.Term.Long
        is C.Value.Float -> C.Term.Float
        is C.Value.Double -> C.Term.Double
        is C.Value.String -> C.Term.String
        is C.Value.ByteArray -> C.Term.ByteArray
        is C.Value.IntArray -> C.Term.IntArray
        is C.Value.LongArray -> C.Term.LongArray
        is C.Value.List -> C.Term.List(quote(forced.element.value))
        is C.Value.Compound -> C.Term.Compound(forced.elements.map { quote(it.value) })
        is C.Value.Function -> TODO()
        is C.Value.Type -> C.Term.Type
    }

    private fun Level.unify(value1: C.Value, value2: C.Value): Boolean {
        val forced1 = force(value1)
        val forced2 = force(value2)
        return when {
            forced1 is C.Value.Meta -> when (val solved1 = metas[forced1.index]) {
                null -> {
                    metas[forced1.index] = forced2
                    true
                }
                else -> unify(solved1, forced2)
            }
            forced2 is C.Value.Meta -> unify(forced2, forced1)
            forced1 is C.Value.Variable && forced2 is C.Value.Variable -> forced1.level == forced2.level
            forced1 is C.Value.BooleanOf && forced2 is C.Value.BooleanOf -> forced1.value == forced2.value
            forced1 is C.Value.ByteOf && forced2 is C.Value.ByteOf -> forced1.value == forced2.value
            forced1 is C.Value.ShortOf && forced2 is C.Value.ShortOf -> forced1.value == forced2.value
            forced1 is C.Value.IntOf && forced2 is C.Value.IntOf -> forced1.value == forced2.value
            forced1 is C.Value.LongOf && forced2 is C.Value.LongOf -> forced1.value == forced2.value
            forced1 is C.Value.FloatOf && forced2 is C.Value.FloatOf -> forced1.value == forced2.value
            forced1 is C.Value.DoubleOf && forced2 is C.Value.DoubleOf -> forced1.value == forced2.value
            forced1 is C.Value.StringOf && forced2 is C.Value.StringOf -> forced1.value == forced2.value
            forced1 is C.Value.ByteArrayOf && forced2 is C.Value.ByteArrayOf -> forced1.elements.size == forced2.elements.size &&
                    (forced1.elements zip forced2.elements).all { unify(it.first.value, it.second.value) }
            forced1 is C.Value.IntArrayOf && forced2 is C.Value.IntArrayOf -> forced1.elements.size == forced2.elements.size &&
                    (forced1.elements zip forced2.elements).all { unify(it.first.value, it.second.value) }
            forced1 is C.Value.LongArrayOf && forced2 is C.Value.LongArrayOf -> forced1.elements.size == forced2.elements.size &&
                    (forced1.elements zip forced2.elements).all { unify(it.first.value, it.second.value) }
            forced1 is C.Value.ListOf && forced2 is C.Value.ListOf -> forced1.elements.size == forced2.elements.size &&
                    (forced1.elements zip forced2.elements).all { unify(it.first.value, it.second.value) }
            forced1 is C.Value.CompoundOf && forced2 is C.Value.CompoundOf -> forced1.elements.size == forced2.elements.size &&
                    (forced1.elements zip forced2.elements).all { unify(it.first.value, it.second.value) }
            forced1 is C.Value.FunctionOf && forced2 is C.Value.FunctionOf -> forced1.parameters.size == forced2.parameters.size &&
                    forced1.parameters.mapIndexed { index, parameter ->
                        lazyOf(C.Value.Variable(parameter, this + index))
                    }.let {
                        (this + forced1.parameters.size).unify(it.evaluate(forced1.body), it.evaluate(forced2.body))
                    }
            forced1 is C.Value.Apply && forced2 is C.Value.Apply -> unify(forced1.function, forced2.function) &&
                    (forced1.arguments zip forced2.arguments).all { unify(it.first.value, it.second.value) }
            forced1 is C.Value.Boolean && forced2 is C.Value.Boolean -> true
            forced1 is C.Value.Byte && forced2 is C.Value.Byte -> true
            forced1 is C.Value.Short && forced2 is C.Value.Short -> true
            forced1 is C.Value.Int && forced2 is C.Value.Int -> true
            forced1 is C.Value.Long && forced2 is C.Value.Long -> true
            forced1 is C.Value.Float && forced2 is C.Value.Float -> true
            forced1 is C.Value.Double && forced2 is C.Value.Double -> true
            forced1 is C.Value.String && forced2 is C.Value.String -> true
            forced1 is C.Value.ByteArray && forced2 is C.Value.ByteArray -> true
            forced1 is C.Value.IntArray && forced2 is C.Value.IntArray -> true
            forced1 is C.Value.LongArray && forced2 is C.Value.LongArray -> true
            forced1 is C.Value.List && forced2 is C.Value.List -> unify(forced1.element.value, forced2.element.value)
            forced1 is C.Value.Compound && forced2 is C.Value.Compound -> forced1.elements.size == forced2.elements.size &&
                    (forced1.elements zip forced2.elements).all { unify(it.first.value, it.second.value) }
            forced1 is C.Value.Function && forced2 is C.Value.Function -> forced1.parameters.size == forced2.parameters.size &&
                    (forced1.parameters zip forced2.parameters).withIndex().all { (index, parameter) ->
                        val (parameter1, parameter2) = parameter
                        (this + index).unify(parameter1.second.value, parameter2.second.value)
                    } &&
                    forced1.parameters.mapIndexed { index, parameter ->
                        lazyOf(C.Value.Variable(parameter.first, this + index))
                    }.let {
                        (this + forced1.parameters.size).unify(
                            it.evaluate(forced1.resultant),
                            it.evaluate(forced2.resultant)
                        )
                    }
            forced1 is C.Value.Type && forced2 is C.Value.Type -> true
            else -> false
        }
    }

    private tailrec fun force(value: C.Value): C.Value = when (value) {
        is C.Value.Meta -> when (metas[value.index]) {
            null -> value
            else -> force(value)
        }
        else -> value
    }

    private fun fresh(): C.Value = C.Value.Meta(metas.size).also { metas += null }
}
