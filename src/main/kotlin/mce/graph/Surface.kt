package mce.graph

import java.util.*
import kotlin.Boolean as KBoolean
import kotlin.Byte as KByte
import kotlin.Double as KDouble
import kotlin.Float as KFloat
import kotlin.Int as KInt
import kotlin.Long as KLong
import kotlin.Short as KShort
import kotlin.String as KString
import kotlin.collections.List as KList

object Surface {
    sealed class Item {
        abstract val name: KString
        abstract val imports: KList<KString>

        class Definition(
            override val name: KString,
            override val imports: KList<KString>,
            val type: Term,
            val body: Term,
        ) : Item()
    }

    sealed class Term {
        val id: UUID = UUID.randomUUID()

        class Variable(val name: KString) : Term()
        class BooleanOf(val value: KBoolean) : Term()
        class ByteOf(val value: KByte) : Term()
        class ShortOf(val value: KShort) : Term()
        class IntOf(val value: KInt) : Term()
        class LongOf(val value: KLong) : Term()
        class FloatOf(val value: KFloat) : Term()
        class DoubleOf(val value: KDouble) : Term()
        class StringOf(val value: KString) : Term()
        class ByteArrayOf(val elements: KList<Term>) : Term()
        class IntArrayOf(val elements: KList<Term>) : Term()
        class LongArrayOf(val elements: KList<Term>) : Term()
        class ListOf(val elements: KList<Term>) : Term()
        class CompoundOf(val elements: KList<Term>) : Term()
        class FunctionOf(val parameters: KList<KString>, val body: Term) : Term()
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
        class Function(val parameters: KList<Pair<KString, Term>>, val resultant: Term) : Term()
        class Type : Term()
    }
}
