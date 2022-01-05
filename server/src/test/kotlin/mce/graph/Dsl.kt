package mce.graph

import mce.graph.Surface.Item
import mce.graph.Surface.Subtyping
import mce.graph.Surface.Term

object Dsl {
    fun definition(name: String, type: Term, body: Term, vararg imports: String) = Item.Definition(name, imports.toList(), type, body)

    fun hole() = Term.Hole(freshId())
    fun meta(index: Int) = Term.Meta(index, freshId())
    fun variable(name: String, level: Int) = Term.Variable(name, level, freshId())
    fun definition(name: String) = Term.Definition(name, freshId())
    fun let_in(name: String, init: Term, body: Term) = Term.Let(name, init, body, freshId())
    fun ff() = Term.BooleanOf(false, freshId())
    fun tt() = Term.BooleanOf(true, freshId())
    fun byte_of(value: Byte) = Term.ByteOf(value, freshId())
    fun short_of(value: Short) = Term.ShortOf(value, freshId())
    fun int_of(value: Int) = Term.IntOf(value, freshId())
    fun long_of(value: Long) = Term.LongOf(value, freshId())
    fun float_of(value: Float) = Term.FloatOf(value, freshId())
    fun double_of(value: Double) = Term.DoubleOf(value, freshId())
    fun string_of(value: String) = Term.StringOf(value, freshId())
    fun byte_array_of(vararg elements: Term) = Term.ByteArrayOf(elements.toList(), freshId())
    fun int_array_of(vararg elements: Term) = Term.IntArrayOf(elements.toList(), freshId())
    fun long_array_of(vararg elements: Term) = Term.LongArrayOf(elements.toList(), freshId())
    fun list_of(vararg elements: Term) = Term.ListOf(elements.toList(), freshId())
    fun compound_of(vararg elements: Term) = Term.CompoundOf(elements.toList(), freshId())
    fun function_of(body: Term, vararg parameters: String) = Term.FunctionOf(parameters.toList(), body, freshId())
    operator fun Term.invoke(vararg arguments: Term) = Term.Apply(this, arguments.toList(), freshId())
    fun code_of(element: Term) = Term.CodeOf(element, freshId())
    operator fun Term.not() = Term.Splice(this, freshId())
    fun union(vararg variants: Term) = Term.Union(variants.toList(), freshId())
    fun intersection(vararg variants: Term) = Term.Intersection(variants.toList(), freshId())
    fun boolean() = Term.Boolean(freshId())
    fun byte() = Term.Byte(freshId())
    fun short() = Term.Short(freshId())
    fun int() = Term.Int(freshId())
    fun long() = Term.Long(freshId())
    fun float() = Term.Float(freshId())
    fun double() = Term.Double(freshId())
    fun string() = Term.String(freshId())
    fun byte_array() = Term.ByteArray(freshId())
    fun int_array() = Term.IntArray(freshId())
    fun long_array() = Term.LongArray(freshId())
    fun list(element: Term) = Term.List(element, freshId())
    fun compound(vararg elements: Pair<String, Term>) = Term.Compound(elements.toList(), freshId())
    fun function(resultant: Term, vararg parameters: Subtyping) = Term.Function(parameters.toList(), resultant, freshId())
    fun code(element: Term) = Term.Code(element, freshId())
    fun type() = Term.Type(freshId())

    fun end() = union()
    fun any() = intersection()

    fun subtyping(name: String, lower: Term, upper: Term, type: Term): Subtyping = Subtyping(name, lower, upper, type)
}
