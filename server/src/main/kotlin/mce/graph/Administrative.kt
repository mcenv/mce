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

object Administrative {
    sealed class Item {
        abstract val imports: KList<KString>
        abstract val name: KString

        data class Definition(
            override val imports: KList<KString>,
            override val name: KString,
            val body: Term
        ) : Item()
    }

    sealed class Term {
        data class Let(val name: KString, val init: Value, val body: Term) : Term()
        data class Match(val scrutinee: Value, val clauses: KList<Pair<Pattern, Term>>) : Term()
        data class Return(val value: Value) : Term()
    }

    sealed class Value {
        data class Meta(val index: KInt) : Value()
        data class Variable(val name: KString, val level: KInt) : Value()
        data class Definition(val name: KString) : Value()
        data class BooleanOf(val value: KBoolean) : Value()
        data class ByteOf(val value: KByte) : Value()
        data class ShortOf(val value: KShort) : Value()
        data class IntOf(val value: KInt) : Value()
        data class LongOf(val value: KLong) : Value()
        data class FloatOf(val value: KFloat) : Value()
        data class DoubleOf(val value: KDouble) : Value()
        data class StringOf(val value: KString) : Value()
        data class ByteArrayOf(val elements: KList<Value>) : Value()
        data class IntArrayOf(val elements: KList<Value>) : Value()
        data class LongArrayOf(val elements: KList<Value>) : Value()
        data class ListOf(val elements: KList<Value>) : Value()
        data class CompoundOf(val elements: KList<Value>) : Value()
        data class ReferenceOf(val element: Value) : Value()
        data class FunctionOf(val parameters: KList<KString>, val body: Value) : Value()
        data class Apply(val function: Value, val arguments: KList<Value>) : Value()
        data class Union(val variants: KList<Value>) : Value()
        data class Intersection(val variants: KList<Value>) : Value()
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
        data class List(val element: Value) : Value()
        data class Compound(val elements: KList<Pair<KString, Value>>) : Value()
        data class Reference(val element: Value) : Value()
        data class Function(val parameters: KList<Value>, val resultant: Value) : Value()
        object Type : Value()
    }

    sealed class Pattern {
        data class Variable(val name: KString) : Pattern()
        data class BooleanOf(val value: KBoolean) : Pattern()
        data class ByteOf(val value: KByte) : Pattern()
        data class ShortOf(val value: KShort) : Pattern()
        data class IntOf(val value: KInt) : Pattern()
        data class LongOf(val value: KLong) : Pattern()
        data class FloatOf(val value: KFloat) : Pattern()
        data class DoubleOf(val value: KDouble) : Pattern()
        data class StringOf(val value: KString) : Pattern()
        data class ByteArrayOf(val elements: KList<Pattern>) : Pattern()
        data class IntArrayOf(val elements: KList<Pattern>) : Pattern()
        data class LongArrayOf(val elements: KList<Pattern>) : Pattern()
        data class ListOf(val elements: KList<Pattern>) : Pattern()
        data class CompoundOf(val elements: KList<Pattern>) : Pattern()
        data class ReferenceOf(val element: Pattern) : Pattern()
    }
}
