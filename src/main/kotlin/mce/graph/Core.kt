package mce.graph

import kotlin.collections.List as KList

class Core(
    val items: KList<Item>
) {
    sealed class Item {
        class Definition(
            val name: String,
            val imports: KList<String>,
            val body: Term,
        ) : Item()
    }

    sealed class Term {
        abstract val type: Value

        class Variable(val level: kotlin.Int, override val type: Value) : Term()

        class BooleanOf(val value: kotlin.Boolean) : Term() {
            override val type: Value = Value.Boolean
        }

        class ByteOf(val value: kotlin.Byte) : Term() {
            override val type: Value = Value.Byte
        }

        class ShortOf(val value: kotlin.Short) : Term() {
            override val type: Value = Value.Short
        }

        class IntOf(val value: kotlin.Int) : Term() {
            override val type: Value = Value.Int
        }

        class LongOf(val value: kotlin.Long) : Term() {
            override val type: Value = Value.Long
        }

        class FloatOf(val value: kotlin.Float) : Term() {
            override val type: Value = Value.Float
        }

        class DoubleOf(val value: kotlin.Double) : Term() {
            override val type: Value = Value.Double
        }

        class StringOf(val value: kotlin.String) : Term() {
            override val type: Value = Value.String
        }

        class ByteArrayOf(val elements: KList<Term>) : Term() {
            override val type: Value = Value.ByteArray
        }

        class IntArrayOf(val elements: KList<Term>) : Term() {
            override val type: Value = Value.IntArray
        }

        class LongArrayOf(val elements: KList<Term>) : Term() {
            override val type: Value = Value.LongArray
        }

        class ListOf(val elements: KList<Term>, override val type: Value) : Term()

        class CompoundOf(val elements: KList<Term>, override val type: Value) : Term()

        class FunctionOf(val parameters: KList<Term>, val body: Term, override val type: Value) : Term()

        class Apply(val function: Term, val arguments: KList<Term>, override val type: Value) : Term()

        class Boolean : Term() {
            override val type: Value = Value.Type
        }

        class Byte : Term() {
            override val type: Value = Value.Type
        }

        class Short : Term() {
            override val type: Value = Value.Type
        }

        class Int : Term() {
            override val type: Value = Value.Type
        }

        class Long : Term() {
            override val type: Value = Value.Type
        }

        class Float : Term() {
            override val type: Value = Value.Type
        }

        class Double : Term() {
            override val type: Value = Value.Type
        }

        class String : Term() {
            override val type: Value = Value.Type
        }

        class ByteArray : Term() {
            override val type: Value = Value.Type
        }

        class IntArray : Term() {
            override val type: Value = Value.Type
        }

        class LongArray : Term() {
            override val type: Value = Value.Type
        }

        class List(val element: Term) : Term() {
            override val type: Value = Value.Type
        }

        class Compound(val elements: KList<Term>) : Term() {
            override val type: Value = Value.Type
        }

        class Function(val parameters: KList<Term>, val resultant: Term) : Term() {
            override val type: Value = Value.Type
        }

        class Type : Term() {
            override val type: Value = Value.Type
        }
    }

    sealed class Value {
        class Variable(val level: kotlin.Int) : Value()
        class BooleanOf(val value: kotlin.Boolean) : Value()
        class ByteOf(val value: kotlin.Byte) : Value()
        class ShortOf(val value: kotlin.Short) : Value()
        class IntOf(val value: kotlin.Int) : Value()
        class LongOf(val value: kotlin.Long) : Value()
        class FloatOf(val value: kotlin.Float) : Value()
        class DoubleOf(val value: kotlin.Double) : Value()
        class StringOf(val value: kotlin.String) : Value()
        class ByteArrayOf(val elements: KList<Lazy<Value>>) : Value()
        class IntArrayOf(val elements: KList<Lazy<Value>>) : Value()
        class LongArrayOf(val elements: KList<Lazy<Value>>) : Value()
        class ListOf(val elements: KList<Lazy<Value>>) : Value()
        class CompoundOf(val elements: KList<Lazy<Value>>) : Value()
        class FunctionOf(val parameters: kotlin.Int, val body: Term) : Value()
        class Apply(val function: Value, val arguments: KList<Lazy<Value>>) : Value()
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
        class List(val element: Lazy<Value>) : Value()
        class Compound(val elements: KList<Lazy<Value>>) : Value()
        class Function(val parameters: KList<Lazy<Value>>, val resultant: Term) : Value()
        object Type : Value()
    }
}
