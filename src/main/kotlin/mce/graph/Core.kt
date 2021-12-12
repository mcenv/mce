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
        abstract val type: Value

        data class Hole(override val type: Value) : Term()

        data class Dummy(override val type: Value) : Term()

        data class Variable(val name: KString, val level: KInt, override val type: Value) : Term()

        data class BooleanOf(val value: KBoolean) : Term() {
            override val type: Value = Value.Boolean
        }

        data class ByteOf(val value: KByte) : Term() {
            override val type: Value = Value.Byte
        }

        data class ShortOf(val value: KShort) : Term() {
            override val type: Value = Value.Short
        }

        data class IntOf(val value: KInt) : Term() {
            override val type: Value = Value.Int
        }

        data class LongOf(val value: KLong) : Term() {
            override val type: Value = Value.Long
        }

        data class FloatOf(val value: KFloat) : Term() {
            override val type: Value = Value.Float
        }

        data class DoubleOf(val value: KDouble) : Term() {
            override val type: Value = Value.Double
        }

        data class StringOf(val value: KString) : Term() {
            override val type: Value = Value.String
        }

        data class ByteArrayOf(val elements: KList<Term>) : Term() {
            override val type: Value = Value.ByteArray
        }

        data class IntArrayOf(val elements: KList<Term>) : Term() {
            override val type: Value = Value.IntArray
        }

        data class LongArrayOf(val elements: KList<Term>) : Term() {
            override val type: Value = Value.LongArray
        }

        data class ListOf(val elements: KList<Term>, override val type: Value) : Term()

        data class CompoundOf(val elements: KList<Term>, override val type: Value) : Term()

        data class FunctionOf(val parameters: KList<KString>, val body: Term, override val type: Value) : Term()

        data class Apply(val function: Term, val arguments: KList<Term>, override val type: Value) : Term()

        object Boolean : Term() {
            override val type: Value = Value.Type
        }

        object Byte : Term() {
            override val type: Value = Value.Type
        }

        object Short : Term() {
            override val type: Value = Value.Type
        }

        object Int : Term() {
            override val type: Value = Value.Type
        }

        object Long : Term() {
            override val type: Value = Value.Type
        }

        object Float : Term() {
            override val type: Value = Value.Type
        }

        object Double : Term() {
            override val type: Value = Value.Type
        }

        object String : Term() {
            override val type: Value = Value.Type
        }

        object ByteArray : Term() {
            override val type: Value = Value.Type
        }

        object IntArray : Term() {
            override val type: Value = Value.Type
        }

        object LongArray : Term() {
            override val type: Value = Value.Type
        }

        class List(val element: Term) : Term() {
            override val type: Value = Value.Type
        }

        class Compound(val elements: KList<Term>) : Term() {
            override val type: Value = Value.Type
        }

        class Function(val parameters: KList<Pair<KString, Term>>, val resultant: Term) : Term() {
            override val type: Value = Value.Type
        }

        object Type : Term() {
            override val type: Value = Value.Type
        }
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
