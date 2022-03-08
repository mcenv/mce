package mce

import mce.graph.Id
import mce.graph.freshId
import mce.graph.Core as C
import mce.graph.Surface as S

sealed class Diagnostic {
    abstract val id: Id

    data class TermExpected(val type: S.Term, override val id: Id) : Diagnostic()
    data class VarNotFound(val name: String, override val id: Id) : Diagnostic()
    data class DefNotFound(val name: String, override val id: Id) : Diagnostic()
    data class ModNotFound(val name: String, override val id: Id) : Diagnostic()
    data class FunctionExpected(override val id: Id) : Diagnostic()
    data class CodeExpected(override val id: Id) : Diagnostic()
    data class SizeMismatch(val expected: Int, val actual: Int, override val id: Id) : Diagnostic()
    data class TermMismatch(val expected: S.Term, val actual: S.Term, override val id: Id) : Diagnostic()
    data class ModuleMismatch(override val id: Id) : Diagnostic()
    data class SignatureMismatch(override val id: Id) : Diagnostic()
    data class PhaseMismatch(override val id: Id) : Diagnostic()
    data class StageMismatch(val expected: Int, val actual: Int, override val id: Id) : Diagnostic()
    data class RelevanceMismatch(override val id: Id) : Diagnostic()
    data class EffectMismatch(val expected: List<S.Effect>, val actual: List<S.Effect>, override val id: Id) : Diagnostic()
    data class PolymorphicRepresentation(override val id: Id) : Diagnostic()
    data class UnsolvedMeta(override val id: Id) : Diagnostic()

