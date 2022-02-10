package mce

import mce.graph.Id
import mce.graph.freshId
import mce.graph.Core as C
import mce.graph.Surface as S

sealed class Diagnostic {
    abstract val id: Id

    data class TermExpected(val type: S.Term, override val id: Id) : Diagnostic()
    data class NameNotFound(val name: String, override val id: Id) : Diagnostic()
    data class FunctionExpected(override val id: Id) : Diagnostic()
    data class ThunkExpected(override val id: Id) : Diagnostic()
    data class CodeExpected(override val id: Id) : Diagnostic()
    data class TypeMismatch(val expected: S.Term, val actual: S.Term, override val id: Id) : Diagnostic()
    data class PhaseMismatch(override val id: Id) : Diagnostic()
    data class StageMismatch(val expected: Int, val actual: Int, override val id: Id) : Diagnostic()
    data class EffectMismatch(override val id: Id) : Diagnostic()

    companion object {
        fun serializeTerm(term: C.Term): S.Term = when (term) {
            is C.Term.Hole -> S.Term.Hole(freshId())
            is C.Term.Meta -> S.Term.Meta(term.index, freshId())
            is C.Term.Variable -> S.Term.Name(term.name, freshId())
            is C.Term.Definition -> S.Term.Name(term.name, freshId())
            is C.Term.Let -> S.Term.Let(term.name, serializeTerm(term.init), serializeTerm(term.body), freshId())
            is C.Term.Match -> S.Term.Match(serializeTerm(term.scrutinee), term.clauses.map { serializePattern(it.first) to serializeTerm(it.second) }, freshId())
            is C.Term.BooleanOf -> S.Term.BooleanOf(term.value, freshId())
            is C.Term.ByteOf -> S.Term.ByteOf(term.value, freshId())
            is C.Term.ShortOf -> S.Term.ShortOf(term.value, freshId())
            is C.Term.IntOf -> S.Term.IntOf(term.value, freshId())
            is C.Term.LongOf -> S.Term.LongOf(term.value, freshId())
            is C.Term.FloatOf -> S.Term.FloatOf(term.value, freshId())
            is C.Term.DoubleOf -> S.Term.DoubleOf(term.value, freshId())
            is C.Term.StringOf -> S.Term.StringOf(term.value, freshId())
            is C.Term.ByteArrayOf -> S.Term.ByteArrayOf(term.elements.map { serializeTerm(it) }, freshId())
            is C.Term.IntArrayOf -> S.Term.IntArrayOf(term.elements.map { serializeTerm(it) }, freshId())
            is C.Term.LongArrayOf -> S.Term.LongArrayOf(term.elements.map { serializeTerm(it) }, freshId())
            is C.Term.ListOf -> S.Term.ListOf(term.elements.map { serializeTerm(it) }, freshId())
            is C.Term.CompoundOf -> S.Term.CompoundOf(term.elements.map { serializeTerm(it) }, freshId())
            is C.Term.ReferenceOf -> S.Term.ReferenceOf(serializeTerm(term.element), freshId())
            is C.Term.FunctionOf -> S.Term.FunctionOf(term.parameters, serializeTerm(term.body), freshId())
            is C.Term.Apply -> S.Term.Apply(serializeTerm(term.function), term.arguments.map { serializeTerm(it) }, freshId())
            is C.Term.ThunkOf -> S.Term.ThunkOf(serializeTerm(term.body), freshId())
            is C.Term.Force -> S.Term.Force(serializeTerm(term.element), freshId())
            is C.Term.CodeOf -> S.Term.CodeOf(serializeTerm(term.element), freshId())
            is C.Term.Splice -> S.Term.Splice(serializeTerm(term.element), freshId())
            is C.Term.Union -> S.Term.Union(term.variants.map { serializeTerm(it) }, freshId())
            is C.Term.Intersection -> S.Term.Intersection(term.variants.map { serializeTerm(it) }, freshId())
            is C.Term.Boolean -> S.Term.Boolean(freshId())
            is C.Term.Byte -> S.Term.Byte(freshId())
            is C.Term.Short -> S.Term.Short(freshId())
            is C.Term.Int -> S.Term.Int(freshId())
            is C.Term.Long -> S.Term.Long(freshId())
            is C.Term.Float -> S.Term.Float(freshId())
            is C.Term.Double -> S.Term.Double(freshId())
            is C.Term.String -> S.Term.String(freshId())
            is C.Term.ByteArray -> S.Term.ByteArray(freshId())
            is C.Term.IntArray -> S.Term.IntArray(freshId())
            is C.Term.LongArray -> S.Term.LongArray(freshId())
            is C.Term.List -> S.Term.List(serializeTerm(term.element), freshId())
            is C.Term.Compound -> S.Term.Compound(term.elements.map { it.first to serializeTerm(it.second) }, freshId())
            is C.Term.Reference -> S.Term.Reference(serializeTerm(term.element), freshId())
            is C.Term.Function -> S.Term.Function(term.parameters.map { S.Parameter(it.name, serializeTerm(it.lower), serializeTerm(it.upper), serializeTerm(it.type)) }, serializeTerm(term.resultant), freshId())
            is C.Term.Thunk -> S.Term.Thunk(serializeTerm(term.element), serializeEffects(term.effects), freshId())
            is C.Term.Code -> S.Term.Code(serializeTerm(term.element), freshId())
            is C.Term.Type -> S.Term.Type(freshId())
        }

        fun serializePattern(pattern: C.Pattern): S.Pattern = when (pattern) {
            is C.Pattern.Variable -> S.Pattern.Variable(pattern.name, freshId())
            is C.Pattern.BooleanOf -> S.Pattern.BooleanOf(pattern.value, freshId())
            is C.Pattern.ByteOf -> S.Pattern.ByteOf(pattern.value, freshId())
            is C.Pattern.ShortOf -> S.Pattern.ShortOf(pattern.value, freshId())
            is C.Pattern.IntOf -> S.Pattern.IntOf(pattern.value, freshId())
            is C.Pattern.LongOf -> S.Pattern.LongOf(pattern.value, freshId())
            is C.Pattern.FloatOf -> S.Pattern.FloatOf(pattern.value, freshId())
            is C.Pattern.DoubleOf -> S.Pattern.DoubleOf(pattern.value, freshId())
            is C.Pattern.StringOf -> S.Pattern.StringOf(pattern.value, freshId())
            is C.Pattern.ByteArrayOf -> S.Pattern.ByteArrayOf(pattern.elements.map { serializePattern(it) }, freshId())
            is C.Pattern.IntArrayOf -> S.Pattern.IntArrayOf(pattern.elements.map { serializePattern(it) }, freshId())
            is C.Pattern.LongArrayOf -> S.Pattern.LongArrayOf(pattern.elements.map { serializePattern(it) }, freshId())
            is C.Pattern.ListOf -> S.Pattern.ListOf(pattern.elements.map { serializePattern(it) }, freshId())
            is C.Pattern.CompoundOf -> S.Pattern.CompoundOf(pattern.elements.map { serializePattern(it) }, freshId())
            is C.Pattern.ReferenceOf -> S.Pattern.ReferenceOf(serializePattern(pattern.element), freshId())
        }

        fun serializeEffect(effect: C.Effect): S.Effect = TODO()

        fun serializeEffects(effects: C.Effects): S.Effects = when (effects) {
            is C.Effects.Any -> S.Effects.Any
            is C.Effects.Set -> S.Effects.Set(effects.effects.map { serializeEffect(it) })
        }
    }
}
