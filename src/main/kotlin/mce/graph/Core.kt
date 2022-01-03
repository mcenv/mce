package mce.graph

import mce.graph.Surface as S
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
        abstract val name: KString
        abstract val imports: KList<KString>

        data class Definition(
            override val name: KString,
            override val imports: KList<KString>,
            val body: S.Term,
            val type: Value
        ) : Item()
    }

    sealed class Value {
        object Hole : Value()
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
        data class ByteArrayOf(val elements: KList<Lazy<Value>>) : Value()
        data class IntArrayOf(val elements: KList<Lazy<Value>>) : Value()
        data class LongArrayOf(val elements: KList<Lazy<Value>>) : Value()
        data class ListOf(val elements: KList<Lazy<Value>>) : Value()
        data class CompoundOf(val elements: KList<Lazy<Value>>) : Value()
        data class FunctionOf(val parameters: KList<KString>, val body: S.Term) : Value()
        data class Apply(val function: Value, val arguments: KList<Lazy<Value>>) : Value()
        data class CodeOf(val element: Lazy<Value>) : Value()
        data class Splice(val element: Lazy<Value>) : Value()
        data class Union(val variants: KList<Lazy<Value>>) : Value()
        data class Intersection(val variants: KList<Lazy<Value>>) : Value()
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
        data class Compound(val elements: KList<Pair<KString, S.Term>>) : Value()
        data class Function(val parameters: KList<S.Subtyping>, val resultant: S.Term) : Value()
        data class Code(val element: Lazy<Value>) : Value()
        object Type : Value()
    }
}