    companion object {
        fun serializeTerm(term: C.Term): S.Term = when (term) {
            is C.Term.Hole -> S.Term.Hole(term.id ?: freshId())
            is C.Term.Meta -> S.Term.Meta(term.id ?: freshId())
            is C.Term.Var -> S.Term.Var(term.name, term.id ?: freshId())
            is C.Term.Def -> S.Term.Def(term.name, term.arguments.map { serializeTerm(it) }, term.id ?: freshId())
            is C.Term.Let -> S.Term.Let(term.name, serializeTerm(term.init), serializeTerm(term.body), term.id ?: freshId())
            is C.Term.Match -> S.Term.Match(serializeTerm(term.scrutinee), term.clauses.map { serializePattern(it.first) to serializeTerm(it.second) }, term.id ?: freshId())
            is C.Term.UnitOf -> S.Term.UnitOf(term.id ?: freshId())
            is C.Term.BoolOf -> S.Term.BoolOf(term.value, term.id ?: freshId())
            is C.Term.ByteOf -> S.Term.ByteOf(term.value, term.id ?: freshId())
            is C.Term.ShortOf -> S.Term.ShortOf(term.value, term.id ?: freshId())
            is C.Term.IntOf -> S.Term.IntOf(term.value, term.id ?: freshId())
            is C.Term.LongOf -> S.Term.LongOf(term.value, term.id ?: freshId())
            is C.Term.FloatOf -> S.Term.FloatOf(term.value, term.id ?: freshId())
            is C.Term.DoubleOf -> S.Term.DoubleOf(term.value, term.id ?: freshId())
            is C.Term.StringOf -> S.Term.StringOf(term.value, term.id ?: freshId())
            is C.Term.ByteArrayOf -> S.Term.ByteArrayOf(term.elements.map { serializeTerm(it) }, term.id ?: freshId())
            is C.Term.IntArrayOf -> S.Term.IntArrayOf(term.elements.map { serializeTerm(it) }, term.id ?: freshId())
            is C.Term.LongArrayOf -> S.Term.LongArrayOf(term.elements.map { serializeTerm(it) }, term.id ?: freshId())
            is C.Term.ListOf -> S.Term.ListOf(term.elements.map { serializeTerm(it) }, term.id ?: freshId())
            is C.Term.CompoundOf -> S.Term.CompoundOf(term.elements.map { (name, element) -> name to serializeTerm(element) }, term.id ?: freshId())
            is C.Term.BoxOf -> S.Term.BoxOf(serializeTerm(term.content), serializeTerm(term.tag), term.id ?: freshId())
            is C.Term.RefOf -> S.Term.RefOf(serializeTerm(term.element), term.id ?: freshId())
            is C.Term.Refl -> S.Term.Refl(term.id ?: freshId())
            is C.Term.FunOf -> S.Term.FunOf(term.parameters, serializeTerm(term.body), term.id ?: freshId())
            is C.Term.Apply -> S.Term.Apply(serializeTerm(term.function), term.arguments.map { serializeTerm(it) }, term.id ?: freshId())
            is C.Term.CodeOf -> S.Term.CodeOf(serializeTerm(term.element), term.id ?: freshId())
            is C.Term.Splice -> S.Term.Splice(serializeTerm(term.element), term.id ?: freshId())
            is C.Term.Union -> S.Term.Union(term.variants.map { serializeTerm(it) }, term.id ?: freshId())
            is C.Term.Intersection -> S.Term.Intersection(term.variants.map { serializeTerm(it) }, term.id ?: freshId())
            is C.Term.Unit -> S.Term.Unit(term.id ?: freshId())
            is C.Term.Bool -> S.Term.Bool(term.id ?: freshId())
            is C.Term.Byte -> S.Term.Byte(term.id ?: freshId())
            is C.Term.Short -> S.Term.Short(term.id ?: freshId())
            is C.Term.Int -> S.Term.Int(term.id ?: freshId())
            is C.Term.Long -> S.Term.Long(term.id ?: freshId())
            is C.Term.Float -> S.Term.Float(term.id ?: freshId())
            is C.Term.Double -> S.Term.Double(term.id ?: freshId())
            is C.Term.String -> S.Term.String(term.id ?: freshId())
            is C.Term.ByteArray -> S.Term.ByteArray(term.id ?: freshId())
            is C.Term.IntArray -> S.Term.IntArray(term.id ?: freshId())
            is C.Term.LongArray -> S.Term.LongArray(term.id ?: freshId())
            is C.Term.List -> S.Term.List(serializeTerm(term.element), serializeTerm(term.size), term.id ?: freshId())
            is C.Term.Compound -> S.Term.Compound(term.elements.map { (name, element) -> name to serializeTerm(element) }, term.id ?: freshId())
            is C.Term.Box -> S.Term.Box(serializeTerm(term.content), term.id ?: freshId())
            is C.Term.Ref -> S.Term.Ref(serializeTerm(term.element), term.id ?: freshId())
            is C.Term.Eq -> S.Term.Eq(serializeTerm(term.left), serializeTerm(term.right), term.id ?: freshId())
            is C.Term.Fun -> S.Term.Fun(
                term.parameters.map { S.Parameter(it.relevant, it.name, it.lower?.let(::serializeTerm), it.upper?.let(::serializeTerm), serializeTerm(it.type), it.id) },
                serializeTerm(term.resultant),
                term.effects.map(::serializeEffect),
                term.id ?: freshId()
            )
            is C.Term.Code -> S.Term.Code(serializeTerm(term.element), term.id ?: freshId())
            is C.Term.Type -> S.Term.Type(term.id ?: freshId())
        }

        fun serializePattern(pattern: C.Pattern): S.Pattern = when (pattern) {
            is C.Pattern.Var -> S.Pattern.Var(pattern.name, pattern.id)
            is C.Pattern.UnitOf -> S.Pattern.UnitOf(pattern.id)
            is C.Pattern.BoolOf -> S.Pattern.BoolOf(pattern.value, pattern.id)
            is C.Pattern.ByteOf -> S.Pattern.ByteOf(pattern.value, pattern.id)
            is C.Pattern.ShortOf -> S.Pattern.ShortOf(pattern.value, pattern.id)
            is C.Pattern.IntOf -> S.Pattern.IntOf(pattern.value, pattern.id)
            is C.Pattern.LongOf -> S.Pattern.LongOf(pattern.value, pattern.id)
            is C.Pattern.FloatOf -> S.Pattern.FloatOf(pattern.value, pattern.id)
            is C.Pattern.DoubleOf -> S.Pattern.DoubleOf(pattern.value, pattern.id)
            is C.Pattern.StringOf -> S.Pattern.StringOf(pattern.value, pattern.id)
            is C.Pattern.ByteArrayOf -> S.Pattern.ByteArrayOf(pattern.elements.map { serializePattern(it) }, pattern.id)
            is C.Pattern.IntArrayOf -> S.Pattern.IntArrayOf(pattern.elements.map { serializePattern(it) }, pattern.id)
            is C.Pattern.LongArrayOf -> S.Pattern.LongArrayOf(pattern.elements.map { serializePattern(it) }, pattern.id)
            is C.Pattern.ListOf -> S.Pattern.ListOf(pattern.elements.map { serializePattern(it) }, pattern.id)
            is C.Pattern.CompoundOf -> S.Pattern.CompoundOf(pattern.elements.map { (name, element) -> name to serializePattern(element) }, pattern.id)
            is C.Pattern.BoxOf -> S.Pattern.BoxOf(serializePattern(pattern.content), serializePattern(pattern.tag), pattern.id)
            is C.Pattern.RefOf -> S.Pattern.RefOf(serializePattern(pattern.element), pattern.id)
            is C.Pattern.Refl -> S.Pattern.Refl(pattern.id)
            is C.Pattern.Unit -> S.Pattern.Unit(pattern.id)
            is C.Pattern.Bool -> S.Pattern.Bool(pattern.id)
            is C.Pattern.Byte -> S.Pattern.Byte(pattern.id)
            is C.Pattern.Short -> S.Pattern.Short(pattern.id)
            is C.Pattern.Int -> S.Pattern.Int(pattern.id)
            is C.Pattern.Long -> S.Pattern.Long(pattern.id)
            is C.Pattern.Float -> S.Pattern.Float(pattern.id)
            is C.Pattern.Double -> S.Pattern.Double(pattern.id)
            is C.Pattern.String -> S.Pattern.String(pattern.id)
            is C.Pattern.ByteArray -> S.Pattern.ByteArray(pattern.id)
            is C.Pattern.IntArray -> S.Pattern.IntArray(pattern.id)
            is C.Pattern.LongArray -> S.Pattern.LongArray(pattern.id)
        }

        fun serializeEffect(effect: C.Effect): S.Effect = when (effect) {
            is C.Effect.Name -> S.Effect.Name(effect.name)
        }
    }
}
