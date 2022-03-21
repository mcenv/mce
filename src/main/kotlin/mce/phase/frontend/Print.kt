package mce.phase.frontend

import mce.ast.freshId
import mce.ast.core.Effect as CEffect
import mce.ast.core.Pattern as CPattern
import mce.ast.core.Term as CTerm
import mce.ast.surface.Effect as SEffect
import mce.ast.surface.Entry as SEntry
import mce.ast.surface.Parameter as SParameter
import mce.ast.surface.Pattern as SPattern
import mce.ast.surface.Term as STerm

fun printTerm(term: CTerm): STerm = when (term) {
    is CTerm.Hole -> STerm.Hole(term.id ?: freshId())
    is CTerm.Meta -> STerm.Meta(term.id ?: freshId())
    is CTerm.Var -> STerm.Var(term.name, term.id ?: freshId())
    is CTerm.Def -> STerm.Def(term.name, term.arguments.map { printTerm(it) }, term.id ?: freshId())
    is CTerm.Let -> STerm.Let(term.name, printTerm(term.init), printTerm(term.body), term.id ?: freshId())
    is CTerm.Match -> STerm.Match(printTerm(term.scrutinee), term.clauses.map { printPattern(it.first) to printTerm(it.second) }, term.id ?: freshId())
    is CTerm.UnitOf -> STerm.UnitOf(term.id ?: freshId())
    is CTerm.BoolOf -> STerm.BoolOf(term.value, term.id ?: freshId())
    is CTerm.ByteOf -> STerm.ByteOf(term.value, term.id ?: freshId())
    is CTerm.ShortOf -> STerm.ShortOf(term.value, term.id ?: freshId())
    is CTerm.IntOf -> STerm.IntOf(term.value, term.id ?: freshId())
    is CTerm.LongOf -> STerm.LongOf(term.value, term.id ?: freshId())
    is CTerm.FloatOf -> STerm.FloatOf(term.value, term.id ?: freshId())
    is CTerm.DoubleOf -> STerm.DoubleOf(term.value, term.id ?: freshId())
    is CTerm.StringOf -> STerm.StringOf(term.value, term.id ?: freshId())
    is CTerm.ByteArrayOf -> STerm.ByteArrayOf(term.elements.map { printTerm(it) }, term.id ?: freshId())
    is CTerm.IntArrayOf -> STerm.IntArrayOf(term.elements.map { printTerm(it) }, term.id ?: freshId())
    is CTerm.LongArrayOf -> STerm.LongArrayOf(term.elements.map { printTerm(it) }, term.id ?: freshId())
    is CTerm.ListOf -> STerm.ListOf(term.elements.map { printTerm(it) }, term.id ?: freshId())
    is CTerm.CompoundOf -> STerm.CompoundOf(term.elements.map { (name, element) -> name to printTerm(element) }, term.id ?: freshId())
    is CTerm.BoxOf -> STerm.BoxOf(printTerm(term.content), printTerm(term.tag), term.id ?: freshId())
    is CTerm.RefOf -> STerm.RefOf(printTerm(term.element), term.id ?: freshId())
    is CTerm.Refl -> STerm.Refl(term.id ?: freshId())
    is CTerm.FunOf -> STerm.FunOf(term.parameters, printTerm(term.body), term.id ?: freshId())
    is CTerm.Apply -> STerm.Apply(printTerm(term.function), term.arguments.map { printTerm(it) }, term.id ?: freshId())
    is CTerm.CodeOf -> STerm.CodeOf(printTerm(term.element), term.id ?: freshId())
    is CTerm.Splice -> STerm.Splice(printTerm(term.element), term.id ?: freshId())
    is CTerm.Or -> STerm.Or(term.variants.map { printTerm(it) }, term.id ?: freshId())
    is CTerm.And -> STerm.And(term.variants.map { printTerm(it) }, term.id ?: freshId())
    is CTerm.Unit -> STerm.Unit(term.id ?: freshId())
    is CTerm.Bool -> STerm.Bool(term.id ?: freshId())
    is CTerm.Byte -> STerm.Byte(term.id ?: freshId())
    is CTerm.Short -> STerm.Short(term.id ?: freshId())
    is CTerm.Int -> STerm.Int(term.id ?: freshId())
    is CTerm.Long -> STerm.Long(term.id ?: freshId())
    is CTerm.Float -> STerm.Float(term.id ?: freshId())
    is CTerm.Double -> STerm.Double(term.id ?: freshId())
    is CTerm.String -> STerm.String(term.id ?: freshId())
    is CTerm.ByteArray -> STerm.ByteArray(term.id ?: freshId())
    is CTerm.IntArray -> STerm.IntArray(term.id ?: freshId())
    is CTerm.LongArray -> STerm.LongArray(term.id ?: freshId())
    is CTerm.List -> STerm.List(printTerm(term.element), printTerm(term.size), term.id ?: freshId())
    is CTerm.Compound -> STerm.Compound(term.elements.map { (name, element) -> SEntry(element.relevant, name, printTerm(element.type), element.id ?: freshId()) }, term.id ?: freshId())
    is CTerm.Box -> STerm.Box(printTerm(term.content), term.id ?: freshId())
    is CTerm.Ref -> STerm.Ref(printTerm(term.element), term.id ?: freshId())
    is CTerm.Eq -> STerm.Eq(printTerm(term.left), printTerm(term.right), term.id ?: freshId())
    is CTerm.Fun -> STerm.Fun(
        term.parameters.map { SParameter(it.termRelevant, it.name, it.lower?.let(::printTerm), it.upper?.let(::printTerm), it.typeRelevant, printTerm(it.type), it.id) },
        printTerm(term.resultant),
        term.effects.map(::printEffect),
        term.id ?: freshId()
    )
    is CTerm.Code -> STerm.Code(printTerm(term.element), term.id ?: freshId())
    is CTerm.Type -> STerm.Type(term.id ?: freshId())
}

