package mce.phase

import mce.graph.Core as C

inline fun C.Term.map(transform: (C.Term) -> C.Term): C.Term = when (this) {
    is C.Term.Hole -> C.Term.Hole(id)
    is C.Term.Meta -> C.Term.Meta(index, id)
    is C.Term.Var -> C.Term.Var(name, level, id)
    is C.Term.Def -> C.Term.Def(name, arguments.map(transform), id)
    is C.Term.Let -> C.Term.Let(name, transform(init), transform(body), id)
    is C.Term.Match -> C.Term.Match(transform(scrutinee), clauses.map { it.first to transform(it.second) }, id)
    is C.Term.BoolOf -> C.Term.BoolOf(value, id)
    is C.Term.ByteOf -> C.Term.ByteOf(value, id)
    is C.Term.ShortOf -> C.Term.ShortOf(value, id)
    is C.Term.IntOf -> C.Term.IntOf(value, id)
    is C.Term.LongOf -> C.Term.LongOf(value, id)
    is C.Term.FloatOf -> C.Term.FloatOf(value, id)
    is C.Term.DoubleOf -> C.Term.DoubleOf(value, id)
    is C.Term.StringOf -> C.Term.StringOf(value, id)
    is C.Term.ByteArrayOf -> C.Term.ByteArrayOf(elements.map(transform), id)
    is C.Term.IntArrayOf -> C.Term.IntArrayOf(elements.map(transform), id)
    is C.Term.LongArrayOf -> C.Term.LongArrayOf(elements.map(transform), id)
    is C.Term.ListOf -> C.Term.ListOf(elements.map(transform), id)
    is C.Term.CompoundOf -> C.Term.CompoundOf(elements.map(transform), id)
    is C.Term.BoxOf -> C.Term.BoxOf(transform(content), transform(tag), id)
    is C.Term.RefOf -> C.Term.RefOf(transform(element), id)
    is C.Term.Refl -> C.Term.Refl(id)
    is C.Term.FunOf -> C.Term.FunOf(parameters, transform(body), id)
    is C.Term.Apply -> C.Term.Apply(transform(function), arguments.map(transform), id)
    is C.Term.CodeOf -> C.Term.CodeOf(transform(element), id)
    is C.Term.Splice -> C.Term.Splice(transform(element), id)
    is C.Term.Union -> C.Term.Union(variants.map(transform), id)
    is C.Term.Intersection -> C.Term.Intersection(variants.map(transform), id)
    is C.Term.Bool -> C.Term.Bool(id)
    is C.Term.Byte -> C.Term.Byte(id)
    is C.Term.Short -> C.Term.Short(id)
    is C.Term.Int -> C.Term.Int(id)
    is C.Term.Long -> C.Term.Long(id)
    is C.Term.Float -> C.Term.Float(id)
    is C.Term.Double -> C.Term.Double(id)
    is C.Term.String -> C.Term.String(id)
    is C.Term.ByteArray -> C.Term.ByteArray(id)
    is C.Term.IntArray -> C.Term.IntArray(id)
    is C.Term.LongArray -> C.Term.LongArray(id)
    is C.Term.List -> C.Term.List(transform(element), transform(size), id)
    is C.Term.Compound -> C.Term.Compound(elements.map { it.first to transform(it.second) }, id)
    is C.Term.Box -> C.Term.Box(transform(content), id)
    is C.Term.Ref -> C.Term.Ref(transform(element), id)
    is C.Term.Eq -> C.Term.Eq(transform(left), transform(right), id)
    is C.Term.Fun -> C.Term.Fun(parameters.map { C.Parameter(it.relevant, it.name, it.lower?.let(transform), it.upper?.let(transform), transform(it.type), it.id) }, transform(resultant), effects, id)
    is C.Term.Code -> C.Term.Code(transform(element), id)
    is C.Term.Type -> C.Term.Type(id)
}
