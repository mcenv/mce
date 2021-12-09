package mce.graph

import java.util.*
import kotlin.collections.List as KList

class Surface(
    val items: KList<Item>
) {
    sealed class Item {
        class Definition(
            val name: String,
            val imports: KList<String>,
            val resultant: Term,
            val body: Term,
        ) : Item()
    }

    sealed class Term {
        val id: UUID = UUID.randomUUID()

        class Variable(val name: kotlin.String) : Term()
        class BooleanOf(val value: kotlin.Boolean) : Term()
        class ByteOf(val value: kotlin.Byte) : Term()
        class ShortOf(val value: kotlin.Short) : Term()
        class IntOf(val value: kotlin.Int) : Term()
        class LongOf(val value: kotlin.Long) : Term()
        class FloatOf(val value: kotlin.Float) : Term()
        class DoubleOf(val value: kotlin.Double) : Term()
        class StringOf(val value: kotlin.String) : Term()
        class ByteArrayOf(val elements: KList<Term>) : Term()
        class IntArrayOf(val elements: KList<Term>) : Term()
        class LongArrayOf(val elements: KList<Term>) : Term()
        class ListOf(val elements: KList<Term>) : Term()
        class CompoundOf(val elements: KList<Term>) : Term()
        class FunctionOf(val parameters: KList<Pair<kotlin.String, Term>>, val body: Term) : Term()
        class Apply(val function: Term, val arguments: KList<Term>) : Term()
        class Boolean : Term()
        class Byte : Term()
        class Short : Term()
        class Int : Term()
        class Long : Term()
        class Float : Term()
        class Double : Term()
        class String : Term()
        class ByteArray : Term()
        class IntArray : Term()
        class LongArray : Term()
        class List(val element: Term) : Term()
        class Compound(val elements: KList<Term>) : Term()
        class Function(val parameters: KList<Pair<kotlin.String, Term>>, val resultant: Term) : Term()
        class Type : Term()
    }
}
