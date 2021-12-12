package mce

import java.util.*
import mce.graph.Core as C
import mce.graph.Surface as S

sealed class Diagnostic {
    abstract val id: UUID

    class TermExpected(val type: S.Term, override val id: UUID) : Diagnostic()
    class VariableNotFound(val name: String, override val id: UUID) : Diagnostic()
    class InferenceFailed(override val id: UUID) : Diagnostic()
    class FunctionExpected(override val id: UUID) : Diagnostic()
    class TypeMismatch(val expected: S.Term, val actual: S.Term, override val id: UUID) : Diagnostic()

    companion object {
        fun delaborate(term: C.Term): S.Term = when (term) {
            is C.Term.Hole -> S.Term.Hole()
            is C.Term.Dummy -> S.Term.Dummy()
            is C.Term.Variable -> S.Term.Variable(term.name)
            is C.Term.BooleanOf -> S.Term.BooleanOf(term.value)
            is C.Term.ByteOf -> S.Term.ByteOf(term.value)
            is C.Term.ShortOf -> S.Term.ShortOf(term.value)
            is C.Term.IntOf -> S.Term.IntOf(term.value)
            is C.Term.LongOf -> S.Term.LongOf(term.value)
            is C.Term.FloatOf -> S.Term.FloatOf(term.value)
            is C.Term.DoubleOf -> S.Term.DoubleOf(term.value)
            is C.Term.StringOf -> S.Term.StringOf(term.value)
            is C.Term.ByteArrayOf -> S.Term.ByteArrayOf(term.elements.map(::delaborate))
            is C.Term.IntArrayOf -> S.Term.IntArrayOf(term.elements.map(::delaborate))
            is C.Term.LongArrayOf -> S.Term.LongArrayOf(term.elements.map(::delaborate))
            is C.Term.ListOf -> S.Term.ListOf(term.elements.map(::delaborate))
            is C.Term.CompoundOf -> S.Term.CompoundOf(term.elements.map(::delaborate))
            is C.Term.FunctionOf -> S.Term.FunctionOf(term.parameters, delaborate(term.body))
            is C.Term.Apply -> S.Term.Apply(delaborate(term.function), term.arguments.map(::delaborate))
            is C.Term.Boolean -> S.Term.Boolean()
            is C.Term.Byte -> S.Term.Byte()
            is C.Term.Short -> S.Term.Short()
            is C.Term.Int -> S.Term.Int()
            is C.Term.Long -> S.Term.Long()
            is C.Term.Float -> S.Term.Float()
            is C.Term.Double -> S.Term.Double()
            is C.Term.String -> S.Term.String()
            is C.Term.ByteArray -> S.Term.ByteArray()
            is C.Term.IntArray -> S.Term.IntArray()
            is C.Term.LongArray -> S.Term.LongArray()
            is C.Term.List -> S.Term.List(delaborate(term.element))
            is C.Term.Compound -> S.Term.Compound(term.elements.map(::delaborate))
            is C.Term.Function ->
                S.Term.Function(term.parameters.map { it.first to delaborate(it.second) }, delaborate(term.resultant))
            is C.Term.Type -> S.Term.Type()
        }

        fun List<C.Value?>.pretty(value: C.Value): S.Term = when (value) {
            is C.Value.Hole -> S.Term.Hole()
            is C.Value.Dummy -> S.Term.Dummy()
            is C.Value.Meta -> this[value.index]?.let { pretty(it) } ?: S.Term.Meta(value.index)
            is C.Value.Variable -> S.Term.Variable(value.name)
            is C.Value.BooleanOf -> S.Term.BooleanOf(value.value)
            is C.Value.ByteOf -> S.Term.ByteOf(value.value)
            is C.Value.ShortOf -> S.Term.ShortOf(value.value)
            is C.Value.IntOf -> S.Term.IntOf(value.value)
            is C.Value.LongOf -> S.Term.LongOf(value.value)
            is C.Value.FloatOf -> S.Term.FloatOf(value.value)
            is C.Value.DoubleOf -> S.Term.DoubleOf(value.value)
            is C.Value.StringOf -> S.Term.StringOf(value.value)
            is C.Value.ByteArrayOf -> S.Term.ByteArrayOf(value.elements.map { pretty(it.value) })
            is C.Value.IntArrayOf -> S.Term.IntArrayOf(value.elements.map { pretty(it.value) })
            is C.Value.LongArrayOf -> S.Term.LongArrayOf(value.elements.map { pretty(it.value) })
            is C.Value.ListOf -> S.Term.ListOf(value.elements.map { pretty(it.value) })
            is C.Value.CompoundOf -> S.Term.CompoundOf(value.elements.map { pretty(it.value) })
            is C.Value.FunctionOf -> S.Term.FunctionOf(value.parameters, delaborate(value.body))
            is C.Value.Apply -> S.Term.Apply(pretty(value.function), value.arguments.map { pretty(it.value) })
            is C.Value.Boolean -> S.Term.Boolean()
            is C.Value.Byte -> S.Term.Byte()
            is C.Value.Short -> S.Term.Short()
            is C.Value.Int -> S.Term.Int()
            is C.Value.Long -> S.Term.Long()
            is C.Value.Float -> S.Term.Float()
            is C.Value.Double -> S.Term.Double()
            is C.Value.String -> S.Term.String()
            is C.Value.ByteArray -> S.Term.ByteArray()
            is C.Value.IntArray -> S.Term.IntArray()
            is C.Value.LongArray -> S.Term.LongArray()
            is C.Value.List -> S.Term.List(pretty(value.element.value))
            is C.Value.Compound -> S.Term.Compound(value.elements.map { pretty(it.value) })
            is C.Value.Function ->
                S.Term.Function(
                    value.parameters.map { it.first to pretty(it.second.value) },
                    delaborate(value.resultant)
                )
            is C.Value.Type -> S.Term.Type()
        }
    }
}
