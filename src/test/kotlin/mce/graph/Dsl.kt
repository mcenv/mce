package mce.graph

import mce.graph.Surface.Item
import mce.graph.Surface.Subtyping
import mce.graph.Surface.Term

@Suppress("NonAsciiCharacters")
object Dsl {
    fun definition(name: String, type: Term, body: Term, vararg imports: String) = Item.Definition(name, imports.toList(), type, body)

    fun hole() = Term.Hole()
    fun meta(index: Int) = Term.Meta(index)
    fun name(name: String) = Term.Name(name)
    fun let_in(name: String, init: Term, body: Term) = Term.Let(name, init, body)
    fun ff() = Term.BooleanOf(false)
    fun tt() = Term.BooleanOf(true)
    fun byte_of(value: Byte) = Term.ByteOf(value)
    fun short_of(value: Short) = Term.ShortOf(value)
    fun int_of(value: Int) = Term.IntOf(value)
    fun long_of(value: Long) = Term.LongOf(value)
    fun float_of(value: Float) = Term.FloatOf(value)
    fun double_of(value: Double) = Term.DoubleOf(value)
    fun string_of(value: String) = Term.StringOf(value)
    fun byte_array_of(vararg elements: Term) = Term.ByteArrayOf(elements.toList())
    fun int_array_of(vararg elements: Term) = Term.IntArrayOf(elements.toList())
    fun long_array_of(vararg elements: Term) = Term.LongArrayOf(elements.toList())
    fun list_of(vararg elements: Term) = Term.ListOf(elements.toList())
    fun compound_of(vararg elements: Term) = Term.CompoundOf(elements.toList())
    fun function_of(body: Term, vararg parameters: String) = Term.FunctionOf(parameters.toList(), body)
    operator fun Term.invoke(vararg arguments: Term) = Term.Apply(this, arguments.toList())
    fun union(vararg variants: Term) = Term.Union(variants.toList())
    fun intersection(vararg variants: Term) = Term.Intersection(variants.toList())
    fun boolean() = Term.Boolean()
    fun byte() = Term.Byte()
    fun short() = Term.Short()
    fun int() = Term.Int()
    fun long() = Term.Long()
    fun float() = Term.Float()
    fun double() = Term.Double()
    fun string() = Term.String()
    fun byte_array() = Term.ByteArray()
    fun int_array() = Term.IntArray()
    fun long_array() = Term.LongArray()
    fun list(element: Term) = Term.List(element)
    fun compound(vararg elements: Pair<String, Term>) = Term.Compound(elements.toList())
    fun function(resultant: Term, vararg parameters: Subtyping) = Term.Function(parameters.toList(), resultant)
    fun type() = Term.Type()

    fun end() = union()
    fun any() = intersection()

    infix fun String.`≤`(that: Term): Pair<String, Term> = this to that
    infix fun Pair<String, Term>.`∷`(that: Term): Subtyping = Subtyping(this.first, this.second, that)
}
