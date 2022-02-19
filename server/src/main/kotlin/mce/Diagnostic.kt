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
    data class CodeExpected(override val id: Id) : Diagnostic()
    data class ArityMismatch(val expected: Int, val actual: Int, override val id: Id) : Diagnostic()
    data class TypeMismatch(val expected: S.Term, val actual: S.Term, override val id: Id) : Diagnostic()
    data class PhaseMismatch(override val id: Id) : Diagnostic()
    data class StageMismatch(val expected: Int, val actual: Int, override val id: Id) : Diagnostic()
    data class EffectMismatch(val expected: List<S.Effect>, val actual: List<S.Effect>, override val id: Id) : Diagnostic()
    data class PolymorphicRepresentation(override val id: Id) : Diagnostic()
    data class UnsolvedMeta(override val id: Id) : Diagnostic()

    companion object {
        fun serializeTerm(term: C.Term): S.Term = when (term) {
            is C.Term.Hole -> S.Term.Hole(freshId())
            is C.Term.Meta -> S.Term.Meta(freshId())
            is C.Term.Var -> S.Term.Name(term.name, freshId())
            is C.Term.Def -> S.Term.Name(term.name, freshId())
            is C.Term.Let -> S.Term.Let(term.name, serializeTerm(term.init), serializeTerm(term.body), freshId())
            is C.Term.Match -> S.Term.Match(serializeTerm(term.scrutinee), term.clauses.map { serializePattern(it.first) to serializeTerm(it.second) }, freshId())
            is C.Term.BoolOf -> S.Term.BoolOf(term.value, freshId())
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
            is C.Term.BoxOf -> S.Term.BoxOf(serializeTerm(term.content), freshId())
            is C.Term.RefOf -> S.Term.RefOf(serializeTerm(term.element), freshId())
            is C.Term.Refl -> S.Term.Refl(freshId())
            is C.Term.FunOf -> S.Term.FunOf(term.parameters, serializeTerm(term.body), freshId())
            is C.Term.Apply -> S.Term.Apply(serializeTerm(term.function), term.arguments.map { serializeTerm(it) }, freshId())
            is C.Term.CodeOf -> S.Term.CodeOf(serializeTerm(term.element), freshId())
            is C.Term.Splice -> S.Term.Splice(serializeTerm(term.element), freshId())
            is C.Term.Union -> S.Term.Union(term.variants.map { serializeTerm(it) }, freshId())
            is C.Term.Intersection -> S.Term.Intersection(term.variants.map { serializeTerm(it) }, freshId())
            is C.Term.Bool -> S.Term.Bool(freshId())
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
            is C.Term.Box -> S.Term.Box(serializeTerm(term.content), freshId())
            is C.Term.Ref -> S.Term.Ref(serializeTerm(term.element), freshId())
            is C.Term.Eq -> S.Term.Eq(serializeTerm(term.left), serializeTerm(term.right), freshId())
            is C.Term.Fun -> S.Term.Fun(
                term.parameters.map { S.Parameter(it.name, it.lower?.let(::serializeTerm), it.upper?.let(::serializeTerm), serializeTerm(it.type)) },
                serializeTerm(term.resultant),
                term.effects.map(::serializeEffect),
                freshId()
            )
            is C.Term.Code -> S.Term.Code(serializeTerm(term.element), freshId())
            is C.Term.Type -> S.Term.Type(freshId())
        }

        fun serializePattern(pattern: C.Pattern): S.Pattern = when (pattern) {
            is C.Pattern.Var -> S.Pattern.Variable(pattern.name, freshId())
            is C.Pattern.BoolOf -> S.Pattern.BoolOf(pattern.value, freshId())
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
            is C.Pattern.BoxOf -> S.Pattern.BoxOf(serializePattern(pattern.content), freshId())
            is C.Pattern.RefOf -> S.Pattern.RefOf(serializePattern(pattern.element), freshId())
            is C.Pattern.Refl -> S.Pattern.Refl(freshId())
        }

        fun serializeEffect(effect: C.Effect): S.Effect = when (effect) {
            is C.Effect.Name -> S.Effect.Name(effect.name)
        }
    }
}
