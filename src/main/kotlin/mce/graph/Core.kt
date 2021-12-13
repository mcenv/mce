package mce.graph

import kotlin.Boolean as KBoolean
import kotlin.Byte as KByte
import kotlin.Double as KDouble
import kotlin.Float as KFloat
import kotlin.Int as KInt
import kotlin.Long as KLong
import kotlin.Short as KShort
import kotlin.String as KString
import kotlin.collections.List as KList

object Core {
    sealed class Item {
        data class Definition(
            val name: KString,
            val imports: KList<KString>,
            val body: Term,
        ) : Item()
    }

    sealed class Term {
        object Hole : Term()
        object Dummy : Term()
        data class Meta(val index: KInt) : Term()
        data class Variable(val name: KString, val level: KInt) : Term()
        data class BooleanOf(val value: KBoolean) : Term()
        data class ByteOf(val value: KByte) : Term()
        data class ShortOf(val value: KShort) : Term()
        data class IntOf(val value: KInt) : Term()
        data class LongOf(val value: KLong) : Term()
        data class FloatOf(val value: KFloat) : Term()
        data class DoubleOf(val value: KDouble) : Term()
        data class StringOf(val value: KString) : Term()
        data class ByteArrayOf(val elements: KList<Term>) : Term()
        data class IntArrayOf(val elements: KList<Term>) : Term()
        data class LongArrayOf(val elements: KList<Term>) : Term()
        data class ListOf(val elements: KList<Term>) : Term()
        data class CompoundOf(val elements: KList<Term>) : Term()
        data class FunctionOf(val parameters: KList<KString>, val body: Term) : Term()
        data class Apply(val function: Term, val arguments: KList<Term>) : Term()
        object Boolean : Term()
        object Byte : Term()
        object Short : Term()
        object Int : Term()
        object Long : Term()
        object Float : Term()
        object Double : Term()
        object String : Term()
        object ByteArray : Term()
        object IntArray : Term()
        object LongArray : Term()
        data class List(val element: Term) : Term()
        data class Compound(val elements: KList<Term>) : Term()
        data class Function(val parameters: KList<Pair<KString, Term>>, val resultant: Term) : Term()
        object Type : Term()
    }

    sealed class Value {
        object Hole : Value()
        object Dummy : Value()
        data class Meta(val index: KInt) : Value()
        data class Variable(val name: KString, val level: KInt) : Value()
        data class BooleanOf(val value: KBoolean) : Value()
        data class ByteOf(val value: KByte) : Value()
        data class ShortOf(val value: KShort) : Value()
        data class IntOf(val value: KInt) : Value()
        data class LongOf(val value: KLong) : Value()
        data class FloatOf(val value: KFloat) : Value()
        data class DoubleOf(val value: KDouble) : Value()
        data class StringOf(val value: KString) : Value()
        data class ByteArrayOf(val elements: KList<Lazy<Value>>) : Value()
        data class IntArrayOf(val elements: KList<Lazy<Value>>) : Value()
        data class LongArrayOf(val elements: KList<Lazy<Value>>) : Value()
        data class ListOf(val elements: KList<Lazy<Value>>) : Value()
        data class CompoundOf(val elements: KList<Lazy<Value>>) : Value()
        data class FunctionOf(val parameters: KList<KString>, val body: Term) : Value()
        data class Apply(val function: Value, val arguments: KList<Lazy<Value>>) : Value()
        object Boolean : Value()
        object Byte : Value()
        object Short : Value()
        object Int : Value()
        object Long : Value()
        object Float : Value()
        object Double : Value()
        object String : Value()
        object ByteArray : Value()
        object IntArray : Value()
        object LongArray : Value()
        data class List(val element: Lazy<Value>) : Value()
        data class Compound(val elements: KList<Lazy<Value>>) : Value()
        data class Function(val parameters: KList<Pair<KString, Lazy<Value>>>, val resultant: Term) : Value()
        object Type : Value()
    }
}
