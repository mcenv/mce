package mce.phase

import mce.graph.Core as C
import mce.graph.Surface as S

class Elaborate {
    operator fun invoke(surface: S): C = C(surface.items.map(::elaborateItem))

    private fun elaborateItem(item: S.Item): C.Item = when (item) {
        is S.Item.Definition -> C.Item.Definition(
            item.name,
            item.imports,
            checkTerm(item.body, emptyList<C.Value>().evaluate(checkTerm(item.type, C.Value.Type)))
        )
    }

    private fun inferTerm(term: S.Term): C.Term = when (term) {
        is S.Term.Variable -> TODO()
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
        is S.Term.ListOf -> if (term.elements.isEmpty()) TODO() else {
            val first = inferTerm(term.elements.first())
            C.Term.ListOf(
                listOf(first) + term.elements.drop(1).map { checkTerm(it, first.type) },
                C.Value.List(lazyOf(first.type))
            )
        }
        is S.Term.CompoundOf -> {
            val elements = term.elements.map(::inferTerm)
            C.Term.CompoundOf(elements, C.Value.Compound(elements.map { lazyOf(it.type) }))
        }
        is S.Term.FunctionOf -> TODO()
        is S.Term.Apply -> TODO()
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
        is S.Term.Function -> TODO()
        is S.Term.Type -> C.Term.Type
    }

    private fun checkTerm(term: S.Term, type: C.Value): C.Term = when {
        term is S.Term.ListOf && type is C.Value.List ->
            C.Term.ListOf(term.elements.map { checkTerm(it, type.element.value) }, type)
        term is S.Term.CompoundOf && type is C.Value.Compound ->
            C.Term.CompoundOf((term.elements zip type.elements).map { checkTerm(it.first, it.second.value) }, type)
        else -> {
            val inferred = inferTerm(term)
            if (inferred.type convertTo type) inferred else TODO()
        }
    }

    private fun List<C.Value>.evaluate(term: C.Term): C.Value = when (term) {
        is C.Term.Variable -> this[term.level]
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
        is C.Term.FunctionOf -> C.Value.Function(term.parameters.map { lazy { evaluate(it) } }, term.body)
        is C.Term.Apply -> when (val function = evaluate(term.function)) {
            is C.Value.FunctionOf -> term.arguments.map { evaluate(it) }.evaluate(function.body)
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
        is C.Term.Function -> C.Value.Function(term.parameters.map { lazy { evaluate(it) } }, term.resultant)
        is C.Term.Type -> C.Value.Type
    }

    private infix fun C.Value.convertTo(that: C.Value): Boolean = when {
        this is C.Value.Variable && that is C.Value.Variable -> this.level == that.level
        this is C.Value.BooleanOf && that is C.Value.BooleanOf -> this.value == that.value
        this is C.Value.ByteOf && that is C.Value.ByteOf -> this.value == that.value
        this is C.Value.ShortOf && that is C.Value.ShortOf -> this.value == that.value
        this is C.Value.IntOf && that is C.Value.IntOf -> this.value == that.value
        this is C.Value.LongOf && that is C.Value.LongOf -> this.value == that.value
        this is C.Value.FloatOf && that is C.Value.FloatOf -> this.value == that.value
        this is C.Value.DoubleOf && that is C.Value.DoubleOf -> this.value == that.value
        this is C.Value.StringOf && that is C.Value.StringOf -> this.value == that.value
        this is C.Value.ByteArrayOf && that is C.Value.ByteArrayOf -> (this.elements zip that.elements).all { it.first.value convertTo it.second.value }
        this is C.Value.IntArrayOf && that is C.Value.IntArrayOf -> (this.elements zip that.elements).all { it.first.value convertTo it.second.value }
        this is C.Value.LongArrayOf && that is C.Value.LongArrayOf -> (this.elements zip that.elements).all { it.first.value convertTo it.second.value }
        this is C.Value.ListOf && that is C.Value.ListOf -> (this.elements zip that.elements).all { it.first.value convertTo it.second.value }
        this is C.Value.CompoundOf && that is C.Value.CompoundOf -> (this.elements zip that.elements).all { it.first.value convertTo it.second.value }
        this is C.Value.FunctionOf && that is C.Value.FunctionOf -> TODO()
        this is C.Value.Apply && that is C.Value.Apply -> this.function convertTo that.function && (this.arguments zip that.arguments).all { it.first.value convertTo it.second.value }
        this is C.Value.Boolean && that is C.Value.Boolean -> true
        this is C.Value.Byte && that is C.Value.Byte -> true
        this is C.Value.Short && that is C.Value.Short -> true
        this is C.Value.Int && that is C.Value.Int -> true
        this is C.Value.Long && that is C.Value.Long -> true
        this is C.Value.Float && that is C.Value.Float -> true
        this is C.Value.Double && that is C.Value.Double -> true
        this is C.Value.String && that is C.Value.String -> true
        this is C.Value.ByteArray && that is C.Value.ByteArray -> true
        this is C.Value.IntArray && that is C.Value.IntArray -> true
        this is C.Value.LongArray && that is C.Value.LongArray -> true
        this is C.Value.List && that is C.Value.List -> this.element.value convertTo that.element.value
        this is C.Value.Compound && that is C.Value.Compound -> (this.elements zip that.elements).all { it.first.value convertTo it.second.value }
        this is C.Value.Function && that is C.Value.Function -> TODO()
        this is C.Value.Type && that is C.Value.Type -> true
        else -> false
    }
}