fun printPattern(pattern: CPattern): SPattern = when (pattern) {
    is CPattern.Var -> SPattern.Var(pattern.name, pattern.id)
    is CPattern.UnitOf -> SPattern.UnitOf(pattern.id)
    is CPattern.BoolOf -> SPattern.BoolOf(pattern.value, pattern.id)
    is CPattern.ByteOf -> SPattern.ByteOf(pattern.value, pattern.id)
    is CPattern.ShortOf -> SPattern.ShortOf(pattern.value, pattern.id)
    is CPattern.IntOf -> SPattern.IntOf(pattern.value, pattern.id)
    is CPattern.LongOf -> SPattern.LongOf(pattern.value, pattern.id)
    is CPattern.FloatOf -> SPattern.FloatOf(pattern.value, pattern.id)
    is CPattern.DoubleOf -> SPattern.DoubleOf(pattern.value, pattern.id)
    is CPattern.StringOf -> SPattern.StringOf(pattern.value, pattern.id)
    is CPattern.ByteArrayOf -> SPattern.ByteArrayOf(pattern.elements.map { printPattern(it) }, pattern.id)
    is CPattern.IntArrayOf -> SPattern.IntArrayOf(pattern.elements.map { printPattern(it) }, pattern.id)
    is CPattern.LongArrayOf -> SPattern.LongArrayOf(pattern.elements.map { printPattern(it) }, pattern.id)
    is CPattern.ListOf -> SPattern.ListOf(pattern.elements.map { printPattern(it) }, pattern.id)
    is CPattern.CompoundOf -> SPattern.CompoundOf(pattern.elements.map { (name, element) -> name to printPattern(element) }, pattern.id)
    is CPattern.BoxOf -> SPattern.BoxOf(printPattern(pattern.content), printPattern(pattern.tag), pattern.id)
    is CPattern.RefOf -> SPattern.RefOf(printPattern(pattern.element), pattern.id)
    is CPattern.Refl -> SPattern.Refl(pattern.id)
    is CPattern.Unit -> SPattern.Unit(pattern.id)
    is CPattern.Bool -> SPattern.Bool(pattern.id)
    is CPattern.Byte -> SPattern.Byte(pattern.id)
    is CPattern.Short -> SPattern.Short(pattern.id)
    is CPattern.Int -> SPattern.Int(pattern.id)
    is CPattern.Long -> SPattern.Long(pattern.id)
    is CPattern.Float -> SPattern.Float(pattern.id)
    is CPattern.Double -> SPattern.Double(pattern.id)
    is CPattern.String -> SPattern.String(pattern.id)
    is CPattern.ByteArray -> SPattern.ByteArray(pattern.id)
    is CPattern.IntArray -> SPattern.IntArray(pattern.id)
    is CPattern.LongArray -> SPattern.LongArray(pattern.id)
}

fun printEffect(effect: CEffect): SEffect = when (effect) {
    is CEffect.Name -> SEffect.Name(effect.name)
}
