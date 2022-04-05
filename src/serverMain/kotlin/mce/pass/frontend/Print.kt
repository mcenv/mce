package mce.pass.frontend

import mce.pass.freshId
import mce.ast.core.Eff as CEff
import mce.ast.core.Pat as CPat
import mce.ast.core.Term as CTerm
import mce.ast.surface.Eff as SEff
import mce.ast.surface.Entry as SEntry
import mce.ast.surface.Param as SParam
import mce.ast.surface.Pat as SPat
import mce.ast.surface.Term as STerm

fun printTerm(term: CTerm): STerm = when (term) {
    is CTerm.Hole -> STerm.Hole(term.id ?: freshId())
    is CTerm.Meta -> STerm.Meta(term.id ?: freshId())
    is CTerm.Block -> STerm.Block(term.elements.map { printTerm(it) }, term.id ?: freshId())
    is CTerm.Var -> STerm.Var(term.name, term.id ?: freshId())
    is CTerm.Def -> STerm.Def(term.name, term.arguments.map { printTerm(it) }, term.id ?: freshId())
    is CTerm.Let -> STerm.Let(term.name, printTerm(term.init), term.id ?: freshId())
    is CTerm.Match -> STerm.Match(printTerm(term.scrutinee), term.clauses.map { printPat(it.first) to printTerm(it.second) }, term.id ?: freshId())
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
    is CTerm.RefOf -> STerm.RefOf(printTerm(term.element), term.id ?: freshId())
    is CTerm.Refl -> STerm.Refl(term.id ?: freshId())
    is CTerm.FunOf -> STerm.FunOf(term.params, printTerm(term.body), term.id ?: freshId())
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
    is CTerm.Ref -> STerm.Ref(printTerm(term.element), term.id ?: freshId())
    is CTerm.Eq -> STerm.Eq(printTerm(term.left), printTerm(term.right), term.id ?: freshId())
    is CTerm.Fun -> STerm.Fun(
        term.params.map { SParam(it.termRelevant, it.name, it.lower?.let(::printTerm), it.upper?.let(::printTerm), it.typeRelevant, printTerm(it.type), it.id) },
        printTerm(term.resultant),
        term.effs.map(::printEff),
        term.id ?: freshId()
    )
    is CTerm.Code -> STerm.Code(printTerm(term.element), term.id ?: freshId())
    is CTerm.Type -> STerm.Type(term.id ?: freshId())
}

fun printPat(pat: CPat): SPat = when (pat) {
    is CPat.Var -> SPat.Var(pat.name, pat.id)
    is CPat.UnitOf -> SPat.UnitOf(pat.id)
    is CPat.BoolOf -> SPat.BoolOf(pat.value, pat.id)
    is CPat.ByteOf -> SPat.ByteOf(pat.value, pat.id)
    is CPat.ShortOf -> SPat.ShortOf(pat.value, pat.id)
    is CPat.IntOf -> SPat.IntOf(pat.value, pat.id)
    is CPat.LongOf -> SPat.LongOf(pat.value, pat.id)
    is CPat.FloatOf -> SPat.FloatOf(pat.value, pat.id)
    is CPat.DoubleOf -> SPat.DoubleOf(pat.value, pat.id)
    is CPat.StringOf -> SPat.StringOf(pat.value, pat.id)
    is CPat.ByteArrayOf -> SPat.ByteArrayOf(pat.elements.map { printPat(it) }, pat.id)
    is CPat.IntArrayOf -> SPat.IntArrayOf(pat.elements.map { printPat(it) }, pat.id)
    is CPat.LongArrayOf -> SPat.LongArrayOf(pat.elements.map { printPat(it) }, pat.id)
    is CPat.ListOf -> SPat.ListOf(pat.elements.map { printPat(it) }, pat.id)
    is CPat.CompoundOf -> SPat.CompoundOf(pat.elements.map { (name, element) -> name to printPat(element) }, pat.id)
    is CPat.RefOf -> SPat.RefOf(printPat(pat.element), pat.id)
    is CPat.Refl -> SPat.Refl(pat.id)
    is CPat.Or -> SPat.Or(pat.variants.map { printPat(it) }, pat.id)
    is CPat.And -> SPat.And(pat.variants.map { printPat(it) }, pat.id)
    is CPat.Unit -> SPat.Unit(pat.id)
    is CPat.Bool -> SPat.Bool(pat.id)
    is CPat.Byte -> SPat.Byte(pat.id)
    is CPat.Short -> SPat.Short(pat.id)
    is CPat.Int -> SPat.Int(pat.id)
    is CPat.Long -> SPat.Long(pat.id)
    is CPat.Float -> SPat.Float(pat.id)
    is CPat.Double -> SPat.Double(pat.id)
    is CPat.String -> SPat.String(pat.id)
    is CPat.ByteArray -> SPat.ByteArray(pat.id)
    is CPat.IntArray -> SPat.IntArray(pat.id)
    is CPat.LongArray -> SPat.LongArray(pat.id)
    is CPat.List -> SPat.List(printPat(pat.element), printPat(pat.size), pat.id)
    is CPat.Compound -> SPat.Compound(pat.elements.map { (name, element) -> name to printPat(element) }, pat.id)
    is CPat.Ref -> SPat.Ref(printPat(pat.element), pat.id)
    is CPat.Eq -> SPat.Eq(printPat(pat.left), printPat(pat.right), pat.id)
    is CPat.Code -> SPat.Code(printPat(pat.element), pat.id)
    is CPat.Type -> SPat.Type(pat.id)
}

fun printEff(eff: CEff): SEff = when (eff) {
    is CEff.Name -> SEff.Name(eff.name)
}
