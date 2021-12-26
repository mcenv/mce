package mce.phase

import mce.graph.Core as C
import mce.graph.Erased as E

class Erase private constructor() {
    private fun eraseItem(item: C.Item): E.Item = when (item) {
        is C.Item.Definition -> E.Item.Definition(item.name, eraseTerm(item.body))
    }

    private fun eraseTerm(term: C.Term): E.Term = when (term) {
        is C.Term.Hole -> TODO()
        is C.Term.Meta -> TODO()
        is C.Term.Variable -> E.Term.Variable(term.name, term.level)
        is C.Term.Definition -> E.Term.Definition(term.name)
        is C.Term.Let -> E.Term.Let(term.name, eraseTerm(term.init), eraseTerm(term.body))
        is C.Term.BooleanOf -> E.Term.BooleanOf(term.value)
        is C.Term.ByteOf -> E.Term.ByteOf(term.value)
        is C.Term.ShortOf -> E.Term.ShortOf(term.value)
        is C.Term.IntOf -> E.Term.IntOf(term.value)
        is C.Term.LongOf -> E.Term.LongOf(term.value)
        is C.Term.FloatOf -> E.Term.FloatOf(term.value)
        is C.Term.DoubleOf -> E.Term.DoubleOf(term.value)
        is C.Term.StringOf -> E.Term.StringOf(term.value)
        is C.Term.ByteArrayOf -> E.Term.ByteArrayOf(term.elements.map { eraseTerm(it) })
        is C.Term.IntArrayOf -> E.Term.IntArrayOf(term.elements.map { eraseTerm(it) })
        is C.Term.LongArrayOf -> E.Term.LongArrayOf(term.elements.map { eraseTerm(it) })
        is C.Term.ListOf -> E.Term.ListOf(term.elements.map { eraseTerm(it) })
        is C.Term.CompoundOf -> E.Term.CompoundOf(term.elements.map { eraseTerm(it) })
        is C.Term.FunctionOf -> E.Term.FunctionOf(term.parameters, eraseTerm(term.body))
        is C.Term.Apply -> E.Term.Apply(eraseTerm(term.function), term.arguments.map { eraseTerm(it) })
        is C.Term.Union -> E.Term.Type
        is C.Term.Intersection -> E.Term.Type
        is C.Term.Boolean -> E.Term.Type
        is C.Term.Byte -> E.Term.Type
        is C.Term.Short -> E.Term.Type
        is C.Term.Int -> E.Term.Type
        is C.Term.Long -> E.Term.Type
        is C.Term.Float -> E.Term.Type
        is C.Term.Double -> E.Term.Type
        is C.Term.String -> E.Term.Type
        is C.Term.ByteArray -> E.Term.Type
        is C.Term.IntArray -> E.Term.Type
        is C.Term.LongArray -> E.Term.Type
        is C.Term.List -> E.Term.Type
        is C.Term.Compound -> E.Term.Type
        is C.Term.Function -> E.Term.Type
        is C.Term.Type -> E.Term.Type
    }

    companion object {
        operator fun invoke(item: C.Item): E.Item = Erase().eraseItem(item)
    }
}
